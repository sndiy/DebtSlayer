// File: app/src/main/java/com/hyse/debtslayer/ui/screens/LoadingScreen.kt
package com.hyse.debtslayer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hyse.debtslayer.R
import com.hyse.debtslayer.viewmodel.DebtViewModel
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LoadingScreen(
    viewModel: DebtViewModel,
    isFirstSetup: Boolean = false,
    onReady: () -> Unit
) {
    val isDataReady by viewModel.isDataReady.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()

    val steps = remember {
        if (isFirstSetup) listOf(
            "Menyimpan data hutang...",
            "Menghitung target harian...",
            "Menjadwalkan reminder...",
            "Memuat riwayat percakapan...",
            "Menginisialisasi Mai AI...",
            "Sinkronisasi selesai ✓"
        ) else listOf(
            "Memuat preferensi...",
            "Memuat data hutang...",
            "Memuat riwayat chat...",
            "Menginisialisasi Mai AI...",
            "Siap ✓"
        )
    }

    var currentStepIndex by remember { mutableStateOf(0) }
    var displayedProgress by remember { mutableStateOf(0f) }

    val animatedProgress by animateFloatAsState(
        targetValue = displayedProgress,
        animationSpec = tween(350, easing = EaseInOutCubic),
        label = "progress"
    )

    // ✅ Animasi 1: Float naik-turun (Mai melayang)
    val floatingOffset by rememberInfiniteTransition(label = "float").animateFloat(
        initialValue = -8f, targetValue = 8f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = EaseInOutSine), RepeatMode.Reverse
        ),
        label = "floating"
    )

    // ✅ Animasi 2: Glow pulse
    val glowAlpha by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            tween(1500, easing = EaseInOutCubic), RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // ✅ Animasi 3: Particles rotation
    val particleRotation by rememberInfiniteTransition(label = "particles").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(20000, easing = LinearEasing)
        ),
        label = "particle_rotation"
    )

    // Step cycling
    LaunchedEffect(Unit) {
        val stepCount = steps.size
        for (i in 0 until stepCount - 1) {
            currentStepIndex = i
            displayedProgress = (i + 1).toFloat() / stepCount
            delay(420L)
        }
        currentStepIndex = stepCount - 1
        displayedProgress = 0.95f
    }

    // Tunggu isDataReady dari ViewModel
    LaunchedEffect(isDataReady) {
        if (isDataReady) {
            currentStepIndex = steps.lastIndex
            displayedProgress = 1f
            delay(450) // animasi progress bar selesai

            // ✅ Kirim greeting SETELAH data confirmed ready
            if (isFirstSetup) {
                // First setup: kirim greeting khusus
                viewModel.sendFirstSetupGreeting()
                delay(150)
            } else {
                // Normal open: kirim greeting jika belum ada chat hari ini
                viewModel.sendInitialGreeting()
                delay(150)
            }

            onReady()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDarkTheme) Color(0xFF0A0A0A)
                else Color(0xFFFAFAFA)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(36.dp),
            modifier = Modifier.padding(40.dp)
        ) {

            // ── Logo Area dengan Animasi ──
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                // ✅ Background glow (pulse)
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .offset(y = floatingOffset.dp)
                        .scale(1f + (glowAlpha - 0.5f) * 0.3f)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF9C27B0).copy(alpha = glowAlpha),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // ✅ Particles (rotating sparkles)
                Canvas(
                    modifier = Modifier
                        .size(140.dp)
                        .rotate(particleRotation)
                ) {
                    val radius = size.width / 2
                    val particleCount = 8
                    for (i in 0 until particleCount) {
                        val angle = (i * 360f / particleCount) * (Math.PI / 180f)
                        val x = center.x + (radius * 0.8f * cos(angle)).toFloat()
                        val y = center.y + (radius * 0.8f * sin(angle)).toFloat()
                        drawCircle(
                            color = Color(0xFF9C27B0).copy(alpha = 0.4f),
                            radius = 3f,
                            center = Offset(x, y)
                        )
                    }
                }

                // ✅ Mai image (floating)
                Image(
                    painter = painterResource(R.drawable.loading_mai),
                    contentDescription = "Mai",
                    modifier = Modifier
                        .size(100.dp)
                        .offset(y = floatingOffset.dp),
                    contentScale = ContentScale.Fit
                )

                // ✅ Circle outline (rotating slowly)
                Canvas(
                    modifier = Modifier
                        .size(110.dp)
                        .offset(y = floatingOffset.dp)
                        .rotate(-particleRotation / 4)
                ) {
                    drawCircle(
                        color = Color(0xFF9C27B0).copy(alpha = 0.2f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            // App name
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "DebtSlayer",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkTheme) Color(0xFFBB86FC) else Color(0xFF6A1B9A)
                )
                Text(
                    if (isFirstSetup) "Mempersiapkan semuanya untukmu..." else "Memuat aplikasi...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }

            // Progress bar + label
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = if (isDarkTheme) Color(0xFFBB86FC) else Color(0xFF9C27B0),
                    trackColor = if (isDarkTheme) Color(0xFF2A2A2A) else Color(0xFFE1BEE7)
                )

                AnimatedContent(
                    targetState = steps.getOrElse(currentStepIndex) { steps.last() },
                    transitionSpec = {
                        (fadeIn(tween(180)) + slideInVertically { it / 3 })
                            .togetherWith(fadeOut(tween(120)))
                    },
                    label = "step_label"
                ) { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center
                    )
                }

                Text(
                    "${(animatedProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkTheme) Color(0xFFBB86FC) else Color(0xFF9C27B0)
                )
            }
        }
    }
}