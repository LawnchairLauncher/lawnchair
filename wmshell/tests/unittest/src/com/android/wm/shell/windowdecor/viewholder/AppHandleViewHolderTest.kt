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

package com.android.wm.shell.windowdecor.viewholder

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.app.ActivityManager.RunningTaskInfo
import android.graphics.Point
import android.os.Handler
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import androidx.test.filters.SmallTest
import com.android.internal.policy.SystemBarUtils
import com.android.window.flags2.Flags
import com.android.wm.shell.R
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger
import com.android.wm.shell.windowdecor.WindowManagerWrapper
import com.android.wm.shell.windowdecor.viewholder.AppHandleAnimator.Companion.APP_HANDLE_ALPHA_FADE_IN_ANIMATION_DURATION_MS
import com.android.wm.shell.windowdecor.viewholder.AppHandleAnimator.Companion.APP_HANDLE_ALPHA_FADE_OUT_ANIMATION_DURATION_MS
import com.android.wm.shell.windowdecor.viewholder.AppHandleAnimator.Companion.APP_HANDLE_FADE_ANIMATION_INTERPOLATOR
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [AppHandleViewHolder].
 *
 * Build/Install/Run: atest WMShellUnitTests:AppHandleViewHolderTest
 */
@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidTestingRunner::class)
class AppHandleViewHolderTest : ShellTestCase() {

    companion object {
        private const val ALPHA_ANIMATION_ERROR_TOLERANCE = 0.015f

        private const val DEFAULT_CAPTION_WIDTH = 500
        private const val DEFAULT_CAPTION_HEIGHT = 100
        private val DEFAULT_CAPTION_POSITION = Point(0, 0)
        private const val DEFAULT_SHOW_INPUT_LAYER = false
        private const val DEFAULT_IS_CAPTION_VISIBLE = true
    }

    private val mockView = mock<View>()
    private val mockImageButton = mock<ImageButton>()
    private val mockOnTouchListener = mock<View.OnTouchListener>()
    private val mockOnClickListener = mock<View.OnClickListener>()
    private val mockWindowManagerWrapper = mock<WindowManagerWrapper>()
    private val mockHandler = mock<Handler>()
    private val mockTaskInfo = mock<RunningTaskInfo>()
    private val mockDesktopModeUiEventLogger = mock<DesktopModeUiEventLogger>()

    @Before
    fun setup() {
        whenever(mockView.context).thenReturn(mContext)
        whenever(mockView.requireViewById<View>(R.id.desktop_mode_caption)).thenReturn(mockView)
        whenever(mockView.requireViewById<ImageButton>(R.id.caption_handle))
            .thenReturn(mockImageButton)
        ValueAnimator.setDurationScale(0f)
    }

    @After
    fun tearDown() {
        ValueAnimator.setDurationScale(1f)
    }

