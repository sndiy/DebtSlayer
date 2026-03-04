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
    onSignUpSuccess: () -> Unit = onLoginSuccess, // ✅ callback khusus registrasi
    onBack: () -> Unit,
    onContinueAsGuest: (() -> Unit)? = null
) {
    var email          by remember { mutableStateOf("") }
    var password       by remember { mutableStateOf("") }
    var nickname       by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSignUp       by remember { mutableStateOf(false) }
    var showReset      by remember { mutableStateOf(false) }
    var justRegistered by remember { mutableStateOf(false) } // ✅ flag daftar

    val authState by authViewModel.authState.collectAsState()

    if (onContinueAsGuest == null) {
        BackHandler { onBack() }
    }

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            authViewModel.clearState()
            if (justRegistered) {
                justRegistered = false
                onSignUpSuccess() // ✅ ke onboarding
            } else {
                onLoginSuccess()  // ✅ ke checking cloud / main
            }
        }
    }

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
            if (onContinueAsGuest == null) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Kembali",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            } else {
                Spacer(Modifier.height(32.dp))
            }

            // ── Icon ──────────────────────────────────────────────
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(8.dp))

            // ── Judul ─────────────────────────────────────────────
            Text(
                when {
                    showReset -> "Reset Password"
                    isSignUp  -> "Buat Akun"
                    onContinueAsGuest != null -> "Selamat Datang"
                    else      -> "Masuk"
                },
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(4.dp))

            Text(
                when {
                    showReset -> "Link reset akan dikirim ke email kamu"
                    isSignUp  -> "Daftar untuk sync data hutang ke cloud"
                    onContinueAsGuest != null -> "Login atau daftar untuk mulai"
                    else      -> "Sinkronkan data hutang ke cloud"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            // ── Nickname (hanya saat daftar) ──────────────────────
            if (isSignUp && !showReset) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Nickname") },
                    leadingIcon = {
                        Icon(Icons.Default.Badge, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Icon(Icons.Default.Email, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Icon(Icons.Default.Lock, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    visualTransformation = if (passwordVisible)
                        VisualTransformation.None else PasswordVisualTransformation(),
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
                            MaterialTheme.colorScheme.tertiaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
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
                            if (isInfo) Icons.Default.CheckCircle else Icons.Default.Error,
                            null,
                            tint = if (isInfo) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            msg,
                            color = if (isInfo) MaterialTheme.colorScheme.onTertiaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ══════════════════════════════════════════════════════
            // ✅ TOMBOL DIPISAH: Login dan Daftar tidak lagi toggle
            // ══════════════════════════════════════════════════════

            if (!showReset) {
                if (!isSignUp) {
                    // ── Mode Login: tampilkan tombol Masuk + Daftar ──
                    Button(
                        onClick = {
                            authViewModel.clearState()
                            authViewModel.signIn(email, password)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = authState !is AuthState.Loading &&
                                email.isNotBlank() && password.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor   = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor   = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        if (authState is AuthState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Login, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Masuk", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // ✅ Tombol Daftar — terpisah, selalu terlihat di mode login
                    OutlinedButton(
                        onClick = {
                            isSignUp = true
                            email = ""; password = ""; nickname = ""
                            authViewModel.clearState()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = authState !is AuthState.Loading,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Buat Akun Baru", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }

                } else {
                    // ── Mode Daftar: tampilkan tombol Daftar + Kembali ke Login ──
                    Button(
                        onClick = {
                            authViewModel.clearState()
                            justRegistered = true  // ✅ tandai ini registrasi
                            authViewModel.signUp(email, password, nickname)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = authState !is AuthState.Loading &&
                                email.isNotBlank() &&
                                password.isNotBlank() &&
                                nickname.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor   = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor   = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        if (authState is AuthState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Daftar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    TextButton(
                        onClick = {
                            isSignUp = false
                            email = ""; password = ""; nickname = ""
                            authViewModel.clearState()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.ArrowBack, null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Sudah punya akun? Masuk",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // ── Mode Reset Password ───────────────────────────
                Button(
                    onClick = {
                        authViewModel.clearState()
                        authViewModel.resetPassword(email)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = authState !is AuthState.Loading && email.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor   = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor   = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    if (authState is AuthState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Kirim Email Reset", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Lupa Password (hanya di mode login) ───────────────
            if (!isSignUp) {
                TextButton(onClick = {
                    showReset = !showReset
                    email = ""
                    authViewModel.clearState()
                }) {
                    Text(
                        if (showReset) "Kembali ke Login" else "Lupa password?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            // ── Tombol Guest (hanya di screen awal, mode login) ───
            if (onContinueAsGuest != null && !isSignUp && !showReset) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onContinueAsGuest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(Icons.Default.PersonOutline, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Lanjut sebagai Guest", fontSize = 14.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Data guest tidak tersinkron ke cloud",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor             = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor           = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor           = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor         = MaterialTheme.colorScheme.outline,
    focusedLabelColor            = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor          = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor                  = MaterialTheme.colorScheme.primary,
    focusedLeadingIconColor      = MaterialTheme.colorScheme.primary,
    unfocusedLeadingIconColor    = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedTrailingIconColor     = MaterialTheme.colorScheme.primary,
    unfocusedTrailingIconColor   = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    unfocusedPlaceholderColor    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    focusedSupportingTextColor   = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedContainerColor        = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor      = MaterialTheme.colorScheme.surface
)