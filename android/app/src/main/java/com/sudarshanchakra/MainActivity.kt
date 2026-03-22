package com.sudarshanchakra

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.sudarshanchakra.data.repository.AppLockPreferencesRepository
import com.sudarshanchakra.service.MqttForegroundService
import com.sudarshanchakra.ui.navigation.MainNavViewModel
import com.sudarshanchakra.ui.navigation.NavGraph
import com.sudarshanchakra.ui.theme.SudarshanChakraTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val mainNavViewModel: MainNavViewModel by viewModels()

    @Inject
    lateinit var appLockPreferencesRepository: AppLockPreferencesRepository

    /** True after [onStop] — ignore first [onStart] after cold launch. */
    private var returnFromBackground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            SudarshanChakraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavGraph(mainNavViewModel = mainNavViewModel)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        returnFromBackground = true
    }

    override fun onStart() {
        super.onStart()
        if (!returnFromBackground) return
        returnFromBackground = false
        lifecycleScope.launch {
            val lockOn = appLockPreferencesRepository.lockOnResumeEnabled.first()
            if (!lockOn) return@launch
            runOnUiThread { showUnlockPrompt() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getStringExtra(MqttForegroundService.EXTRA_NAVIGATE_TO) == MqttForegroundService.NAV_ALERT_DETAIL) {
            intent.getStringExtra(MqttForegroundService.EXTRA_ALERT_ID)?.let { id ->
                mainNavViewModel.offerAlertDeepLink(id)
            }
        }
    }

    private fun showUnlockPrompt() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val mgr = BiometricManager.from(this)
        val can = mgr.canAuthenticate(authenticators)
        if (can == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ||
            can == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
        ) {
            Log.w(TAG, "Biometric unavailable ($can); skipping app lock")
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Log.w(TAG, "Unlock error $errorCode: $errString")
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                Log.d(TAG, "App unlocked")
            }
        }
        val prompt = BiometricPrompt(this, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock SudarshanChakra")
            .setSubtitle("Use biometric or screen lock")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
