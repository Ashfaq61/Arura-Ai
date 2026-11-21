package com.example.aura.github

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.MessageDigest

data class ApkIntegrityReport(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val fileSha256: String,
    val signingCertSha256: String
)

sealed interface ApkInstallResult {
    data class SessionStarted(val message: String) : ApkInstallResult
    data class NeedsUnknownSourcesPermission(val message: String) : ApkInstallResult
    data class Failed(val error: String) : ApkInstallResult
}

sealed interface PackageInstallFeedback {
    data object Idle : PackageInstallFeedback
    data class Succeeded(val message: String) : PackageInstallFeedback
    data class Failed(val message: String) : PackageInstallFeedback
}

class ApkInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirmIntent) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                ApkSelfUpdater.updateInstallFeedback(
                    PackageInstallFeedback.Succeeded("Update installed successfully.")
                )
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                ApkSelfUpdater.updateInstallFeedback(
                    PackageInstallFeedback.Failed("Install cancelled by user.")
                )
            }
            else -> {
                ApkSelfUpdater.updateInstallFeedback(
                    PackageInstallFeedback.Failed(
                        "PackageInstaller failed (status=$status)${if (message.isNotBlank()) ": $message" else ""}"
                    )
                )
            }
        }
    }
}

class ApkSelfUpdater(private val context: Context) {

    fun verifyApkIntegrity(
        apkFile: File,
        expectedSha256: String? = null,
        allowDowngradeOrSame: Boolean = false
    ): Result<ApkIntegrityReport> {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return Result.failure(IllegalArgumentException("Downloaded APK file is missing or empty."))
        }

        val actualFileSha256 = computeSha256Hex(apkFile)
        val cleanExpected = expectedSha256?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        if (cleanExpected != null && !actualFileSha256.equals(cleanExpected, ignoreCase = true)) {
            return Result.failure(
                SecurityException(
                    "APK SHA-256 integrity mismatch: expected $cleanExpected, got $actualFileSha256. Install aborted."
                )
            )
        }

