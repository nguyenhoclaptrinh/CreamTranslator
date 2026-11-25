package com.cream.translator.service

import android.content.Intent
import android.os.Looper
import com.cream.translator.testutil.BaseRobolectricTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowSettings

class ServiceLifecycleTest : BaseRobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        TranslatorController.updateServiceState(false)
        val shadowApp = Shadows.shadowOf(application)
        shadowApp.clearStartedServices()
        while (shadowApp.nextStoppedService != null) {
            // Drain residual stopped-service queue from previous tests
        }
    }

    @After
    override fun tearDown() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        super.tearDown()
    }

    @Test
    fun overlayService_whenOverlayPermissionMissing_stopsCaptureServiceToo() {
        ShadowSettings.setCanDrawOverlays(false)

        val controller = Robolectric.buildService(OverlayService::class.java).create()
        assertFalse(OverlayService.isRunning)

        val stoppedOverlay = Shadows.shadowOf(application).nextStoppedService
        val stoppedCapture = Shadows.shadowOf(application).nextStoppedService
        val stoppedClasses = listOfNotNull(stoppedOverlay?.component?.className, stoppedCapture?.component?.className)

        assertTrue(stoppedClasses.contains(OverlayService::class.java.name))
        assertTrue(stoppedClasses.contains(ScreenCaptureService::class.java.name))

        controller.destroy()
        assertFalse(OverlayService.isRunning)
    }

    @Test
    fun overlayService_createAndDestroy_updatesRunningFlagCleanly() {
        ShadowSettings.setCanDrawOverlays(true)

        val controller = Robolectric.buildService(OverlayService::class.java).create()
        assertTrue(OverlayService.isRunning)

        controller.destroy()
        assertFalse(OverlayService.isRunning)
        assertFalse(TranslatorController.isServiceRunning.value)
    }

    @Test
    fun screenCaptureService_actionStop_stopsBothServicesAndClearsInstance() {
        TranslatorController.updateServiceState(true)
        val stopIntent =
            Intent(context, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_STOP
            }
        val serviceController = Robolectric.buildService(ScreenCaptureService::class.java, stopIntent).create()
        assertNotNull(ScreenCaptureService.instance)

        serviceController.startCommand(0, 0)

        assertFalse(TranslatorController.isServiceRunning.value)
        val stoppedOverlay = Shadows.shadowOf(application).nextStoppedService
        assertEquals(OverlayService::class.java.name, stoppedOverlay?.component?.className)

        serviceController.destroy()
        assertNull(ScreenCaptureService.instance)
    }

    @Test
    fun screenCaptureService_missingProjectionToken_clearsStateAndStopsServices() {
        TranslatorController.updateServiceState(true)
        val emptyIntent = Intent(context, ScreenCaptureService::class.java)
        val serviceController = Robolectric.buildService(ScreenCaptureService::class.java, emptyIntent).create()

        serviceController.startCommand(0, 0)

        assertFalse(TranslatorController.isServiceRunning.value)
        val stoppedOverlay = Shadows.shadowOf(application).nextStoppedService
        assertEquals(OverlayService::class.java.name, stoppedOverlay?.component?.className)

        serviceController.destroy()
        assertNull(ScreenCaptureService.instance)
    }

    @Test
    fun overlayService_actionStop_stopsBothServicesAndClearsRunningState() {
        ShadowSettings.setCanDrawOverlays(true)
        TranslatorController.updateServiceState(true)
        val stopIntent =
            Intent(context, OverlayService::class.java).apply {
                action = OverlayService.ACTION_STOP
            }
        val controller = Robolectric.buildService(OverlayService::class.java, stopIntent).create()
        assertTrue(OverlayService.isRunning)

        controller.startCommand(0, 0)

        assertFalse(TranslatorController.isServiceRunning.value)
        val stoppedFirst = Shadows.shadowOf(application).nextStoppedService
        val stoppedSecond = Shadows.shadowOf(application).nextStoppedService
        val stoppedClasses = listOfNotNull(stoppedFirst?.component?.className, stoppedSecond?.component?.className)
        assertTrue(stoppedClasses.contains(OverlayService::class.java.name))
        assertTrue(stoppedClasses.contains(ScreenCaptureService::class.java.name))

        controller.destroy()
        assertFalse(OverlayService.isRunning)
    }
}
