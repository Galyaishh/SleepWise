package com.example.sleepwisepoc.auth

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sleepwisepoc.R
import com.example.sleepwisepoc.ui.theme.Eyebrow
import com.example.sleepwisepoc.ui.theme.InstrumentSerif
import com.example.sleepwisepoc.ui.theme.LocalSleepColors
import com.example.sleepwisepoc.ui.theme.PlexMono
import com.example.sleepwisepoc.ui.theme.PlexSans
import com.example.sleepwisepoc.ui.theme.PrimaryButton
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(viewModel: AuthViewModel = viewModel()) {
    val c = LocalSleepColors.current
    val state by viewModel.state.collectAsState()
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val submitEmail: () -> Unit = {
        if (email.isNotBlank() && password.isNotBlank()) {
            if (isSignUp) viewModel.signUpWithEmail(email, password)
            else viewModel.signInWithEmail(email, password)
        }
    }

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
            .background(c.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp),
        ) {
            Spacer(Modifier.height(72.dp))

            Eyebrow("SleepWise · Wake up well")
            Spacer(Modifier.height(26.dp))

            // Heading + lede
            Text(
                text = if (isSignUp) "Create an account" else "Welcome back",
                fontFamily = InstrumentSerif,
                fontSize = 40.sp,
                lineHeight = 44.sp,
                color = c.text,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Your nights are stored on your device.",
                fontFamily = PlexSans,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                color = c.dim,
            )

            Spacer(Modifier.height(34.dp))

            // Email + password fields
            NfField(
                label = "Email",
                value = email,
                onValueChange = { email = it },
                placeholder = "you@example.com",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
            )
            Spacer(Modifier.height(18.dp))
            NfField(
                label = "Password",
                value = password,
                onValueChange = { password = it },
                placeholder = if (isSignUp) "At least 6 characters" else "Your password",
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                isPassword = true,
                onImeAction = {
                    focusManager.clearFocus()
                    submitEmail()
                },
            )

            // Error notice
            state.error?.let { errorText ->
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.accentSoft)
                        .border(1.dp, c.accent, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = errorText,
                        fontFamily = PlexSans,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = c.accent,
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            // Primary action / loading
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = c.accent, modifier = Modifier.size(30.dp))
                }
            } else {
                PrimaryButton(
                    text = if (isSignUp) "Create account" else "Sign in",
                    onClick = submitEmail,
                    enabled = email.isNotBlank() && password.isNotBlank(),
                )

                Spacer(Modifier.height(22.dp))

                OrDivider()

                Spacer(Modifier.height(22.dp))

                GoogleButton(onClick = onGoogleClick)
            }

            Spacer(Modifier.height(34.dp))

            // Footer link — toggles between sign-in and create-account
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isSignUp) "Already have an account?" else "New here?",
                    fontFamily = PlexSans,
                    fontSize = 14.sp,
                    color = c.dim,
                )
                Text(
                    text = if (isSignUp) "Sign in" else "Create an account",
                    fontFamily = PlexSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = c.accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clickable {
                            isSignUp = !isSignUp
                            viewModel.clearError()
                        }
                        .padding(start = 6.dp, top = 6.dp, bottom = 6.dp),
                )
            }

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

// ─── "or" divider: hairline / faint 12sp / hairline ────────────────────────────
@Composable
private fun OrDivider() {
    val c = LocalSleepColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(c.line))
        Text("or", fontFamily = PlexSans, fontSize = 12.sp, color = c.faint)
        Box(Modifier.weight(1f).height(1.dp).background(c.line))
    }
}

// ─── Secondary "Continue with Google" button (SecondaryButton metrics + mark) ───
@Composable
private fun GoogleButton(onClick: () -> Unit) {
    val c = LocalSleepColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, c.lineStrong, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GoogleMark(Modifier.size(20.dp))
            Text(
                "Continue with Google",
                fontFamily = PlexSans,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = c.text,
            )
        }
    }
}

/** Four-color Google "G", drawn from primitives — a ring split into four arcs
 *  (blue right, red top, yellow left, green bottom) with a blue crossbar. */
@Composable
private fun GoogleMark(modifier: Modifier = Modifier) {
    val blue = Color(0xFF4285F4)
    val red = Color(0xFFEA4335)
    val yellow = Color(0xFFFBBC05)
    val green = Color(0xFF34A853)
    Canvas(modifier) {
        val sw = size.minDimension * 0.24f
        val inset = sw / 2f
        val arcSize = Size(size.width - sw, size.height - sw)
        val topLeft = Offset(inset, inset)
        fun ring(color: Color, start: Float, sweep: Float) = drawArc(
            color = color,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = sw, cap = StrokeCap.Butt),
        )
        // 0° = 3 o'clock; negative sweeps go counter-clockwise (upward).
        // Ring leaves a gap on the right where the crossbar enters.
        ring(blue, -12f, -78f)    // right → top-right
        ring(red, -90f, -78f)     // top → upper-left
        ring(yellow, -168f, -78f) // left → lower-left
        ring(green, -246f, -78f)  // bottom → lower-right
        // Blue crossbar: from centre out to the right edge, at the vertical middle.
        drawRect(
            color = blue,
            topLeft = Offset(size.width * 0.5f, size.height / 2f - sw / 2f),
            size = Size(size.width * 0.5f - inset, sw),
        )
    }
}

// ─── Styled field: mono micro label + surface BasicTextField (radius 16) ───────
@Composable
private fun NfField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    isPassword: Boolean = false,
    onImeAction: () -> Unit = {},
) {
    val c = LocalSleepColors.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            fontFamily = PlexMono,
            fontSize = 10.sp,
            letterSpacing = 1.6.sp,
            color = c.faint,
        )
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = PlexSans, fontSize = 16.sp, color = c.text),
            cursorBrush = SolidColor(c.accent),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.surface)
                        .border(1.dp, c.lineStrong, RoundedCornerShape(16.dp))
                        .padding(horizontal = 18.dp, vertical = 17.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontFamily = PlexSans,
                            fontSize = 16.sp,
                            color = c.faint,
                        )
                    }
                    inner()
                }
            },
        )
    }
}
