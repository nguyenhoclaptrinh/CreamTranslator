package com.cream.translator.testutil

import android.app.Application
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Lớp kiểm thử cơ sở tích hợp Robolectric dành cho AI Agent và nhà phát triển.
 * Cung cấp sẵn TestCoroutineDispatcher và Application Context giả lập trên JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
abstract class BaseRobolectricTest {
    protected val testDispatcher: TestDispatcher = StandardTestDispatcher()
    protected lateinit var context: Context
    protected lateinit var application: Application

    @Before
    open fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = RuntimeEnvironment.getApplication()
        application = RuntimeEnvironment.getApplication()
    }

    @After
    open fun tearDown() {
        Dispatchers.resetMain()
    }
}
