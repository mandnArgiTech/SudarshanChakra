package com.sudarshanchakra.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sudarshanchakra.ui.components.BottomNavBar
import com.sudarshanchakra.ui.screens.alerts.AlertDetailScreen
import com.sudarshanchakra.ui.screens.alerts.AlertFeedScreen
import com.sudarshanchakra.ui.screens.cameras.CameraGridScreen
import com.sudarshanchakra.ui.screens.devices.DeviceStatusScreen
import com.sudarshanchakra.ui.screens.login.LoginScreen
import com.sudarshanchakra.ui.screens.siren.SirenControlScreen
import com.sudarshanchakra.ui.screens.water.MotorControlScreen
import com.sudarshanchakra.ui.screens.water.WaterTanksScreen

object Routes {
    const val LOGIN        = "login"
    const val ALERTS       = "alerts"
    const val ALERT_DETAIL = "alertDetail/{id}"
    const val CAMERAS      = "cameras"
    const val SIREN        = "siren"
    const val DEVICES      = "devices"
    const val PROFILE      = "profile"
    // ── Water ────────────────────────────────────────────────────────────────
    const val WATER_TANKS    = "water_tanks"
    const val MOTOR_CONTROL  = "motor_control/{motorId}"

    fun alertDetail(id: String) = "alertDetail/$id"
    fun motorControl(motorId: String) = "motor_control/$motorId"
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute != Routes.LOGIN

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentRoute = currentRoute ?: Routes.ALERTS,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Routes.ALERTS) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController    = navController,
            startDestination = Routes.LOGIN,
            modifier         = Modifier.padding(paddingValues),
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(onLoginSuccess = {
                    navController.navigate(Routes.ALERTS) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                })
            }

            composable(Routes.ALERTS) {
                AlertFeedScreen(
                    onAlertClick      = { id -> navController.navigate(Routes.alertDetail(id)) },
                    onSirenClick      = { navController.navigate(Routes.SIREN) },
                    onWaterCardClick  = { navController.navigate(Routes.WATER_TANKS) },
                )
            }

            composable(
                route     = Routes.ALERT_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { back ->
                AlertDetailScreen(
                    alertId    = back.arguments?.getString("id") ?: "",
                    onBackClick  = { navController.popBackStack() },
                    onSirenClick = { navController.navigate(Routes.SIREN) },
                )
            }

            composable(Routes.CAMERAS) { CameraGridScreen() }
            composable(Routes.SIREN)   { SirenControlScreen() }
            composable(Routes.DEVICES) { DeviceStatusScreen() }
            composable(Routes.PROFILE) { DeviceStatusScreen() }

            // ── Water ────────────────────────────────────────────────────────
            composable(Routes.WATER_TANKS) {
                WaterTanksScreen(
                    onMotorClick = { motorId -> navController.navigate(Routes.motorControl(motorId)) },
                )
            }

            composable(
                route     = Routes.MOTOR_CONTROL,
                arguments = listOf(navArgument("motorId") { type = NavType.StringType }),
            ) { back ->
                MotorControlScreen(
                    motorId = back.arguments?.getString("motorId") ?: "",
                    onBack  = { navController.popBackStack() },
                )
            }
        }
    }
}
