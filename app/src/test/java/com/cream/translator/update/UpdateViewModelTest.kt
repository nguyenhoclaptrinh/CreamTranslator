package com.cream.translator.update

import android.app.Application
import app.cash.turbine.test
import com.cream.translator.update.model.AppReleaseInfo
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val application = mockk<Application>(relaxed = true)
    private val engine = mockk<UpdateEngine>()
    private lateinit var viewModel: UpdateViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = UpdateViewModel(application, engine)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun verifySingleApplicationConstructorExistsForViewModelFactory() {
        val constructor = UpdateViewModel::class.java.getConstructor(Application::class.java)
        assertNotNull(constructor)
    }

    @Test
    fun initialUiState_isIdle() {
        assertEquals(UpdateUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun checkForUpdate_whenUpdateAvailable_emitsUpdateAvailableState() =
        runTest(testDispatcher) {
            val fakeRelease =
                AppReleaseInfo(
                    tagName = "v1.2.0",
                    versionName = "1.2.0",
                    releaseNotes = "Major performance upgrade",
                    downloadUrl = "https://example.com/app.apk",
                    apkSize = 10485760L,
                )

            coEvery { engine.checkForUpdate(any()) } returns Result.success(fakeRelease)

            viewModel.uiState.test {
                assertEquals(UpdateUiState.Idle, awaitItem())

                viewModel.checkForUpdate()
                assertEquals(UpdateUiState.Checking, awaitItem())

                testDispatcher.scheduler.advanceUntilIdle()
                val successItem = awaitItem()
                assertTrue(successItem is UpdateUiState.UpdateAvailable)
                assertEquals("v1.2.0", (successItem as UpdateUiState.UpdateAvailable).releaseInfo.tagName)
            }
        }

    @Test
    fun checkForUpdate_whenNoUpdateAvailable_emitsNoUpdateAvailableState() =
        runTest(testDispatcher) {
            coEvery { engine.checkForUpdate(any()) } returns Result.success(null)

            viewModel.uiState.test {
                assertEquals(UpdateUiState.Idle, awaitItem())

                viewModel.checkForUpdate()
                assertEquals(UpdateUiState.Checking, awaitItem())

                testDispatcher.scheduler.advanceUntilIdle()
                val noUpdateItem = awaitItem()
                assertTrue(noUpdateItem is UpdateUiState.NoUpdateAvailable)
            }
        }

    @Test
    fun checkForUpdate_whenFailure_emitsErrorState() =
        runTest(testDispatcher) {
            coEvery { engine.checkForUpdate(any()) } returns Result.failure(IllegalStateException("Network timeout"))

            viewModel.uiState.test {
                assertEquals(UpdateUiState.Idle, awaitItem())

                viewModel.checkForUpdate()
                assertEquals(UpdateUiState.Checking, awaitItem())

                testDispatcher.scheduler.advanceUntilIdle()
                val errorItem = awaitItem()
                assertTrue(errorItem is UpdateUiState.Error)
                assertEquals("Network timeout", (errorItem as UpdateUiState.Error).message)
            }
        }

    @Test
    fun dismissDialog_resetsStateToIdle() =
        runTest(testDispatcher) {
            coEvery { engine.checkForUpdate(any()) } returns Result.success(null)

            viewModel.checkForUpdate()
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(viewModel.uiState.value is UpdateUiState.NoUpdateAvailable)

            viewModel.dismissDialog()
            assertEquals(UpdateUiState.Idle, viewModel.uiState.value)
        }
}
