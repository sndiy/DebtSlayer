package com.hyse.debtslayer.ui.screens

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyse.debtslayer.R
import com.hyse.debtslayer.viewmodel.ApiKeyStatus
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
    val apiKeyStatus by viewModel.apiKeyStatus.collectAsState()
    val loadingStep by viewModel.loadingStep.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()
    val context = LocalContext.current

    // ── Daftar step, urutan sesuai eksekusi di ViewModel ──────────────────────
    val steps = remember {
        if (isFirstSetup) listOf(
            "Menyimpan preferensi...",         // step 0
            "Menghitung target harian...",      // step 1
            "Memuat riwayat percakapan...",     // step 2
            "Menginisialisasi Mai AI...",        // step 3
            "Memvalidasi API Key...",            // step 4
            "Membersihkan data lama...",         // step 5
            "Siap ✓"                            // final
        ) else listOf(
            "Memuat preferensi...",             // step 0
            "Memuat data hutang...",            // step 1
            "Memuat riwayat chat...",           // step 2
            "Menginisialisasi Mai AI...",        // step 3
            "Memvalidasi API Key...",            // step 4
            "Membersihkan data lama...",         // step 5
            "Siap ✓"                            // final
        )
    }

    // Progress: ikuti loadingStep dari ViewModel, tiap step ada delay visual
    var displayedStepIndex by remember { mutableStateOf(0) }
    var displayedProgress by remember { mutableStateOf(0f) }

    // Sinkronisasi displayedStep dengan loadingStep dari ViewModel
    // Tiap kali step naik, animasikan dengan delay supaya terbaca
    LaunchedEffect(loadingStep) {
        val stepCount = steps.size - 1 // excludes final "Siap ✓"
        displayedStepIndex = loadingStep.coerceIn(0, steps.lastIndex - 1)
        displayedProgress = (loadingStep + 1).toFloat() / steps.size
        delay(600L) // delay per step agar terbaca
    }

    val animatedProgress by animateFloatAsState(
        targetValue = displayedProgress,
        animationSpec = tween(400, easing = EaseInOutCubic),
        label = "progress"
    )

    // ── Animasi dekorasi ───────────────────────────────────────────────────────
    val floatingOffset by rememberInfiniteTransition(label = "float").animateFloat(
        initialValue = -8f, targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "floating"
    )
    val glowAlpha by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "glow_alpha"
    )
    val particleRotation by rememberInfiniteTransition(label = "particles").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "particle_rotation"
    )

    // ── Tunggu data ready lalu lanjut ─────────────────────────────────────────
    LaunchedEffect(isDataReady) {
        if (isDataReady) {
            displayedStepIndex = steps.lastIndex
            displayedProgress = 1f
            delay(500)

            if (isFirstSetup) {
                viewModel.sendFirstSetupGreeting()
                delay(150)
            } else {
                viewModel.sendInitialGreeting()
                delay(150)
            }
            onReady()
        }
    }

    // ── Dialog error API Key ───────────────────────────────────────────────────
    val showApiError = apiKeyStatus is ApiKeyStatus.Invalid
    if (showApiError) {
        val errorReason = (apiKeyStatus as ApiKeyStatus.Invalid).reason
        ApiKeyErrorDialog(
            reason = errorReason,
            onExit = {
                // Tutup aplikasi
                (context as? Activity)?.finishAffinity()
            }
        )
    }

    // ── UI Loading ─────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDarkTheme) Color(0xFF0A0A0A) else Color(0xFFFAFAFA)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(36.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            // ── Animasi logo Mai ───────────────────────────────────────────────
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
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
                Canvas(modifier = Modifier.size(140.dp).rotate(particleRotation)) {
                    val radius = size.width / 2
                    for (i in 0 until 8) {
                        val angle = (i * 45f) * (Math.PI / 180f)
                        val x = center.x + (radius * 0.8f * cos(angle)).toFloat()
                        val y = center.y + (radius * 0.8f * sin(angle)).toFloat()
                        drawCircle(
                            color = Color(0xFF9C27B0).copy(alpha = 0.4f),
                            radius = 3f, center = Offset(x, y)
                        )
                    }
                }
                Image(
                    painter = painterResource(R.drawable.loading_mai),
                    contentDescription = "Mai",
                    modifier = Modifier.size(100.dp).offset(y = floatingOffset.dp),
                    contentScale = ContentScale.Fit
                )
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

            // ── Judul app ──────────────────────────────────────────────────────
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
                    if (isFirstSetup) "Mempersiapkan semuanya untukmu..."
                    else "Memuat aplikasi...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }

            // ── Progress bar + step label ──────────────────────────────────────
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
                    targetState = steps.getOrElse(displayedStepIndex) { steps.last() },
                    transitionSpec = {
                        (fadeIn(tween(200)) + slideInVertically { it / 3 })
                            .togetherWith(fadeOut(tween(150)))
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

// ── Dialog Error API Key ───────────────────────────────────────────────────────
@Composable
private fun ApiKeyErrorDialog(
    reason: String,
    onExit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* tidak bisa dismiss — harus keluar */ },
        icon = {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Gagal Memuat AI",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Kotak instruksi
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                ) {
                    Text(
                        text = "Langkah perbaikan:\n" +
                                "1. Buka file local.properties\n" +
                                "2. Pastikan ada baris:\n" +
                                "   GEMINI_API_KEY=api_key_kamu\n" +
                                "3. Build → Rebuild Project",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        lineHeight = 20.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onExit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Keluar Aplikasi")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}