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
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.support.v7.widget.ActionMenuView
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import ch.deletescape.lawnchair.*
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.font.CustomFontManager
import ch.deletescape.lawnchair.font.FontCache
import ch.deletescape.lawnchair.font.googlefonts.GoogleFontsListing
import ch.deletescape.lawnchair.settings.ui.SettingsBaseActivity
import com.android.launcher3.R
import kotlinx.android.synthetic.main.activity_settings_search.*
import java.io.File

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

        search_view.queryHint = getString(R.string.pref_fonts_find_fonts)
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

    private fun addFont() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        startActivityForResult(intent, REQUEST_CODE_ADD_FONTS)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_ADD_FONTS) {
            data?.data?.also {
                try {
                    addCustomFont(it)?.let { adapter.addCustomFont(it) }
                } catch (e: AddFontException) {
                    Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun addCustomFont(uri: Uri): FontCache.TTFFont? {
        val name = contentResolver.getDisplayName(uri) ?: throw AddFontException("Couldn't get file name")
        val file = FontCache.TTFFont.getFile(this, name)
        val tmpFile = File(cacheDir.apply { mkdirs() }, file.name)

        if (file.exists()) return null

        contentResolver.openInputStream(uri)?.use { input ->
            tmpFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw AddFontException("Couldn't open file")

        if (Typeface.createFromFile(tmpFile) === Typeface.DEFAULT) {
            tmpFile.delete()
            throw AddFontException("Not a valid font file")
        }

        tmpFile.setLastModified(System.currentTimeMillis())
        tmpFile.renameTo(file)

        return FontCache.TTFFont(this, file)
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

    private fun loadCustomFonts(): Collection<FontCache.TTFFont> {
        val fontsDir = FontCache.TTFFont.getFontsDir(this)
        return fontsDir.listFiles().map { FontCache.TTFFont(this, it) }
    }

    inner class FontAdapter(private val context: Context) : RecyclerView.Adapter<FontAdapter.Holder>() {

        private val items = ArrayList<Item>()
        private val filtered = ArrayList<Item>()
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
                items.add(AddButton())
                loadCustomFonts().mapTo(items) { font ->
                    FamilyCache(FontCache.Family(font))
                }
                items.add(Divider())
                items.add(FamilyCache(FontCache.Family(FontCache.SystemFont("sans-serif"))))
                items.add(FamilyCache(FontCache.Family(FontCache.SystemFont("sans-serif-medium"))))
                items.add(FamilyCache(FontCache.Family(FontCache.SystemFont("sans-serif-condensed"))))
                it.mapTo(items) { font ->
                    val variantsMap = HashMap<String, FontCache.Font>()
                    val variants = font.variants.toTypedArray()
                    font.variants.forEach { variant ->
                        variantsMap[variant] = FontCache.GoogleFont(context, font.family, variant, variants)
                    }
                    FamilyCache(FontCache.Family(font.family, variantsMap))
                }
                filterItems()
            }
        }

        fun addCustomFont(font: FontCache.TTFFont) {
            setFont(font)
            items.add(1, FamilyCache(FontCache.Family(font)))
            if (searchQuery.isEmpty()) {
                filtered.add(1, items[1])
                notifyItemInserted(1)
            } else {
                filterItems()
            }
        }

        fun search(query: String) {
            searchQuery = query.toLowerCase()
            filterItems()
        }

        private fun filterItems() {
            filtered.clear()
            if (!searchQuery.isEmpty()) {
                items.filterTo(filtered) {
                    it is FamilyCache && it.displayName.toLowerCase().contains(searchQuery)
                }
            } else {
                filtered.addAll(items)
            }
            notifyDataSetChanged()
        }

        override fun getItemCount() = filtered.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return when (viewType) {
                TYPE_FAMILY -> FamilyHolder(parent)
                TYPE_DIVIDER -> DividerHolder(parent)
                TYPE_ADD_BUTTON -> AddButtonHolder(parent)
                else -> throw IllegalArgumentException("Unknown viewType $viewType")
            }
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(filtered[position])
        }

        override fun getItemViewType(position: Int) = filtered[position].viewType

        abstract inner class Holder(parent: ViewGroup, layout: Int) :
                RecyclerView.ViewHolder(LayoutInflater.from(context).inflate(layout, parent, false)) {

            open var selected = false

            open fun bind(item: Item) {

            }
        }

        inner class DividerHolder(parent: ViewGroup) : Holder(parent, R.layout.font_list_divider)

        inner class AddButtonHolder(parent: ViewGroup) :
                Holder(parent, R.layout.font_list_add_button), View.OnClickListener {

            override fun bind(item: Item) {
                super.bind(item)
                itemView.setOnClickListener(this)
                itemView.findViewById<ImageView>(android.R.id.icon).tintDrawable(getColorEngineAccent())
            }

            override fun onClick(v: View) {
                addFont()
            }
        }

        open inner class FamilyHolder(parent: ViewGroup) : Holder(parent, R.layout.font_item),
                View.OnClickListener, AdapterView.OnItemSelectedListener {

            private val radioButton: RadioButton = itemView.findViewById(R.id.radio_button)
            private val title: TextView = itemView.findViewById(android.R.id.title)
            private val spinner: Spinner = itemView.findViewById(R.id.spinner)
            private val deleteButton: ImageView = itemView.findViewById(R.id.delete)
            private val adapter = FamilySpinner(context).also { spinner.adapter = it }

            private var deleted = false
            override var selected = false
                set(value) {
                    field = value
                    radioButton.isChecked = value
                    updateSpinnerVisibility()
                    if (value) {
                        selectedHolder = this
                    }
                }

            private var selectedVariant = 0

            override fun onClick(v: View) {
                when (v.id) {
                    R.id.font_item -> {
                        if (selected) return
                        setFont((spinner.selectedItem as Cache).font)
                        selected = true
                    }
                    R.id.delete -> {
                        deleteItem()
                    }
                }
            }

            private fun deleteItem() {
                if (deleted) return
                val item = filtered[adapterPosition]
                val font = (item as? FamilyCache)?.default?.font as? FontCache.TTFFont ?: return
                deleted = true
                if (selected) {
                    fontPref.reset()
                }
                items.remove(item)
                filtered.removeAt(adapterPosition)
                notifyItemRemoved(adapterPosition)
                font.delete()
            }

            override fun bind(item: Item) {
                super.bind(item)
                val family = item as? FamilyCache ?: throw IllegalArgumentException("item is not a font family")
                deleted = false
                itemView.setOnClickListener(this)
                deleteButton.setOnClickListener(this)
                deleteButton.tintDrawable(getColorEngineAccent())
                deleteButton.isVisible = family.default.font is FontCache.TTFFont
                spinner.onItemSelectedListener = null
                title.text = family.displayName
                title.typeface = Typeface.DEFAULT
                FontSwitcher.get(title).toLoad = family.default
                adapter.clear()
                adapter.addAll(family.variants)
                setSpinnerItem(family)
                updateSpinnerVisibility()
                spinner.onItemSelectedListener = this
            }

            private fun updateSpinnerVisibility() {
                spinner.isVisible = selected && adapter.count > 1
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
                selectedVariant = selectedPosition ?: defaultPosition
                spinner.setSelection(selectedVariant)
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

        abstract inner class Item {

            abstract val viewType: Int
        }

        inner class Divider : Item() {

            override val viewType = TYPE_DIVIDER
        }

        inner class AddButton : Item() {

            override val viewType = TYPE_ADD_BUTTON
        }

        inner class FamilyCache(family: FontCache.Family) : Item() {

            override val viewType = TYPE_FAMILY
            val displayName = family.displayName
            val default = Cache(family.default)
            val variants = family.variants.values.sortedBy { it.familySorter }.map { Cache(it) }
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

    class FontSwitcher private constructor(private val textView: TextView) : FontCache.Font.LoadCallback {

        var toLoad: FontAdapter.Cache? = null
            set(value) {
                textView.typeface = Typeface.DEFAULT
                field?.callback = null
                field = value
                field?.let {
                    it.callback = this
                    it.loadFont()
                }
            }

        init {
            textView.setTag(R.id.font_switcher, this)
        }

        override fun onFontLoaded(typeface: Typeface?) {
            textView.typeface = typeface ?: Typeface.DEFAULT
        }

        companion object {

            fun get(textView: TextView): FontSwitcher {
                return textView.getTag(R.id.font_switcher) as? FontSwitcher ?: FontSwitcher(textView)
            }
        }
    }

    class FamilySpinner(context: Context) : ArrayAdapter<FontAdapter.Cache>(context, android.R.layout.simple_spinner_item) {

        init {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        private fun setFont(view: View, position: Int): View {
            return view.apply {
                FontSwitcher.get(findViewById(android.R.id.text1)).toLoad = getItem(position)
            }
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return setFont(super.getView(position, convertView, parent), position)
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            return setFont(super.getDropDownView(position, convertView, parent), position)
        }
    }

    companion object {

        const val EXTRA_KEY = "key"

        private const val REQUEST_CODE_ADD_FONTS = 0

        private const val TYPE_FAMILY = 0
        private const val TYPE_DIVIDER = 1
        private const val TYPE_ADD_BUTTON = 2
    }

    private class AddFontException(message: String? = null) : RuntimeException(message)
}
