/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.graphics

import android.content.Context
import android.content.res.Resources
import com.android.launcher3.LauncherPrefChangeListener
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.backedUpItem
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.ShapeDelegate.Companion.DEFAULT_PATH_SIZE_INT
import com.android.launcher3.graphics.ShapeDelegate.Companion.pickBestShape
import com.android.launcher3.graphics.theme.IconThemeFactory
import com.android.launcher3.graphics.theme.ThemePreference
import com.android.launcher3.graphics.theme.ThemePreference.Companion.MONO_THEME_VALUE
import com.android.launcher3.icons.DotRenderer.IconShapeInfo
import com.android.launcher3.icons.GraphicsUtils.generateIconShape
import com.android.launcher3.icons.IconShape
import com.android.launcher3.icons.IconThemeController
import com.android.launcher3.shapes.IconShapeModel.Companion.DEFAULT_ICON_RADIUS
import com.android.launcher3.shapes.ShapesProvider
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.ListenableRef
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.util.SimpleBroadcastReceiver
import com.android.launcher3.util.SimpleBroadcastReceiver.Companion.packageFilter
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Named

/** Centralized class for managing Launcher icon theming */
@LauncherAppSingleton
class ThemeManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @Ui private val uiExecutor: LooperExecutor,
    private val prefs: LauncherPrefs,
    private val themePreference: ThemePreference,
    @Named(ICON_FACTORY_DAGGER_KEY)
    private val iconThemeFactories: Map<String, @JvmSuppressWildcards IconThemeFactory>,
    @Ui mainExecutor: LooperExecutor,
    lifecycle: DaggerSingletonTracker,
) {

    private val _iconShapeData = MutableListenableRef(IconShape.EMPTY)

    /** listenable value holder for current IconShape */
    val iconShapeData: ListenableRef<IconShape> = _iconShapeData.asListenable()
    /** Representation of the current icon state */
    var iconState = parseIconState(null)
        private set

    @Deprecated("Use [ThemePreference] instead")
    var isMonoThemeEnabled
        set(value) = themePreference.setValue(if (value) MONO_THEME_VALUE else null)
        get() = MONO_THEME_VALUE == themePreference.value

    val themeController
        get() = iconState.themeController

    val isIconThemeEnabled
        get() = themeController != null

    val iconShape
        get() = iconState.iconShape

    val folderShape
        get() = iconState.folderShape

    private val listeners = CopyOnWriteArrayList<ThemeChangeListener>()

    init {
        val receiver = SimpleBroadcastReceiver(context, uiExecutor) { verifyIconState() }
        receiver.register(packageFilter("android", ACTION_OVERLAY_CHANGED))
        lifecycle.addCloseable(receiver)

        val prefListener = LauncherPrefChangeListener {
            if (it == PREF_ICON_SHAPE.sharedPrefKey) verifyIconState()
        }
        prefs.addListener(prefListener, PREF_ICON_SHAPE)
        lifecycle.addCloseable(themePreference.forEach(mainExecutor) { verifyIconState() })
        lifecycle.addCloseable {
            prefs.removeListener(prefListener, PREF_ICON_SHAPE)
            iconState.closeController()
        }
    }

    private fun verifyIconState() {
        val newState = parseIconState(iconState)
        if (newState == iconState) return
        val hasThemedChanged = newState.toUniqueId() != iconState.toUniqueId()
        iconState = newState
        if (hasThemedChanged) {
            // trigger listeners only for theme change, not shape change
            listeners.forEach { it.onThemeChanged() }
        }
        _iconShapeData.dispatchValue(iconShape.createIconShape(iconShapeData.value.pathSize))
    }

    fun addChangeListener(listener: ThemeChangeListener) = listeners.add(listener)

    fun removeChangeListener(listener: ThemeChangeListener) = listeners.remove(listener)

    /**
     * Generates new IconShape based given [iconSize] and current [iconShape] Allocates new Bitmap
     * via [createIconShape]
     */
    fun generateIconShape(iconSize: Int) {
        if (iconShapeData.value.pathSize == iconSize) return
        _iconShapeData.dispatchValue(iconShape.createIconShape(iconSize))
    }

    private fun parseIconState(oldState: IconState?): IconState {
        val shapeModel =
            prefs.get(PREF_ICON_SHAPE).let { shapeOverride ->
                ShapesProvider.iconShapes.firstOrNull { it.key == shapeOverride }
            }
        val iconMask =
            when {
                shapeModel != null -> shapeModel.pathString
                CONFIG_ICON_MASK_RES_ID == Resources.ID_NULL -> ""
                else -> context.resources.getString(CONFIG_ICON_MASK_RES_ID)
            }

        val iconShape =
            if (oldState != null && oldState.iconMask == iconMask) {
                oldState.iconShape
            } else {
                pickBestShape(iconMask)
            }

        val folderRadius = shapeModel?.folderRadiusRatio ?: 1f
        val folderShape =
            if (oldState != null && oldState.folderRadius == folderRadius) {
                oldState.folderShape
            } else if (folderRadius == 1f) {
                ShapeDelegate.Circle()
            } else {
                ShapeDelegate.RoundedSquare(folderRadius)
            }

        val themeKey = themePreference.value
        val themeCode = themeKey?.toString() ?: "no-theme"

        val iconControllerFactory =
            if (oldState?.themeCode == themeCode) {
                oldState.themeController
            } else {
                oldState?.closeController()
                themeKey?.run { iconThemeFactories[factoryId]?.createController(themeId) }
            }

        return IconState(
            iconMask = iconMask,
            folderRadius = folderRadius,
            themeController = iconControllerFactory,
            iconShape = iconShape,
            folderShape = folderShape,
            shapeRadius = shapeModel?.shapeRadius ?: DEFAULT_ICON_RADIUS,
            themeCode = themeCode,
        )
    }

    data class IconState(
        val iconMask: String,
        val folderRadius: Float,
        val themeController: IconThemeController?,
        val themeCode: String,
        val iconShape: ShapeDelegate,
        /* Icon content may change when using Circle shape due to android:roundIcon property */
        val isCircle: Boolean = iconShape is ShapeDelegate.Circle,
        val folderShape: ShapeDelegate,
        val shapeRadius: Float,
    ) {
        fun toUniqueId() = "$themeCode,$isCircle"

        val iconShapeInfo = IconShapeInfo.fromPath(iconShape.getPath(), DEFAULT_PATH_SIZE_INT)
        val folderShapeInfo = IconShapeInfo.fromPath(folderShape.getPath(), DEFAULT_PATH_SIZE_INT)
    }

    private fun IconState.closeController() {
        if (themeController is SafeCloseable) {
            themeController.close()
        }
    }

    /** Interface for receiving theme change events */
    fun interface ThemeChangeListener {
        fun onThemeChanged()
    }

    companion object {

        @JvmField val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getThemeManager)
        @JvmField val PREF_ICON_SHAPE = backedUpItem("icon_shape_model", "")

        @JvmField val DEFAULT_SHAPE_DELEGATE = pickBestShape(shapeStr = "")

        private const val ACTION_OVERLAY_CHANGED = "android.intent.action.OVERLAY_CHANGED"
        val CONFIG_ICON_MASK_RES_ID: Int =
            Resources.getSystem().getIdentifier("config_icon_mask", "string", "android")

        private fun ShapeDelegate.createIconShape(size: Int) =
            generateIconShape(size, getPath(size.toFloat()))

        const val ICON_FACTORY_DAGGER_KEY = "ICON_FACTORIES"
    }
}
