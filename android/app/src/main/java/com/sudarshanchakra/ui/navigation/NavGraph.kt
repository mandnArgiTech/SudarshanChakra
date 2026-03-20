package com.sudarshanchakra.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sudarshanchakra.service.MqttForegroundService
import com.sudarshanchakra.ui.components.BottomNavBar
import com.sudarshanchakra.ui.screens.alerts.AlertDetailScreen
import com.sudarshanchakra.ui.screens.alerts.AlertFeedScreen
import com.sudarshanchakra.ui.screens.cameras.CameraGridScreen
import com.sudarshanchakra.ui.screens.devices.DeviceStatusScreen
import com.sudarshanchakra.ui.screens.login.LoginScreen
import com.sudarshanchakra.ui.screens.settings.ServerSettingsScreen
import com.sudarshanchakra.ui.screens.settings.SettingsScreen
import com.sudarshanchakra.ui.screens.siren.SirenControlScreen
import com.sudarshanchakra.ui.screens.water.MotorControlScreen
import com.sudarshanchakra.ui.screens.water.WaterTanksScreen

object Routes {
    const val LOGIN = "login"
    const val SERVER_SETTINGS = "server_settings"
    const val ALERTS = "alerts"
    const val ALERT_DETAIL = "alertDetail/{id}"
    const val CAMERAS = "cameras"
    const val SIREN = "siren"
    const val DEVICES = "devices"
    const val PROFILE = "profile"
    const val WATER_TANKS = "water_tanks"
    const val MOTOR_CONTROL = "motor_control/{motorId}"

    fun alertDetail(id: String) = "alertDetail/$id"
    fun motorControl(motorId: String) = "motor_control/$motorId"
}

@Composable
fun NavGraph(
    mainNavViewModel: MainNavViewModel,
    sessionViewModel: SessionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val isLoggedIn by sessionViewModel.isLoggedIn.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val pendingAlertId by mainNavViewModel.pendingAlertId.collectAsStateWithLifecycle()

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            MqttForegroundService.start(context)
        }
    }

    LaunchedEffect(isLoggedIn, currentRoute) {
        if (isLoggedIn) {
            if (currentRoute == Routes.LOGIN) {
                navController.navigate(Routes.ALERTS) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            }
        } else {
            if (currentRoute != null &&
                currentRoute != Routes.LOGIN &&
                currentRoute != Routes.SERVER_SETTINGS
            ) {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(pendingAlertId, isLoggedIn) {
        val id = pendingAlertId ?: return@LaunchedEffect
        if (!isLoggedIn) return@LaunchedEffect
        navController.navigate(Routes.alertDetail(id)) {
            launchSingleTop = true
        }
        mainNavViewModel.consumeAlertDeepLink()
    }

    val showBottomBar = currentRoute != Routes.LOGIN && currentRoute != Routes.SERVER_SETTINGS

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentRoute = currentRoute ?: Routes.ALERTS,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Routes.ALERTS) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Routes.LOGIN,
            modifier = Modifier.padding(paddingValues),
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Routes.ALERTS) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onConfigureServer = { navController.navigate(Routes.SERVER_SETTINGS) },
                )
            }

            composable(Routes.SERVER_SETTINGS) {
                ServerSettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.ALERTS) {
                AlertFeedScreen(
                    onAlertClick = { id -> navController.navigate(Routes.alertDetail(id)) },
                    onSirenClick = { navController.navigate(Routes.SIREN) },
                    onWaterCardClick = { navController.navigate(Routes.WATER_TANKS) },
                )
            }

            composable(
                route = Routes.ALERT_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { back ->
                AlertDetailScreen(
                    alertId = back.arguments?.getString("id") ?: "",
                    onBackClick = { navController.popBackStack() },
                    onSirenClick = { navController.navigate(Routes.SIREN) },
                )
            }

            composable(Routes.CAMERAS) { CameraGridScreen() }
            composable(Routes.SIREN) { SirenControlScreen() }
            composable(Routes.DEVICES) { DeviceStatusScreen() }
            composable(Routes.PROFILE) {
                SettingsScreen(
                    sessionViewModel = sessionViewModel,
                    onOpenServerSettings = { navController.navigate(Routes.SERVER_SETTINGS) },
                )
            }

            composable(Routes.WATER_TANKS) {
                WaterTanksScreen(
                    onMotorClick = { motorId -> navController.navigate(Routes.motorControl(motorId)) },
                )
            }

            composable(
                route = Routes.MOTOR_CONTROL,
                arguments = listOf(navArgument("motorId") { type = NavType.StringType }),
            ) { back ->
                MotorControlScreen(
                    motorId = back.arguments?.getString("motorId") ?: "",
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
