package com.example.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle

object BiometricAuthManager {

    /**
     * Checks if biometric hardware (Fingerprint, Face unlock, or Iris) is present and enrolled.
     */
    fun isBiometricAvailable(context: Context): Boolean {
        return try {
            val biometricManager = BiometricManager.from(context)
            val canAuth = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            canAuth == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Detailed status text for settings UI.
     */
    fun canAuthenticateStatus(context: Context): String {
        return try {
            val biometricManager = BiometricManager.from(context)
            when (biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )) {
                BiometricManager.BIOMETRIC_SUCCESS -> "Fingerprint & Face unlock hardware ready"
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "No biometric sensor hardware on device"
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Biometric sensor temporarily busy"
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No fingerprint or face registered in Android Settings"
                else -> "Biometric security unavailable"
            }
        } catch (_: Throwable) {
            "Biometrics not supported"
        }
    }

    /**
     * Authenticate via BiometricPrompt (supports Fingerprint and Face Unlock).
     * confirmationRequired is set to false for instant unlock upon recognition.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "KRS Bullion Security",
        subtitle: String = "Scan fingerprint or face to unlock app",
        negativeButtonText: String = "Use 4-Digit PIN",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            if (activity.isFinishing || activity.isDestroyed) {
                return
            }

            if (!isBiometricAvailable(activity)) {
                onError("Biometric sensor not enrolled or available. Please use your 4-digit PIN.")
                return
            }

            val executor = ContextCompat.getMainExecutor(activity)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText(negativeButtonText)
                .setConfirmationRequired(false) // Instant unlock for face and fingerprint recognition
                .build()

            val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // If user tapped negative button ("Use 4-Digit PIN") or cancelled, do not treat as error toast
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Biometric not recognized. Please retry or enter your 4-digit PIN.")
                }
            })

            prompt.authenticate(promptInfo)
        } catch (e: Throwable) {
            onError(e.message ?: "Could not launch biometric prompt")
        }
    }
}
