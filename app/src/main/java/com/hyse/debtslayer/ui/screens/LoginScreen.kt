package com.hyse.debtslayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyse.debtslayer.viewmodel.AuthState
import com.hyse.debtslayer.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSignUp by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }

    val authState by authViewModel.authState.collectAsState()

    BackHandler { onBack() }

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            authViewModel.clearState()
            onLoginSuccess()
        }
    }

    // ── Pakai background dari tema ────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // ── Tombol back ───────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Kembali",
                        tint = MaterialTheme.colorScheme.onBackground  // ✅ ikut tema
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Icon header ───────────────────────────────────────
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary  // ✅ ikut tema
            )

            Spacer(Modifier.height(8.dp))

            // ── Judul ─────────────────────────────────────────────
            Text(
                when {
                    showReset -> "Reset Password"
                    isSignUp  -> "Buat Akun"
                    else      -> "Masuk"
                },
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground  // ✅ ikut tema
            )

            Spacer(Modifier.height(4.dp))

            Text(
                when {
                    showReset -> "Masukkan email kamu"
                    isSignUp  -> "Daftar untuk sync data hutang"
                    else      -> "Sinkronkan data hutang ke cloud"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant  // ✅ ikut tema
            )

            Spacer(Modifier.height(32.dp))

            // ── Nickname (hanya saat daftar) ──────────────────────
            if (isSignUp && !showReset) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Nickname") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Badge, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    placeholder = { Text("Nama panggilanmu") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    supportingText = { Text("Min. 2 karakter") },
                    colors = outlinedTextFieldColors()
                )
                Spacer(Modifier.height(10.dp))
            }

            // ── Email ─────────────────────────────────────────────
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Email, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = outlinedTextFieldColors()
            )

            Spacer(Modifier.height(10.dp))

            // ── Password ──────────────────────────────────────────
            if (!showReset) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Lock, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    visualTransformation = if (passwordVisible)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    supportingText = if (isSignUp) {
                        { Text("Min. 6 karakter") }
                    } else null,
                    colors = outlinedTextFieldColors()
                )
                Spacer(Modifier.height(20.dp))
            } else {
                Spacer(Modifier.height(10.dp))
            }

            // ── Pesan Error / Info ────────────────────────────────
            if (authState is AuthState.Error) {
                val msg = (authState as AuthState.Error).message
                val isInfo = msg.startsWith("Email reset")
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isInfo)
                            MaterialTheme.colorScheme.tertiaryContainer  // ✅ ikut tema
                        else
                            MaterialTheme.colorScheme.errorContainer     // ✅ ikut tema
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (isInfo) Icons.Default.CheckCircle
                            else Icons.Default.Error,
                            null,
                            tint = if (isInfo)
                                MaterialTheme.colorScheme.tertiary        // ✅ ikut tema
                            else
                                MaterialTheme.colorScheme.error,          // ✅ ikut tema
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            msg,
                            color = if (isInfo)
                                MaterialTheme.colorScheme.onTertiaryContainer  // ✅ ikut tema
                            else
                                MaterialTheme.colorScheme.onErrorContainer,    // ✅ ikut tema
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Tombol Aksi ───────────────────────────────────────
            Button(
                onClick = {
                    authViewModel.clearState()
                    when {
                        showReset -> authViewModel.resetPassword(email)
                        isSignUp  -> authViewModel.signUp(email, password, nickname)
                        else      -> authViewModel.signIn(email, password)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = authState !is AuthState.Loading &&
                        email.isNotBlank() &&
                        (showReset || password.isNotBlank()) &&
                        (!isSignUp || nickname.isNotBlank()),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,         // ✅ ikut tema
                    contentColor = MaterialTheme.colorScheme.onPrimary,         // ✅ ikut tema
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,  // ✅ ikut tema
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        when {
                            showReset -> "Kirim Email Reset"
                            isSignUp  -> "Daftar"
                            else      -> "Masuk"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Toggle Sign In / Sign Up ──────────────────────────
            if (!showReset) {
                TextButton(onClick = {
                    isSignUp = !isSignUp
                    nickname = ""
                    authViewModel.clearState()
                }) {
                    Text(
                        if (isSignUp) "Sudah punya akun? Masuk"
                        else "Belum punya akun? Daftar",
                        color = MaterialTheme.colorScheme.primary  // ✅ ikut tema
                    )
                }
            }

            // ── Lupa Password ─────────────────────────────────────
            if (!isSignUp) {
                TextButton(onClick = {
                    showReset = !showReset
                    authViewModel.clearState()
                }) {
                    Text(
                        if (showReset) "Kembali ke Login" else "Lupa password?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,  // ✅ ikut tema
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ── Helper: warna OutlinedTextField yang konsisten dengan tema ────────────────
@Composable
private fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
    unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    focusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface
)