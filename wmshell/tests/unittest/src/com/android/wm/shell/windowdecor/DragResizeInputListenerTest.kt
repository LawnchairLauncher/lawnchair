/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.wm.shell.windowdecor

import android.app.ActivityManager
import android.content.Context
import android.graphics.Region
import android.os.Handler
import android.os.Looper
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.util.Size
import android.view.Choreographer
import android.view.Display
import android.view.IWindowSession
import android.view.InputChannel
import android.view.SurfaceControl
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestHandler
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.DesktopModeEventLogger
import com.android.wm.shell.util.StubTransaction
import com.android.wm.shell.windowdecor.DragResizeInputListener.TaskResizeInputEventReceiver
import com.google.common.truth.Truth.assertThat
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.test.assertNotNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [DragResizeInputListener].
 *
 * Build/Install/Run: atest WMShellUnitTests:DragResizeInputListenerTest
 */
@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidTestingRunner::class)
class DragResizeInputListenerTest : ShellTestCase() {
    private val testMainExecutor = TestShellExecutor()
    private val testBgExecutor = TestShellExecutor()
    private val mockWindowSession = mock<IWindowSession>()
    private val mockInputEventReceiver = mock<TaskResizeInputEventReceiver>()
    private val decorationSurface = SurfaceControl.Builder().setName("decoration surface").build()
    private val createdSurfaces = ArrayList<SurfaceControl>()
    private val removedSurfaces = ArrayList<SurfaceControl>()

    @Before
    fun setUp() {
        whenever(
                mockWindowSession.grantInputChannel(
                    anyInt(), // displayId
                    any(), // decorationSurface
                    any(), // clientToken
                    anyOrNull(), // hostInputToken
                    anyInt(), // flags
                    anyInt(), // privateFlags
                    anyInt(), // inputFeatures
                    anyInt(), // type
                    anyOrNull(), // windowToken
                    any(), // inputTransferToken
                    any(), // name
                )
            )
            .thenReturn(InputChannel())
    }

    @After
    fun tearDown() {
        createdSurfaces.clear()
        removedSurfaces.clear()
        decorationSurface.release()
    }

    @Test
    fun testGrantInputChannelOffMainThread() {
        create()
        testMainExecutor.flushAll()

        verifyNoInputChannelGrantRequests()
    }

