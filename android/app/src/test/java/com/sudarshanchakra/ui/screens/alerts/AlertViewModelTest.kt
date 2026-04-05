package com.sudarshanchakra.ui.screens.alerts

import com.sudarshanchakra.MainDispatcherRule
import com.sudarshanchakra.data.repository.AlertBadgeRepository
import com.sudarshanchakra.data.repository.AlertRepository
import com.sudarshanchakra.data.repository.ServerSettings
import com.sudarshanchakra.data.repository.ServerSettingsRepository
import com.sudarshanchakra.domain.model.Alert
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlertViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `after idle loadAlerts success shows alerts and clears badge`() = runTest(mainDispatcherRule.dispatcher) {
        val alert = Alert(id = "a1")
        val alertRepository = mockk<AlertRepository>(relaxed = true)
        val alertBadgeRepository = mockk<AlertBadgeRepository>(relaxed = true)
        val serverSettingsRepository = mockk<ServerSettingsRepository>(relaxed = true)
        every { serverSettingsRepository.settings } returns flowOf(
            ServerSettings(apiBaseUrl = "http://x/", mqttBrokerUrl = "tcp://x/", edgeGuiBaseUrl = "http://edge/"),
        )
        coEvery { alertRepository.getAlerts() } returns Result.success(listOf(alert))
        coEvery { alertBadgeRepository.clear() } returns Unit

        val vm = AlertViewModel(alertRepository, alertBadgeRepository, serverSettingsRepository)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertNull(state.error)
        assertEquals(listOf(alert), state.filteredAlerts)
        coVerify(exactly = 1) { alertRepository.getAlerts() }
        coVerify(exactly = 1) { alertBadgeRepository.clear() }
    }

    @Test
    fun `loadAlerts failure sets error and stops loading`() = runTest(mainDispatcherRule.dispatcher) {
        val alertRepository = mockk<AlertRepository>(relaxed = true)
        val alertBadgeRepository = mockk<AlertBadgeRepository>(relaxed = true)
        val serverSettingsRepository = mockk<ServerSettingsRepository>(relaxed = true)
        every { serverSettingsRepository.settings } returns flowOf(
            ServerSettings(apiBaseUrl = "http://x/", mqttBrokerUrl = "tcp://x/"),
        )
        coEvery { alertRepository.getAlerts() } returns Result.failure(Exception("network"))

        val vm = AlertViewModel(alertRepository, alertBadgeRepository, serverSettingsRepository)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertTrue(state.filteredAlerts.isEmpty())
    }

    @Test
    fun `refresh calls getAlerts again`() = runTest(mainDispatcherRule.dispatcher) {
        val alertRepository = mockk<AlertRepository>(relaxed = true)
        val alertBadgeRepository = mockk<AlertBadgeRepository>(relaxed = true)
        val serverSettingsRepository = mockk<ServerSettingsRepository>(relaxed = true)
        every { serverSettingsRepository.settings } returns flowOf(
            ServerSettings(apiBaseUrl = "http://x/", mqttBrokerUrl = "tcp://x/"),
        )
        coEvery { alertRepository.getAlerts() } returns Result.success(emptyList())
        coEvery { alertBadgeRepository.clear() } returns Unit

        val vm = AlertViewModel(alertRepository, alertBadgeRepository, serverSettingsRepository)
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()

        coVerify(atLeast = 2) { alertRepository.getAlerts() }
    }
}
