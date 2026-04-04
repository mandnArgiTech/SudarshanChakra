package com.sudarshanchakra.mdm

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sudarshanchakra.ui.navigation.NavGraph
import com.sudarshanchakra.ui.theme.SudarshanChakraTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class KioskLauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SudarshanChakraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    KioskLauncherContent()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val kioskManager = KioskManager(this)
        if (kioskManager.isDeviceOwner) {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
                startLockTask()
            }
        }
    }
}

private data class AppShortcut(
    val label: String,
    val packageName: String,
    val emoji: String,
)

private val whitelistedApps = listOf(
    AppShortcut("WhatsApp", "com.whatsapp", "\uD83D\uDCAC"),
    AppShortcut("YouTube", "com.google.android.youtube", "\u25B6\uFE0F"),
    AppShortcut("Maps", "com.google.android.apps.maps", "\uD83D\uDDFA\uFE0F"),
    AppShortcut("Camera", "com.android.camera2", "\uD83D\uDCF7"),
    AppShortcut("Phone", "com.google.android.dialer", "\uD83D\uDCDE"),
)

@Composable
private fun KioskLauncherContent() {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            NavGraph(
                mainNavViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
            )
        }
        WhitelistedAppBar(
            apps = whitelistedApps,
            onAppClick = { shortcut ->
                val intent = context.packageManager.getLaunchIntentForPackage(shortcut.packageName)
                if (intent != null) {
                    context.startActivity(intent)
                }
            },
        )
    }
}

@Composable
private fun WhitelistedAppBar(
    apps: List<AppShortcut>,
    onAppClick: (AppShortcut) -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (app in apps) {
            val installed = isAppInstalled(context, app.packageName)
            AppShortcutCard(
                app = app,
                installed = installed,
                onClick = { if (installed) onAppClick(app) },
            )
        }
    }
}

@Composable
private fun AppShortcutCard(
    app: AppShortcut,
    installed: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .size(72.dp)
            .alpha(if (installed) 1f else 0.4f)
            .clickable(enabled = installed, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(app.emoji, style = MaterialTheme.typography.headlineSmall)
            Text(
                app.label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun isAppInstalled(context: Context, packageName: String): Boolean {
    return try {
        context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
