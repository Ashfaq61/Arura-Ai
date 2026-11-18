package com.example.aura.offline

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import com.example.aura.automation.DeviceControlEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class OfflineEmergencyState(
    val active: Boolean = false,
    val torchEnabled: Boolean = false,
    val torchSupported: Boolean = false,
    val sosContactNumber: String = "112",
    val lastUtilityOutput: String = "Offline Emergency Engine ready — works with zero internet."
)

/**
 * Provides zero-internet deterministic execution: hardware flashlight toggle,
 * arithmetic & unit conversion, local system telemetry, saved emergency notes,
 * and confirmed SOS dialer launch.
 */
class OfflineEmergencyEngine(
    private val context: Context,
    private val deviceControl: DeviceControlEngine
) {
    private val _state = MutableStateFlow(
        OfflineEmergencyState(
            torchSupported = discoverFlashCameraId() != null
        )
    )
    val state: StateFlow<OfflineEmergencyState> = _state.asStateFlow()

    fun setEmergencyModeActive(enabled: Boolean) {
        _state.value = _state.value.copy(
            active = enabled,
            lastUtilityOutput = if (enabled) {
                "Offline Emergency Mode ACTIVE. Cloud APIs bypassed; local deterministic tools prioritized."
            } else {
                "Offline Emergency Mode deactivated."
            }
        )
    }

    fun setSosContactNumber(number: String) {
        val clean = number.filter { it.isDigit() || it == '+' }.ifBlank { "112" }
        _state.value = _state.value.copy(sosContactNumber = clean)
    }

    fun toggleFlashlight(turnOn: Boolean = !_state.value.torchEnabled): Pair<Boolean, String> {
        val camId = discoverFlashCameraId()
        if (camId == null) {
            val msg = "No camera flash hardware detected on this device/environment."
            _state.value = _state.value.copy(torchEnabled = false, torchSupported = false, lastUtilityOutput = msg)
            return false to msg
        }
        return runCatching {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cm.setTorchMode(camId, turnOn)
            }
            val msg = if (turnOn) "Flashlight turned ON (offline)." else "Flashlight turned OFF."
            _state.value = _state.value.copy(torchEnabled = turnOn, torchSupported = true, lastUtilityOutput = msg)
            true to msg
        }.getOrElse { err ->
            val msg = "Flashlight control failed: ${err.localizedMessage ?: "hardware unavailable"}"
            _state.value = _state.value.copy(lastUtilityOutput = msg)
            false to msg
        }
    }

    fun openSosDialer(customNumber: String = _state.value.sosContactNumber): Pair<Boolean, String> {
        val clean = customNumber.filter { it.isDigit() || it == '+' }.ifBlank { "112" }
        return runCatching {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val msg = "Opened SOS dialer for $clean (tap call to confirm)."
            _state.value = _state.value.copy(lastUtilityOutput = msg)
            true to msg
        }.getOrElse { err ->
            val msg = "Failed to open dialer: ${err.localizedMessage}"
            _state.value = _state.value.copy(lastUtilityOutput = msg)
            false to msg
        }
    }

    fun runOfflineCommand(input: String): String {
        val raw = input.trim()
        val lower = raw.lowercase(Locale.US)
        val result = when {
            lower.contains("flashlight on") || lower.contains("torch on") -> toggleFlashlight(true).second
            lower.contains("flashlight off") || lower.contains("torch off") -> toggleFlashlight(false).second
            lower.contains("battery") || lower.contains("ram") || lower.contains("storage") || lower.contains("health") -> {
                val h = deviceControl.readDeviceHealth()
                "Offline Health: Battery ${h.batteryPercent}% (${if (h.isCharging) "Charging" else "Discharging"}), RAM ${h.availableRamMb}/${h.totalRamMb}MB, Free Storage ${"%.2f".format(h.freeStorageGb)}GB, Volume ${h.mediaVolumePercent}%."
            }
            lower.startsWith("convert ") -> convertUnits(raw.removePrefix("convert ").removePrefix("Convert ").trim())
            looksLikeMath(lower) -> evaluateMath(raw)
            else -> {
                val mathAttempt = evaluateMath(raw)
                if (!mathAttempt.startsWith("Unable")) mathAttempt
                else "Offline Mode processed locally: \"$raw\". Supported offline tools: arithmetic (e.g. (12+8)*4), unit convert (e.g. convert 10 km to mi), flashlight on/off, battery/RAM check, volume +/- , and saved notes."
            }
        }
        _state.value = _state.value.copy(lastUtilityOutput = result)
        return result
    }

    fun convertUnits(expr: String): String {
        val match = Regex("""(-?\d+(?:\.\d+)?)\s*([a-zA-Z]+)\s+(?:to|in)\s+([a-zA-Z]+)""", RegexOption.IGNORE_CASE)
            .find(expr.trim()) ?: return "Format example: '10 km to mi', '100 c to f', '5 kg to lb', '2 gb to mb'."
        val value = match.groupValues[1].toDoubleOrNull() ?: return "Invalid number."
        val from = match.groupValues[2].lowercase(Locale.US)
        val to = match.groupValues[3].lowercase(Locale.US)
        val converted: Double? = when (from to to) {
            "km" to "mi" -> value * 0.621371
            "mi" to "km" -> value / 0.621371
            "c" to "f" -> (value * 9.0 / 5.0) + 32.0
            "f" to "c" -> (value - 32.0) * 5.0 / 9.0
            "kg" to "lb" -> value * 2.20462
            "lb" to "kg" -> value / 2.20462
            "gb" to "mb" -> value * 1024.0
            "mb" to "gb" -> value / 1024.0
            else -> null
        }
        return if (converted != null) {
            "$value $from = ${"%.2f".format(Locale.US, converted)} $to"
        } else {
            "Unsupported offline conversion pair: $from -> $to"
        }
    }

    fun evaluateMath(expression: String): String {
        val cleaned = expression.lowercase(Locale.US)
            .replace("calc", "")
            .replace("calculate", "")
            .replace("what is", "")
            .replace("=", "")
            .trim()
        return runCatching {
            val parser = RecursiveMathParser(cleaned)
            val ans = parser.parse()
            if (ans % 1.0 == 0.0) {
                "Result: ${ans.toLong()}"
            } else {
                "Result: ${"%.4f".format(Locale.US, ans).trimEnd('0').trimEnd('.')}"
            }
        }.getOrElse {
            "Unable to evaluate offline expression '$expression'."
        }
    }

    private fun looksLikeMath(lower: String): Boolean {
        return lower.any { it.isDigit() } && (lower.contains("+") || lower.contains("-") || lower.contains("*") || lower.contains("/") || lower.contains("calc"))
    }

    private fun discoverFlashCameraId(): String? {
        return runCatching {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null
            cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val lens = chars.get(CameraCharacteristics.LENS_FACING)
                hasFlash && lens == CameraCharacteristics.LENS_FACING_BACK
            } ?: cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    private class RecursiveMathParser(private val str: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            pos++
            ch = if (pos < str.length) str[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            while (ch == ' '.code) nextChar()
            if (pos < str.length) throw IllegalArgumentException("Unexpected char: ${ch.toChar()}")
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor
                    }
                    eat('%'.code) -> x %= parseFactor()
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()
            val startPos = pos
            val x: Double
            if (eat('('.code)) {
                x = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing closing parenthesis")
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = str.substring(startPos, pos).toDouble()
            } else {
                throw IllegalArgumentException("Unexpected token")
            }
            return x
        }
    }
}