        val pm = context.packageManager
        val signingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val archiveInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(signingFlags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, signingFlags)
            }
        }.getOrNull() ?: return Result.failure(
            IllegalStateException("Invalid APK package archive: could not read package manifest.")
        )

        val expectedPackage = context.packageName
        if (archiveInfo.packageName != expectedPackage) {
            return Result.failure(
                SecurityException(
                    "Package name mismatch: APK is '${archiveInfo.packageName}' but installed app is '$expectedPackage'. Install aborted."
                )
            )
        }

        val archiveVersionCode = extractVersionCode(archiveInfo)
        val (_, installedVersionCode) = ApkVersionComparator.getInstalledVersionInfo(context)
        if (!allowDowngradeOrSame && archiveVersionCode <= installedVersionCode) {
            return Result.failure(
                SecurityException(
                    "APK versionCode check failed: downloaded build ($archiveVersionCode) is not newer than installed build ($installedVersionCode)."
                )
            )
        }

        val archiveSigners = extractSigningCertSha256Set(archiveInfo)
        val installedInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(expectedPackage, PackageManager.PackageInfoFlags.of(signingFlags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(expectedPackage, signingFlags)
            }
        }.getOrNull()
        val installedSigners = if (installedInfo != null) extractSigningCertSha256Set(installedInfo) else emptySet()

        if (installedSigners.isNotEmpty()) {
            if (archiveSigners.isEmpty()) {
                return Result.failure(
                    SecurityException("APK signing certificate missing or unsigned; refused to replace signed app.")
                )
            }
            if (archiveSigners.intersect(installedSigners).isEmpty()) {
                return Result.failure(
                    SecurityException(
                        "Signing certificate mismatch: downloaded APK signer (${archiveSigners.first()}) does not match installed app signer (${installedSigners.first()})."
                    )
                )
            }
        }

        return Result.success(
            ApkIntegrityReport(
                packageName = archiveInfo.packageName,
                versionCode = archiveVersionCode,
                versionName = archiveInfo.versionName ?: "unknown",
                fileSha256 = actualFileSha256,
                signingCertSha256 = archiveSigners.firstOrNull() ?: "UNAVAILABLE_IN_HOST"
            )
        )
    }

    fun verifyApkPackageName(apkFile: File): Result<PackageInfo> {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return Result.failure(IllegalArgumentException("Downloaded APK file is missing or empty."))
        }
        val pm = context.packageManager
        val archiveInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            }
        }.getOrNull() ?: return Result.failure(
            IllegalStateException("Invalid APK package archive: could not read package manifest.")
        )

        val expectedPackage = context.packageName
        if (archiveInfo.packageName != expectedPackage) {
            return Result.failure(
                SecurityException(
                    "Package name mismatch: APK is '${archiveInfo.packageName}' but installed app is '$expectedPackage'. Install aborted."
                )
            )
        }
        return Result.success(archiveInfo)
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
        } else {
            true
        }
    }

    fun openUnknownSourcesSettings(): Boolean {
        return runCatching {
            val intent = createUnknownSourcesIntent(context)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun installApk(
        apkFile: File,
        expectedSha256: String? = null,
        allowDowngradeOrSame: Boolean = false
    ): ApkInstallResult {
        val verified = verifyApkIntegrity(
            apkFile = apkFile,
            expectedSha256 = expectedSha256,
            allowDowngradeOrSame = allowDowngradeOrSame
        )
        if (verified.isFailure) {
            runCatching { apkFile.delete() }
            return ApkInstallResult.Failed(
                verified.exceptionOrNull()?.message ?: "APK integrity verification failed."
            )
        }

        if (!canRequestPackageInstalls()) {
            return ApkInstallResult.NeedsUnknownSourcesPermission(
                "Please enable 'Allow from this source' in Android Settings so Aura AI can install updates."
            )
        }

        val sessionOutcome = runCatching {
            commitPackageInstallerSession(apkFile)
        }
        if (sessionOutcome.isSuccess) {
            return ApkInstallResult.SessionStarted("Installer prompt launched for ${apkFile.name}.")
        }

        // Fallback to FileProvider ACTION_VIEW if PackageInstaller session fails on an OEM ROM
        val fileProviderOutcome = runCatching {
            launchFileProviderInstaller(apkFile)
        }
        return fileProviderOutcome.fold(
            onSuccess = {
                ApkInstallResult.SessionStarted("FileProvider installer launched for ${apkFile.name}.")
            },
            onFailure = { err ->
                ApkInstallResult.Failed(
                    "Could not start installer: ${err.localizedMessage ?: sessionOutcome.exceptionOrNull()?.localizedMessage}"
                )
            }
        )
    }

    private fun commitPackageInstallerSession(apkFile: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apkFile.length())
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            session.openWrite("aura_update.apk", 0, apkFile.length()).use { out ->
                apkFile.inputStream().use { input ->
                    input.copyTo(out)
                }
                session.fsync(out)
            }
            val callbackIntent = Intent(context, ApkInstallStatusReceiver::class.java).apply {
                action = ACTION_INSTALL_COMPLETE
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, callbackIntent, flags)
            session.commit(pendingIntent.intentSender)
        } catch (e: Exception) {
            runCatching { session.abandon() }
            throw e
        } finally {
            runCatching { session.close() }
        }
    }

    private fun launchFileProviderInstaller(apkFile: File) {
        val authority = "${context.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(context, authority, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun extractVersionCode(pkgInfo: PackageInfo): Long {
        return PackageInfoCompat.getLongVersionCode(pkgInfo)
    }

    @Suppress("DEPRECATION")
    private fun extractSigningCertSha256Set(pkgInfo: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pkgInfo.signingInfo
            if (info != null) {
                if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
            } else {
                pkgInfo.signatures
            }
        } else {
            pkgInfo.signatures
        } ?: return emptySet()

        val md = MessageDigest.getInstance("SHA-256")
        return signatures.mapNotNull { sig ->
            val bytes = sig?.toByteArray() ?: return@mapNotNull null
            md.reset()
            md.digest(bytes).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    companion object {
        private const val ACTION_INSTALL_COMPLETE = "com.example.aura.ACTION_APK_INSTALL_COMPLETE"

        private val _installFeedback = MutableStateFlow<PackageInstallFeedback>(PackageInstallFeedback.Idle)
        val installFeedback: StateFlow<PackageInstallFeedback> = _installFeedback.asStateFlow()

        internal fun updateInstallFeedback(feedback: PackageInstallFeedback) {
            _installFeedback.value = feedback
        }

        fun computeSha256Hex(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    md.update(buf, 0, read)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        fun createUnknownSourcesIntent(context: Context): Intent {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
