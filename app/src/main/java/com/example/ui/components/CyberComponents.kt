package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.core.InstalledAppInfo
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Zero-lag dark neon glow modifier using layered vector strokes (no blur / zero GPU stall).
 */
fun Modifier.darkNeonGlow(
    glowColor: Color,
    cornerRadius: Dp = 14.dp,
    glowAlpha: Float = 0.28f,
    strokeWidth: Dp = 0.9.dp
): Modifier = this.drawBehind {
    val r = cornerRadius.toPx()
    val outer = 3.2.dp.toPx()
    drawRoundRect(
        color = glowColor.copy(alpha = glowAlpha * 0.42f),
        topLeft = Offset(-outer, -outer),
        size = Size(size.width + outer * 2f, size.height + outer * 2f),
        cornerRadius = CornerRadius(r + outer, r + outer),
        style = Stroke(width = 2.8.dp.toPx())
    )
    val mid = 1.2.dp.toPx()
    drawRoundRect(
        color = glowColor.copy(alpha = glowAlpha * 0.85f),
        topLeft = Offset(-mid, -mid),
        size = Size(size.width + mid * 2f, size.height + mid * 2f),
        cornerRadius = CornerRadius(r + mid, r + mid),
        style = Stroke(width = 1.4.dp.toPx())
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                glowColor.copy(alpha = 0.92f),
                glowColor.copy(alpha = 0.40f),
                glowColor.copy(alpha = 0.92f)
            )
        ),
        cornerRadius = CornerRadius(r, r),
        style = Stroke(width = strokeWidth.toPx())
    )
}

/**
 * GPU-accelerated Glowing Dark Emerald Green Geometric Structure (#030D08 obsidian base
 * with glowing #00FF7F / #00C853 hexagonal circuit wireframe lines and pulsing energy nodes).
 */
@Composable
fun EmeraldHexGridBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "emeraldHex")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nodePulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianGreenBg)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val hexRadius = 34.dp.toPx()
            val hexW = sqrt(3f) * hexRadius
            val hexH = 2f * hexRadius
            val vertStep = hexH * 0.75f

            val wireColor = EmeraldCircuit.copy(alpha = 0.14f + pulse * 0.05f)
            val brightWire = EmeraldWireframe.copy(alpha = 0.09f + pulse * 0.07f)
            val nodeCore = EmeraldWireframe.copy(alpha = 0.30f + pulse * 0.35f)
            val nodeHalo = EmeraldGlow.copy(alpha = 0.10f + pulse * 0.12f)
            val strokePx = 0.9.dp.toPx()

            val cols = (size.width / hexW).toInt() + 2
            val rows = (size.height / vertStep).toInt() + 2

            val path = Path()
            for (row in -1..rows) {
                val xOffset = if (row % 2 != 0) hexW * 0.5f else 0f
                val cy = row * vertStep
                for (col in -1..cols) {
                    val cx = col * hexW + xOffset
                    path.reset()
                    for (i in 0 until 6) {
                        val angle = (60.0 * i - 30.0) * PI / 180.0
                        val vx = cx + hexRadius * cos(angle).toFloat()
                        val vy = cy + hexRadius * sin(angle).toFloat()
                        if (i == 0) path.moveTo(vx, vy) else path.lineTo(vx, vy)
                    }
                    path.close()
                    val isAccentCell = ((row * 3 + col * 5) and 7) == 0
                    drawPath(
                        path = path,
                        color = if (isAccentCell) brightWire else wireColor,
                        style = Stroke(width = if (isAccentCell) strokePx * 1.25f else strokePx)
                    )

                    // Pulsing circuit energy nodes on selected vertices
                    if (((row + col * 2) and 3) == 0) {
                        val angle = -30.0 * PI / 180.0
                        val nx = cx + hexRadius * cos(angle).toFloat()
                        val ny = cy + hexRadius * sin(angle).toFloat()
                        drawCircle(color = nodeHalo, radius = 5.5.dp.toPx(), center = Offset(nx, ny))
                        drawCircle(color = nodeCore, radius = 1.9.dp.toPx(), center = Offset(nx, ny))
                    }
                }
            }
        }

        // Deep Emerald Top & Violet Bottom Ambient Glow (pure gradient, zero blur lag)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            EmeraldDeepGlow.copy(alpha = 0.22f),
                            EmeraldGlow.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(540f, 0f),
                        radius = 760f
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(260.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            HeavyFillDeep.copy(alpha = 0.28f),
                            Color.Transparent
                        ),
                        radius = 480f
                    )
                )
        )

        content()
    }
}

/**
 * 900ms Full-Screen "Tagda Launch Sequence" Cyber-Magic Animation.
 * Expanding dark-glowing emerald/purple energy shockwave rings, converging laser grid lines,
 * and 3D-style icon zoom before launching the selected game/app.
 */
