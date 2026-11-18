package com.example.aura.ui.screens

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.BatteryManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import java.util.concurrent.Executors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aura.automation.AutomationCommand
import com.example.aura.ui.AuraViewModel
import com.example.aura.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlHubScreen(viewModel: AuraViewModel) {
    var subTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "Accessibility",
        "CameraX Vision",
        "Apps & Device",
        "Calls & Docs",
        "Knowledge Graph",
        "Productivity",
        "Smart Inbox",
        "Offline SOS",
        "Share & Clip"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = subTab, edgePadding = 12.dp) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = subTab == idx,
                    onClick = { subTab = idx },
                    text = { Text(title, maxLines = 1) }
                )
            }
        }
        when (subTab) {
            0 -> AccessibilityAutomationTab(viewModel)
            1 -> CameraVisionTab(viewModel)
            2 -> AppsAndDeviceTab(viewModel)
            3 -> CallsMessagingDocsTab(viewModel)
            4 -> KnowledgeGraphTab(viewModel)
            5 -> ProductivityAndCalendarTab(viewModel)
            6 -> IntelligentNotificationsTab(viewModel)
            7 -> OfflineEmergencyTab(viewModel)
            8 -> ShareAndClipboardTab(viewModel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccessibilityAutomationTab(viewModel: AuraViewModel) {
    val connected by viewModel.accessibilityConnected.collectAsStateWithLifecycle()
    val snapshot by viewModel.screenSnapshot.collectAsStateWithLifecycle()
    val screenshot by viewModel.latestScreenshot.collectAsStateWithLifecycle()
    val verifyLog by viewModel.accessibilityVerificationLog.collectAsStateWithLifecycle()
    var typeInput by remember { mutableStateOf("") }
    var nodeClickQuery by remember { mutableStateOf("") }
    var confidenceMessage by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Real-Time Phone Computer-Use Engine", style = MaterialTheme.typography.titleMedium)
                        }
                        StatusPill(
                            text = if (connected) "ACTIVE" else "OPT-IN REQUIRED",
                            accent = if (connected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Verification: $verifyLog", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatusPill(text = "Hash: ${snapshot.screenHash.ifBlank { "none" }}", accent = MaterialTheme.colorScheme.secondary)
                        if (snapshot.hasPopupOrDialog) {
                            StatusPill(text = "POPUP DETECTED", accent = MaterialTheme.colorScheme.error)
                        }
                        if (snapshot.isLoadingScreen) {
                            StatusPill(text = "LOADING UI", accent = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.deviceControl.openAccessibilitySettings() }) {
                            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Accessibility Settings")
                        }
                        OutlinedButton(onClick = { viewModel.dispatchAutomation(AutomationCommand.RefreshSnapshot) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Read & Tag Screen")
                        }
                        OutlinedButton(
                            onClick = { viewModel.dispatchAutomation(AutomationCommand.CaptureScreenshot) },
                            modifier = Modifier.testTag("capture_accessibility_screenshot_btn")
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Capture Screen (API 30+)")
                        }
                    }
                    screenshot?.let { bmp ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Captured Accessibility screen frame",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Gesture, Freshness Guard & Confidence Targeting", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.dispatchAutomation(AutomationCommand.Swipe(500f, 1400f, 500f, 450f)) }) { Text("Scroll Down") }
                        OutlinedButton(onClick = { viewModel.dispatchAutomation(AutomationCommand.Swipe(500f, 500f, 500f, 1450f)) }) { Text("Scroll Up") }
                        OutlinedButton(onClick = { viewModel.dispatchAutomation(AutomationCommand.Swipe(900f, 900f, 150f, 900f)) }) { Text("Swipe Left") }
                        OutlinedButton(onClick = { viewModel.dispatchAutomation(AutomationCommand.Swipe(150f, 900f, 900f, 900f)) }) { Text("Swipe Right") }
                        OutlinedButton(onClick = { viewModel.dispatchAutomation(AutomationCommand.Tap(540f, 960f, snapshot.screenHash)) }) { Text("Safe Tap Center") }
                        OutlinedButton(onClick = { viewModel.dispatchAutomation(AutomationCommand.LongPress(540f, 960f, snapshot.screenHash)) }) { Text("Long Press") }
                        OutlinedButton(onClick = { viewModel.dispatchAutomation(AutomationCommand.Drag(300f, 1200f, 750f, 600f)) }) { Text("Drag") }
                        OutlinedButton(onClick = { viewModel.dispatchAutomation(AutomationCommand.ScrollNode(true)) }) { Text("Node Scroll +") }
                        OutlinedButton(onClick = { viewModel.dispatchAutomation(AutomationCommand.ScrollNode(false)) }) { Text("Node Scroll -") }
                        OutlinedButton(onClick = { viewModel.dispatchAutomation(AutomationCommand.GlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) }) { Text("Back") }
                        OutlinedButton(onClick = { viewModel.dispatchAutomation(AutomationCommand.GlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) }) { Text("Home") }
                        OutlinedButton(onClick = { viewModel.dispatchAutomation(AutomationCommand.GlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)) }) { Text("Recents") }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = nodeClickQuery,
                            onValueChange = { nodeClickQuery = it },
                            label = { Text("Find & Tap element (Confidence Guard)") },
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            if (nodeClickQuery.isNotBlank()) {
                                val (target, msg) = com.example.aura.automation.AccessibilityBridge.findTargetWithConfidence(nodeClickQuery)
                                confidenceMessage = msg
                                if (target != null) {
                                    viewModel.dispatchAutomation(AutomationCommand.TapElementIndex(target.index, snapshot.screenHash))
                                }
                            }
                        }) { Text("Tap") }
                    }
                    if (confidenceMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(confidenceMessage, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = typeInput,
                            onValueChange = { typeInput = it },
                            label = { Text("Type into focused input field") },
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            if (typeInput.isNotBlank()) viewModel.dispatchAutomation(AutomationCommand.TypeIntoFocusedOrFirstInput(typeInput))
                        }) { Text("Type") }
                    }
                }
            }
        }

        item {
            Text(
                text = "Numbered Screen Nodes (${snapshot.nodes.size}) • ${snapshot.windowTitle.ifBlank { "No Window" }} (${snapshot.packageName.ifBlank { "None" }})",
                style = MaterialTheme.typography.titleSmall
            )
        }

        items(snapshot.nodes.take(25)) { node ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("[#${node.index}] ${node.displayLabel.ifBlank { node.className }}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${node.className} • Click=${node.isClickable} • Edit=${node.isEditable} • Bounds=${node.bounds.toShortString()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (node.isClickable) {
                        OutlinedButton(onClick = {
                            viewModel.dispatchAutomation(AutomationCommand.TapElementIndex(node.index, snapshot.screenHash))
                        }) {
                            Text("Tap #${node.index}")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CameraVisionTab(viewModel: AuraViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val visionAnalysis by viewModel.visionAnalysis.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var useFrontCamera by remember { mutableStateOf(false) }
    var liveStreamEnabled by remember { mutableStateOf(false) }
    var visionMode by remember { mutableStateOf("SCENE") }
    val modePrompts = mapOf(
        "SCENE" to "Describe the live camera scene, spatial layout, and actionable elements concisely.",
        "OCR" to "Extract all visible printed or handwritten text (OCR) accurately with line breaks.",
        "OBJECTS" to "Identify and list the main objects, labels, and brands visible in frame.",
        "DOCUMENT" to "Scan this document/receipt: extract title, dates, totals, and key structured fields.",
        "QA" to "Answer the user's visual question precisely based only on what is visible in the frame."
    )
    var prompt by remember { mutableStateOf(modePrompts["SCENE"].orEmpty()) }
    var lastCapturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var liveStreamNote by remember { mutableStateOf("Live ImageAnalysis paused (enable toggle for throttled 3.5s stream).") }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            if (!hasCameraPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("CameraX Multimodal Vision", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Grant CAMERA permission to preview front/rear cameras, run live ImageAnalysis vision streaming, and execute OCR / Document / Object Q&A.", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.testTag("grant_camera_btn")
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Enable Camera Permission")
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .clip(RoundedCornerShape(14.dp))
                        ) {
                            CameraXPreviewBox(
                                useFrontCamera = useFrontCamera,
                                lifecycleOwner = lifecycleOwner,
                                liveStreamEnabled = liveStreamEnabled,
                                isBusy = isBusy,
                                onImageCaptureReady = { imageCapture = it },
                                onLiveFrameSampled = { frameBmp ->
                                    val bm = context.getSystemService(BatteryManager::class.java)
                                    val batPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
                                    if (batPct < 20 && bm?.isCharging == false) {
                                        liveStreamEnabled = false
                                        liveStreamNote = "Paused Live Vision: battery < 20% protection."
                                    } else {
                                        lastCapturedBitmap = frameBmp
                                        liveStreamNote = "Live $visionMode frame analyzed (${frameBmp.width}x${frameBmp.height})."
                                        viewModel.analyzeCameraBitmap(frameBmp, "[$visionMode LIVE] $prompt")
                                    }
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("SCENE" to "Scene", "OCR" to "OCR Text", "OBJECTS" to "Objects", "DOCUMENT" to "Document", "QA" to "Visual Q&A").forEach { (id, label) ->
                                FilterChip(
                                    selected = visionMode == id,
                                    onClick = {
                                        visionMode = id
                                        prompt = modePrompts[id].orEmpty()
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("vision_mode_${id.lowercase()}")
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Live Camera Analysis (3.5s throttled)", style = MaterialTheme.typography.bodyMedium)
                                Text(liveStreamNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = liveStreamEnabled,
                                onCheckedChange = {
                                    liveStreamEnabled = it
                                    liveStreamNote = if (it) "Live $visionMode ImageAnalysis active..." else "Live ImageAnalysis paused."
                                },
                                modifier = Modifier.testTag("live_camera_analysis_switch")
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            label = { Text("Vision Question / $visionMode Prompt") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { useFrontCamera = !useFrontCamera }) {
                                Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch camera")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (useFrontCamera) "Front" else "Rear")
                            }
                            Button(
                                onClick = {
                                    val capture = imageCapture ?: return@Button
                                    capture.takePicture(
                                        ContextCompat.getMainExecutor(context),
                                        object : ImageCapture.OnImageCapturedCallback() {
                                            override fun onCaptureSuccess(image: ImageProxy) {
                                                val rotated = try {
                                                    val rawBmp = image.toBitmap()
                                                    val rotation = image.imageInfo.rotationDegrees.toFloat()
                                                    if (rotation != 0f) {
                                                        val m = Matrix().apply { postRotate(rotation) }
                                                        Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, m, true)
                                                    } else rawBmp
                                                } finally {
                                                    image.close()
                                                }
                                                lastCapturedBitmap = rotated
                                                viewModel.analyzeCameraBitmap(rotated, "[$visionMode] $prompt")
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                // Surface cleanly
                                            }
                                        }
                                    )
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("camera_capture_analyze_btn")
                            ) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Capture & Analyze")
                            }
                        }
                    }
                }
            }
        }

        if (isBusy) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        }

        lastCapturedBitmap?.let { bmp ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Captured frame",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(visionAnalysis.ifBlank { "Analyzing..." }, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraXPreviewBox(
    useFrontCamera: Boolean,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    liveStreamEnabled: Boolean,
    isBusy: Boolean,
    onImageCaptureReady: (ImageCapture) -> Unit,
    onLiveFrameSampled: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val latestLiveEnabled = remember(liveStreamEnabled) { liveStreamEnabled }
    val latestBusy = remember(isBusy) { isBusy }
    var liveFlag by remember { mutableStateOf(liveStreamEnabled) }
    var busyFlag by remember { mutableStateOf(isBusy) }
    liveFlag = latestLiveEnabled
    busyFlag = latestBusy

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    DisposableEffect(useFrontCamera) {
        val future = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var lastSampleTimeMs = 0L

        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder().build()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { analyzerUseCase ->
                        analyzerUseCase.setAnalyzer(analysisExecutor) { image ->
                            try {
                                val now = System.currentTimeMillis()
                                if (liveFlag && !busyFlag && now - lastSampleTimeMs >= 3500L) {
                                    lastSampleTimeMs = now
                                    val rawBmp = image.toBitmap()
                                    val rotation = image.imageInfo.rotationDegrees.toFloat()
                                    val upright = if (rotation != 0f) {
                                        val m = Matrix().apply { postRotate(rotation) }
                                        Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, m, true)
                                    } else rawBmp
                                    mainExecutor.execute { onLiveFrameSampled(upright) }
                                }
                            } finally {
                                image.close()
                            }
                        }
                    }

                val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, capture, analysis)
                onImageCaptureReady(capture)
            }
        }, mainExecutor)

        onDispose {
            runCatching { future.get().unbindAll() }
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppsAndDeviceTab(viewModel: AuraViewModel) {
    val apps = remember { viewModel.deviceControl.listLaunchableApps() }
    var searchQuery by remember { mutableStateOf("") }
    var actionFeedback by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Quick App & Intent Launcher", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("YouTube", "WhatsApp", "Maps", "Chrome", "Facebook", "TikTok").forEach { app ->
                            OutlinedButton(onClick = {
                                actionFeedback = viewModel.deviceControl.launchAppByNameOrPackage(app).second
                            }) { Text(app) }
                        }
                        OutlinedButton(onClick = {
                            actionFeedback = viewModel.deviceControl.openSystemCamera().second
                        }) { Text("Camera") }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search Web / YouTube / Maps") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (searchQuery.isNotBlank()) actionFeedback = viewModel.deviceControl.searchWeb(searchQuery).second
                        }) { Text("Web") }
                        Button(onClick = {
                            if (searchQuery.isNotBlank()) actionFeedback = viewModel.deviceControl.searchYouTube(searchQuery).second
                        }) { Text("YouTube") }
                        Button(onClick = {
                            if (searchQuery.isNotBlank()) actionFeedback = viewModel.deviceControl.searchGoogleMaps(searchQuery).second
                        }) { Text("Maps") }
                        OutlinedButton(onClick = {
                            actionFeedback = viewModel.deviceControl.adjustMediaVolume(+15).second
                        }) { Text("Vol +15%") }
                        OutlinedButton(onClick = {
                            actionFeedback = viewModel.deviceControl.adjustMediaVolume(-15).second
                        }) { Text("Vol -15%") }
                        OutlinedButton(onClick = {
                            actionFeedback = viewModel.deviceControl.setBrightnessPercent(75).second
                        }) { Text("Brightness 75%") }
                    }
                    if (actionFeedback.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(actionFeedback, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        item {
            Text("Installed Launchable Apps (${apps.size})", style = MaterialTheme.typography.titleSmall)
        }

        items(apps.take(30), key = { it.packageName }) { app ->
            Card(
                onClick = { actionFeedback = viewModel.deviceControl.launchAppByNameOrPackage(app.packageName).second },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Launch", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun CallsMessagingDocsTab(viewModel: AuraViewModel) {
    val context = LocalContext.current
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    val callerInfo by viewModel.lastCallerInfo.collectAsStateWithLifecycle()
    val transcribeConsent by viewModel.transcribeConsent.collectAsStateWithLifecycle()
    val notifConnected by viewModel.notificationConnected.collectAsStateWithLifecycle()
    val notifications by viewModel.capturedNotifications.collectAsStateWithLifecycle()
    val loadedDoc by viewModel.loadedDocument.collectAsStateWithLifecycle()
    val docSummary by viewModel.documentSummary.collectAsStateWithLifecycle()

    var phoneInput by remember { mutableStateOf("") }
    var waMessage by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }

    val phonePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.callMessaging.startMonitoringCallState()
    }

    val docPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.loadAndSummarizeDocument(uri)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Phone Call Agent
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Phone Call Agent", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Call State: ${callState.label} • $callerInfo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(viewModel.callMessaging.carrierCapabilitiesSummary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Consent for Call Summaries / AI Identification", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = transcribeConsent, onCheckedChange = { viewModel.callMessaging.setTranscribeConsent(it) })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Phone Number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            phonePermLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE))
                        }) { Text("Monitor Calls") }
                        Button(onClick = {
                            statusText = viewModel.callMessaging.openDialer(phoneInput).second
                        }) { Text("Open Dialer") }
                    }
                }
            }
        }

        // WhatsApp & Notification Reader
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("WhatsApp & Notifications", style = MaterialTheme.typography.titleMedium)
                        }
                        StatusPill(
                            text = if (notifConnected) "LISTENER ON" else "OPT-IN",
                            accent = if (notifConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.deviceControl.openNotificationListenerSettings() }) {
                        Text("Configure Notification Access")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = waMessage,
                        onValueChange = { waMessage = it },
                        label = { Text("Draft WhatsApp / Social Reply") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        if (waMessage.isNotBlank()) {
                            statusText = viewModel.callMessaging.openWhatsAppDraft(phoneInput, waMessage).second
                        }
                    }) { Text("Open Confirmed WhatsApp Draft") }
                    if (notifications.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        notifications.take(4).forEach { n ->
                            Text("• [${n.appLabel}] ${n.title}: ${n.text}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // PDF & Document Agent
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("File & PDF Document Intelligence", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Uses Android Storage Access Framework (zero broad storage permissions) + PdfRenderer + Gemini multimodal OCR.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            docPicker.launch(arrayOf("application/pdf", "text/*", "image/*"))
                        }) { Text("Pick PDF / Text / Image") }
                        if (docSummary.isNotBlank()) {
                            OutlinedButton(onClick = {
                                viewModel.fileManager.shareSummary(loadedDoc?.displayName ?: "AURA Summary", docSummary)
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share")
                            }
                        }
                    }
                    loadedDoc?.previewBitmap?.let { bmp ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Document page preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    }
                    if (docSummary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(docSummary, style = MaterialTheme.typography.bodySmall)
                    }
                    if (statusText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(statusText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
