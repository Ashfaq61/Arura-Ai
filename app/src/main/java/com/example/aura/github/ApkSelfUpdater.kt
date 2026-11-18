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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

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
                @Suppress("DEPRECATION")
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
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

    fun installApk(apkFile: File): ApkInstallResult {
        val verified = verifyApkPackageName(apkFile)
        if (verified.isFailure) {
            runCatching { apkFile.delete() }
            return ApkInstallResult.Failed(
                verified.exceptionOrNull()?.message ?: "APK package verification failed."
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

    companion object {
        private const val ACTION_INSTALL_COMPLETE = "com.example.aura.ACTION_APK_INSTALL_COMPLETE"

        private val _installFeedback = MutableStateFlow<PackageInstallFeedback>(PackageInstallFeedback.Idle)
        val installFeedback: StateFlow<PackageInstallFeedback> = _installFeedback.asStateFlow()

        internal fun updateInstallFeedback(feedback: PackageInstallFeedback) {
            _installFeedback.value = feedback
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
