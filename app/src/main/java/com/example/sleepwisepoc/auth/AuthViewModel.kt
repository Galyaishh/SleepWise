package com.example.sleepwisepoc.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sleepwisepoc.DeviceRepository
import com.example.sleepwisepoc.DeviceStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    init {
        viewModelScope.launch {
            // Brief splash delay then check if already "signed in"
            delay(1200)
            val stored = DeviceStore(getApplication()).load()
            if (stored != null) {
                _state.update { it.copy(screen = AppRoute.MAIN) }
            } else {
                _state.update { it.copy(screen = AppRoute.ONBOARDING) }
            }
        }
    }

    fun onOnboardingComplete() {
        _state.update { it.copy(screen = AppRoute.AUTH) }
    }

    fun signInWithGoogle() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            delay(1200)
            DeviceRepository.ensureRegistered(getApplication())
            _state.update { it.copy(isLoading = false, screen = AppRoute.SETUP) }
        }
    }

    fun signInWithEmail(email: String, password: String) {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            delay(1200)
            DeviceRepository.ensureRegistered(getApplication())
            _state.update { it.copy(isLoading = false, screen = AppRoute.SETUP) }
        }
    }

    fun onSetupComplete() {
        _state.update { it.copy(screen = AppRoute.MAIN) }
    }
}