    @Test
    @DisableFlags(
        Flags.FLAG_ENABLE_REMOVE_STATUS_BAR_INPUT_LAYER,
        Flags.FLAG_ENABLE_DRAWING_APP_HANDLE,
    )
    fun statusBarInputLayer_disposedWhenCaptionBelowStatusBar() {
        val appHandleViewHolder: AppHandleViewHolder = spy(createAppHandleViewHolder(mockView))
        val captionPosition = Point(0, SystemBarUtils.getStatusBarHeight(mContext) + 10)

        appHandleViewHolder.bindData(
            AppHandleViewHolder.HandleData(
                taskInfo = mockTaskInfo,
                position = captionPosition,
                width = DEFAULT_CAPTION_WIDTH,
                height = DEFAULT_CAPTION_HEIGHT,
                showInputLayer = DEFAULT_SHOW_INPUT_LAYER,
                isCaptionVisible = DEFAULT_IS_CAPTION_VISIBLE,
            )
        )

        verify(appHandleViewHolder).disposeStatusBarInputLayer()
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_WINDOW_DECORATION_REFACTOR,
        Flags.FLAG_REENABLE_APP_HANDLE_ANIMATIONS,
    )
    fun animatesFromVisibleToInvisible() {
        runVisibilityAnimationTest(
            toVisible = false,
            expectedStartViewVisibility = View.VISIBLE,
            expectedEndViewVisibility = View.GONE,
            expectedAlphaValues = listOf(1.0f, 0.75f, 0.5f, 0.25f, 0f),
            expectedDuration = APP_HANDLE_ALPHA_FADE_OUT_ANIMATION_DURATION_MS,
            // Use a linear interpolator to simplify expected values.
            overrideInterpolator = LinearInterpolator(),
        )
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_WINDOW_DECORATION_REFACTOR,
        Flags.FLAG_REENABLE_APP_HANDLE_ANIMATIONS,
    )
    fun animatesFromInvisibleToVisible() {
        runVisibilityAnimationTest(
            toVisible = true,
            expectedStartViewVisibility = View.GONE,
            expectedEndViewVisibility = View.VISIBLE,
            expectedAlphaValues = listOf(0f, 0.25f, 0.5f, 0.75f, 1.0f),
            expectedDuration = APP_HANDLE_ALPHA_FADE_IN_ANIMATION_DURATION_MS,
            // Use a linear interpolator to simplify expected values.
            overrideInterpolator = LinearInterpolator(),
        )
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_WINDOW_DECORATION_REFACTOR,
        Flags.FLAG_REENABLE_APP_HANDLE_ANIMATIONS,
    )
    fun animatesFromVisibleToInvisible_withPathInterpolator() {
        runVisibilityAnimationTest(
            toVisible = false,
            expectedStartViewVisibility = View.VISIBLE,
            expectedEndViewVisibility = View.GONE,
            expectedAlphaValues =
                listOf(
                    1f - APP_HANDLE_FADE_ANIMATION_INTERPOLATOR.getInterpolation(0f),
                    1f - APP_HANDLE_FADE_ANIMATION_INTERPOLATOR.getInterpolation(0.25f),
                    1f - APP_HANDLE_FADE_ANIMATION_INTERPOLATOR.getInterpolation(0.5f),
                    1f - APP_HANDLE_FADE_ANIMATION_INTERPOLATOR.getInterpolation(0.75f),
                    1f - APP_HANDLE_FADE_ANIMATION_INTERPOLATOR.getInterpolation(1f),
                ),
            expectedDuration = APP_HANDLE_ALPHA_FADE_OUT_ANIMATION_DURATION_MS,
        )
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_WINDOW_DECORATION_REFACTOR,
        Flags.FLAG_REENABLE_APP_HANDLE_ANIMATIONS,
    )
    fun animatesFromInvisibleToVisible_withPathInterpolator() {
        runVisibilityAnimationTest(
            toVisible = true,
            expectedStartViewVisibility = View.GONE,
            expectedEndViewVisibility = View.VISIBLE,
            expectedAlphaValues =
                listOf(
                    APP_HANDLE_FADE_ANIMATION_INTERPOLATOR.getInterpolation(0f),
                    APP_HANDLE_FADE_ANIMATION_INTERPOLATOR.getInterpolation(0.25f),
                    APP_HANDLE_FADE_ANIMATION_INTERPOLATOR.getInterpolation(0.5f),
                    APP_HANDLE_FADE_ANIMATION_INTERPOLATOR.getInterpolation(0.75f),
                    APP_HANDLE_FADE_ANIMATION_INTERPOLATOR.getInterpolation(1f),
                ),
            expectedDuration = APP_HANDLE_ALPHA_FADE_IN_ANIMATION_DURATION_MS,
        )
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_WINDOW_DECORATION_REFACTOR,
        Flags.FLAG_REENABLE_APP_HANDLE_ANIMATIONS,
    )
    fun continuesAnimationOnRepeatedBindToInvisibleWhileAnimating() {
        // Assumes view is inflated with visibility=VISIBLE
        val appHandle = createAppHandleViewHolder()

        appHandle.bindData(
            AppHandleViewHolder.HandleData(
                taskInfo = mockTaskInfo,
                position = DEFAULT_CAPTION_POSITION,
                width = DEFAULT_CAPTION_WIDTH,
                height = DEFAULT_CAPTION_HEIGHT,
                showInputLayer = DEFAULT_SHOW_INPUT_LAYER,
                isCaptionVisible = false,
            )
        )
        val animator =
            appHandle.getTestableAnimator(
                expectedDuration = APP_HANDLE_ALPHA_FADE_OUT_ANIMATION_DURATION_MS,
                // Use a linear interpolator to simplify assertions.
                overrideInterpolator = LinearInterpolator(),
            )
        val advanceStep = APP_HANDLE_ALPHA_FADE_OUT_ANIMATION_DURATION_MS / 4L

        // Should start with a visible view.
        assertThat(appHandle.rootView.visibility).isEqualTo(View.VISIBLE)
        // Check alpha is fading out as expected at (start, 1/4).
        assertThat(appHandle.rootView.alpha).isWithin(ALPHA_ANIMATION_ERROR_TOLERANCE).of(1f)
        animator.advanceTimeBy(millis = advanceStep)
        assertThat(appHandle.rootView.alpha).isWithin(ALPHA_ANIMATION_ERROR_TOLERANCE).of(0.75f)

        // Second bind to invisible happens mid-animation.
        appHandle.bindData(
            AppHandleViewHolder.HandleData(
                taskInfo = mockTaskInfo,
                position = DEFAULT_CAPTION_POSITION,
                width = DEFAULT_CAPTION_WIDTH,
                height = DEFAULT_CAPTION_HEIGHT,
                showInputLayer = DEFAULT_SHOW_INPUT_LAYER,
                isCaptionVisible = false,
            )
        )

        // Check alpha continues fading out as expected at (1/4, 1/2, 3/4, end).
        assertThat(appHandle.rootView.alpha).isWithin(ALPHA_ANIMATION_ERROR_TOLERANCE).of(0.75f)
        animator.advanceTimeBy(millis = advanceStep)
        assertThat(appHandle.rootView.alpha).isWithin(ALPHA_ANIMATION_ERROR_TOLERANCE).of(0.50f)
        animator.advanceTimeBy(millis = advanceStep)
        assertThat(appHandle.rootView.alpha).isWithin(ALPHA_ANIMATION_ERROR_TOLERANCE).of(0.25f)
        animator.advanceTimeBy(millis = advanceStep)
        assertThat(appHandle.rootView.alpha).isWithin(ALPHA_ANIMATION_ERROR_TOLERANCE).of(0f)
        // Should end with a gone view.
        assertThat(appHandle.rootView.visibility).isEqualTo(View.GONE)
    }

