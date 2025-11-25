package app.lawnchair.smartspace

import android.content.Context
import android.graphics.Color
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import app.lawnchair.preferences2.PreferenceManager2.Companion.getInstance
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.theme.color.ColorMode
import app.lawnchair.util.isWallpaperDark
import com.android.launcher3.R
import com.android.launcher3.util.Themes
import com.patrykmichalik.opto.core.firstBlocking

class CardPagerAdapter(context: Context) : PagerAdapter() {
    private val preferenceManager2 = getInstance(context)

    val colorMode: ColorMode = preferenceManager2.workspaceTextColor.firstBlocking()
    private val currentTextColor = Themes.getAttrColor(context, R.attr.workspaceTextColor)
    private val targets = mutableListOf<SmartspaceTarget>()
    private var smartspaceTargets = targets
    private val holders = SparseArray<ViewHolder>()

    fun setTargets(newTargets: List<SmartspaceTarget>) {
        targets.clear()
        targets.addAll(newTargets)
        notifyDataSetChanged()
    }

    override fun instantiateItem(container: ViewGroup, position: Int): ViewHolder {
        val target = smartspaceTargets[position]
        val card = createBaseCard(container, getFeatureType(target))
        val viewHolder = ViewHolder(position, card, target)
        onBindViewHolder(viewHolder)
        container.addView(card)
        holders.put(position, viewHolder)
        return viewHolder
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        val viewHolder = obj as ViewHolder
        container.removeView(viewHolder.card)
        if (holders[position] == viewHolder) {
            holders.remove(position)
        }
    }

    fun getCardAtPosition(position: Int) = holders[position]?.card

    override fun getItemPosition(obj: Any): Int {
        val viewHolder = obj as ViewHolder
        val target = getTargetAtPosition(viewHolder.position)
        if (viewHolder.target === target) {
            return POSITION_UNCHANGED
        }
        if (target == null ||
            getFeatureType(target) !== getFeatureType(viewHolder.target) ||
            target.id != viewHolder.target.id
        ) {
            return POSITION_NONE
        }
        viewHolder.target = target
        onBindViewHolder(viewHolder)
        return POSITION_UNCHANGED
    }

    private fun getTargetAtPosition(position: Int): SmartspaceTarget? {
        if (position !in 0 until smartspaceTargets.size) {
            return null
        }
        return smartspaceTargets[position]
    }

    private fun onBindViewHolder(viewHolder: ViewHolder) {
        val target = smartspaceTargets[viewHolder.position]
        val card = viewHolder.card
        card.setSmartspaceTarget(target, smartspaceTargets.size > 1)
        val isDark = isWallpaperDark(card.context)
        val dynamicColors = if (isDark) Color.WHITE else Color.BLACK
        card.setPrimaryTextColor(if (colorMode == ColorMode.AUTO) dynamicColors else currentTextColor)
    }

    override fun getCount() = smartspaceTargets.size

    override fun isViewFromObject(view: View, obj: Any): Boolean {
        return view === (obj as ViewHolder).card
    }

    private fun createBaseCard(
        container: ViewGroup,
        featureType: SmartspaceTarget.FeatureType,
    ): BcSmartspaceCard {
        val layout = when (featureType) {
            SmartspaceTarget.FeatureType.FEATURE_WEATHER -> R.layout.smartspace_card_date
            else -> R.layout.smartspace_card
        }
        return LayoutInflater.from(container.context)
            .inflate(layout, container, false) as BcSmartspaceCard
    }

    private fun getFeatureType(target: SmartspaceTarget) = target.featureType

    class ViewHolder internal constructor(
        val position: Int,
        val card: BcSmartspaceCard,
        var target: SmartspaceTarget,
    )
}
