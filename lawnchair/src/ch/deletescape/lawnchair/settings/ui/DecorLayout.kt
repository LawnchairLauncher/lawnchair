/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.settings.ui

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.os.Environment
import android.support.design.widget.Snackbar
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import ch.deletescape.lawnchair.blur.BlurDrawable
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider
import ch.deletescape.lawnchair.getBooleanAttr
import ch.deletescape.lawnchair.getColorAttr
import ch.deletescape.lawnchair.getDimenAttr
import ch.deletescape.lawnchair.isVisible
import ch.deletescape.lawnchair.util.parents
import com.android.launcher3.Insettable
import com.android.launcher3.InsettableFrameLayout
import com.android.launcher3.R
import com.android.launcher3.Utilities
import java.io.File

@SuppressLint("ViewConstructor")
class DecorLayout(context: Context, private val window: Window) : InsettableFrameLayout(context, null),
        View.OnClickListener, BlurWallpaperProvider.Listener {

    private var tapCount = 0

    private val contentFrame: View
    private val actionBarContainer: View
    private val toolbar: View
    private val largeTitle: View
    private val toolbarShadow: View
    internal var toolbarElevated = false
        set(value) {
            if (field != value) {
                field = value
                if (value) {
                    toolbarShadow.animate().alpha(1f).setDuration(200).start()
                } else {
                    toolbarShadow.animate().alpha(0f).setDuration(200).start()
                }
            }
        }

    private val shouldDrawBackground by lazy { context.getBooleanAttr(android.R.attr.windowShowWallpaper) }
    private val settingsBackground by lazy { context.getColorAttr(R.attr.settingsBackground) }

    var actionBarElevation: Float
        get() = actionBarContainer.elevation
        set(value) {
            actionBarContainer.elevation = value
            if (value.compareTo(0f) == 0) {
                actionBarContainer.background = null
                window.statusBarColor = 0
            } else {
                val backgroundColor = settingsBackground
                actionBarContainer.background = ColorDrawable(backgroundColor)
                window.statusBarColor = backgroundColor
            }
        }

    var useLargeTitle: Boolean = false
        set(value) {
            field = value
            updateToolbar()
        }

    var hideToolbar: Boolean = false
        set(value) {
            field = value
            updateToolbar()
        }

    private val contentTop get() = when {
        hideToolbar -> 0
        useLargeTitle -> context.resources.getDimensionPixelSize(R.dimen.large_title_height)
        else -> context.getDimenAttr(R.attr.actionBarSize)
    }

    private fun updateToolbar() {
        largeTitle.visibility = if (useLargeTitle && !hideToolbar) View.VISIBLE else View.GONE
        toolbar.visibility = if (!useLargeTitle && !hideToolbar) View.VISIBLE else View.GONE
        toolbarShadow.visibility = toolbar.visibility
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.decor_layout, this)

        contentFrame = findViewById(android.R.id.content)
        actionBarContainer = findViewById(R.id.action_bar_container)
        toolbar = findViewById(R.id.toolbar)
        largeTitle = findViewById(R.id.large_title)
        largeTitle.setOnClickListener(this)
        toolbarShadow = findViewById(R.id.toolbar_shadow)

        onEnabledChanged()

        if (shouldDrawBackground) {
            setWillNotDraw(false)
        }
    }

    override fun onEnabledChanged() {
        val enabled = BlurWallpaperProvider.isEnabled
        (background as? BlurDrawable)?.let {
            if (!enabled) {
                it.stopListening()
                background = null
            }
        } ?: if (enabled) {
            background = BlurWallpaperProvider.getInstance(context).createDrawable().apply {
                startListening()
            }
        }
    }

    override fun draw(canvas: Canvas) {
        if (shouldDrawBackground) canvas.drawColor(settingsBackground)
        super.draw(canvas)
    }

    override fun onClick(v: View?) {
        if (tapCount == 6 && allowDevOptions()) {
            Utilities.getLawnchairPrefs(context).developerOptionsEnabled = true
            Snackbar.make(
                    findViewById(R.id.content),
                    R.string.developer_options_enabled,
                    Snackbar.LENGTH_LONG).show()
            tapCount++
        } else if (tapCount < 6) {
            tapCount++
        }
    }

    private fun allowDevOptions(): Boolean {
        return try {
            File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), "Lawnchair/dev").exists()
        } catch (e: SecurityException) {
            false
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        BlurWallpaperProvider.getInstance(context).addListener(this)
        (background as BlurDrawable?)?.startListening()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        BlurWallpaperProvider.getInstance(context).removeListener(this)
        (background as BlurDrawable?)?.stopListening()
    }

    class ContentFrameLayout(context: Context, attrs: AttributeSet?) : InsettableFrameLayout(context, attrs) {

        private var decorLayout: DecorLayout? = null

        private val contentPath = Path()
        internal val dividerPath = Path()
        internal val backScrimPath = Path()
        internal val frontScrimPath = Path()

        private val selfRect = RectF()
        private val insetsRect = RectF()
        private val contentRect = RectF()

        private val dividerSize = Utilities.pxFromDp(1f, resources.displayMetrics).toFloat()

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            decorLayout = parents.first { it is DecorLayout } as DecorLayout
        }

        override fun setInsets(insets: Rect) {
            decorLayout?.also {
                setInsetsInternal(Rect(
                        insets.left,
                        insets.top + it.contentTop,
                        insets.right,
                        insets.bottom))
            } ?: setInsetsInternal(insets)
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            if (changed) {
                selfRect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
                computeClip()
            }
        }

        private fun setInsetsInternal(insets: Rect) {
            super.setInsets(insets)
            insetsRect.set(insets)
            computeClip()
        }

        private fun computeClip() {
            contentRect.set(
                    selfRect.left + insetsRect.left,
                    selfRect.top + insetsRect.top,
                    selfRect.right - insetsRect.right,
                    selfRect.bottom - insetsRect.bottom
            )

            dividerPath.reset()
            when {
                isNavBarToRightEdge() -> dividerPath.addRect(
                        contentRect.right,
                        selfRect.top,
                        contentRect.right + dividerSize,
                        selfRect.bottom,
                        Path.Direction.CW)
                isNavBarToLeftEdge() -> dividerPath.addRect(
                        contentRect.left - dividerSize,
                        selfRect.top,
                        contentRect.left,
                        selfRect.bottom,
                        Path.Direction.CW)
                else -> dividerPath.addRect(
                        selfRect.left,
                        contentRect.bottom,
                        selfRect.right,
                        contentRect.bottom + dividerSize,
                        Path.Direction.CW)
            }

            contentPath.reset()
            contentPath.addRect(contentRect, Path.Direction.CW)

            frontScrimPath.reset()
            frontScrimPath.addRect(selfRect, Path.Direction.CW)
            frontScrimPath.op(contentPath, Path.Op.DIFFERENCE)
            frontScrimPath.op(dividerPath, Path.Op.DIFFERENCE)

            backScrimPath.reset()
            backScrimPath.addRect(selfRect, Path.Direction.CW)
            backScrimPath.op(frontScrimPath, Path.Op.DIFFERENCE)

            invalidate()
        }

        private fun isNavBarToRightEdge(): Boolean {
            return insetsRect.bottom == 0f && insetsRect.right > 0
        }

        private fun isNavBarToLeftEdge(): Boolean {
            return insetsRect.bottom == 0f && insetsRect.left > 0
        }
    }

    class BackScrimView(context: Context, attrs: AttributeSet?) : View(context, attrs), Insettable {

        private val parent by lazy { parents.first { it is ContentFrameLayout } as ContentFrameLayout }

        override fun onFinishInflate() {
            super.onFinishInflate()
            background = background.mutate().apply { alpha = 230 }
        }

        override fun draw(canvas: Canvas) {
            val count = canvas.save()
            canvas.clipPath(parent.backScrimPath)
            super.draw(canvas)
            canvas.restoreToCount(count)
        }

        override fun setInsets(insets: Rect?) {
            // ignore this
        }
    }

    class FrontScrimView(context: Context, attrs: AttributeSet?) : View(context, attrs), Insettable {

        private val parent by lazy { parents.first { it is ContentFrameLayout } as ContentFrameLayout }

        override fun onFinishInflate() {
            super.onFinishInflate()
            background = background.mutate().apply { alpha = 230 }
        }

        override fun draw(canvas: Canvas) {
            val count = canvas.save()
            canvas.clipPath(parent.frontScrimPath)
            super.draw(canvas)
            canvas.restoreToCount(count)
            canvas.drawPath(parent.dividerPath, Paint().apply { color = 0x1f000000 })
        }

        override fun setInsets(insets: Rect?) {
            // ignore this
        }
    }

    class ToolbarElevationHelper constructor(private val scrollingView: View) : ViewTreeObserver.OnScrollChangedListener {

        private val decorLayout by lazy { scrollingView.parents.first { it is DecorLayout } as DecorLayout }

        init {
            scrollingView.viewTreeObserver.addOnScrollChangedListener(this)
        }

        override fun onScrollChanged() {
            decorLayout.toolbarElevated = scrollingView.canScrollVertically(-1)
        }

        fun destroy() {
            scrollingView.viewTreeObserver.removeOnScrollChangedListener(this)
        }
    }
}
