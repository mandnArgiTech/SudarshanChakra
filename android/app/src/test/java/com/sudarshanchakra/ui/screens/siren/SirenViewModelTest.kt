package com.sudarshanchakra.ui.screens.siren

import com.sudarshanchakra.MainDispatcherRule
import com.sudarshanchakra.data.repository.DeviceRepository
import com.sudarshanchakra.data.repository.SirenRepository
import com.sudarshanchakra.domain.model.EdgeNode
import com.sudarshanchakra.domain.model.SirenAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SirenViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val nodeA = EdgeNode(id = "n-a", name = "A")
    private val nodeB = EdgeNode(id = "n-b", name = "B")

    @Test
    fun `triggerAllSirens calls triggerSiren for each node`() = runTest(mainDispatcherRule.dispatcher) {
        val sirenRepository = mockk<SirenRepository>(relaxed = true)
        val deviceRepository = mockk<DeviceRepository>(relaxed = true)
        coEvery { deviceRepository.getNodes() } returns Result.success(listOf(nodeA, nodeB))
        coEvery { sirenRepository.getHistory() } returns Result.success(emptyList())
        coEvery { sirenRepository.triggerSiren(any(), any()) } returns Result.success(
            SirenAction(nodeId = "x", action = "trigger"),
        )

        val vm = SirenViewModel(sirenRepository, deviceRepository)
        advanceUntilIdle()
        vm.triggerAllSirens()
        advanceUntilIdle()

        coVerify(exactly = 1) { sirenRepository.triggerSiren("n-a", "Manual trigger - all nodes") }
        coVerify(exactly = 1) { sirenRepository.triggerSiren("n-b", "Manual trigger - all nodes") }
    }

    @Test
    fun `stopAllSirens calls stopSiren for each node`() = runTest(mainDispatcherRule.dispatcher) {
        val sirenRepository = mockk<SirenRepository>(relaxed = true)
        val deviceRepository = mockk<DeviceRepository>(relaxed = true)
        coEvery { deviceRepository.getNodes() } returns Result.success(listOf(nodeA, nodeB))
        coEvery { sirenRepository.getHistory() } returns Result.success(emptyList())
        coEvery { sirenRepository.stopSiren(any()) } returns Result.success(
            SirenAction(nodeId = "x", action = "stop"),
        )

        val vm = SirenViewModel(sirenRepository, deviceRepository)
        advanceUntilIdle()
        vm.stopAllSirens()
        advanceUntilIdle()

        coVerify(exactly = 1) { sirenRepository.stopSiren("n-a") }
        coVerify(exactly = 1) { sirenRepository.stopSiren("n-b") }
    }

    @Test
    fun `toggleNodeSiren off calls stopSiren`() = runTest(mainDispatcherRule.dispatcher) {
        val sirenRepository = mockk<SirenRepository>(relaxed = true)
        val deviceRepository = mockk<DeviceRepository>(relaxed = true)
        coEvery { deviceRepository.getNodes() } returns Result.success(listOf(nodeA))
        coEvery { sirenRepository.getHistory() } returns Result.success(emptyList())
        coEvery { sirenRepository.triggerSiren(any(), any()) } returns Result.success(
            SirenAction(nodeId = "n-a", action = "trigger"),
        )
        coEvery { sirenRepository.stopSiren(any()) } returns Result.success(
            SirenAction(nodeId = "n-a", action = "stop"),
        )

        val vm = SirenViewModel(sirenRepository, deviceRepository)
        advanceUntilIdle()
        vm.toggleNodeSiren("n-a")
        advanceUntilIdle()
        vm.toggleNodeSiren("n-a")
        advanceUntilIdle()

        coVerify(exactly = 1) { sirenRepository.triggerSiren("n-a", null) }
        coVerify(exactly = 1) { sirenRepository.stopSiren("n-a") }
    }
}
