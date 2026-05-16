package com.example.sleepwisepoc.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
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

    init {
        viewModelScope.launch {
            kotlinx.coroutines.delay(800)
            val firebaseUser = auth.currentUser
            if (firebaseUser != null) {
                Log.d(TAG, "resuming session uid=${firebaseUser.uid}")
                _state.update {
                    it.copy(
                        screen = AppRoute.MAIN,
                        userName = firebaseUser.displayName.orEmpty(),
                        userEmail = firebaseUser.email.orEmpty(),
                    )
                }
            } else {
                _state.update { it.copy(screen = AppRoute.ONBOARDING) }
            }
        }
    }

    fun onOnboardingComplete() {
        _state.update { it.copy(screen = AppRoute.AUTH) }
    }

    /** Called by the AuthScreen after Credential Manager returns a Google ID token. */
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
                        screen = AppRoute.SETUP,
                        userName = user.displayName.orEmpty(),
                        userEmail = user.email.orEmpty(),
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Google sign-in failed: ${t.message}", t)
                _state.update { it.copy(isLoading = false, error = t.message ?: "Sign-in failed") }
            }
        }
    }

    /** Email path: try to sign in, auto-create the account if it does not exist. */
    fun signInWithEmail(email: String, password: String) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val result = try {
                    auth.signInWithEmailAndPassword(email, password).await()
                } catch (e: FirebaseAuthInvalidUserException) {
                    Log.d(TAG, "no account for $email — creating")
                    auth.createUserWithEmailAndPassword(email, password).await()
                }
                val user = result.user ?: error("Firebase user null after email sign-in")
                Log.d(TAG, "email sign-in ok uid=${user.uid}")
                _state.update {
                    it.copy(
                        isLoading = false,
                        screen = AppRoute.SETUP,
                        userEmail = user.email.orEmpty(),
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "email sign-in failed: ${t.message}", t)
                _state.update { it.copy(isLoading = false, error = t.message ?: "Sign-in failed") }
            }
        }
    }

    fun onSetupComplete() {
        _state.update { it.copy(screen = AppRoute.MAIN) }
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
