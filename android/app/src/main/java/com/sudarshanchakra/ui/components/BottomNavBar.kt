package com.sudarshanchakra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sudarshanchakra.ui.navigation.Routes
import com.sudarshanchakra.ui.theme.CriticalRed
import com.sudarshanchakra.ui.theme.NavBarBackground
import com.sudarshanchakra.ui.theme.NavBarInactive
import com.sudarshanchakra.ui.theme.Terracotta

data class NavItem(
    val route: String,
    val label: String,
    val icon: String
)

@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavItem(Routes.ALERTS, "Alerts", "🔔"),
        NavItem(Routes.CAMERAS, "Cameras", "📷"),
        NavItem(Routes.SIREN, "Siren", "🚨"),
        NavItem(Routes.DEVICES, "Devices", "📡"),
        NavItem(Routes.PROFILE, "Profile", "👤")
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp)
            .background(NavBarBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = currentRoute == item.route ||
                    (item.route == Routes.ALERTS && currentRoute?.startsWith("alertDetail") == true)
                val isSiren = item.route == Routes.SIREN

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onNavigate(item.route) }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isSiren) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) CriticalRed else CriticalRed.copy(alpha = 0.8f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = item.icon, fontSize = 18.sp)
                        }
                    } else {
                        Text(
                            text = item.icon,
                            fontSize = 22.sp
                        )
                    }

                    Text(
                        text = item.label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = when {
                            isSiren -> CriticalRed
                            isSelected -> Terracotta
                            else -> NavBarInactive
                        },
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
