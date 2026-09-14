/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.launcher3.icons

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.os.UserHandle
import app.lawnchair.icons.CustomAdaptiveIconDrawable
import app.lawnchair.icons.ExtendedBitmapDrawable.Companion.isFromIconPack
import app.lawnchair.icons.getIconBackgroundColor
import app.lawnchair.icons.shouldWrapAdaptive
import app.lawnchair.ui.liquid.LiquidRimLight
import com.android.launcher3.LauncherFiles
import com.android.launcher3.Flags
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.UserIconInfo
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject

/**
 * Wrapper class to provide access to [BaseIconFactory] and also to provide pool of this class that
 * are threadsafe.
 */
class LauncherIcons
@AssistedInject
internal constructor(
    @ApplicationContext context: Context,
    idp: InvariantDeviceProfile,
    private var themeManager: ThemeManager,
    private var userCache: UserCache,
    @Assisted private val pool: ConcurrentLinkedQueue<LauncherIcons>,
) : BaseIconFactory(context, idp.fillResIconDpi, idp.iconBitmapSize), AutoCloseable {

    init {
        mThemeController = themeManager.themeController
    }

    /** Recycles a LauncherIcons that may be in-use. */
    fun recycle() {
        clear()
        pool.add(this)
    }

    override fun getUserInfo(user: UserHandle): UserIconInfo {
        return userCache.getUserInfo(user)
    }

    /**
     * Sora: lights the icon's edge, baked into the bitmap rather than drawn over it.
     *
     * The same top-and-bottom light the glass surfaces catch. Drawing it per
     * frame in the icon view was the obvious way and the wrong one: with a
     * screenful of icons in the app drawer it cost a quarter of the frames.
     * Here it is traced once, when the bitmap is made, and costs nothing to show
     * afterwards. The shape path arrives already built and already in the right
     * coordinate space, so it is exactly the edge the icon will have.
     *
     * No blur: an icon's edge is a hard edge, and softening it only smears the
     * highlight into the artwork.
     */
    override fun drawAdaptiveIcon(canvas: Canvas, drawable: AdaptiveIconDrawable, shapePath: Path) {
        super.drawAdaptiveIcon(canvas, drawable, shapePath)
        // The same thickness the folder plate's rim has, in dp -- not a fraction
        // of the icon.
        //
        // A rim is light landing on an edge, and light does not get thicker
        // because the thing it lands on is bigger. Stated as 2.4% of the icon it
        // came out at 4.3px on a 179px icon against the plate's 2.5px on a 450px
        // one: over four times the weight for its size, which is what made the
        // white edge read as coarse next to the same effect on a folder.
        val rim = mContext.resources.displayMetrics.density * LiquidRimLight.WIDTH_DP
        LiquidRimLight.bake(canvas, shapePath, rim)
    }

    override fun getShapePath(drawable: AdaptiveIconDrawable, iconBounds: Rect): Path {
        if (!Flags.enableLauncherIconShapes()) return super.getShapePath(drawable, iconBounds)
        return themeManager.iconShape.getPath(iconBounds)
    }

    /**
     * Sora Launcher: draw legacy icons edge to edge and let the shape crop them,
     * instead of shrinking them onto an opaque plate.
     *
     * Upstream wraps a non-adaptive icon in an adaptive icon whose background is
     * a solid colour -- white at the default background lightness -- and whose
     * foreground is scaled by [BaseIconFactory.LEGACY_ICON_SCALE], roughly 0.47
     * of the adaptive canvas. The mask covers about 0.67 of that canvas, and the
     * gap between the two is the thick ring of background visible around every
     * legacy icon.
     *
     * Here the background is transparent and the foreground is scaled to fill
     * the masked area, so the shape acts purely as a cutter.
     */
    override fun normalizeAndWrapToAdaptiveIcon(icon: Drawable?, outScale: FloatArray): Drawable? {
        if (icon == null) return null
        if (!shouldMaskOnly(icon)) return super.normalizeAndWrapToAdaptiveIcon(icon, outScale)
        // The result is a real AdaptiveIconDrawable, so it takes the same scale
        // an adaptive icon would; re-normalising it here would shrink it twice.
        outScale[0] = IconNormalizer.ICON_VISIBLE_AREA_FACTOR
        return maskOnlyWrap(icon)
    }

    override fun wrapToAdaptiveIcon(icon: Drawable): AdaptiveIconDrawable {
        if (icon is AdaptiveIconDrawable) return icon
        if (!shouldMaskOnly(icon)) return super.wrapToAdaptiveIcon(icon)
        return maskOnlyWrap(icon)
    }

    /** Honours the user's "auto adaptive icons" switch, and leaves icon packs alone. */
    private fun shouldMaskOnly(icon: Drawable): Boolean =
        maskOnlyIcons &&
            icon !is AdaptiveIconDrawable &&
            !icon.isFromIconPack &&
            shouldWrapAdaptive(mContext)

    private fun maskOnlyWrap(icon: Drawable): CustomAdaptiveIconDrawable {
        // Fraction of the adaptive canvas the mask actually reveals. Scaling the
        // icon to exactly this makes it span the mask edge to edge, so the shape
        // crops it rather than floating it on a plate.
        val maskedFraction = 1f / (1f + 2f * AdaptiveIconDrawable.getExtraInsetFraction())
        // The icon's own dominant colour, not transparency. Artwork rarely
        // reaches the mask edge, and a transparent background lets the wallpaper
        // through the gap -- which on a dark wallpaper reads as a black corner
        // bitten out of the icon rather than as part of it.
        return CustomAdaptiveIconDrawable(
            ColorDrawable(getIconBackgroundColor(mContext, icon)),
            scaledToFill(icon, maskedFraction),
        )
    }

    /**
     * Scales [icon] to [scale] of its bounds, keeping its aspect ratio.
     *
     * This mirrors BaseIconFactory's own private createScaledDrawable(). It uses an
     * [InsetDrawable] rather than FixedScaleDrawable on purpose: FixedScaleDrawable
     * is a DrawableWrapper whose getConstantState() can be null, and FloatingIconView
     * calls Objects.requireNonNull() on it when animating an app open.
     */
    private fun scaledToFill(icon: Drawable, scale: Float): Drawable {
        val h = icon.intrinsicHeight.toFloat()
        val w = icon.intrinsicWidth.toFloat()
        var scaleX = scale
        var scaleY = scale
        if (h > w && w > 0) {
            scaleX *= w / h
        } else if (w > h && h > 0) {
            scaleY *= h / w
        }
        return InsetDrawable(icon, (1 - scaleX) / 2, (1 - scaleY) / 2, (1 - scaleX) / 2, (1 - scaleY) / 2)
    }

    private val maskOnlyIcons: Boolean
        get() = mContext
            .getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
            .getBoolean(PREF_MASK_ONLY_ICONS, true)

    override fun close() {
        recycle()
    }

    @AssistedFactory
    internal interface LauncherIconsFactory {
        fun create(pool: ConcurrentLinkedQueue<LauncherIcons>): LauncherIcons
    }

    @LauncherAppSingleton
    class IconPool @Inject internal constructor(private val factory: LauncherIconsFactory) {
        private var pool = ConcurrentLinkedQueue<LauncherIcons>()

        fun obtain(): LauncherIcons = pool.let { it.poll() ?: factory.create(it) }

        fun clear() {
            pool = ConcurrentLinkedQueue()
        }
    }

    companion object {
        /**
         * Rim thickness as a fraction of the icon's edge, so it reads the same
         * whatever bitmap size the device asks for.
         */


        /** Key of the "shape as mask" switch, shared with Lawnchair's PreferenceManager. */
        const val PREF_MASK_ONLY_ICONS = "prefs_maskOnlyIcons"

        /**
         * Return a new LauncherIcons instance from the global pool. Allows us to avoid allocating
         * new objects in many cases.
         */
        @JvmStatic
        fun obtain(context: Context): LauncherIcons = context.appComponent.iconPool.obtain()

        @JvmStatic fun clearPool(context: Context) = context.appComponent.iconPool.clear()
    }
}
