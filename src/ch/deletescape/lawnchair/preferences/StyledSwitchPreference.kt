package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.content.res.ColorStateList
import android.support.v14.preference.SwitchPreference
import android.support.v4.graphics.ColorUtils
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.preference.AndroidResources
import android.support.v7.preference.PreferenceViewHolder
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.Switch
import ch.deletescape.lawnchair.colors.ColorEngine
import com.android.launcher3.util.Themes


open class StyledSwitchPreference(context: Context, attrs: AttributeSet?) : SwitchPreference(context, attrs), ColorEngine.OnAccentChangeListener {

    private val normalLight = android.support.v7.preference.R.color.switch_thumb_normal_material_light
    private val disabledLight = android.support.v7.appcompat.R.color.switch_thumb_disabled_material_light
    private var checkableView: View? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        checkableView = holder.findViewById(AndroidResources.ANDROID_R_SWITCH_WIDGET)
        ColorEngine.getInstance(context).addAccentChangeListener(this)
    }

    override fun onAccentChange(color: Int, foregroundColor: Int) {
        if (checkableView is Switch) {
            val colorForeground = Themes.getAttrColor(context, android.R.attr.colorForeground)
            val alphaDisabled = Themes.getAlpha(context, android.R.attr.disabledAlpha)
            val switchThumbNormal = context.resources.getColor(normalLight)
            val switchThumbDisabled = context.resources.getColor(disabledLight)
            val thstateList = ColorStateList(arrayOf(
                    intArrayOf(-android.R.attr.state_enabled),
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()),
                    intArrayOf(
                            switchThumbDisabled,
                            color,
                            switchThumbNormal))
            val trstateList = ColorStateList(arrayOf(
                    intArrayOf(-android.R.attr.state_enabled),
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()),
                    intArrayOf(
                            ColorUtils.setAlphaComponent(colorForeground, alphaDisabled),
                            color,
                            colorForeground))
            DrawableCompat.setTintList((checkableView as Switch).thumbDrawable, thstateList)
            DrawableCompat.setTintList((checkableView as Switch).trackDrawable, trstateList)
        }
    }

    override fun onDetached() {
        super.onAttached()
        ColorEngine.getInstance(context).removeAccentChangeListener(this)
    }
}
