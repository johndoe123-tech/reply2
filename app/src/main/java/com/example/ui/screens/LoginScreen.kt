package com.example.ui.screens

import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.supabase.SupabaseClientProvider
import com.example.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isSignUpMode by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val auth = SupabaseClientProvider.client.auth

    // Keyframe shake animation for error state
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    0f at 0
                    (-14f) at 80
                    14f at 160
                    (-10f) at 240
                    10f at 320
                    0f at 400
                }
            )
        }
    }

    Scaffold(
        containerColor = AuthBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2B1F45),
                            AuthBackground
                        ),
                        center = Offset(0.5f, 0.32f),
                        radius = 1200f
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Signature Breathing Orb
                BreathingOrb()

                Spacer(modifier = Modifier.height(24.dp))

                // Headline & Subtitle
                Text(
                    text = "AutoReply",
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    color = AuthTextPrimary,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                AnimatedContent(
                    targetState = isSignUpMode,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                    },
                    label = "SubtitleTransition"
                ) { signUp ->
                    Text(
                        text = if (signUp) "Join to sync your contacts & memories securely." else "Quietly listening to your messages before it speaks for you.",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        color = AuthTextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Glass Form Card with Shake animation
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = AuthSurface.copy(alpha = 0.92f)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationX = shakeOffset.value }
                        .border(
                            width = 1.dp,
                            color = AuthSurfaceBorder,
                            shape = RoundedCornerShape(24.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Error message
                        AnimatedVisibility(
                            visible = errorMessage != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Surface(
                                color = AuthError.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AuthError.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            ) {
                                Text(
                                    text = errorMessage ?: "",
                                    color = AuthError,
                                    fontFamily = InterFontFamily,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        // Info message
                        AnimatedVisibility(
                            visible = infoMessage != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Surface(
                                color = AuthAccentTeal.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AuthAccentTeal.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            ) {
                                Text(
                                    text = infoMessage ?: "",
                                    color = AuthAccentTeal,
                                    fontFamily = InterFontFamily,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        // Email Field
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = {
                                emailInput = it
                                errorMessage = null
                            },
                            label = { Text("Email Address", fontFamily = InterFontFamily) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = AuthTextMuted
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AuthAccentTeal,
                                unfocusedBorderColor = AuthSurfaceBorder,
                                focusedLabelColor = AuthAccentTeal,
                                unfocusedLabelColor = AuthTextMuted,
                                focusedTextColor = AuthTextPrimary,
                                unfocusedTextColor = AuthTextPrimary,
                                cursorColor = AuthAccentTeal
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_email_input")
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Password Field
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = {
                                passwordInput = it
                                errorMessage = null
                            },
                            label = { Text("Password", fontFamily = InterFontFamily) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = AuthTextMuted
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                        tint = AuthTextMuted
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AuthAccentTeal,
                                unfocusedBorderColor = AuthSurfaceBorder,
                                focusedLabelColor = AuthAccentTeal,
                                unfocusedLabelColor = AuthTextMuted,
                                focusedTextColor = AuthTextPrimary,
                                unfocusedTextColor = AuthTextPrimary,
                                cursorColor = AuthAccentTeal
                            ),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (emailInput.isNotBlank() && passwordInput.isNotBlank() && !isLoading) {
                                        executeAuth(
                                            context = context,
                                            auth = auth,
                                            emailInput = emailInput,
                                            passwordInput = passwordInput,
                                            isSignUpMode = isSignUpMode,
                                            scope = scope,
                                            setLoading = { isLoading = it },
                                            setError = { errorMessage = it },
                                            setInfo = { infoMessage = it },
                                            onSuccess = onLoginSuccess
                                        )
                                    }
                                }
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_password_input")
                        )

                        if (!isSignUpMode) {
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(
                                onClick = {
                                    resetEmail = emailInput
                                    showForgotPasswordDialog = true
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(
                                    text = "Forgot password?",
                                    fontFamily = InterFontFamily,
                                    fontSize = 13.sp,
                                    color = AuthTextMuted
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Gradient Button with Press Scale Animation
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val buttonScale by animateFloatAsState(
                            targetValue = if (isPressed) 0.97f else 1.0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "ButtonScale"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .scale(buttonScale)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(AuthAccentTeal, AuthAccentCoral),
                                        start = Offset(0f, 0f),
                                        end = Offset(800f, 800f)
                                    )
                                )
                        ) {
                            Button(
                                onClick = {
                                    if (emailInput.isBlank() || passwordInput.isBlank()) {
                                        errorMessage = "Please enter both email and password."
                                        return@Button
                                    }
                                    executeAuth(
                                        context = context,
                                        auth = auth,
                                        emailInput = emailInput,
                                        passwordInput = passwordInput,
                                        isSignUpMode = isSignUpMode,
                                        scope = scope,
                                        setLoading = { isLoading = it },
                                        setError = { errorMessage = it },
                                        setInfo = { infoMessage = it },
                                        onSuccess = onLoginSuccess
                                    )
                                },
                                enabled = !isLoading,
                                interactionSource = interactionSource,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent
                                ),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("login_submit_button")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color(0xFF0D0915),
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    AnimatedContent(
                                        targetState = isSignUpMode,
                                        transitionSpec = {
                                            (fadeIn(tween(220)) + slideInVertically { height -> height / 2 }) togetherWith
                                                    (fadeOut(tween(180)) + slideOutVertically { height -> -height / 2 })
                                        },
                                        label = "ButtonLabelTransition"
                                    ) { signUp ->
                                        Text(
                                            text = if (signUp) "Create Account" else "Log In",
                                            fontFamily = SoraFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color(0xFF0D0915)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Toggle Mode Button with Animated Content
                        TextButton(
                            onClick = {
                                isSignUpMode = !isSignUpMode
                                errorMessage = null
                                infoMessage = null
                            },
                            modifier = Modifier.height(48.dp)
                        ) {
                            AnimatedContent(
                                targetState = isSignUpMode,
                                transitionSpec = {
                                    fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                                },
                                label = "ToggleTextTransition"
                            ) { signUp ->
                                Text(
                                    text = if (signUp) "Already have an account? Log in" else "Don't have an account? Create one",
                                    fontFamily = InterFontFamily,
                                    fontSize = 14.sp,
                                    color = AuthTextMuted
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Reset Password Dialog
    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            containerColor = AuthSurface,
            title = {
                Text(
                    text = "Reset Password",
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = AuthTextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter your account email to receive a password reset link.",
                        fontFamily = InterFontFamily,
                        color = AuthTextMuted,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Email Address", fontFamily = InterFontFamily) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AuthAccentTeal,
                            unfocusedBorderColor = AuthSurfaceBorder,
                            focusedLabelColor = AuthAccentTeal,
                            unfocusedLabelColor = AuthTextMuted,
                            focusedTextColor = AuthTextPrimary,
                            unfocusedTextColor = AuthTextPrimary,
                            cursorColor = AuthAccentTeal
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (resetEmail.isNotBlank()) {
                            scope.launch {
                                try {
                                    auth.resetPasswordForEmail(resetEmail.trim())
                                    infoMessage = "Password reset email sent to $resetEmail"
                                } catch (e: Exception) {
                                    errorMessage = e.localizedMessage ?: "Failed to send reset email."
                                }
                            }
                        }
                        showForgotPasswordDialog = false
                    }
                ) {
                    Text(
                        text = "Send Email",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = AuthAccentTeal
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text(
                        text = "Cancel",
                        fontFamily = InterFontFamily,
                        color = AuthTextMuted
                    )
                }
            }
        )
    }
}

private fun executeAuth(
    context: android.content.Context,
    auth: io.github.jan.supabase.auth.Auth,
    emailInput: String,
    passwordInput: String,
    isSignUpMode: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    setLoading: (Boolean) -> Unit,
    setError: (String?) -> Unit,
    setInfo: (String?) -> Unit,
    onSuccess: () -> Unit
) {
    val cleanEmail = emailInput.trim().lowercase()
    val cleanPassword = passwordInput.trim()

    if (cleanEmail.isBlank() || cleanPassword.isBlank()) {
        setError("Please enter both email and password.")
        return
    }

    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
        setError("Please enter a valid email address (e.g. name@example.com).")
        return
    }

    if (cleanPassword.length < 6) {
        setError("Password must be at least 6 characters long.")
        return
    }

    setLoading(true)
    setError(null)
    setInfo(null)

    scope.launch {
        try {
            val db = com.example.data.db.AppDatabase.getDatabase(context)
            val prefs = com.example.data.pref.UserPreferencesRepository.getInstance(context)

            if (isSignUpMode) {
                auth.signUpWith(Email) {
                    email = cleanEmail
                    password = cleanPassword
                }
                setLoading(false)
                val newUserId = auth.currentUserOrNull()?.id
                if (newUserId != null) {
                    // Always wipe local database on new account creation so new user gets clean state
                    db.clearUserData()
                    prefs.updateLastUserId(newUserId)
                    onSuccess()
                } else {
                    setInfo("Account created! If email confirmation is enabled, please check your inbox before logging in.")
                }
            } else {
                auth.signInWith(Email) {
                    email = cleanEmail
                    password = cleanPassword
                }
                setLoading(false)
                val currentUserId = auth.currentUserOrNull()?.id
                if (currentUserId != null) {
                    val lastUserId = prefs.lastUserIdFlow.firstOrNull()
                    if (lastUserId == null || lastUserId != currentUserId) {
                        // User changed! Clear old local database and pull this user's records from cloud
                        db.clearUserData()
                        prefs.updateLastUserId(currentUserId)
                        com.example.data.supabase.SupabaseSyncRepository(db).restoreAllDataFromCloud(context)
                    }
                }
                onSuccess()
            }
        } catch (e: Exception) {
            setLoading(false)
            val rawMsg = e.localizedMessage ?: e.message ?: "Authentication failed."
            val friendlyMsg = when {
                rawMsg.contains("invalid", ignoreCase = true) && rawMsg.contains("email", ignoreCase = true) ->
                    "Invalid email address. Please enter a valid email (e.g. name@domain.com)."
                rawMsg.contains("already registered", ignoreCase = true) || rawMsg.contains("already exists", ignoreCase = true) || rawMsg.contains("user_already_exists", ignoreCase = true) ->
                    "User already registered. Please switch to 'Log In' or use a different email."
                rawMsg.contains("credentials", ignoreCase = true) || rawMsg.contains("invalid_grant", ignoreCase = true) ->
                    "Invalid email or password. Please check your credentials."
                else -> rawMsg
            }
            setError(friendlyMsg)
        }
    }
}

@Composable
fun BreathingOrb(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isReducedMotion = remember(context) {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (e: Exception) {
            false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "OrbBreathing")

    val scale by if (isReducedMotion) {
        remember { mutableStateOf(1.0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "OrbScale"
        )
    }

    val colorProgress by if (isReducedMotion) {
        remember { mutableStateOf(0.5f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(6000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "OrbColor"
        )
    }

    val currentColor = lerp(AuthAccentTeal, AuthAccentCoral, colorProgress)

    Box(
        modifier = modifier
            .semantics { contentDescription = "AI listening indicator" },
        contentAlignment = Alignment.Center
    ) {
        // Outer Glow Halo
        Box(
            modifier = Modifier
                .size(130.dp)
                .scale(scale * 1.15f)
                .blur(24.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            currentColor.copy(alpha = 0.55f),
                            currentColor.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Core Breathing Orb Canvas
        Canvas(
            modifier = Modifier
                .size(96.dp)
                .scale(scale)
        ) {
            val centerOffset = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width / 2f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        currentColor,
                        currentColor.copy(alpha = 0.85f),
                        currentColor.copy(alpha = 0.4f)
                    ),
                    center = centerOffset,
                    radius = radius
                ),
                radius = radius,
                center = centerOffset
            )
        }
    }
}
