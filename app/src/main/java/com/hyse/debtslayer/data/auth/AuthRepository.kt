package com.hyse.debtslayer.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class UserData(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val nickname: String? = null
)

sealed class AuthResult {
    data class Success(val user: UserData) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    val currentUser: Flow<UserData?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { fa ->
            trySend(fa.currentUser?.let {
                UserData(it.uid, it.email, it.displayName)
            })
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    val isLoggedIn: Boolean get() = auth.currentUser != null
    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun getNickname(): String? {
        val uid = currentUid ?: return null
        return try {
            firestore.collection("users").document(uid).get().await().getString("nickname")
        } catch (e: Exception) { null }
    }

    suspend fun signIn(email: String, password: String): AuthResult {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user!!
            val nickname = try {
                firestore.collection("users").document(user.uid)
                    .get().await().getString("nickname")
            } catch (e: Exception) { null }
            AuthResult.Success(UserData(user.uid, user.email, user.displayName, nickname))
        } catch (e: Exception) {
            AuthResult.Error(friendlyError(e))
        }
    }

    suspend fun signUp(email: String, password: String, nickname: String): AuthResult {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user!!
            firestore.collection("users").document(user.uid).set(
                mapOf(
                    "email"     to email,
                    "nickname"  to nickname.trim(),
                    "createdAt" to System.currentTimeMillis()
                )
            ).await()
            AuthResult.Success(UserData(user.uid, user.email, user.displayName, nickname.trim()))
        } catch (e: Exception) {
            AuthResult.Error(friendlyError(e))
        }
    }

    suspend fun resetPassword(email: String): AuthResult {
        return try {
            auth.sendPasswordResetEmail(email).await()
            AuthResult.Success(UserData("", email, null))
        } catch (e: Exception) {
            AuthResult.Error(friendlyError(e))
        }
    }

    fun signOut() = auth.signOut()

    private fun friendlyError(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("no user record", true) ||
                    msg.contains("user-not-found", true)       -> "Email tidak terdaftar."
            msg.contains("password is invalid", true) ||
                    msg.contains("wrong-password", true)       -> "Password salah."
            msg.contains("email address is already", true) ||
                    msg.contains("email-already-in-use", true) -> "Email sudah digunakan."
            msg.contains("badly formatted", true)      -> "Format email tidak valid."
            msg.contains("at least 6", true)           -> "Password minimal 6 karakter."
            msg.contains("network", true)              -> "Cek koneksi internet."
            else -> "Error: ${msg.take(80)}"
        }
    }
}