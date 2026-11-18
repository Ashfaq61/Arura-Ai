package com.example.aura.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.aura.voice.VoiceOrbState

@Composable
fun AuraVoiceOrb(
    state: VoiceOrbState,
    rmsLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 176.dp
) {
    val infinite = rememberInfiniteTransition(label = "orb_anim")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (state == VoiceOrbState.THINKING) 2200 else 8000, easing = LinearEasing)
        ),
        label = "orb_rotation"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )
    val animatedRms by animateFloatAsState(targetValue = rmsLevel.coerceIn(0f, 1f), label = "rms")

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error

    val coreColor = when (state) {
        VoiceOrbState.IDLE -> primaryColor
        VoiceOrbState.LISTENING -> tertiaryColor
        VoiceOrbState.THINKING -> secondaryColor
        VoiceOrbState.SPEAKING -> primaryColor
        VoiceOrbState.STOPPED, VoiceOrbState.ERROR -> errorColor
    }

    val stateLabel = when (state) {
        VoiceOrbState.IDLE -> "Tap to Speak"
        VoiceOrbState.LISTENING -> "Listening..."
        VoiceOrbState.THINKING -> "Reasoning..."
        VoiceOrbState.SPEAKING -> "Speaking (Tap to Interrupt)"
        VoiceOrbState.STOPPED -> "STOPPED"
        VoiceOrbState.ERROR -> "Voice Ready (Tap Retry)"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .testTag("aura_voice_orb")
                .semantics { contentDescription = "AURA Voice Orb: $stateLabel" }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val baseRadius = (this.size.minDimension / 2f) * 0.62f * pulse + (animatedRms * 18f)

                // Outer atmospheric radial glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            coreColor.copy(alpha = 0.40f + animatedRms * 0.25f),
                            secondaryColor.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = this.size.minDimension / 2f
                    ),
                    radius = this.size.minDimension / 2f,
                    center = center
                )

                // Rotating neural orbital arcs
                rotate(degrees = rotation, pivot = center) {
                    val arcRadius = baseRadius * 1.18f
                    drawArc(
                        brush = Brush.sweepGradient(listOf(coreColor, secondaryColor, Color.Transparent, coreColor)),
                        startAngle = 0f,
                        sweepAngle = 210f,
                        useCenter = false,
                        topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                        size = Size(arcRadius * 2f, arcRadius * 2f),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                rotate(degrees = -rotation * 1.4f, pivot = center) {
                    val innerArc = baseRadius * 1.03f
                    drawArc(
                        color = secondaryColor.copy(alpha = 0.75f),
                        startAngle = 90f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(center.x - innerArc, center.y - innerArc),
                        size = Size(innerArc * 2f, innerArc * 2f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Glowing inner sphere
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.85f),
                            coreColor.copy(alpha = 0.9f),
                            secondaryColor.copy(alpha = 0.6f)
                        ),
                        center = Offset(center.x - baseRadius * 0.2f, center.y - baseRadius * 0.2f),
                        radius = baseRadius * 1.1f
                    ),
                    radius = baseRadius * 0.65f,
                    center = center
                )
            }

            Icon(
                imageVector = when (state) {
                    VoiceOrbState.LISTENING -> Icons.Filled.GraphicEq
                    VoiceOrbState.THINKING -> Icons.Filled.Psychology
                    VoiceOrbState.STOPPED -> Icons.Filled.Stop
                    else -> Icons.Filled.Mic
                },
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.82f),
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        StatusPill(text = stateLabel, accent = coreColor)
    }
}

@Composable
fun StatusPill(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = accent.copy(alpha = 0.16f),
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun AuraTopStatusBar(
    activeModel: String,
    agentsOnline: Int,
    emergencyStopActive: Boolean,
    pendingApprovals: Int,
    onEmergencyStop: () -> Unit,
    onResume: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (emergencyStopActive) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.tertiary
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AURA AI CORE",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (pendingApprovals > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusPill(
                            text = "$pendingApprovals APPROVAL",
                            accent = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Text(
                    text = "$agentsOnline/24 Agents • $activeModel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (emergencyStopActive) {
                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier.testTag("resume_system_button")
                ) {
                    Text("RESUME", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onEmergencyStop,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("emergency_stop_button")
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = "Emergency Stop", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("STOP", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