    @Test
    fun testGrantInputChannelAfterDecorSurfaceReleased() {
        // Keep tracking the underlying surface that the decorationSurface points to.
        val forVerification = SurfaceControl(decorationSurface, "forVerification")
        try {
            create()
            decorationSurface.release()
            testBgExecutor.flushAll()

            verify(mockWindowSession)
                .grantInputChannel(
                    anyInt(),
                    argThat<SurfaceControl> { isValid && isSameSurface(forVerification) },
                    any(),
                    anyOrNull(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyOrNull(),
                    any(),
                    any(),
                )
        } finally {
            forVerification.release()
        }
    }

    @Test
    fun testInitializationCallback_waitsForBgSetup() {
        val inputListener = create()

        val callback = TestInitializationCallback()
        inputListener.addInitializedCallback(callback)
        assertThat(callback.initialized).isFalse()

        testBgExecutor.flushAll()
        testMainExecutor.flushAll()

        assertThat(callback.initialized).isTrue()
    }

    @Test
    fun testInitializationCallback_alreadyInitialized_callsBackImmediately() {
        val inputListener = create()
        testBgExecutor.flushAll()
        testMainExecutor.flushAll()

        val callback = TestInitializationCallback()
        inputListener.addInitializedCallback(callback)

        assertThat(callback.initialized).isTrue()
    }

    @Test
    fun testClose_beforeBgSetup_cancelsBgSetup() {
        val inputListener = create()

        inputListener.close()
        testBgExecutor.flushAll()

        verifyNoInputChannelGrantRequests()
    }

    @Test
    fun testClose_beforeBgSetupResultSet_cancelsInit() {
        val inputListener = create()
        val callback = TestInitializationCallback()
        inputListener.addInitializedCallback(callback)

        testBgExecutor.flushAll()
        inputListener.close()
        testMainExecutor.flushAll()

        assertThat(callback.initialized).isFalse()
    }

    @Test
    fun testClose_afterInit_disposesOfReceiver() {
        val inputListener = create()

        testBgExecutor.flushAll()
        testMainExecutor.flushAll()
        inputListener.close()

        verify(mockInputEventReceiver).dispose()
    }

    @Test
    fun testClose_afterInit_removesTokens() {
        val inputListener = create()

        inputListener.close()
        testBgExecutor.flushAll()

        verify(mockWindowSession).remove(inputListener.mClientToken)
        verify(mockWindowSession).remove(inputListener.mSinkClientToken)
    }

    @Test
    fun testClose_afterBgSetup_disposesOfInputChannels() {
        val inputListener = create()
        testBgExecutor.flushAll()
        inputListener.close()
        testMainExecutor.flushAll()
    }

    @Test
    fun testClose_beforeBgSetup_releaseSurfaces() {
        val inputListener = create()
        inputListener.close()
        testBgExecutor.flushAll()
        testMainExecutor.flushAll()

        assertThat(createdSurfaces).hasSize(1)
        assertThat(createdSurfaces[0].isValid).isFalse()
    }

    @Test
    fun testClose_afterBgSetup_releaseSurfaces() {
        val inputListener = create()
        testBgExecutor.flushAll()
        inputListener.close()
        testMainExecutor.flushAll()
        testBgExecutor.flushAll()

        assertThat(createdSurfaces).hasSize(2)
        assertThat(createdSurfaces[0].isValid).isFalse()
        assertThat(createdSurfaces[1].isValid).isFalse()
    }

    @Test
    fun testClose_releasesDecorationSurfaceWithoutRemoval() {
        val inputListener = create()
        testBgExecutor.flushAll()
        inputListener.close()
        testMainExecutor.flushAll()
        testBgExecutor.flushAll()

        val decorationSurface = assertNotNull(createdSurfaces[0])
        assertThat(decorationSurface.isValid).isFalse()
        assertThat(removedSurfaces.contains(decorationSurface)).isFalse()
    }

    private fun verifyNoInputChannelGrantRequests() {
        verify(mockWindowSession, never())
            .grantInputChannel(
                anyInt(),
                any(),
                any(),
                anyOrNull(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyOrNull(),
                any(),
                any(),
            )
    }

    private fun create(): DragResizeInputListener =
        DragResizeInputListener(
            context,
            mockWindowSession,
            testMainExecutor,
            testBgExecutor,
            TestTaskResizeInputEventReceiverFactory(mockInputEventReceiver),
            TestRunningTaskInfoBuilder().build(),
            TestHandler(Looper.getMainLooper()),
            mock<Choreographer>(),
            Display.DEFAULT_DISPLAY,
            decorationSurface,
            mock<DragPositioningCallback>(),
            {
                object : SurfaceControl.Builder() {
                    override fun build(): SurfaceControl {
                        return super.build().also { createdSurfaces.add(it) }
                    }
                }
            },
            {
                object : StubTransaction() {
                    override fun remove(sc: SurfaceControl): SurfaceControl.Transaction {
                        return super.remove(sc).also {
                            sc.release()
                            removedSurfaces.add(sc)
                        }
                    }
                }
            },
            mock<DisplayController>(),
            mock<DesktopModeEventLogger>(),
        )

    private class TestInitializationCallback : Runnable {
        var initialized: Boolean = false
            private set

        override fun run() {
            initialized = true
        }
    }

    private class TestTaskResizeInputEventReceiverFactory(
        private val mockInputEventReceiver: TaskResizeInputEventReceiver
    ) : DragResizeInputListener.TaskResizeInputEventReceiverFactory {
        override fun create(
            context: Context,
            taskInfo: ActivityManager.RunningTaskInfo,
            inputChannel: InputChannel,
            callback: DragPositioningCallback,
            handler: Handler,
            choreographer: Choreographer,
            displayLayoutSizeSupplier: Supplier<Size?>,
            touchRegionConsumer: Consumer<Region?>,
            desktopModeEventLogger: DesktopModeEventLogger,
        ): TaskResizeInputEventReceiver = mockInputEventReceiver
    }
}
