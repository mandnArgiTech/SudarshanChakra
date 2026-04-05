package com.sudarshanchakra.ui.screens.login

import com.sudarshanchakra.MainDispatcherRule
import com.sudarshanchakra.data.repository.AuthRepository
import com.sudarshanchakra.data.repository.RememberedLoginForm
import com.sudarshanchakra.domain.model.AuthResponse
import com.sudarshanchakra.domain.model.User
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `remembered form applied after idle`() = runTest(mainDispatcherRule.dispatcher) {
        val auth = mockk<AuthRepository>(relaxed = true)
        coEvery { auth.getRememberedLoginForm() } returns RememberedLoginForm(
            username = "alice",
            password = "secret",
            rememberMe = true,
        )

        val vm = LoginViewModel(auth)
        advanceUntilIdle()

        assertEquals("alice", vm.uiState.value.username)
        assertEquals("secret", vm.uiState.value.password)
        assertTrue(vm.uiState.value.rememberMe)
    }

    @Test
    fun `blank username or password sets error without calling login`() = runTest(mainDispatcherRule.dispatcher) {
        val auth = mockk<AuthRepository>(relaxed = true)
        coEvery { auth.getRememberedLoginForm() } returns RememberedLoginForm()

        val vm = LoginViewModel(auth)
        advanceUntilIdle()
        vm.login()
        advanceUntilIdle()

        assertEquals("Please enter username and password", vm.uiState.value.error)
        coVerify(exactly = 0) { auth.login(any(), any(), any()) }
    }

    @Test
    fun `login success sets isLoggedIn`() = runTest(mainDispatcherRule.dispatcher) {
        val auth = mockk<AuthRepository>(relaxed = true)
        coEvery { auth.getRememberedLoginForm() } returns RememberedLoginForm()
        val response = AuthResponse(
            token = "t",
            refreshToken = "r",
            user = User(id = "1", username = "u", email = "e", role = "USER"),
        )
        coEvery { auth.login("u", "p", false) } returns Result.success(response)

        val vm = LoginViewModel(auth)
        advanceUntilIdle()
        vm.onUsernameChange("u")
        vm.onPasswordChange("p")
        vm.onRememberMeChange(false)
        vm.login()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isLoggedIn)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `login failure sets error`() = runTest(mainDispatcherRule.dispatcher) {
        val auth = mockk<AuthRepository>(relaxed = true)
        coEvery { auth.getRememberedLoginForm() } returns RememberedLoginForm()
        coEvery { auth.login(any(), any(), any()) } returns Result.failure(Exception("bad creds"))

        val vm = LoginViewModel(auth)
        advanceUntilIdle()
        vm.onUsernameChange("u")
        vm.onPasswordChange("p")
        vm.login()
        advanceUntilIdle()

        assertEquals("bad creds", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoggedIn)
    }
}
