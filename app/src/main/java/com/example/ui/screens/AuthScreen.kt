package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AuthScreen(
    onAuthSuccess: (userEmail: String?) -> Unit,
    onContinueAsGuest: () -> Unit = {},
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember {
        try {
            CredentialManager.create(context)
        } catch (_: Throwable) {
            null
        }
    }
    var isLoading by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler {
        if (onBackClick != null) {
            onBackClick()
        } else {
            onContinueAsGuest()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F2B48),
                        Color(0xFF0A192F),
                        Color(0xFF040D1A)
                    )
                )
            )
    ) {
        if (onBackClick != null) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .padding(top = 40.dp, end = 16.dp)
                    .align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "رجوع إلى الكتالوج",
                    tint = Color.White
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Branding Logo
            Surface(
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier
                    .size(105.dp)
                    .clip(CircleShape)
                    .border(3.dp, Color(0xFF38BDF8), CircleShape)
                    .shadow(8.dp, CircleShape)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_albaraa),
                    contentDescription = "شعار البراء إلكترونيك",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "البراء إلكترونيك",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "المنظومة الشاملة لمخططات وضفائر ومواتير الغسالات",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Interactive Google Sign-In Button
            Button(
                onClick = {
                    isLoading = true
                    handleInteractiveGoogleSignIn(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = { signedInEmail ->
                            isLoading = false
                            onAuthSuccess(signedInEmail)
                        },
                        onAuthError = { errorMsg ->
                            isLoading = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(errorMsg)
                            }
                        },
                        onCancelled = {
                            isLoading = false
                        },
                        scope = coroutineScope
                    )
                },
                modifier = Modifier
                    .testTag("google_sign_in_button")
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0284C7),
                    contentColor = Color.White
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "G",
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "تسجيل الدخول عبر Google",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Skip / Continue as Guest Button
            OutlinedButton(
                onClick = onContinueAsGuest,
                modifier = Modifier
                    .testTag("skip_as_guest_button")
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFF1F5F9)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "دخول كضيف",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "تخطي / دخول كضيف",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF8FAFC)
                        )
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}



private const val DEFAULT_WEB_CLIENT_ID_FALLBACK = "194875724444-sj3st9c7cvgbic0lgtcnaa3m6mpehq7i.apps.googleusercontent.com"

private fun getGoogleWebClientId(context: Context): String {
    return try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) {
                context.getString(resId)
            } else {
                DEFAULT_WEB_CLIENT_ID_FALLBACK
            }
        } catch (_: Exception) {
            DEFAULT_WEB_CLIENT_ID_FALLBACK
        }
    }
}

fun attemptSilentAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    scope: CoroutineScope
) {
    val app = com.example.util.FirebaseInitializer.ensureInitialized(context)
    val firebaseAuth = if (app != null) {
        try { com.google.firebase.auth.FirebaseAuth.getInstance(app) } catch (_: Throwable) { null }
    } else {
        try { Firebase.auth } catch (_: Throwable) { null }
    }

    if (firebaseAuth?.currentUser != null) {
        onAuthSuccess()
        return
    }

    val clientId = getGoogleWebClientId(context)
    if (clientId.isBlank()) {
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val currentAuth = firebaseAuth ?: try { Firebase.auth } catch (_: Throwable) { null }
                currentAuth?.signInWithCredential(authCredential)?.await()
                onAuthSuccess()
            }
        } catch (_: Exception) {
            // Fallback gracefully to interactive sign in
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun handleInteractiveGoogleSignIn(
    context: Context,
    credentialManager: CredentialManager?,
    onAuthSuccess: (userEmail: String?) -> Unit,
    onAuthError: (String) -> Unit,
    onCancelled: () -> Unit,
    scope: CoroutineScope
) {
    try {
        val app = com.example.util.FirebaseInitializer.ensureInitialized(context)
        if (credentialManager == null) {
            onAuthError("خدمات تسجيل الدخول غير متوفرة على هذا الجهاز")
            return
        }
        val targetActivity = context.findActivity()
        if (targetActivity == null) {
            onAuthError("تعذر العثور على شاشة النشاط الرئيسية للمتابعة")
            return
        }
        val clientId = getGoogleWebClientId(context)
        if (clientId.isBlank()) {
            onAuthError("إعدادات Google Sign-In غير متوفرة حالياً")
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(targetActivity, request)
                val credential = result.credential
                val googleIdToken: String? = try {
                    if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        try {
                            GoogleIdTokenCredential.createFrom(credential.data).idToken
                        } catch (_: Throwable) {
                            credential.data.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID_TOKEN")
                                ?: credential.data.getString("id_token")
                                ?: credential.data.getString("idToken")
                        }
                    } else if (credential is CustomCredential) {
                        try {
                            GoogleIdTokenCredential.createFrom(credential.data).idToken
                        } catch (_: Throwable) {
                            credential.data.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID_TOKEN")
                                ?: credential.data.getString("id_token")
                                ?: credential.data.getString("idToken")
                        }
                    } else {
                        val bundle = try {
                            val dataField = credential::class.java.getDeclaredMethod("getData")
                            dataField.invoke(credential) as? android.os.Bundle
                        } catch (_: Throwable) {
                            null
                        }
                        bundle?.let { b ->
                            try {
                                GoogleIdTokenCredential.createFrom(b).idToken
                            } catch (_: Throwable) {
                                b.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID_TOKEN")
                                    ?: b.getString("id_token")
                                    ?: b.getString("idToken")
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.e("Auth", "Failed extracting Google ID token", e)
                    null
                }

                if (googleIdToken != null) {
                    val activeApp = app ?: com.example.util.FirebaseInitializer.ensureInitialized(context)
                    val firebaseAuth = if (activeApp != null) {
                        com.google.firebase.auth.FirebaseAuth.getInstance(activeApp)
                    } else {
                        try { Firebase.auth } catch (_: Throwable) { com.google.firebase.auth.FirebaseAuth.getInstance() }
                    }
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = firebaseAuth.signInWithCredential(authCredential).await()
                    val email = authResult.user?.email ?: firebaseAuth.currentUser?.email
                    Log.i("Auth", "Google sign in completed successfully. User email: $email, uid: ${authResult.user?.uid}")
                    onAuthSuccess(email)
                } else {
                    onAuthError("تعذر استخراج بيانات المصادقة من حساب Google")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Google Sign-In flow cancelled: ${e.message}", e)
                onCancelled()
            } catch (e: Throwable) {
                Log.e("Auth", "Google Sign-In failed", e)
                onAuthError(e.localizedMessage ?: "فشل تسجيل الدخول عبر Google")
            }
        }
    } catch (t: Throwable) {
        Log.e("Auth", "Unexpected error initiating Google Sign-In", t)
        onAuthError("حدث خطأ غير متوقع أثناء بدء تسجيل الدخول: ${t.localizedMessage ?: ""}")
    }
}