    private fun runVisibilityAnimationTest(
        toVisible: Boolean,
        @View.Visibility expectedStartViewVisibility: Int,
        @View.Visibility expectedEndViewVisibility: Int,
        expectedAlphaValues: List<Float>,
        expectedDuration: Long,
        overrideInterpolator: TimeInterpolator? = null,
    ) {
        val appHandle = createAppHandleViewHolder(visible = !toVisible)
        assertThat(appHandle.rootView.visibility).isEqualTo(expectedStartViewVisibility)

        appHandle.bindData(
            AppHandleViewHolder.HandleData(
                taskInfo = mockTaskInfo,
                position = DEFAULT_CAPTION_POSITION,
                width = DEFAULT_CAPTION_WIDTH,
                height = DEFAULT_CAPTION_HEIGHT,
                showInputLayer = DEFAULT_SHOW_INPUT_LAYER,
                isCaptionVisible = toVisible,
            )
        )
        val animator =
            appHandle.getTestableAnimator(
                expectedDuration = expectedDuration,
                overrideInterpolator = overrideInterpolator,
            )
        val advanceStep = expectedDuration / 4L

        // Should start with a visible view always.
        assertThat(appHandle.rootView.visibility).isEqualTo(View.VISIBLE)
        // Check alpha is fading out as expected at (start, 1/4, 1/2, 3/4, end).
        assertThat(appHandle.rootView.alpha)
            .isWithin(ALPHA_ANIMATION_ERROR_TOLERANCE)
            .of(expectedAlphaValues[0])
        for (i in 1..4) {
            animator.advanceTimeBy(millis = advanceStep)
            assertThat(appHandle.rootView.alpha)
                .isWithin(ALPHA_ANIMATION_ERROR_TOLERANCE)
                .of(expectedAlphaValues[i])
        }
        // Should end with a gone view.
        assertThat(appHandle.rootView.visibility).isEqualTo(expectedEndViewVisibility)
    }

    private fun AppHandleViewHolder.getTestableAnimator(
        expectedDuration: Long,
        overrideInterpolator: TimeInterpolator? = null,
    ): TestableValueAnimator {
        val animator = assertNotNull(getAnimator(), "Expected a non-null animator")
        return TestableValueAnimator(animator, expectedDuration, overrideInterpolator)
    }

    private fun createAppHandleViewHolder(
        view: View? = null,
        visible: Boolean? = null,
    ): AppHandleViewHolder {
        return AppHandleViewHolder(
                view,
                mContext,
                mockOnTouchListener,
                mockOnClickListener,
                mockWindowManagerWrapper,
                mockHandler,
                mockDesktopModeUiEventLogger,
            )
            .apply {
                visible?.let { this.rootView.visibility = if (visible) View.VISIBLE else View.GONE }
            }
    }

    private class TestableValueAnimator(
        private val animator: ValueAnimator,
        private val expectedDuration: Long,
        overrideInterpolator: TimeInterpolator?,
    ) {
        init {
            overrideInterpolator?.let { animator.interpolator = it }
        }

        /** Advance the animation by [millis]. */
        fun advanceTimeBy(millis: Long) {
            animator.currentPlayTime += millis
            if (animator.currentPlayTime >= expectedDuration) {
                animator.end()
            }
        }
    }
}
