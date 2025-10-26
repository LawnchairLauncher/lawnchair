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

package com.android.wm.shell.shared.multiinstance
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.ColorInt
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.util.TypedValue
import android.view.MotionEvent.ACTION_OUTSIDE
import android.view.SurfaceView
import android.view.View
import android.view.View.ALPHA
import android.view.View.SCALE_X
import android.view.View.SCALE_Y
import android.view.ViewGroup.MarginLayoutParams
import android.widget.LinearLayout
import android.window.TaskSnapshot
import com.android.wm.shell.R

/**
 * View for the All Windows menu option, used by both Desktop Windowing and Taskbar.
 * The menu displays icons of all open instances of an app. Clicking the icon should launch
 * the instance, which will be performed by the child class.
 */
abstract class ManageWindowsViewContainer(
    val context: Context,
    @ColorInt private val menuBackgroundColor: Int
) {
    lateinit var menuView: ManageWindowsView

    /** Creates the base menu view and fills it with icon views. */
    fun createMenu(snapshotList: List<Pair<Int, TaskSnapshot?>>,
             onIconClickListener: ((Int) -> Unit),
             onOutsideClickListener: (() -> Unit)): ManageWindowsView {
        val bitmapList = snapshotList
            .filter { it.second != null }
            .map { (index, snapshot) ->
                index to Bitmap.wrapHardwareBuffer(snapshot!!.hardwareBuffer, snapshot.colorSpace)
            }
        return createAndShowMenuView(
            bitmapList,
            onIconClickListener,
            onOutsideClickListener
        )
    }

    /** Creates the menu view with the given bitmaps, and displays it. */
    fun createAndShowMenuView(
        snapshotList: List<Pair<Int, Bitmap?>>,
        onIconClickListener: ((Int) -> Unit),
        onOutsideClickListener: (() -> Unit)
    ): ManageWindowsView {
        menuView = ManageWindowsView(context, menuBackgroundColor).apply {
            this.onOutsideClickListener = onOutsideClickListener
            this.onIconClickListener = onIconClickListener
            this.generateIconViews(snapshotList)
        }
        addToContainer(menuView)
        return menuView
    }

    /** Play the animation for opening the menu. */
    fun animateOpen() {
        menuView.animateOpen()
    }

    /**
     * Play the animation for closing the menu. On finish, will run the provided callback,
     * which will be responsible for removing the view from the container used in [addToContainer].
     */
    fun animateClose() {
        menuView.animateClose { removeFromContainer() }
    }

    /** Adds the menu view to the container responsible for displaying it. */
    abstract fun addToContainer(menuView: ManageWindowsView)

    /** Removes the menu view from the container used in the method above */
    abstract fun removeFromContainer()

    companion object {
        const val MANAGE_WINDOWS_MINIMUM_INSTANCES = 2
    }

    class ManageWindowsView(
        private val context: Context,
        menuBackgroundColor: Int
    ) {
        private val animators = mutableListOf<Animator>()
        private val iconViews = mutableListOf<SurfaceView>()
        val rootView: LinearLayout = LinearLayout(context)
        var menuHeight = 0
        var menuWidth = 0
        var onIconClickListener: ((Int) -> Unit)? = null
        var onOutsideClickListener: (() -> Unit)? = null

        init {
            rootView.orientation = LinearLayout.VERTICAL
            val menuBackground = ShapeDrawable()
            val menuRadius = getDimensionPixelSize(MENU_RADIUS_DP)
            menuBackground.shape = RoundRectShape(
                FloatArray(8) { menuRadius },
                null,
                null
            )
            menuBackground.paint.color = menuBackgroundColor
            rootView.background = menuBackground
            rootView.elevation = getDimensionPixelSize(MENU_ELEVATION_DP)
            rootView.setOnTouchListener { _, event ->
                if (event.actionMasked == ACTION_OUTSIDE) {
                    onOutsideClickListener?.invoke()
                }
                return@setOnTouchListener true
            }
        }

        private fun getDimensionPixelSize(sizeDp: Float): Float {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                sizeDp, context.resources.displayMetrics)
        }

        fun generateIconViews(
            snapshotList: List<Pair<Int, Bitmap?>>
        ) {
            menuWidth = 0
            menuHeight = 0
            rootView.removeAllViews()
            val instanceIconHeight = getDimensionPixelSize(ICON_HEIGHT_DP)
            val instanceIconWidth = getDimensionPixelSize(ICON_WIDTH_DP)
            val iconRadius = getDimensionPixelSize(ICON_RADIUS_DP)
            val iconMargin = getDimensionPixelSize(ICON_MARGIN_DP)
            var rowLayout: LinearLayout? = null
            // Add each icon to the menu, adding a new row when needed.
            for ((iconCount, taskInfoSnapshotPair) in snapshotList.withIndex()) {
                val taskId = taskInfoSnapshotPair.first
                val snapshotBitmap = taskInfoSnapshotPair.second
                // Once a row is filled, make a new row and increase the menu height.
                if (iconCount % MENU_MAX_ICONS_PER_ROW == 0) {
                    rowLayout = LinearLayout(context)
                    rowLayout.orientation = LinearLayout.HORIZONTAL
                    rootView.addView(rowLayout)
                    menuHeight += (instanceIconHeight + iconMargin).toInt()
                }

                val croppedBitmap = snapshotBitmap?.let { cropBitmap(it) }
                val scaledSnapshotBitmap = croppedBitmap?.let {
                    Bitmap.createScaledBitmap(
                        it, instanceIconWidth.toInt(), instanceIconHeight.toInt(), true /* filter */
                    )
                }
                val appSnapshotButton = SurfaceView(context)
                appSnapshotButton.cornerRadius = iconRadius
                appSnapshotButton.setZOrderOnTop(true)
                appSnapshotButton.contentDescription = context.resources.getString(
                    R.string.manage_windows_icon_text, iconCount + 1
                )
                appSnapshotButton.setOnClickListener {
                    onIconClickListener?.invoke(taskId)
                }
                val lp = MarginLayoutParams(
                    instanceIconWidth.toInt(), instanceIconHeight.toInt()
                )
                lp.apply {
                    marginStart = iconMargin.toInt()
                    topMargin = iconMargin.toInt()
                }
                appSnapshotButton.layoutParams = lp
                // If we haven't already reached one full row, increment width.
                if (iconCount < MENU_MAX_ICONS_PER_ROW) {
                    menuWidth += (instanceIconWidth + iconMargin).toInt()
                }
                rowLayout?.addView(appSnapshotButton)
                iconViews += appSnapshotButton
                appSnapshotButton.requestLayout()
                rowLayout?.post {
                    appSnapshotButton.holder.surface
                        .attachAndQueueBufferWithColorSpace(
                            scaledSnapshotBitmap?.hardwareBuffer,
                            scaledSnapshotBitmap?.colorSpace
                        )
                }
            }
            // Add margin again for the right/bottom of the menu.
            menuWidth += iconMargin.toInt()
            menuHeight += iconMargin.toInt()
        }

        private fun cropBitmap(
            bitmapToCrop: Bitmap
        ): Bitmap {
            val ratioToMatch = ICON_WIDTH_DP / ICON_HEIGHT_DP
            val bitmapWidth = bitmapToCrop.width
            val bitmapHeight = bitmapToCrop.height
            if (bitmapWidth > bitmapHeight * ratioToMatch) {
                // Crop based on height
                val newWidth = bitmapHeight * ratioToMatch
                return Bitmap.createBitmap(
                    bitmapToCrop,
                    ((bitmapWidth - newWidth) / 2).toInt(),
                    0,
                    newWidth.toInt(),
                    bitmapHeight
                )
            } else {
                // Crop based on width
                val newHeight = bitmapWidth / ratioToMatch
                return Bitmap.createBitmap(
                    bitmapToCrop,
                    0,
                    ((bitmapHeight - newHeight) / 2).toInt(),
                    bitmapWidth,
                    newHeight.toInt()
                )
            }
        }

        /** Play the animation for opening the menu. */
        fun animateOpen() {
            animateView(rootView, MENU_BOUNDS_SHRUNK_SCALE, MENU_BOUNDS_FULL_SCALE,
                MENU_START_ALPHA, MENU_FULL_ALPHA
            )
            for (view in iconViews) {
                animateView(view, MENU_BOUNDS_SHRUNK_SCALE, MENU_BOUNDS_FULL_SCALE,
                    MENU_START_ALPHA, MENU_FULL_ALPHA
                )
            }
            createAnimatorSet().start()
        }

        /** Play the animation for closing the menu. */
        fun animateClose(callback: () -> Unit) {
            animateView(rootView, MENU_BOUNDS_FULL_SCALE, MENU_BOUNDS_SHRUNK_SCALE,
                MENU_FULL_ALPHA, MENU_START_ALPHA
            )
            for (view in iconViews) {
                animateView(view, MENU_BOUNDS_FULL_SCALE, MENU_BOUNDS_SHRUNK_SCALE,
                    MENU_FULL_ALPHA, MENU_START_ALPHA
                )
            }
            createAnimatorSet().apply {
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            callback.invoke()
                        }
                    }
                )
                start()
            }
        }

        private fun animateView(
            view: View,
            startBoundsScale: Float,
            endBoundsScale: Float,
            startAlpha: Float,
            endAlpha: Float) {
            animators += ObjectAnimator.ofFloat(
                view,
                SCALE_X,
                startBoundsScale,
                endBoundsScale
            ).apply {
                duration = MENU_BOUNDS_ANIM_DURATION
            }
            animators += ObjectAnimator.ofFloat(
                view,
                SCALE_Y,
                startBoundsScale,
                endBoundsScale
            ).apply {
                duration = MENU_BOUNDS_ANIM_DURATION
            }
            animators += ObjectAnimator.ofFloat(
                view,
                ALPHA,
                startAlpha,
                endAlpha
            ).apply {
                duration = MENU_ALPHA_ANIM_DURATION
                startDelay = MENU_ALPHA_ANIM_DELAY
            }
        }

        private fun createAnimatorSet(): AnimatorSet {
            val animatorSet = AnimatorSet().apply {
                playTogether(animators)
            }
            animators.clear()
            return animatorSet
        }

        companion object {
            private const val MENU_RADIUS_DP = 26f
            private const val ICON_WIDTH_DP = 204f
            private const val ICON_HEIGHT_DP = 127.5f
            private const val ICON_RADIUS_DP = 16f
            private const val ICON_MARGIN_DP = 16f
            private const val MENU_ELEVATION_DP = 1f
            private const val MENU_MAX_ICONS_PER_ROW = 3
            private const val MENU_BOUNDS_ANIM_DURATION = 200L
            private const val MENU_BOUNDS_SHRUNK_SCALE = 0.8f
            private const val MENU_BOUNDS_FULL_SCALE = 1f
            private const val MENU_ALPHA_ANIM_DURATION = 100L
            private const val MENU_ALPHA_ANIM_DELAY = 50L
            private const val MENU_START_ALPHA = 0f
            private const val MENU_FULL_ALPHA = 1f
        }
    }
}