@Composable
fun CyberMagicLaunchOverlay(
    app: InstalledAppInfo,
    onLaunchComplete: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val appBitmap = remember(app.packageName) {
        runCatching { app.icon?.toBitmap(144, 144)?.asImageBitmap() }.getOrNull()
    }

    LaunchedEffect(app.packageName) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 880, easing = FastOutSlowInEasing)
        )
        onLaunchComplete()
    }

    val p = progress.value
    val iconScale = if (p < 0.72f) {
        0.55f + (p / 0.72f) * 0.65f
    } else {
        1.20f + ((p - 0.72f) / 0.28f) * 0.45f
    }
    val iconAlpha = if (p < 0.85f) 1f else (1f - ((p - 0.85f) / 0.15f)).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianGreenBg.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center
    ) {
        // Expanding Shockwave Rings + Converging Laser Grid Lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = size.maxDimension * 0.65f

            // Converging 12 laser grid beams
            val beamAlpha = (1f - p * 0.55f).coerceIn(0.1f, 0.85f)
            for (i in 0 until 12) {
                val angle = (i * 30.0 + p * 25.0) * PI / 180.0
                val innerR = (1f - p) * maxR * 0.35f + 42.dp.toPx()
                val outerR = maxR
                val sx = cx + innerR * cos(angle).toFloat()
                val sy = cy + innerR * sin(angle).toFloat()
                val ex = cx + outerR * cos(angle).toFloat()
                val ey = cy + outerR * sin(angle).toFloat()
                val beamColor = if (i % 2 == 0) EmeraldWireframe else HeavyPurpleGlow
                drawLine(
                    color = beamColor.copy(alpha = beamAlpha * 0.55f),
                    start = Offset(sx, sy),
                    end = Offset(ex, ey),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // 3 Staggered Expanding Shockwave Rings (Emerald & Royal Violet)
            val ringConfigs = listOf(
                Triple(0.0f, EmeraldGlow, 3.2.dp.toPx()),
                Triple(0.18f, HeavyPurpleGlow, 2.8.dp.toPx()),
                Triple(0.36f, EmeraldWireframe, 2.0.dp.toPx())
            )
            ringConfigs.forEach { (delayFraction, color, width) ->
                val localP = ((p - delayFraction) / (1f - delayFraction)).coerceIn(0f, 1f)
                if (localP > 0f) {
                    val r = 36.dp.toPx() + localP * maxR * 0.85f
                    val a = (1f - localP).coerceIn(0f, 1f)
                    drawCircle(
                        color = color.copy(alpha = a * 0.25f),
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(width = width * 3.5f)
                    )
                    drawCircle(
                        color = color.copy(alpha = a * 0.92f),
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(width = width)
                    )
                }
            }

            // Rotating Hexagonal Magic Portal Frame behind icon
            val hexPath = Path()
            val portalR = 64.dp.toPx() * (0.85f + p * 0.35f)
            for (i in 0 until 6) {
                val angle = (60.0 * i + p * 120.0) * PI / 180.0
                val vx = cx + portalR * cos(angle).toFloat()
                val vy = cy + portalR * sin(angle).toFloat()
                if (i == 0) hexPath.moveTo(vx, vy) else hexPath.lineTo(vx, vy)
            }
            hexPath.close()
            drawPath(
                path = hexPath,
                brush = Brush.linearGradient(listOf(EmeraldGlow, HeavyPurpleGlow, EmeraldWireframe)),
                style = Stroke(width = 2.4.dp.toPx())
            )
        }

        // 3D-Style Zooming App Icon & Launch Title
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.graphicsLayer {
                alpha = iconAlpha
            }
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        rotationY = (1f - p) * 28f
                        cameraDistance = 12f * density
                    }
                    .clip(RoundedCornerShape(22.dp))
                    .background(ObsidianCard)
                    .darkNeonGlow(
                        glowColor = if (p < 0.5f) EmeraldGlow else HeavyPurpleGlow,
                        cornerRadius = 22.dp,
                        glowAlpha = 0.45f,
                        strokeWidth = 1.4.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (appBitmap != null) {
                    Image(
                        bitmap = appBitmap,
                        contentDescription = app.name,
                        modifier = Modifier
                            .size(74.dp)
                            .clip(RoundedCornerShape(17.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.SportsEsports,
                        contentDescription = app.name,
                        tint = EmeraldGlow,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = app.name.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MagicTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "GAME MAGIC • LAUNCHING",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldWireframe,
                    letterSpacing = 2.sp,
                    fontSize = 11.sp
                )
            }
        }
    }
}
