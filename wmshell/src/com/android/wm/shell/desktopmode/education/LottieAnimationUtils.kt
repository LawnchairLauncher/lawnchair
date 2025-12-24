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

package com.android.wm.shell.desktopmode.education

import android.annotation.RawRes
import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty.COLOR_FILTER
import com.airbnb.lottie.model.KeyPath
import com.android.wm.shell.windowdecor.common.DecorThemeUtil
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext

private fun getMaterialColorMap(colorScheme: ColorScheme) =
    mapOf(
        ".primary" to colorScheme.primary,
        ".onPrimary" to colorScheme.onPrimary,
        ".tertiary" to colorScheme.tertiary,
        ".surface" to colorScheme.surface,
        ".onSurface" to colorScheme.onSurface,
        ".sContainerHighest" to colorScheme.surfaceContainerHighest,
        ".inverseSurface" to colorScheme.inverseSurface,
    )

private fun applyDynamicColors(
    lottieDrawable: LottieDrawable,
    context: Context,
    taskInfo: RunningTaskInfo,
) {
    val colorScheme = DecorThemeUtil(context).getColorScheme(taskInfo)
    getMaterialColorMap(colorScheme).forEach { (lottieColorName, color) ->
        lottieDrawable.addValueCallback(KeyPath("**", lottieColorName, "**"), COLOR_FILTER) {
            PorterDuffColorFilter(color.toArgb(), PorterDuff.Mode.SRC_ATOP)
        }
    }
}

/** Returns a [LottieDrawable] that is playing in an infinite loop. */
suspend fun getLottieDrawable(
    @RawRes lottieRes: Int,
    context: Context,
    taskInfo: RunningTaskInfo,
    bgDispatcher: CoroutineContext,
): LottieDrawable =
    withContext(bgDispatcher) {
        val lottieComposition = LottieCompositionFactory.fromRawResSync(context, lottieRes).value
        return@withContext LottieDrawable().apply {
            setComposition(lottieComposition)
            repeatCount = LottieDrawable.INFINITE
            applyDynamicColors(lottieDrawable = this, context = context, taskInfo = taskInfo)
            playAnimation()
        }
    }
