package com.sudarshanchakra.ui.screens.water

import com.sudarshanchakra.MainDispatcherRule
import com.sudarshanchakra.data.api.ApiService
import com.sudarshanchakra.domain.model.water.WaterMotor
import com.sudarshanchakra.domain.model.water.WaterTank
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class WaterViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `after idle tanks and motors are loaded`() = runTest(mainDispatcherRule.dispatcher) {
        val tank = WaterTank(id = "t1", displayName = "T1", location = "farm")
        val motor = WaterMotor(id = "m1", displayName = "M1", location = "farm")
        val api = mockk<ApiService>(relaxed = true)
        coEvery { api.getWaterTanks() } returns Response.success(listOf(tank))
        coEvery { api.getMotors() } returns Response.success(listOf(motor))

        val vm = WaterViewModel(api)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertFalse(state.isRefreshing)
        assertNull(state.error)
        assertEquals(listOf(tank), state.tanks)
        assertEquals(listOf(motor), state.motors)
    }

    @Test
    fun `api exception sets error`() = runTest(mainDispatcherRule.dispatcher) {
        val api = mockk<ApiService>(relaxed = true)
        coEvery { api.getWaterTanks() } throws RuntimeException("timeout")
        coEvery { api.getMotors() } returns Response.success(emptyList())

        val vm = WaterViewModel(api)
        advanceUntilIdle()

        assertEquals("timeout", vm.uiState.value.error)
        assertFalse(vm.uiState.value.loading)
    }

    @Test
    fun `refresh completes with isRefreshing false`() = runTest(mainDispatcherRule.dispatcher) {
        val api = mockk<ApiService>(relaxed = true)
        coEvery { api.getWaterTanks() } returns Response.success(emptyList())
        coEvery { api.getMotors() } returns Response.success(emptyList())

        val vm = WaterViewModel(api)
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isRefreshing)
        assertFalse(vm.uiState.value.loading)
    }
}
