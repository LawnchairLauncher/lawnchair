package ch.deletescape.lawnchair.colors.preferences

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import ch.deletescape.lawnchair.ViewPagerAdapter
import ch.deletescape.lawnchair.colors.*
import com.android.launcher3.R
import kotlinx.android.synthetic.main.tabbed_color_picker.view.*
import me.priyesh.chroma.*

@SuppressLint("ViewConstructor")
class TabbedPickerView(context: Context, initialColor: Int, private val dismiss: () -> Unit)
    : RelativeLayout(context, null) {

    private val engine = ColorEngine.getInstance(context)

    private val colors = listOf(
            PixelAccentResolver(ColorEngine.ColorResolver.Config(engine)),
            SystemAccentResolver(ColorEngine.ColorResolver.Config(engine)),
            WallpaperMainColorResolver(ColorEngine.ColorResolver.Config(engine)),
            WallpaperSecondaryColorResolver(ColorEngine.ColorResolver.Config(engine)))

    private val isLandscape = orientation(context) == Configuration.ORIENTATION_LANDSCAPE

    private val minItemHeight = context.resources.getDimensionPixelSize(R.dimen.color_preview_height)
    private val minItemWidthLandscape = context.resources.getDimensionPixelSize(R.dimen.color_preview_width)
    private val chromaViewHeight = context.resources.getDimensionPixelSize(R.dimen.chroma_view_height)
    private val viewHeightLandscape = context.resources.getDimensionPixelSize(R.dimen.chroma_dialog_height)

    val chromaView = ChromaView(initialColor, ColorMode.RGB, context).apply {
        enableButtonBar(object : ChromaView.ButtonBarListener {
            override fun onNegativeButtonClick() = dismiss()
            override fun onPositiveButtonClick(color: Int) {
                val red = Color.red(color)
                val green = Color.green(color)
                val blue = Color.blue(color)
                engine.accentResolver = RGBColorResolver(
                        ColorEngine.ColorResolver.Config(engine, args = listOf("$red", "$green", "$blue")))
                dismiss()
            }
        })
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.tabbed_color_picker, this)
        measure(MeasureSpec.EXACTLY,0)
        viewPager.adapter = ViewPagerAdapter(listOf(
                Pair(context.getString(R.string.color_presets), initPresetList()),
                Pair(context.getString(R.string.color_custom), chromaView)
        ))
        if (!isLandscape) {
            viewPager.layoutParams.height = chromaViewHeight
        } else {
            viewPager.layoutParams.height = viewHeightLandscape
        }
        viewPager.childFilter = { it is ChromaView }
        tabLayout.setupWithViewPager(viewPager)
        if (engine.accentResolver is RGBColorResolver) {
            viewPager.currentItem = 1
        }
    }

    @SuppressLint("InflateParams")
    private fun initPresetList(): View {
        return ExpandFillLinearLayout(context, null).apply {
            orientation = if (isLandscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            childWidth = minItemWidthLandscape
            childHeight = minItemHeight

            colors.forEach {
                val preview = LayoutInflater.from(context).inflate(R.layout.color_preview, null) as ColorPreviewView
                preview.colorResolver = it
                preview.setOnClickListener { _ ->
                    engine.accentResolver = it
                    dismiss()
                }
                addView(preview)
            }
        }
    }
}
