package ch.deletescape.lawnchair.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.animation.AccelerateInterpolator
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider
import ch.deletescape.lawnchair.blurWallpaperProvider
import ch.deletescape.lawnchair.graphics.NinePatchDrawHelper
import com.android.launcher3.*
import com.android.launcher3.config.FeatureFlags
import com.android.launcher3.graphics.GradientView
import com.android.launcher3.graphics.ShadowGenerator

/*
 * Copyright (C) 2018 paphonb@xda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class AllAppsScrim(context: Context, attrs: AttributeSet?)
    : GradientView(context, attrs), Insettable {
    private val pStyle = FeatureFlags.LAUNCHER3_P_ALL_APPS

    private val mFillPaint = Paint(1)
    private val mDrawRect = Rect()
    private val mPadding = Rect()
    private val mInsets = Rect()
    private val mRounded by lazy { Utilities.getLawnchairPrefs(context).dockRoundedCorners }
    private val mShadowHelper by lazy { NinePatchDrawHelper() }
    private val mRadius by lazy { if (mRounded) resources.getDimension(R.dimen.all_apps_scrim_radius) else 0f }
    private val mShadowBlur by lazy { resources.getDimension(R.dimen.all_apps_scrim_blur) }
    private val mDrawMargin by lazy { mRadius + mShadowBlur }
    private val mDeviceProfile by lazy { Launcher.getLauncher(context).deviceProfile }
    private val mMinAlpha by lazy { if (mDeviceProfile.isVerticalBarLayout) 235 else 100 }
    private val mAlphaRange by lazy { 235 - mMinAlpha }
    private val mFillAlpha by lazy { mMinAlpha }
    private val mShadowBitmap by lazy {
        val tmp = mRadius + mShadowBlur
        val builder = ShadowGenerator.Builder(0)
        builder.radius = mRadius
        builder.shadowBlur = mShadowBlur
        val round = 2 * Math.round(tmp) + 20
        val bitmap = Bitmap.createBitmap(round, round / 2, Bitmap.Config.ARGB_8888)
        val f = 2.0f * tmp + 20.0f - mShadowBlur
        builder.bounds.set(mShadowBlur, mShadowBlur, f, f)
        builder.drawShadow(Canvas(bitmap))
        bitmap
    }
    private var mDrawOffsetY = 0f
    private var mDrawHeight = 0f
    private val mAccelerator by lazy { AccelerateInterpolator() }

    private val blurDrawableCallback by lazy {
        object : Drawable.Callback {
            override fun unscheduleDrawable(who: Drawable?, what: Runnable?) {

            }

            override fun invalidateDrawable(who: Drawable?) {
                invalidateDrawRect()
            }

            override fun scheduleDrawable(who: Drawable?, what: Runnable?, `when`: Long) {

            }
        }
    }

    private val blurRadius = if (pStyle) mRadius else 0f
    private val blurDrawable = if (BlurWallpaperProvider.isEnabled) {
        context.blurWallpaperProvider.createDrawable(blurRadius, false).apply { callback = blurDrawableCallback }
    } else {
        null
    }

    init {
        updateColors()
    }

    override fun updateColors() {
        super.updateColors()

        if (pStyle) {
            mFillPaint.color = mScrimColor
        }
    }

    override fun createRadialShader() {
        if (!pStyle) super.createRadialShader()
    }

    override fun onDraw(canvas: Canvas) {
        if (pStyle) {
            val height = height.toFloat() + mDrawOffsetY - mDrawHeight + mPadding.top.toFloat()
            val width = (width - mPadding.right).toFloat()
            blurDrawable?.run {
                setBounds(mPadding.left, height.toInt(), width.toInt(), (getHeight().toFloat() + mRadius).toInt())
                draw(canvas)
            }
            if (mRounded) {
                if (mPadding.left <= 0 && mPadding.right <= 0) {
                    mShadowHelper.draw(mShadowBitmap, canvas, mPadding.left.toFloat() - mShadowBlur, height - mShadowBlur, width + mShadowBlur)
                } else {
                    val f = mPadding.left.toFloat() - mShadowBlur
                    val f2 = height - mShadowBlur
                    val f3 = mShadowBlur + width
                    val height2 = getHeight().toFloat()
                    mShadowHelper.draw(mShadowBitmap, canvas, f, f2, f3)
                    val height3 = mShadowBitmap.height
                    mShadowHelper.mSrc.top = height3 - 5
                    mShadowHelper.mSrc.bottom = height3
                    mShadowHelper.mDst.top = f2 + height3.toFloat()
                    mShadowHelper.mDst.bottom = height2
                    mShadowHelper.draw3Patch(mShadowBitmap, canvas, f, f3)
                }
            }
            canvas.drawRoundRect(mPadding.left.toFloat(), height, width, getHeight().toFloat() + mRadius, mRadius, mRadius, mFillPaint)
        } else {
            blurDrawable?.draw(canvas)
            super.onDraw(canvas)
        }
    }

    fun invalidateDrawRect() {
        mDrawRect.top = (height.toFloat() + mDrawOffsetY - mDrawHeight + mPadding.top.toFloat() - mShadowBlur - 0.5f).toInt()
        invalidate(mDrawRect)
    }

    override fun setInsets(insets: Rect) {
        mInsets.set(insets)
        if (mDeviceProfile.isVerticalBarLayout) {
            mPadding.set(mDeviceProfile.getWorkspacePadding(null))
            mPadding.bottom = 0
            val rect = mPadding
            rect.left += mInsets.left
            mPadding.top = mInsets.top
            rect.right += mInsets.right
            mDrawHeight = 0f
        } else {
            mPadding.setEmpty()
            mDrawHeight = getHotseatHeight(insets).toFloat()
            if (mRounded) mDrawHeight += resources.getDimension(R.dimen.all_apps_scrim_margin)
        }
        updateDrawRect(mDeviceProfile)
        invalidate()
    }

    private fun getHotseatHeight(insets: Rect): Int {
        return if (insets.bottom != 0) {
            mDeviceProfile.originalHotseatBarSizePx + insets.bottom
        } else {
            mDeviceProfile.originalHotseatBarSizePx + mDeviceProfile.mBottomMarginHw
        }
    }

    private fun updateDrawRect(deviceProfile: DeviceProfile) {
        mDrawRect.bottom = height
        if (deviceProfile.isVerticalBarLayout) {
            mDrawRect.left = (mPadding.left.toFloat() - mShadowBlur - 0.5f).toInt()
            mDrawRect.right = ((width - mPadding.right).toFloat() + 0.5f).toInt()
            return
        }
        mDrawRect.left = 0
        mDrawRect.right = width
    }

    override fun setProgress(progress: Float, shiftRange: Float) {
        if (pStyle) {
            val interpolatedAlpha = mAlphaRange * mAccelerator.getInterpolation(progress)
            mFillPaint.alpha = (mMinAlpha + interpolatedAlpha).toInt()
            mDrawOffsetY = -shiftRange * progress
            invalidateDrawRect()
        } else {
            super.setProgress(progress, shiftRange)
            blurDrawable?.alpha = (progress * 255).toInt()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        if (pStyle) {
            blurDrawable?.setBounds(left, top, right, bottom)
        } else {
            val screenSize = Utilities.getScreenSize(context)
            blurDrawable?.setBounds(0, 0, screenSize.first, screenSize.second)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        blurDrawable?.startListening()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        blurDrawable?.stopListening()
    }

    override fun setTranslationX(translationX: Float) {
        super.setTranslationX(translationX)

        if (pStyle) blurDrawable?.setPotitionX(translationX)
    }
}