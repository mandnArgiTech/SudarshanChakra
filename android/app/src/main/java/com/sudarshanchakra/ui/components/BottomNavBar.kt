package com.sudarshanchakra.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sudarshanchakra.ui.navigation.Routes
import com.sudarshanchakra.ui.theme.CriticalRed
import com.sudarshanchakra.ui.theme.NavBarBackground
import com.sudarshanchakra.ui.theme.NavBarInactive
import com.sudarshanchakra.ui.theme.Terracotta

data class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val isSiren: Boolean = false,
)

@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        NavItem(Routes.ALERTS, "Alerts", Icons.Filled.NotificationsActive, Icons.Outlined.Notifications),
        NavItem(Routes.CAMERAS, "Cameras", Icons.Filled.Videocam, Icons.Outlined.Videocam),
        NavItem(Routes.SIREN, "Siren", Icons.Filled.Campaign, Icons.Outlined.Campaign, isSiren = true),
        NavItem(Routes.DEVICES, "Devices", Icons.Filled.Sensors, Icons.Outlined.Sensors),
        NavItem(Routes.WATER_TANKS, "Water", Icons.Filled.WaterDrop, Icons.Outlined.WaterDrop),
        NavItem(Routes.PROFILE, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
    )

    NavigationBar(
        modifier = modifier.height(64.dp),
        containerColor = NavBarBackground,
        tonalElevation = 3.dp,
    ) {
        items.forEach { item ->
            val isSelected = currentRoute == item.route ||
                (item.route == Routes.ALERTS && currentRoute.startsWith("alertDetail"))
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        modifier = if (item.isSiren) Modifier.size(26.dp) else Modifier.size(24.dp),
                    )
                },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = if (item.isSiren) CriticalRed else Terracotta,
                    selectedTextColor = if (item.isSiren) CriticalRed else Terracotta,
                    indicatorColor = Terracotta.copy(alpha = 0.12f),
                    unselectedIconColor = NavBarInactive,
                    unselectedTextColor = NavBarInactive,
                ),
            )
        }
    }
}
