package com.hyse.debtslayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyse.debtslayer.data.auth.AuthRepository
import com.hyse.debtslayer.data.auth.AuthResult
import com.hyse.debtslayer.data.auth.UserData
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: UserData) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // ✅ Flag: apakah ini akun baru (daftar) atau login akun lama
    private val _isNewAccount = MutableStateFlow(false)
    val isNewAccount: StateFlow<Boolean> = _isNewAccount.asStateFlow()

    val currentUser: StateFlow<UserData?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val isLoggedIn: Boolean get() = authRepository.isLoggedIn

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val r = authRepository.signIn(email, password)) {
                is AuthResult.Success -> {
                    _isNewAccount.value = false  // ✅ login akun lama → skip onboarding
                    _authState.value = AuthState.Success(r.user)
                }
                is AuthResult.Error -> _authState.value = AuthState.Error(r.message)
            }
        }
    }

    fun signUp(email: String, password: String, nickname: String) {
        if (email.isBlank() || password.isBlank() || nickname.isBlank()) {
            _authState.value = AuthState.Error("Semua field harus diisi.")
            return
        }
        if (password.length < 6) {
            _authState.value = AuthState.Error("Password minimal 6 karakter.")
            return
        }
        if (nickname.length < 2) {
            _authState.value = AuthState.Error("Nickname minimal 2 karakter.")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val r = authRepository.signUp(email, password, nickname)) {
                is AuthResult.Success -> {
                    _isNewAccount.value = true  // ✅ akun baru → wajib onboarding
                    _authState.value = AuthState.Success(r.user)
                }
                is AuthResult.Error -> _authState.value = AuthState.Error(r.message)
            }
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            _authState.value = when (val r = authRepository.resetPassword(email)) {
                is AuthResult.Success -> AuthState.Error("Email reset dikirim ke ${r.user.email}")
                is AuthResult.Error   -> AuthState.Error(r.message)
            }
        }
    }

    fun signOut() {
        authRepository.signOut()
        _isNewAccount.value = false
        _authState.value = AuthState.Idle
    }

    fun clearState() {
        _authState.value = AuthState.Idle
    }
}