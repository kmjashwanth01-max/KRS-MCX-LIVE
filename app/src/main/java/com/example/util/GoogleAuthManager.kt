package com.example.util

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.TitaniumBorder
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Handles Google Sign-In via Jetpack Credential Manager + Firebase Auth.
 * Strictly uses context.getString(R.string.default_web_client_id) generated from google-services.json.
 */
object GoogleAuthManager {

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: (FirebaseUser) -> Unit,
        onUnauthenticated: () -> Unit,
        scope: CoroutineScope
    ) {
        val currentUser = try {
            Firebase.auth.currentUser
        } catch (_: Exception) {
            null
        }
        if (currentUser != null) {
            onAuthSuccess(currentUser)
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(context.getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = context
                )
                handleSignInResult(result, onAuthSuccess) {}
            } catch (e: CancellationException) {
                throw e
            } catch (_: NoCredentialException) {
                onUnauthenticated()
            } catch (_: GetCredentialException) {
                onUnauthenticated()
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    suspend fun handleSignInResult(
        result: GetCredentialResponse,
        onAuthSuccess: (FirebaseUser) -> Unit,
        onAuthError: (String) -> Unit
    ) {
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = Firebase.auth.signInWithCredential(firebaseCredential).await()
                val user = authResult.user
                if (user != null) {
                    onAuthSuccess(user)
                } else {
                    onAuthError("Google authentication failed: empty user.")
                }
            } catch (e: GoogleIdTokenParsingException) {
                Log.e("GoogleAuthManager", "Invalid Google ID token response", e)
                onAuthError("Invalid Google ID token response.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("GoogleAuthManager", "Firebase signInWithCredential failed", e)
                onAuthError(e.localizedMessage ?: "Firebase Google Sign-In failed.")
            }
        } else {
            onAuthError("Unexpected credential type returned.")
        }
    }
}

@Composable
fun GoogleSignInButton(
    onAuthSuccess: (FirebaseUser) -> Unit,
    onAuthError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    OutlinedButton(
        onClick = {
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(
                serverClientId = context.getString(R.string.default_web_client_id)
            ).build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            coroutineScope.launch {
                try {
                    val result = credentialManager.getCredential(
                        request = request,
                        context = context
                    )
                    GoogleAuthManager.handleSignInResult(result, onAuthSuccess, onAuthError)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: GetCredentialCancellationException) {
                    // User dismissed the Google Sign-In sheet
                } catch (e: GetCredentialException) {
                    onAuthError(e.localizedMessage ?: "Google Sign-In unavailable on this device.")
                } catch (e: Exception) {
                    onAuthError(e.localizedMessage ?: "Google Sign-In failed.")
                }
            }
        },
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("google_sign_in_button")
    ) {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = "Sign in with Google",
            tint = GoldPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Continue with Google Account",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color.White
        )
    }
}
