package com.sudarshanchakra

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.sudarshanchakra.service.MqttForegroundService
import com.sudarshanchakra.ui.navigation.MainNavViewModel
import com.sudarshanchakra.ui.navigation.NavGraph
import com.sudarshanchakra.ui.theme.SudarshanChakraTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainNavViewModel: MainNavViewModel by viewModels()

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
}
