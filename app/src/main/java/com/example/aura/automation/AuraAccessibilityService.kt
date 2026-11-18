package com.example.aura.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Core Android AccessibilityService for AURA AI:
 * - Reads live AccessibilityNodeInfo UI hierarchy across active windows into indexed ScreenSnapshots.
 * - Detects popups/dialogs, loading states, and hashes layouts to prevent stale-coordinate taps.
 * - Performs automated taps, long presses, swipes, drags, node clicks, text input, scrolling,
 *   global navigation actions, API 30+ screen capture, and paced sequential action batches.
 * - Immediately respects Emergency STOP via AccessibilityBridge.
 */
class AuraAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var batchJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridge.attachService { command -> executeCommand(command) }
        captureCurrentWindowSnapshot()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> captureCurrentWindowSnapshot()
        }
    }

    override fun onInterrupt() {
        batchJob?.cancel()
        AccessibilityBridge.reportActionResult(false, "AccessibilityService interrupted by system.")
    }

    override fun onDestroy() {
        super.onDestroy()
        batchJob?.cancel()
        serviceScope.cancel()
        AccessibilityBridge.detachService()
    }

    private fun captureCurrentWindowSnapshot(): ScreenSnapshot? {
        val root = rootInActiveWindow ?: return null
        val collected = mutableListOf<ScreenUiNode>()
        traverseNodes(root, collected, depth = 0)

        val pkg = root.packageName?.toString().orEmpty()
        val realWindowTitle = runCatching { root.window?.title?.toString() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: pkg.substringAfterLast('.')

        val summary = collected
            .map { "[#${it.index}] ${it.displayLabel}" }
            .filter { it.isNotBlank() }
            .distinct()
            .take(40)
            .joinToString(" | ")

        val hasPopup = collected.any {
            val lower = "${it.className} ${it.displayLabel}".lowercase()
            lower.contains("dialog") || lower.contains("alert") || lower.contains("allow") || lower.contains("permission")
        }
        val isLoading = collected.any {
            val lower = "${it.className} ${it.displayLabel}".lowercase()
            lower.contains("progressbar") || lower.contains("loading...")
        }
        val hashInput = "$pkg|${collected.take(15).joinToString(",") { "${it.index}:${it.displayLabel}:${it.bounds.centerX()}x${it.bounds.centerY()}" }}"
        val hash = hashInput.hashCode().toString()

        val snap = ScreenSnapshot(
            packageName = pkg,
            windowTitle = realWindowTitle,
            nodes = collected.take(80),
            visibleTextSummary = summary,
            screenHash = hash,
            hasPopupOrDialog = hasPopup,
            isLoadingScreen = isLoading
        )
        AccessibilityBridge.updateSnapshot(snap)
        return snap
    }

    private fun traverseNodes(
        node: AccessibilityNodeInfo,
        out: MutableList<ScreenUiNode>,
        depth: Int
    ) {
        if (depth > 16 || out.size >= 80) return
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val visible = node.isVisibleToUser && !rect.isEmpty
        val text = node.text?.toString().orEmpty().trim()
        val desc = node.contentDescription?.toString().orEmpty().trim()
        val viewId = node.viewIdResourceName.orEmpty()
        if (visible && (text.isNotEmpty() || desc.isNotEmpty() || node.isClickable || node.isEditable || node.isScrollable)) {
            out.add(
                ScreenUiNode(
                    index = out.size + 1,
                    text = text,
                    contentDescription = desc,
                    className = node.className?.toString().orEmpty().substringAfterLast('.'),
                    viewId = viewId,
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    isScrollable = node.isScrollable,
                    bounds = rect,
                    isVisibleToUser = visible,
                    isEnabled = node.isEnabled,
                    isFocused = node.isFocused,
                    isChecked = node.isChecked
                )
            )
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNodes(child, out, depth + 1)
        }
    }

    private fun isFreshScreen(expectedHash: String?): Boolean {
        if (expectedHash.isNullOrBlank()) return true
        val live = captureCurrentWindowSnapshot() ?: return false
        return live.screenHash == expectedHash
    }

    private fun executeCommand(command: AutomationCommand): Boolean {
        if (AccessibilityBridge.isPausedByEmergencyStop.value) {
            batchJob?.cancel()
            AccessibilityBridge.reportActionResult(false, "Blocked: Emergency STOP is active.")
            return false
        }

        return when (command) {
            is AutomationCommand.RefreshSnapshot -> {
                val snap = captureCurrentWindowSnapshot()
                val ok = snap != null
                AccessibilityBridge.reportActionResult(
                    ok,
                    if (ok) "Captured ${snap!!.nodes.size} UI nodes in ${snap.windowTitle}." else "No active window root available."
                )
                ok
            }
            is AutomationCommand.CaptureScreenshot -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    takeScreenshot(
                        Display.DEFAULT_DISPLAY,
                        mainExecutor,
                        object : TakeScreenshotCallback {
                            override fun onSuccess(screenshot: ScreenshotResult) {
                                val hw = screenshot.hardwareBuffer
                                val bmp = Bitmap.wrapHardwareBuffer(hw, screenshot.colorSpace)
                                    ?.copy(Bitmap.Config.ARGB_8888, false)
                                hw.close()
                                AccessibilityBridge.updateScreenshot(bmp)
                                AccessibilityBridge.reportActionResult(true, "Screen image captured (${bmp?.width}x${bmp?.height}).")
                            }

                            override fun onFailure(errorCode: Int) {
                                AccessibilityBridge.reportActionResult(false, "Screenshot failed (code=$errorCode).")
                            }
                        }
                    )
                    true
                } else {
                    AccessibilityBridge.reportActionResult(false, "Accessibility takeScreenshot requires Android 11+ (API 30+).")
                    false
                }
            }
            is AutomationCommand.GlobalAction -> {
                val ok = performGlobalAction(command.actionId)
                AccessibilityBridge.reportActionResult(ok, "Global action ${command.actionId} executed.")
                ok
            }
            is AutomationCommand.Tap -> {
                if (!isFreshScreen(command.expectedScreenHash)) {
                    AccessibilityBridge.reportActionResult(false, "Aborted tap: screen changed before execution (stale coordinate guard).")
                    return false
                }
                dispatchStroke(command.x, command.y, command.x, command.y, 60L, "Tap (${command.x.toInt()}, ${command.y.toInt()})")
            }
            is AutomationCommand.TapElementIndex -> {
                val live = captureCurrentWindowSnapshot()
                if (!isFreshScreen(command.expectedScreenHash)) {
                    AccessibilityBridge.reportActionResult(false, "Aborted tap [#${command.index}]: screen changed before execution.")
                    return false
                }
                val target = live?.nodes?.firstOrNull { it.index == command.index }
                if (target == null) {
                    AccessibilityBridge.reportActionResult(false, "Element [#${command.index}] not found on current screen.")
                    false
                } else {
                    dispatchStroke(
                        target.bounds.centerX().toFloat(),
                        target.bounds.centerY().toFloat(),
                        target.bounds.centerX().toFloat(),
                        target.bounds.centerY().toFloat(),
                        60L,
                        "Tap [#${target.index}] '${target.displayLabel}'"
                    )
                }
            }
            is AutomationCommand.LongPress -> {
                if (!isFreshScreen(command.expectedScreenHash)) {
                    AccessibilityBridge.reportActionResult(false, "Aborted long press: screen changed.")
                    return false
                }
                dispatchStroke(command.x, command.y, command.x, command.y, 700L, "Long press (${command.x.toInt()}, ${command.y.toInt()})")
            }
            is AutomationCommand.Swipe -> {
                dispatchStroke(command.startX, command.startY, command.endX, command.endY, command.durationMs, "Swipe gesture")
            }
            is AutomationCommand.Drag -> {
                dispatchStroke(command.startX, command.startY, command.endX, command.endY, command.durationMs.coerceAtLeast(450L), "Drag gesture")
            }
            is AutomationCommand.ScrollNode -> {
                val root = rootInActiveWindow
                val scrollable = findFirstScrollable(root)
                if (scrollable == null) {
                    AccessibilityBridge.reportActionResult(false, "No scrollable container found on screen.")
                    false
                } else {
                    val action = if (command.forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    val ok = scrollable.performAction(action)
                    if (ok) captureCurrentWindowSnapshot()
                    AccessibilityBridge.reportActionResult(ok, if (command.forward) "Scrolled forward." else "Scrolled backward.")
                    ok
                }
            }
            is AutomationCommand.ClickNodeByText -> clickNodeByQuery(command.query)
            is AutomationCommand.TypeIntoFocusedOrFirstInput -> typeIntoNode(findFocusedOrEditable(rootInActiveWindow), command.text)
            is AutomationCommand.TypeIntoField -> {
                val target = findEditableByQuery(rootInActiveWindow, command.query) ?: findFocusedOrEditable(rootInActiveWindow)
                typeIntoNode(target, command.text)
            }
            is AutomationCommand.BatchActions -> {
                batchJob?.cancel()
                if (command.commands.isEmpty()) {
                    AccessibilityBridge.reportActionResult(false, "Empty action batch.")
                    return false
                }
                batchJob = serviceScope.launch {
                    for ((idx, step) in command.commands.withIndex()) {
                        if (AccessibilityBridge.isPausedByEmergencyStop.value) {
                            AccessibilityBridge.reportActionResult(false, "Batch halted at step ${idx + 1} by Emergency STOP.")
                            break
                        }
                        val ok = executeCommand(step)
                        if (!ok) break
                        if (idx < command.commands.lastIndex) {
                            delay(340L)
                            captureCurrentWindowSnapshot()
                        }
                    }
                }
                true
            }
        }
    }

    private fun clickNodeByQuery(query: String): Boolean {
        val root = rootInActiveWindow
        if (root == null) {
            AccessibilityBridge.reportActionResult(false, "No active window root.")
            return false
        }
        val candidates = root.findAccessibilityNodeInfosByText(query).orEmpty()
        val matched = candidates.firstOrNull { it.isVisibleToUser && it.isEnabled }
            ?: findNodeMatchingLabel(root, query.trim().lowercase())

        if (matched != null) {
            val clickable = findClickableAncestor(matched)
            if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                AccessibilityBridge.reportActionResult(true, "Clicked accessibility node '$query'.")
                captureCurrentWindowSnapshot()
                return true
            }
            val rect = Rect()
            matched.getBoundsInScreen(rect)
            if (!rect.isEmpty) {
                return dispatchStroke(
                    rect.centerX().toFloat(),
                    rect.centerY().toFloat(),
                    rect.centerX().toFloat(),
                    rect.centerY().toFloat(),
                    60L,
                    "Gesture fallback tap on '$query'"
                )
            }
        }
        AccessibilityBridge.reportActionResult(false, "No clickable node matching '$query'.")
        return false
    }

    private fun typeIntoNode(inputNode: AccessibilityNodeInfo?, text: String): Boolean {
        if (inputNode == null) {
            AccessibilityBridge.reportActionResult(false, "No editable input field detected on current screen.")
            return false
        }
        if (!inputNode.isFocused) {
            inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (ok) captureCurrentWindowSnapshot()
        AccessibilityBridge.reportActionResult(ok, if (ok) "Injected text into input field." else "Failed to set text on target field.")
        return ok
    }

    private fun findFocusedOrEditable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) return focused
        return findFirstEditable(root)
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable && node.isVisibleToUser && node.isEnabled) return node
        for (i in 0 until node.childCount) {
            val found = findFirstEditable(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun findEditableByQuery(node: AccessibilityNodeInfo?, query: String): AccessibilityNodeInfo? {
        if (node == null || query.isBlank()) return null
        val q = query.trim().lowercase()
        val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) node.hintText?.toString().orEmpty() else ""
        val label = "${node.text?.toString().orEmpty()} ${node.contentDescription?.toString().orEmpty()} $hint ${node.viewIdResourceName.orEmpty()}".lowercase()
        if (node.isEditable && node.isVisibleToUser && label.contains(q)) return node
        for (i in 0 until node.childCount) {
            val found = findEditableByQuery(node.getChild(i), query)
            if (found != null) return found
        }
        return null
    }

    private fun findFirstScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val found = findFirstScrollable(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun findNodeMatchingLabel(node: AccessibilityNodeInfo?, lowerQuery: String): AccessibilityNodeInfo? {
        if (node == null || lowerQuery.isBlank()) return null
        val text = node.text?.toString().orEmpty().lowercase()
        val desc = node.contentDescription?.toString().orEmpty().lowercase()
        val viewId = node.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()
        if (node.isVisibleToUser && (text.contains(lowerQuery) || desc.contains(lowerQuery) || viewId.contains(lowerQuery))) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = findNodeMatchingLabel(node.getChild(i), lowerQuery)
            if (found != null) return found
        }
        return null
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo?, maxDepth: Int = 4): AccessibilityNodeInfo? {
        var current = node
        var depth = 0
        while (current != null && depth <= maxDepth) {
            if (current.isClickable && current.isEnabled) return current
            current = current.parent
            depth++
        }
        return null
    }

    private fun dispatchStroke(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
        label: String
    ): Boolean {
        val path = Path().apply {
            moveTo(startX.coerceAtLeast(1f), startY.coerceAtLeast(1f))
            if (startX != endX || startY != endY) {
                lineTo(endX.coerceAtLeast(1f), endY.coerceAtLeast(1f))
            }
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(16L)))
            .build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                AccessibilityBridge.reportActionResult(true, label)
                captureCurrentWindowSnapshot()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                AccessibilityBridge.reportActionResult(false, "$label cancelled.")
            }
        }, null)
    }
}
