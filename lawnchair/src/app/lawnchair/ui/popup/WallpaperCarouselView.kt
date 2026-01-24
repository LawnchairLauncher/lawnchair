package app.lawnchair.ui.popup

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import app.lawnchair.LawnchairLauncher
import app.lawnchair.data.wallpaper.Wallpaper
import app.lawnchair.data.wallpaper.model.WallpaperViewModel
import app.lawnchair.views.component.IconFrame
import com.android.launcher3.R
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ActivityContext
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WallpaperCarouselView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val viewModel: WallpaperViewModel by (context as ComponentActivity).viewModels()
    private val deviceProfile = ActivityContext.lookupContext<LawnchairLauncher>(context).deviceProfile
    private var currentItemIndex = 0
    private val iconFrame = IconFrame(context).apply {
        setIcon(R.drawable.ic_tick)
        setBackgroundWithRadius(Themes.getColorAccent(context), 100F)
    }
    private val loadingView = ProgressBar(context).apply { isIndeterminate = true }

    init {
        orientation = HORIZONTAL
        addView(loadingView)
        observeWallpapers()
    }

    private fun observeWallpapers() {
        viewModel.wallpapers.observe(context as LifecycleOwner) { wallpapers ->
            visibility = if (wallpapers.isEmpty()) GONE else VISIBLE
            loadingView.visibility = if (wallpapers.isEmpty()) GONE else VISIBLE
            if (wallpapers.isNotEmpty()) displayWallpapers(wallpapers)
        }
    }

    private fun displayWallpapers(wallpapers: List<Wallpaper>) {
        removeAllViews()
        val totalWidth = calculateTotalWidth()
        val firstItemWidth = totalWidth * 0.4
        val itemWidth = calculateItemWidth(totalWidth, wallpapers.size, firstItemWidth)
        val margin = (totalWidth * 0.03).toInt()

        wallpapers.forEachIndexed { index, wallpaper ->
            val cardView = createCardView(index, firstItemWidth, itemWidth, margin, wallpaper)
            addView(cardView)
            loadWallpaperImage(wallpaper, cardView, index == currentItemIndex)
        }
        loadingView.visibility = GONE
    }

    private fun calculateTotalWidth(): Int {
        return width.takeIf { it > 0 }
            ?: (deviceProfile.deviceProperties.widthPx * if (deviceProfile.deviceProperties.isLandscape || deviceProfile.deviceProperties.isPhone) 0.5 else 0.8).toInt()
    }

    private fun calculateItemWidth(totalWidth: Int, itemCount: Int, firstItemWidth: Double): Double {
        val remainingWidth = totalWidth - firstItemWidth
        val marginBetweenItems = totalWidth * 0.03
        return (remainingWidth - (marginBetweenItems * (itemCount - 1))) / (itemCount - 1)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createCardView(
        index: Int,
        firstItemWidth: Double,
        itemWidth: Double,
        margin: Int,
        wallpaper: Wallpaper,
    ): CardView {
        return CardView(context).apply {
            radius = Themes.getDialogCornerRadius(context) / 2
            layoutParams = LayoutParams(
                if (index == currentItemIndex) firstItemWidth.toInt() else itemWidth.toInt(),
                LayoutParams.MATCH_PARENT,
            ).apply { setMargins(if (index > 0) margin else 0, 0, 0, 0) }

            setOnTouchListener { _, _ ->
                if (index != currentItemIndex) {
                    animateWidthTransition(index, firstItemWidth, itemWidth)
                } else {
                    setWallpaper(wallpaper)
                }
                true
            }
        }
    }

    private fun loadWallpaperImage(wallpaper: Wallpaper, cardView: CardView, isCurrent: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            val bitmap = File(wallpaper.imagePath).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
            withContext(Dispatchers.Main) { addImageView(cardView, bitmap, isCurrent) }
        }
    }

    private fun addImageView(cardView: CardView, bitmap: Bitmap?, isCurrent: Boolean) {
        val imageView = ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_deepshortcut_placeholder))
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0f
        }
        cardView.addView(imageView)
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap)
            imageView.animate().alpha(1f).setDuration(200L).withEndAction {
                if (isCurrent) addIconFrameToCenter(cardView)
            }.start()
        }
    }

    private fun setWallpaper(wallpaper: Wallpaper) {
        val currentCardView = getChildAt(currentItemIndex) as CardView
        val spinner = createLoadingSpinner()

        currentCardView.removeView(iconFrame)
        currentCardView.addView(spinner)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                WallpaperManager.getInstance(context).setBitmap(
                    BitmapFactory.decodeFile(wallpaper.imagePath),
                    null,
                    true,
                    WallpaperManager.FLAG_SYSTEM,
                )
                viewModel.updateWallpaperRank(wallpaper)
            } catch (e: Exception) {
                Log.e("WallpaperCarouselView", "Failed to set wallpaper: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    currentCardView.removeView(spinner)
                    addIconFrameToCenter(currentCardView)
                }
            }
        }
    }

    private fun createLoadingSpinner() = ProgressBar(context).apply {
        isIndeterminate = true
        layoutParams = FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        }
    }

    private fun addIconFrameToCenter(cardView: CardView? = getChildAt(currentItemIndex) as CardView) {
        (iconFrame.parent as? ViewGroup)?.removeView(iconFrame)
        cardView?.addView(
            iconFrame,
            FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            },
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewModel.wallpapers.value?.let { displayWallpapers(it) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(calculateTotalWidth(), MeasureSpec.EXACTLY),
            heightMeasureSpec,
        )
    }

    private fun animateWidthTransition(newIndex: Int, firstItemWidth: Double, itemWidth: Double) {
        currentItemIndex = newIndex
        for (i in 0 until childCount) {
            (getChildAt(i) as? CardView)?.let { cardView ->
                val targetWidth = if (i == currentItemIndex) firstItemWidth.toInt() else itemWidth.toInt()
                if (cardView.layoutParams.width != targetWidth) {
                    ValueAnimator.ofInt(cardView.layoutParams.width, targetWidth).apply {
                        duration = 300L
                        addUpdateListener {
                            cardView.layoutParams.width = it.animatedValue as Int
                            cardView.requestLayout()
                        }
                        start()
                    }
                }
                if (i == currentItemIndex) addIconFrameToCenter(cardView)
            }
        }
    }
}
