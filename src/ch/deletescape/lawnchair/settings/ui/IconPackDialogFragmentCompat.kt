package ch.deletescape.lawnchair.settings.ui

import android.content.Context
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.preference.PreferenceDialogFragmentCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import ch.deletescape.lawnchair.getIcon
import ch.deletescape.lawnchair.iconpack.IconPackManager
import ch.deletescape.lawnchair.isVisible
import com.android.launcher3.R
import com.android.launcher3.Utilities

class IconPackDialogFragmentCompat : PreferenceDialogFragmentCompat(), AdapterView.OnItemClickListener {

    private val prefs by lazy { Utilities.getLawnchairPrefs(context) }
    private var pack = ""

    private lateinit var listView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pack = prefs.iconPack
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        listView = view.findViewById(R.id.pack_list)
        listView.adapter = IconAdapter(context!!, prefs.iconPack, prefs.showDebugInfo)
        listView.onItemClickListener = this
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        parent?.findViewWithTag<View>(pack)?.findViewById<RadioButton>(R.id.radio)?.isChecked = false
        pack = (listView.adapter.getItem(position) as IconPackManager.IconPackInfo).packageName
        view?.findViewById<RadioButton>(R.id.radio)?.isChecked = true
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) prefs.iconPack = pack
    }

    companion object {

        fun newInstance(key: String?) = IconPackDialogFragmentCompat().apply {
            arguments = Bundle(1).apply {
                putString(ARG_KEY, key)
            }
        }
    }

    private class IconAdapter internal constructor(context: Context, internal var current: String, internal var debug: Boolean) : BaseAdapter() {
        internal var packs = IconPackManager.getInstance(context).getPackProviderInfos().values.toMutableList()
        internal var layoutInflater: LayoutInflater = LayoutInflater.from(context)

        init {
            packs = packs.sortedBy { it.label.toString() }.toMutableList()
            val label = context.getString(R.string.iconpack_none)
            packs.add(0, IconPackManager.IconPackInfo("", context.getIcon(), label))
        }

        override fun getItem(position: Int): Any = packs[position]

        override fun getItemId(position: Int): Long = 0

        override fun getCount(): Int = packs.size

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val info = packs[position]
            return (convertView ?: layoutInflater.inflate(R.layout.icon_pack_dialog_item, parent, false)).apply {
                tag = info.packageName
                findViewById<TextView>(android.R.id.title).text = info.label
                findViewById<ImageView>(android.R.id.icon).setImageDrawable(info.icon)
                findViewById<RadioButton>(R.id.radio).isChecked = info.packageName == current
                findViewById<TextView>(android.R.id.text1).apply {
                    text = info.packageName
                    isVisible = debug
                }
            }
        }
    }
}