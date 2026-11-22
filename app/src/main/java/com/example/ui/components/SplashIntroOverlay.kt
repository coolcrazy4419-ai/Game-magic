package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sequential Game Magic Intro Splash:
 * 1) Custom Game Magic logo scales in with expanding dark emerald/violet energy ring.
 * 2) "GAME MAGIC" title reveals smoothly below the logo.
 * 3) Transitions seamlessly into the Home screen (tap anywhere to skip immediately).
 */
@Composable
fun SplashIntroOverlay(
    onFinished: () -> Unit
) {
    val logoScale = remember { Animatable(0.62f) }
    val logoAlpha = remember { Animatable(0f) }
    val ringProgress = remember { Animatable(0f) }
    val exitAlpha = remember { Animatable(1f) }
    var showTitle by remember { mutableStateOf(false) }
    var revealedChars by remember { mutableIntStateOf(0) }
    val fullTitle = "GAME MAGIC"

    LaunchedEffect(Unit) {
        // Step 1: Reveal custom Game Magic emblem logo + shockwave pulse
        launch {
            logoAlpha.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
        }
        launch {
            logoScale.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
        launch {
            ringProgress.animateTo(1f, tween(760, easing = FastOutSlowInEasing))
        }

        delay(560L)

        // Step 2: Reveal "GAME MAGIC" name smoothly letter by letter
        showTitle = true
        for (i in 1..fullTitle.length) {
            revealedChars = i
            delay(48L)
        }

        delay(520L)

        // Step 3: Smooth fade into main app
        exitAlpha.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = exitAlpha.value }
            .background(ObsidianGreenBg)
            .testTag("splash_intro_overlay")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onFinished()
            },
        contentAlignment = Alignment.Center
    ) {
        // Expanding Dark-Glowing Emerald & Deep-Violet Shockwave Rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f - 24.dp.toPx()
            val p = ringProgress.value
            if (p > 0f && p < 1f) {
                val r1 = 56.dp.toPx() + p * 170.dp.toPx()
                val r2 = 44.dp.toPx() + p * 125.dp.toPx()
                val a = (1f - p).coerceIn(0f, 1f)
                drawCircle(
                    color = EmeraldGlow.copy(alpha = a * 0.65f),
                    radius = r1,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.4.dp.toPx())
                )
                drawCircle(
                    color = HeavyPurpleGlow.copy(alpha = a * 0.55f),
                    radius = r2,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.8.dp.toPx())
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Step 1: Game Magic App Logo
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        alpha = logoAlpha.value
                    }
                    .clip(RoundedCornerShape(28.dp))
                    .background(ObsidianGreenBg)
                    .darkNeonGlow(
                        glowColor = EmeraldGlow,
                        cornerRadius = 28.dp,
                        glowAlpha = 0.42f,
                        strokeWidth = 1.2.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.game_magic_emblem_icon_1795276393890),
                    contentDescription = "Game Magic Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp))
                )
            }

            // Step 2: Sequential "GAME MAGIC" Title Reveal
            Box(
                modifier = Modifier.height(64.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showTitle,
                    enter = fadeIn(tween(260)) + slideInVertically(tween(300)) { it / 3 }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = fullTitle.take(revealedChars),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MagicTextPrimary,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 3.2.sp
                        )
                        Box(
                            modifier = Modifier
                                .width(136.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, EmeraldGlow, HeavyPurpleGlow, Color.Transparent)
                                    )
                                )
                        )
                        Text(
                            text = "ZERO-LAG GAMING SUITE",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldWireframe.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }
        }
    }
}
