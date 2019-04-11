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
import android.support.v7.widget.ActionMenuView
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import ch.deletescape.lawnchair.childs
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.font.CustomFontManager
import ch.deletescape.lawnchair.font.FontCache
import ch.deletescape.lawnchair.font.googlefonts.GoogleFontsListing
import ch.deletescape.lawnchair.isVisible
import ch.deletescape.lawnchair.settings.ui.SettingsBaseActivity
import com.android.launcher3.R
import kotlinx.android.synthetic.main.activity_settings_search.*

class FontSelectionActivity : SettingsBaseActivity(), SearchView.OnQueryTextListener {

    private val adapter by lazy { FontAdapter(this) }
    private val key by lazy { intent.getStringExtra(EXTRA_KEY) }
    private val fontPref by lazy { CustomFontManager.getInstance(this).fontPrefs.getValue(key) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        decorLayout.hideToolbar = true

        setContentView(R.layout.activity_settings_search)

        val listResults = list_results
        listResults.shouldTranslateSelf = false
        listResults.adapter = adapter
        listResults.layoutManager = LinearLayoutManager(this)
        listResults.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0) {
                    hideKeyboard()
                }
            }
        })

        setSupportActionBar(search_toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        search_view.setOnQueryTextListener(this)
    }

    private fun hideKeyboard() {
        val view = currentFocus ?: return
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)

        list_results.requestFocus()
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        adapter.search(newText ?: "")
        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return true
    }

    private fun setFont(font: FontCache.Font) {
        fontPref.set(font)
    }

    private fun resetFont() {
        fontPref.reset()
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_font_selection, menu)

        search_toolbar.childs.firstOrNull { it is ActionMenuView }?.let {
            (it as ActionMenuView).overflowIcon?.setTint(ColorEngine.getInstance(this).accent)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_reset -> {
                resetFont()
                return true
            }
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    inner class FontAdapter(private val context: Context) : RecyclerView.Adapter<FontAdapter.Holder>() {

        private val fonts = ArrayList<FamilyCache>()
        private val filtered = ArrayList<FamilyCache>()
        private var searchQuery = ""

        private var selectedHolder: Holder? = null
            set(value) {
                if (field != value) {
                    field?.selected = false
                    field = value
                }
            }

        init {
            GoogleFontsListing.getInstance(context).getFonts {
                fonts.add(FamilyCache(FontCache.Family(FontCache.SystemFont("sans-serif"))))
                fonts.add(FamilyCache(FontCache.Family(FontCache.SystemFont("sans-serif-medium"))))
                fonts.add(FamilyCache(FontCache.Family(FontCache.SystemFont("sans-serif-condensed"))))
                it.mapTo(fonts) { font ->
                    val variantsMap = HashMap<String, FontCache.Font>()
                    font.variants.forEach { variant ->
                        variantsMap[variant] = FontCache.GoogleFont(context, font.family, variant)
                    }
                    FamilyCache(FontCache.Family(font.family, variantsMap))
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
            fonts.filterTo(filtered) { it.displayName.toLowerCase().contains(searchQuery) }
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
                FontCache.Font.LoadCallback, View.OnClickListener, AdapterView.OnItemSelectedListener {

            private val radioButton: RadioButton = itemView.findViewById(R.id.radio_button)
            private val title: TextView = itemView.findViewById(android.R.id.title)
            private val spinner: Spinner = itemView.findViewById(R.id.spinner)
            private val adapter = FamilySpinner(context).also { spinner.adapter = it }

            var selected = false
                set(value) {
                    field = value
                    radioButton.isChecked = value
                    if (value) {
                        selectedHolder = this
                    }
                }

            private var selectedVariant = 0

            private var cache: FamilyCache? = null
                set(value) {
                    field?.default?.callback = null
                    field = value
                    field?.let {
                        it.default.callback = this
                        it.default.loadFont()
                    }
                }

            init {
                itemView.setOnClickListener(this)
            }

            override fun onClick(v: View) {
                if (selected) return
                setFont((spinner.selectedItem as Cache).font)
                selected = true
            }

            fun bind(family: FamilyCache) {
                spinner.onItemSelectedListener = null
                title.text = family.displayName
                title.typeface = Typeface.DEFAULT
                this.cache = family
                spinner.isVisible = family.variants.size > 1
                adapter.clear()
                adapter.addAll(family.variants)
                setSpinnerItem(family)
                spinner.onItemSelectedListener = this
            }

            private fun setSpinnerItem(family: FamilyCache) {
                selected = false
                var selectedPosition: Int? = null
                var defaultPosition = -1
                family.variants.forEachIndexed { index, cache ->
                    if (cache.font == fontPref.font) {
                        selectedPosition = index
                        selected = true
                    }
                    if (cache.font == family.default.font) {
                        defaultPosition = index
                    }
                }
                spinner.setSelection(selectedPosition ?: defaultPosition)
            }

            override fun onFontLoaded(typeface: Typeface?) {
                title.typeface = typeface ?: Typeface.DEFAULT
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (selectedVariant == position) return
                selectedVariant = position
                if (selected) {
                    setFont(adapter.getItem(position)!!.font)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {

            }
        }

        inner class FamilyCache(family: FontCache.Family) {

            val displayName = family.displayName
            val default = Cache(family.default)
            val variants = family.variants.values.sortedBy { it.displayName }.map { Cache(it) }
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

            override fun toString() = font.displayName
        }
    }

    class FamilySpinner(context: Context) : ArrayAdapter<FontAdapter.Cache>(context, android.R.layout.simple_spinner_item) {

        init {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    companion object {

        const val EXTRA_KEY = "key"
    }
}
