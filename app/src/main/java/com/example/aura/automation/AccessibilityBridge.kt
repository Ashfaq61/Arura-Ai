package com.example.aura.automation

import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScreenUiNode(
    val index: Int = 0,
    val text: String,
    val contentDescription: String,
    val className: String,
    val viewId: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val bounds: Rect,
    val isVisibleToUser: Boolean = true,
    val isEnabled: Boolean = true,
    val isFocused: Boolean = false,
    val isChecked: Boolean = false
) {
    val displayLabel: String
        get() = text.ifBlank { contentDescription }.ifBlank { viewId.substringAfterLast("/") }
}

data class ScreenSnapshot(
    val packageName: String = "",
    val windowTitle: String = "",
    val nodes: List<ScreenUiNode> = emptyList(),
    val visibleTextSummary: String = "",
    val screenHash: String = "",
    val hasPopupOrDialog: Boolean = false,
    val isLoadingScreen: Boolean = false,
    val capturedAt: Long = System.currentTimeMillis()
)

sealed interface AutomationCommand {
    data class Tap(val x: Float, val y: Float, val expectedScreenHash: String? = null) : AutomationCommand
    data class TapElementIndex(val index: Int, val expectedScreenHash: String? = null) : AutomationCommand
    data class LongPress(val x: Float, val y: Float, val expectedScreenHash: String? = null) : AutomationCommand
    data class Swipe(val startX: Float, val startY: Float, val endX: Float, val endY: Float, val durationMs: Long = 320L) : AutomationCommand
    data class Drag(val startX: Float, val startY: Float, val endX: Float, val endY: Float, val durationMs: Long = 520L) : AutomationCommand
    data class ScrollNode(val forward: Boolean = true) : AutomationCommand
    data class ClickNodeByText(val query: String) : AutomationCommand
    data class TypeIntoFocusedOrFirstInput(val text: String) : AutomationCommand
    data class TypeIntoField(val query: String, val text: String) : AutomationCommand
    data class GlobalAction(val actionId: Int) : AutomationCommand
    data class BatchActions(val commands: List<AutomationCommand>) : AutomationCommand
    data object CaptureScreenshot : AutomationCommand
    data object RefreshSnapshot : AutomationCommand
}

object AccessibilityBridge {
    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    private val _latestSnapshot = MutableStateFlow(ScreenSnapshot())
    val latestSnapshot: StateFlow<ScreenSnapshot> = _latestSnapshot.asStateFlow()

    private val _latestScreenshot = MutableStateFlow<Bitmap?>(null)
    val latestScreenshot: StateFlow<Bitmap?> = _latestScreenshot.asStateFlow()

    private val _lastVerificationLog = MutableStateFlow("Waiting for AccessibilityService connection.")
    val lastVerificationLog: StateFlow<String> = _lastVerificationLog.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _isPausedByEmergencyStop = MutableStateFlow(false)
    val isPausedByEmergencyStop: StateFlow<Boolean> = _isPausedByEmergencyStop.asStateFlow()

    private var commandHandler: ((AutomationCommand) -> Boolean)? = null

    fun attachService(handler: (AutomationCommand) -> Boolean) {
        commandHandler = handler
        _isServiceConnected.value = true
        _lastVerificationLog.value = "AccessibilityService connected (UI Hierarchy & Gesture Engine ready)."
    }

    fun detachService() {
        commandHandler = null
        _isServiceConnected.value = false
        _isExecuting.value = false
        _lastVerificationLog.value = "AccessibilityService disconnected."
    }

    fun setEmergencyPaused(paused: Boolean) {
        _isPausedByEmergencyStop.value = paused
        if (paused) {
            _isExecuting.value = false
            _lastVerificationLog.value = "PAUSED: Automation halted by Emergency STOP."
        }
    }

    fun updateSnapshot(snapshot: ScreenSnapshot) {
        _latestSnapshot.value = snapshot
    }

    fun updateScreenshot(bitmap: Bitmap?) {
        _latestScreenshot.value = bitmap
    }

    fun reportActionResult(success: Boolean, detail: String) {
        _isExecuting.value = false
        _lastVerificationLog.value = if (success) "VERIFIED: $detail" else "FAILED: $detail"
    }

    /**
     * Checks target confidence before acting: if ambiguous (>1 distinct clickable matches or 0 matches),
     * returns a clarification message instead of tapping randomly.
     */
    fun findTargetWithConfidence(query: String): Pair<ScreenUiNode?, String> {
        val snap = _latestSnapshot.value
        val q = query.trim().lowercase()
        if (q.isBlank()) return null to "Target query is empty."
        val matches = snap.nodes.filter {
            it.isVisibleToUser &&
                it.isEnabled &&
                (it.isClickable || it.isEditable) &&
                it.displayLabel.lowercase().contains(q)
        }
        return when {
            matches.isEmpty() -> null to "Low confidence: no enabled visible target matching '$query'. Ask user or refresh screen."
            matches.size == 1 -> matches.first() to "High confidence target [#${matches.first().index}] '${matches.first().displayLabel}'."
            else -> {
                val exact = matches.firstOrNull { it.displayLabel.equals(query, ignoreCase = true) }
                if (exact != null) {
                    exact to "Exact match target [#${exact.index}] '${exact.displayLabel}'."
                } else {
                    val options = matches.take(4).joinToString { "[#${it.index}] ${it.displayLabel}" }
                    null to "Ambiguous target ($options). Confirm which element number to tap."
                }
            }
        }
    }

    fun dispatch(command: AutomationCommand): Boolean {
        if (_isPausedByEmergencyStop.value) {
            _lastVerificationLog.value = "BLOCKED: Emergency STOP is active."
            return false
        }
        val handler = commandHandler
        if (handler == null) {
            _lastVerificationLog.value = "BLOCKED: Enable AURA Accessibility Service in Android Settings first."
            return false
        }
        _isExecuting.value = true
        return handler(command)
    }
}
