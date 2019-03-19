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

package ch.deletescape.lawnchair.font.settingsui

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.support.annotation.Keep
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.EditText
import android.widget.TextView
import ch.deletescape.lawnchair.font.CustomFontManager
import ch.deletescape.lawnchair.font.FontCache
import ch.deletescape.lawnchair.font.googlefonts.GoogleFontsListing
import ch.deletescape.lawnchair.preferences.RecyclerViewFragment
import com.android.launcher3.R

@Keep
class FontSelectionFragment : RecyclerViewFragment() {

    override val layoutId = R.layout.fragment_font_selection

    private val adapter by lazy { FontAdapter(activity!!) }
    private val title by lazy { arguments!!.getString(ARG_TITLE) }
    private val key by lazy { arguments!!.getString(ARG_KEY) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onResume() {
        super.onResume()

        activity!!.title = title
    }

    private fun setFont(font: FontCache.Font) {
        CustomFontManager.getInstance(activity!!).fontPrefs.getValue(key).set(font)
        activity!!.finish()
    }

    private fun resetFont() {
        CustomFontManager.getInstance(activity!!).fontPrefs.getValue(key).reset()
        activity!!.finish()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_font_selection, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_reset) {
            resetFont()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<EditText>(R.id.search_input).addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(s: Editable) {
                adapter.search(s.toString())
            }
        })
    }

    override fun onRecyclerViewCreated(recyclerView: RecyclerView) {
        val context = recyclerView.context

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    inner class FontAdapter(private val context: Context) : RecyclerView.Adapter<FontAdapter.Holder>() {

        private val fonts = ArrayList<Cache>()
        private val filtered = ArrayList<Cache>()
        private var searchQuery = ""

        init {
            GoogleFontsListing.getInstance(context).getFonts {
                fonts.add(Cache(FontCache.SystemFont("sans-serif")))
                fonts.add(Cache(FontCache.SystemFont("sans-serif-medium")))
                fonts.add(Cache(FontCache.SystemFont("sans-serif-condensed")))
                it.flatMapTo(fonts) { font ->
                    font.variants.map { variant ->
                        Cache(FontCache.GoogleFont(context, font.family, variant))
                    }
                }
                filterItems()
            }
        }

        fun search(query: String) {
            searchQuery = query.toLowerCase()
            filterItems()
        }

        private fun filterItems() {
            filtered.clear()
            fonts.filterTo(filtered) { it.font.displayName.toLowerCase().contains(searchQuery) }
            notifyDataSetChanged()
        }

        override fun getItemCount() = filtered.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(LayoutInflater.from(context).inflate(R.layout.font_item, parent, false))
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(filtered[position])
        }

        inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView),
                FontCache.Font.LoadCallback, View.OnClickListener {

            private val title: TextView = itemView.findViewById(android.R.id.title)

            private var cache: Cache? = null
                set(value) {
                    field?.callback = null
                    field = value
                    field?.let {
                        it.callback = this
                        it.loadFont()
                    }
                }

            init {
                itemView.setOnClickListener(this)
            }

            override fun onClick(v: View) {
                setFont(filtered[adapterPosition].font)
            }

            fun bind(cache: Cache) {
                title.text = cache.font.displayName
                title.typeface = Typeface.DEFAULT
                this.cache = cache
            }

            override fun onFontLoaded(typeface: Typeface?) {
                title.typeface = typeface ?: Typeface.DEFAULT
            }
        }

        inner class Cache(val font: FontCache.Font): FontCache.Font.LoadCallback {

            var callback: FontCache.Font.LoadCallback? = null
            var typeface: Typeface? = null
            var loading = false
            var loaded = false

            fun loadFont() {
                if (loading) return
                if (loaded) {
                    callback?.onFontLoaded(typeface)
                    return
                }
                loading = true
                font.load(this)
            }

            override fun onFontLoaded(typeface: Typeface?) {
                loading = false
                loaded = true
                this.typeface = typeface
                callback?.onFontLoaded(typeface)
            }
        }
    }

    companion object {

        const val ARG_TITLE = "title"
        const val ARG_KEY = "key"
    }
}
