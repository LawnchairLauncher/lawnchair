package ch.deletescape.lawnchair.colors.preferences

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import ch.deletescape.lawnchair.ViewPagerAdapter
import ch.deletescape.lawnchair.colors.*
import com.android.launcher3.R
import kotlinx.android.synthetic.main.tabbed_color_picker.view.*
import me.priyesh.chroma.ChromaView
import me.priyesh.chroma.ColorMode

@SuppressLint("ViewConstructor")
class TabbedPickerView(context: Context, initialColor: Int, private val dismiss: () -> Unit)
    : RelativeLayout(context, null) {

    private val engine = ColorEngine.getInstance(context)

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
        viewPager.adapter = ViewPagerAdapter(listOf(
                Pair(context.getString(R.string.color_presets), initRecyclerView()),
                Pair(context.getString(R.string.color_custom), chromaView)
        ))
        tabLayout.setupWithViewPager(viewPager)
        if (engine.accentResolver is RGBColorResolver) {
            viewPager.currentItem = 1
        }
    }

    private fun initRecyclerView(): View {
        val recyclerView = LayoutInflater.from(context)
                .inflate(R.layout.preference_spring_recyclerview, this, false) as RecyclerView
        recyclerView.adapter = ColorsAdapter()
        recyclerView.layoutManager = LinearLayoutManager(context)
        return recyclerView
    }

    inner class ColorsAdapter : RecyclerView.Adapter<ColorsAdapter.Holder>() {

        private val colors = listOf(
                SystemAccentResolver(ColorEngine.ColorResolver.Config(engine)),
                PixelAccentResolver(ColorEngine.ColorResolver.Config(engine)),
                WallpaperMainColorResolver(ColorEngine.ColorResolver.Config(engine)),
                WallpaperSecondaryColorResolver(ColorEngine.ColorResolver.Config(engine)))

        override fun getItemCount() = colors.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(LayoutInflater.from(parent.context).inflate(R.layout.color_preview, parent, false) as TextView)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val color = colors[position]
            holder.text.setBackgroundColor(color.resolveColor())
            holder.text.text = color.getDisplayName()
        }

        inner class Holder(val text: TextView) : RecyclerView.ViewHolder(text) {

            init {
                text.setOnClickListener {
                    engine.accentResolver = colors[adapterPosition]
                    dismiss()
                }
            }
        }
    }
}
