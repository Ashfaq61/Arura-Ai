package com.example.aura.calls

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder

enum class CallStateLabel(val label: String) {
    IDLE("Idle"),
    RINGING("Incoming Call Ringing"),
    OFFHOOK("Call Active / Dialing"),
    UNAVAILABLE("Permission Required")
}

class PhoneCallAndMessagingManager(private val context: Context) {

    private val _callState = MutableStateFlow(CallStateLabel.IDLE)
    val callState: StateFlow<CallStateLabel> = _callState.asStateFlow()

    private val _lastCallerInfo = MutableStateFlow("No active call detected.")
    val lastCallerInfo: StateFlow<String> = _lastCallerInfo.asStateFlow()

    private val _consentToTranscribe = MutableStateFlow(false)
    val consentToTranscribe: StateFlow<Boolean> = _consentToTranscribe.asStateFlow()

    fun setTranscribeConsent(granted: Boolean) {
        _consentToTranscribe.value = granted
    }

    fun hasCallPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    fun hasReadPhoneStatePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    fun isDefaultDialer(): Boolean {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        return tm?.defaultDialerPackage == context.packageName
    }

    fun buildRequestDefaultDialerIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_DIALER) && !rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
                rm.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            } else null
        } else {
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
            }
        }
    }

    fun startMonitoringCallState() {
        if (!hasReadPhoneStatePermission()) {
            _callState.value = CallStateLabel.UNAVAILABLE
            return
        }
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephony.registerTelephonyCallback(
                    context.mainExecutor,
                    object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) {
                            updateCallState(state, null)
                        }
                    }
                )
            } else {
                @Suppress("DEPRECATION")
                telephony.listen(object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        updateCallState(state, phoneNumber)
                    }
                }, PhoneStateListener.LISTEN_CALL_STATE)
            }
        }
    }

    private fun updateCallState(state: Int, number: String?) {
        _callState.value = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                _lastCallerInfo.value = "Incoming call${if (!number.isNullOrBlank()) " from $number" else ""}"
                CallStateLabel.RINGING
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                _lastCallerInfo.value = "Call in progress${if (!number.isNullOrBlank()) " ($number)" else ""}"
                CallStateLabel.OFFHOOK
            }
            else -> {
                _lastCallerInfo.value = "Call state: Idle"
                CallStateLabel.IDLE
            }
        }
    }

    fun openDialer(phoneNumber: String): Pair<Boolean, String> {
        val cleaned = phoneNumber.trim()
        if (cleaned.isBlank()) return false to "Phone number is empty."
        return runCatching {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleaned")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true to "Opened phone dialer for $cleaned."
        }.getOrElse { false to "Dialer failed: ${it.localizedMessage}" }
    }

    fun placeDirectCall(phoneNumber: String): Pair<Boolean, String> {
        if (!hasCallPhonePermission()) {
            return openDialer(phoneNumber).let { (ok, _) ->
                ok to "CALL_PHONE not granted; opened dialer safely for $phoneNumber."
            }
        }
        return runCatching {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${phoneNumber.trim()}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true to "Initiated call to $phoneNumber."
        }.getOrElse { false to "Call failed: ${it.localizedMessage}" }
    }

    fun openWhatsAppDraft(phoneOrEmpty: String, message: String): Pair<Boolean, String> {
        val encodedText = URLEncoder.encode(message, "UTF-8")
        val digits = phoneOrEmpty.filter { it.isDigit() }
        val url = if (digits.isNotBlank()) {
            "https://wa.me/$digits?text=$encodedText"
        } else {
            "https://wa.me/?text=$encodedText"
        }
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true to "Opened WhatsApp draft composer."
        }.getOrElse {
            // Fallback to generic share sheet
            runCatching {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(share, "Send message").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true to "WhatsApp not installed; opened Android Share sheet."
            }.getOrElse { err ->
                false to "Could not open message composer: ${err.localizedMessage}"
            }
        }
    }

    fun carrierCapabilitiesSummary(): String =
        "Android Telecom monitoring & confirmed outgoing dial/call are active. " +
            "Note: Programmatic two-way SIM call audio injection/recording is restricted by Android OS and carrier hardware unless held as system/default dialer on supported OEM firmware."
}
