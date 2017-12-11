package ch.deletescape.lawnchair.preferences

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.support.v7.preference.Preference
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import ch.deletescape.lawnchair.R
import ch.deletescape.lawnchair.Utilities
import java.util.*

class IconPackPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : Preference(context, attrs, defStyleAttr) {

    private val pm: PackageManager

    init {
        layoutResource = R.layout.preference_iconpack
        pm = context.packageManager
    }

    override fun onAttached() {
        super.onAttached()
        init()
    }

    private fun init() {
        val currentPack = getPersistedString("")
        if (currentPack.isEmpty()) {
            setNone()
        } else {
            try {
                val info = pm.getApplicationInfo(currentPack, 0)
                icon = info.loadIcon(pm)
                summary = info.loadLabel(pm)
            } catch (e: PackageManager.NameNotFoundException) {
                setNone()
                persistString("")
            }

        }
    }

    private fun setNone() {
        icon = Utilities.getMyIcon(context)
        summary = "None"
    }

    override fun onClick() {
        super.onClick()

        // TODO: Add some 'Arr!' flavor to it
        if (Utilities.isBlacklistedAppInstalled(context)) {
            Toast.makeText(context, R.string.unauthorized_device, Toast.LENGTH_SHORT).show()
            return
        }

        showDialog()
    }

    private fun showDialog() {
        val packages = loadAvailableIconPacks()
        val adapter = IconAdapter(context, packages, getPersistedString(""))
        val builder = AlertDialog.Builder(context)
        builder.setAdapter(adapter) { _, position ->
            val item = adapter.getItem(position)
            persistString(item)
            if (!item.isEmpty()) {
                val packInfo = packages[item]
                icon = packInfo!!.icon
                summary = packInfo.label
            } else {
                setNone()
            }

            val alternativeIcons = Utilities.getAlternativeIconList(context);
            if (alternativeIcons.size > 0) {
                Utilities.showResetAlternativeIcons(context, alternativeIcons)
            }
        }
        builder.show()
    }

    private fun loadAvailableIconPacks(): Map<String, IconPackInfo> {
        val iconPacks = HashMap<String, IconPackInfo>()
        val list = pm.queryIntentActivities(Intent("com.novalauncher.THEME"), 0)
        list.addAll(pm.queryIntentActivities(Intent("org.adw.launcher.icons.ACTION_PICK_ICON"), 0))
        list.addAll(pm.queryIntentActivities(Intent("com.dlto.atom.launcher.THEME"), 0))
        list.addAll(pm.queryIntentActivities(Intent("android.intent.action.MAIN").addCategory("com.anddoes.launcher.THEME"), 0))
        for (info in list) {
            iconPacks.put(info.activityInfo.packageName, IconPackInfo(info, pm))
        }
        return iconPacks
    }

    private class IconPackInfo {
        internal var packageName: String
        internal var label: CharSequence
        internal var icon: Drawable

        internal constructor(r: ResolveInfo, packageManager: PackageManager) {
            packageName = r.activityInfo.packageName
            icon = r.loadIcon(packageManager)
            label = r.loadLabel(packageManager)
        }

        constructor(label: String, icon: Drawable, packageName: String) {
            this.label = label
            this.icon = icon
            this.packageName = packageName
        }
    }

    private class IconAdapter internal constructor(context: Context, supportedPackages: Map<String, IconPackInfo>, internal var mCurrentIconPack: String) : BaseAdapter() {
        internal var supportedPackages: ArrayList<IconPackInfo> = ArrayList(supportedPackages.values)
        internal var layoutInflater: LayoutInflater = LayoutInflater.from(context)

        init {
            Collections.sort(this.supportedPackages) { lhs, rhs -> lhs.label.toString().compareTo(rhs.label.toString(), ignoreCase = true) }

            val defaultLabel = "None"
            val icon = Utilities.getMyIcon(context)
            this.supportedPackages.add(0, IconPackInfo(defaultLabel, icon, ""))
        }

        override fun getCount(): Int {
            return supportedPackages.size
        }

        override fun getItem(position: Int): String {
            return supportedPackages[position].packageName
        }

        override fun getItemId(position: Int): Long {
            return 0
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.iconpack_dialog, parent, false)
            val info = supportedPackages[position]
            val txtView = view.findViewById<TextView>(R.id.title)
            txtView.text = info.label
            val imgView = view.findViewById<ImageView>(R.id.icon)
            imgView.setImageDrawable(info.icon)
            val radioButton = view.findViewById<RadioButton>(R.id.radio)
            radioButton.isChecked = info.packageName == mCurrentIconPack
            return view
        }
    }

}

