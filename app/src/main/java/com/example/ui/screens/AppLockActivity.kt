package com.example.ui.screens

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.FocusGuardApplication
import com.example.data.local.FocusGuardPreferences
import com.example.data.local.SecurityManager
import com.example.ui.theme.FocusGuardTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppLockActivity : FragmentActivity() {

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
        const val EXTRA_MODE = "extra_mode"
        const val MODE_UNLOCK = "MODE_UNLOCK"
        const val MODE_SETUP = "MODE_SETUP"
    }

    private var targetPackage: String? = null
    private var isSetupMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_UNLOCK
        isSetupMode = (mode == MODE_SETUP)

        val app = application as FocusGuardApplication
        val securityManager = app.securityManager

        lifecycleScope.launch {
            val isPinSet = securityManager.isPinConfigured()
            val biometricsEnabled = app.preferences.biometricsEnabledFlow.first()

            if (!isPinSet) {
                isSetupMode = true
            }

            setContent {
                FocusGuardTheme {
                    BackHandler {
                        onBackPressedAction()
                    }

                    LockScreenContent(
                        isSetup = isSetupMode,
                        targetPackage = targetPackage,
                        isBiometricAvailable = securityManager.isBiometricAvailable(),
                        onPinEntered = { pin, onResult ->
                            handlePinSubmission(pin, onResult)
                        },
                        onBiometricClick = {
                            showBiometricPrompt()
                        }
                    )
                }
            }

            if (!isSetupMode && biometricsEnabled && securityManager.isBiometricAvailable()) {
                showBiometricPrompt()
            }
        }
    }

    private fun handlePinSubmission(pin: String, onResult: (Boolean) -> Unit) {
        val app = application as FocusGuardApplication
        val securityManager = app.securityManager

        lifecycleScope.launch {
            if (isSetupMode) {
                securityManager.setupNewPin(pin)
                Toast.makeText(this@AppLockActivity, "Security PIN created successfully", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                val success = securityManager.verifyPin(pin)
                if (success) {
                    SecurityManager.markUnlocked()
                    finish()
                } else {
                    onResult(false)
                }
            }
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    SecurityManager.markUnlocked()
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("FocusGuard Authentication")
            .setSubtitle("Unlock FocusGuard using your biometric credential")
            .setNegativeButtonText("Use PIN")
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun onBackPressedAction() {
        if (targetPackage != null) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        }
        finish()
    }
}

@Composable
fun LockScreenContent(
    isSetup: Boolean,
    targetPackage: String?,
    isBiometricAvailable: Boolean,
    onPinEntered: (String, (Boolean) -> Unit) -> Unit,
    onBiometricClick: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var setupFirstPin by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val promptTitle = when {
        isSetup && setupFirstPin == null -> "Create FocusGuard PIN"
        isSetup && setupFirstPin != null -> "Confirm Your PIN"
        targetPackage != null -> "Protected Application"
        else -> "FocusGuard Locked"
    }

    val promptSubtitle = when {
        isSetup && setupFirstPin == null -> "Choose a 4-digit security PIN to protect your focus rules and commitment locks."
        isSetup && setupFirstPin != null -> "Re-enter the 4-digit PIN to confirm."
        else -> "Enter your 4-digit PIN or use biometric scan to unlock."
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSetup) Icons.Default.Security else Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = promptTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = promptSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // PIN Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                        )
                    }
                }

                AnimatedVisibility(visible = errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            // Keypad
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("BIO", "0", "DEL")
                )

                for (row in rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (key in row) {
                            when (key) {
                                "BIO" -> {
                                    if (isBiometricAvailable && !isSetup) {
                                        IconButton(
                                            onClick = onBiometricClick,
                                            modifier = Modifier
                                                .size(72.dp)
                                                .testTag("pin_biometric_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Fingerprint,
                                                contentDescription = "Biometric Unlock",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(72.dp))
                                    }
                                }
                                "DEL" -> {
                                    IconButton(
                                        onClick = {
                                            if (enteredPin.isNotEmpty()) {
                                                enteredPin = enteredPin.dropLast(1)
                                                errorMessage = null
                                            }
                                        },
                                        modifier = Modifier
                                            .size(72.dp)
                                            .testTag("pin_delete_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                if (enteredPin.length < 4) {
                                                    val newPin = enteredPin + key
                                                    enteredPin = newPin
                                                    errorMessage = null

                                                    if (newPin.length == 4) {
                                                        if (isSetup) {
                                                            if (setupFirstPin == null) {
                                                                setupFirstPin = newPin
                                                                enteredPin = ""
                                                            } else {
                                                                if (newPin == setupFirstPin) {
                                                                    onPinEntered(newPin) { }
                                                                } else {
                                                                    errorMessage = "PINs do not match. Try again."
                                                                    setupFirstPin = null
                                                                    enteredPin = ""
                                                                }
                                                            }
                                                        } else {
                                                            onPinEntered(newPin) { success ->
                                                                if (!success) {
                                                                    errorMessage = "Incorrect PIN. Try again."
                                                                    enteredPin = ""
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            .testTag("pin_key_$key"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = key,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
