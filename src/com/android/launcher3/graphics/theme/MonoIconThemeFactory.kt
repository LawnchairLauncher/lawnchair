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

package com.android.launcher3.graphics.theme

import com.android.launcher3.icons.IconThemeController
import com.android.launcher3.icons.mono.MonoIconThemeController
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_THEMED_ICON_ENABLED
import com.android.launcher3.logging.StatsLogManager.StatsLogger

/**
 * A factory for creating [IconThemeController] instances. Each factory is associated with a
 * factoryId provided during dagger binding
 */
interface IconThemeFactory {

    /**
     * Called with the theme id to create a new [IconThemeController] instance if the factory vas
     * matched.
     */
    fun createController(themeId: String): IconThemeController?

    /** Logs the theme information corresponding to the provided [themeId] */
    fun logThemeEvent(themeId: String, logger: StatsLogger)
}

object MonoIconThemeFactory : IconThemeFactory {

    override fun createController(themeId: String): IconThemeController? =
        if (themeId == MONO_THEME_CONTROLLER.themeID) MONO_THEME_CONTROLLER else null

    const val MONO_FACTORY_ID = "mono-icons"

    // Use a constant to allow equality check in verifyIconState
    val MONO_THEME_CONTROLLER = MonoIconThemeController(shouldForceThemeIcon = true)

    override fun logThemeEvent(themeId: String, logger: StatsLogger) {
        logger.log(LAUNCHER_THEMED_ICON_ENABLED)
    }
}
