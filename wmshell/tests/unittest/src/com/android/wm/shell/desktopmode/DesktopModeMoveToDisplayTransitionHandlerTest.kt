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

package com.android.wm.shell.desktopmode

import android.graphics.Rect
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.SurfaceControl
import android.view.WindowManager
import android.window.TransitionInfo
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.util.StubTransaction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify

@SmallTest
@RunWithLooper
@RunWith(AndroidTestingRunner::class)
class DesktopModeMoveToDisplayTransitionHandlerTest : ShellTestCase() {
    private lateinit var handler: DesktopModeMoveToDisplayTransitionHandler

    @Before
    fun setUp() {
        handler =
            DesktopModeMoveToDisplayTransitionHandler(StubTransaction(), mock(), mock(), mock())
    }

    @Test
    fun handleRequest_returnsNull() {
        assertNull(handler.handleRequest(mock(), mock()))
    }

    @Test
    fun startAnimation_changeWithinDisplay_returnsFalse() {
        val animates =
            handler.startAnimation(
                transition = mock(),
                info =
                    TransitionInfo(WindowManager.TRANSIT_CHANGE, /* flags= */ 0).apply {
                        addChange(
                            TransitionInfo.Change(mock(), mock()).apply {
                                setDisplayId(/* start= */ 1, /* end= */ 1)
                            }
                        )
                    },
                startTransaction = StubTransaction(),
                finishTransaction = StubTransaction(),
                finishCallback = mock(),
            )

        assertFalse("Should not animate open transition", animates)
    }

    @Test
    fun startAnimation_changeMoveToDisplay_returnsTrue() {
        val animates =
            handler.startAnimation(
                transition = mock(),
                info =
                    TransitionInfo(WindowManager.TRANSIT_CHANGE, /* flags= */ 0).apply {
                        addChange(
                            TransitionInfo.Change(mock(), mock()).apply {
                                setDisplayId(/* start= */ 1, /* end= */ 2)
                            }
                        )
                    },
                startTransaction = StubTransaction(),
                finishTransaction = StubTransaction(),
                finishCallback = mock(),
            )

        assertTrue("Should animate display change transition", animates)
    }

    @Test
    fun startAnimation_movingActivityEmbedding_shouldSetCorrectBounds() {
        val leashLeft = mock<SurfaceControl>()
        val leashRight = mock<SurfaceControl>()
        val leashContainer = mock<SurfaceControl>()
        val startTransaction = spy(StubTransaction())

        handler.startAnimation(
            transition = mock(),
            info =
                TransitionInfo(WindowManager.TRANSIT_CHANGE, /* flags= */ 0).apply {
                    addChange(
                        TransitionInfo.Change(mock(), mock()).apply {
                            flags = TransitionInfo.FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY
                            leash = leashLeft
                            setDisplayId(/* start= */ 1, /* end= */ 2)
                            setEndAbsBounds(
                                Rect(
                                    /* left= */ 100,
                                    /* top= */ 100,
                                    /* right= */ 500,
                                    /* bottom= */ 700,
                                )
                            )
                            setEndRelOffset(/* left= */ 0, /* top= */ 0)
                        }
                    )
                    addChange(
                        TransitionInfo.Change(mock(), mock()).apply {
                            flags = TransitionInfo.FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY
                            leash = leashRight
                            setDisplayId(1, 2)
                            setEndAbsBounds(
                                Rect(
                                    /* left= */ 500,
                                    /* top= */ 100,
                                    /* right= */ 900,
                                    /* bottom= */ 700,
                                )
                            )
                            setEndRelOffset(/* left= */ 400, /* top= */ 0)
                        }
                    )
                    addChange(
                        TransitionInfo.Change(mock(), mock()).apply {
                            flags = TransitionInfo.FLAG_TRANSLUCENT
                            leash = leashContainer
                            setDisplayId(/* start= */ 1, /* end= */ 2)
                            setEndAbsBounds(
                                Rect(
                                    /* left= */ 100,
                                    /* top= */ 100,
                                    /* right= */ 900,
                                    /* bottom= */ 700,
                                )
                            )
                            setEndRelOffset(/* left= */ 100, /* top= */ 100)
                        }
                    )
                },
            startTransaction = startTransaction,
            finishTransaction = StubTransaction(),
            finishCallback = mock(),
        )

        verify(startTransaction).setPosition(leashLeft, 0f, 0f)
        verify(startTransaction).setPosition(leashRight, 400f, 0f)
        verify(startTransaction).setPosition(leashContainer, 100f, 100f)
        verify(startTransaction).setWindowCrop(leashLeft, 400, 600)
        verify(startTransaction).setWindowCrop(leashRight, 400, 600)
        verify(startTransaction).setWindowCrop(leashContainer, 800, 600)
    }
}
