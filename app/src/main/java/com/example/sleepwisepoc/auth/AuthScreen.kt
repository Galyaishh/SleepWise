package com.example.sleepwisepoc.auth

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sleepwisepoc.R
import com.example.sleepwisepoc.ui.theme.LocalSleepColors
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(viewModel: AuthViewModel = viewModel()) {
    val c = LocalSleepColors.current
    val state by viewModel.state.collectAsState()
    var showEmailForm by remember { mutableStateOf(false) }
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val onGoogleClick: () -> Unit = {
        if (!hasInternet(context)) {
            viewModel.setError("No internet connection. Connect to Wi-Fi or mobile data and try again.")
        } else {
            viewModel.clearError()
            coroutineScope.launch {
                try {
                    // GetSignInWithGoogleOption is the explicit "button click" entry point —
                    // always shows the account chooser, doesn't depend on prior authorization.
                    val option = GetSignInWithGoogleOption.Builder(
                        context.getString(R.string.default_web_client_id)
                    ).build()
                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(option)
                        .build()
                    val response = credentialManager.getCredential(context, request)
                    val credential = response.credential
                    if (credential is CustomCredential &&
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    ) {
                        val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                        viewModel.completeGoogleSignIn(googleCred.idToken)
                    } else {
                        viewModel.setError("Unexpected credential type from Google.")
                    }
                } catch (t: Throwable) {
                    Log.w("AuthScreen", "credential manager error: ${t.message}", t)
                    val msg = t.message.orEmpty()
                    val friendly = when {
                        "reauth" in msg.lowercase() ->
                            "Google account needs to re-sign-in. Check your internet, or remove + re-add the account in phone Settings."
                        "no credentials" in msg.lowercase() ->
                            "No Google account available on this device."
                        "cancel" in msg.lowercase() ->
                            "Sign-in cancelled."
                        else -> "Google sign-in failed: $msg"
                    }
                    viewModel.setError(friendly)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFF0D0F22),
                        0.45f to c.bg,
                        1f to c.bg,
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(72.dp))

            // Logo
            Text("🌙", fontSize = 56.sp)
            Spacer(Modifier.height(20.dp))
            Text(
                "SleepWise",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Your intelligent sleep companion",
                fontSize = 15.sp,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            if (state.isLoading) {
                CircularProgressIndicator(color = c.primary, modifier = Modifier.size(40.dp))
            } else if (!showEmailForm) {
                // Google button
                AuthButton(
                    onClick = onGoogleClick,
                    isPrimary = true,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("G", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                        Text(
                            "Continue with Google",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Divider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = c.border)
                    Text("or", fontSize = 13.sp, color = c.textSecondary)
                    HorizontalDivider(modifier = Modifier.weight(1f), color = c.border)
                }

                Spacer(Modifier.height(12.dp))

                // Email button
                AuthButton(
                    onClick = { showEmailForm = true },
                    isPrimary = false,
                ) {
                    Text(
                        "Continue with Email",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.textPrimary,
                    )
                }
            } else {
                // Email form
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (isSignUp) "Create Account" else "Sign In",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    SleepTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = "Email address",
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    )
                    SleepTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = if (isSignUp) "Password (min 6 characters)" else "Password",
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        isPassword = true,
                        onImeAction = {
                            focusManager.clearFocus()
                            if (email.isNotBlank() && password.isNotBlank()) {
                                if (isSignUp) viewModel.signUpWithEmail(email, password)
                                else viewModel.signInWithEmail(email, password)
                            }
                        },
                    )

                    AuthButton(
                        onClick = {
                            if (email.isNotBlank() && password.isNotBlank()) {
                                if (isSignUp) viewModel.signUpWithEmail(email, password)
                                else viewModel.signInWithEmail(email, password)
                            }
                        },
                        isPrimary = true,
                    ) {
                        Text(
                            if (isSignUp) "Create Account" else "Sign In",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary,
                        )
                    }

                    Text(
                        text = if (isSignUp) "Already have an account? Sign In" else "New here? Create account",
                        fontSize = 14.sp,
                        color = c.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isSignUp = !isSignUp
                                viewModel.clearError()
                            }
                            .padding(vertical = 4.dp),
                    )

                    Text(
                        text = "← Back",
                        fontSize = 14.sp,
                        color = c.textSecondary,
                        modifier = Modifier
                            .clickable {
                                showEmailForm = false
                                isSignUp = false
                                viewModel.clearError()
                            }
                            .padding(vertical = 4.dp),
                    )
                }
            }

            state.error?.let { errorText ->
                Spacer(Modifier.height(20.dp))
                Text(
                    text = errorText,
                    fontSize = 13.sp,
                    color = Color(0xFFFF6B6B),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFF6B6B).copy(alpha = 0.1f))
                        .border(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                )
            }

            Spacer(Modifier.height(40.dp))

            Text(
                text = "Your sleep data belongs to you.\nWe never share or sell it.",
                fontSize = 12.sp,
                color = c.textSecondary.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun hasInternet(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@Composable
private fun AuthButton(
    onClick: () -> Unit,
    isPrimary: Boolean,
    content: @Composable () -> Unit,
) {
    val c = LocalSleepColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(50))
            .then(
                if (isPrimary) Modifier.background(Brush.horizontalGradient(listOf(c.primary, c.primaryEnd)))
                else Modifier.background(c.surface).border(1.dp, c.border, RoundedCornerShape(50))
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun SleepTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    isPassword: Boolean = false,
    onImeAction: () -> Unit = {},
) {
    val c = LocalSleepColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = c.textSecondary) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.primary,
            unfocusedBorderColor = c.border,
            focusedTextColor = c.textPrimary,
            unfocusedTextColor = c.textPrimary,
            cursorColor = c.primary,
            focusedContainerColor = c.surface2,
            unfocusedContainerColor = c.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
