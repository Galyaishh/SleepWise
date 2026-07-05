package com.example.sleepwisepoc.auth

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sleepwisepoc.DeviceRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class AppRoute { SPLASH, ONBOARDING, AUTH, SETUP, MAIN }

data class AuthState(
    val screen: AppRoute = AppRoute.SPLASH,
    val userName: String = "",
    val userEmail: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(AuthState())
    val state = _state.asStateFlow()

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val prefs by lazy {
        getApplication<Application>().getSharedPreferences("sleepwise_prefs", Context.MODE_PRIVATE)
    }

    private fun hasCompletedSetup(uid: String) =
        prefs.getBoolean("setup_done_$uid", false)

    private fun markSetupDone(uid: String) =
        prefs.edit().putBoolean("setup_done_$uid", true).apply()

    private fun routeAfterSignIn(uid: String) =
        if (hasCompletedSetup(uid)) AppRoute.MAIN else AppRoute.SETUP

    init {
        viewModelScope.launch {
            kotlinx.coroutines.delay(800)
            val firebaseUser = auth.currentUser
            if (firebaseUser != null) {
                Log.d(TAG, "resuming session uid=${firebaseUser.uid}")
                _state.update {
                    it.copy(
                        screen    = AppRoute.MAIN,
                        userName  = firebaseUser.displayName.orEmpty(),
                        userEmail = firebaseUser.email.orEmpty(),
                    )
                }
                viewModelScope.launch {
                    DeviceRepository.ensureRegistered(getApplication())
                }
            } else {
                _state.update { it.copy(screen = AppRoute.ONBOARDING) }
            }
        }
    }

    fun onOnboardingComplete() {
        _state.update { it.copy(screen = AppRoute.AUTH) }
    }

    fun completeGoogleSignIn(idToken: String) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(credential).await()
                val user = result.user ?: error("Firebase user null after Google sign-in")
                Log.d(TAG, "Google sign-in ok uid=${user.uid} email=${user.email}")
                _state.update {
                    it.copy(
                        isLoading = false,
                        screen    = routeAfterSignIn(user.uid),
                        userName  = user.displayName.orEmpty(),
                        userEmail = user.email.orEmpty(),
                    )
                }
                DeviceRepository.ensureRegistered(getApplication())
            } catch (t: Throwable) {
                Log.w(TAG, "Google sign-in failed: ${t.message}", t)
                _state.update { it.copy(isLoading = false, error = t.message ?: "Sign-in failed") }
            }
        }
    }

    fun signInWithEmail(email: String, password: String) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                val user = result.user ?: error("Firebase user null after sign-in")
                Log.d(TAG, "email sign-in ok uid=${user.uid}")
                _state.update {
                    it.copy(
                        isLoading = false,
                        screen    = routeAfterSignIn(user.uid),
                        userEmail = user.email.orEmpty(),
                    )
                }
                DeviceRepository.ensureRegistered(getApplication())
            } catch (t: Throwable) {
                Log.w(TAG, "email sign-in failed: ${t.message}", t)
                val msg = t.message.orEmpty()
                val friendly = when {
                    "no user" in msg.lowercase() || "user not found" in msg.lowercase() ->
                        "No account found for this email. Switch to Sign Up to create one."
                    "password" in msg.lowercase() -> "Incorrect password."
                    else -> msg.ifBlank { "Sign-in failed." }
                }
                _state.update { it.copy(isLoading = false, error = friendly) }
            }
        }
    }

    fun signUpWithEmail(email: String, password: String) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user ?: error("Firebase user null after sign-up")
                Log.d(TAG, "email sign-up ok uid=${user.uid}")
                _state.update {
                    it.copy(
                        isLoading = false,
                        screen    = AppRoute.SETUP,   // always show setup for new accounts
                        userEmail = user.email.orEmpty(),
                    )
                }
                DeviceRepository.ensureRegistered(getApplication())
            } catch (t: Throwable) {
                Log.w(TAG, "email sign-up failed: ${t.message}", t)
                val msg = t.message.orEmpty()
                val friendly = when {
                    "already in use" in msg.lowercase() ->
                        "An account already exists for this email. Switch to Sign In."
                    "weak password" in msg.lowercase() -> "Password must be at least 6 characters."
                    "badly formatted" in msg.lowercase() || "invalid email" in msg.lowercase() ->
                        "Please enter a valid email address."
                    else -> msg.ifBlank { "Sign-up failed." }
                }
                _state.update { it.copy(isLoading = false, error = friendly) }
            }
        }
    }

    fun onSetupComplete() {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isNotBlank()) markSetupDone(uid)
        _state.update { it.copy(screen = AppRoute.MAIN) }
    }

    fun signOut() {
        auth.signOut()
        _state.update { AuthState(screen = AppRoute.AUTH) }
    }

    fun setError(message: String) {
        _state.update { it.copy(error = message, isLoading = false) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    companion object {
        private const val TAG = "AuthViewModel"
    }
}
