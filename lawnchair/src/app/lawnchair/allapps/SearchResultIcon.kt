package app.lawnchair.allapps

import android.content.ComponentName
import android.content.Context
import android.os.UserHandle
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import app.lawnchair.launcher
import app.lawnchair.search.SearchTargetCompat
import com.android.launcher3.BubbleTextView
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.model.data.SearchActionItemInfo
import com.android.launcher3.touch.ItemClickHandler
import com.android.launcher3.touch.ItemLongClickListener
import com.android.launcher3.util.ComponentKey

class SearchResultIcon(context: Context, attrs: AttributeSet?) : BubbleTextView(context, attrs),
    SearchResultView, View.OnClickListener, View.OnLongClickListener {

    private val launcher = context.launcher
    private var boundId = ""
    private var flags = 0
    private var allowLongClick = false
    private var callback: ((info: ItemInfoWithIcon) -> Unit)? = null

    private val searchResultMargin = resources.getDimensionPixelSize(R.dimen.search_result_margin)

    override fun onFinishInflate() {
        super.onFinishInflate()
        setLongPressTimeoutFactor(1f)
        onFocusChangeListener = launcher.focusHandler
        setOnClickListener(this)
        setOnLongClickListener(this)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            launcher.deviceProfile.allAppsCellHeightPx
        )
    }

    override fun bind(target: SearchTargetCompat, shortcuts: List<SearchTargetCompat>) {
        if (boundId == target.id) return
        boundId = target.id
        flags = getFlags(target.extras)
        reset()
        setForceHideDot(true)

        val extras = target.extras
        if (target.searchAction != null) {
            allowLongClick = false
            bindFromAction(target)
        } else {
            allowLongClick = true
            val className = extras.getString("class") ?: ""
            val componentName = ComponentName(target.packageName, className)
            bindFromApp(componentName, target.userHandle)
        }
    }

    fun bind(target: SearchTargetCompat, callback: (info: ItemInfoWithIcon) -> Unit) {
        this.callback = callback
        bind(target, emptyList())
        if (!hasFlag(flags, SearchResultView.FLAG_HIDE_ICON)) {
            isVisible = true
            val lp = layoutParams as ViewGroup.MarginLayoutParams
            val size = iconSize + compoundDrawablePadding
            lp.width = size
            lp.height = size
            lp.marginStart = searchResultMargin
        } else {
            isInvisible = true
            val lp = layoutParams as ViewGroup.MarginLayoutParams
            lp.width = 0
            lp.marginStart = 0
        }
    }

    private fun bindFromAction(target: SearchTargetCompat) {
        val action = target.searchAction ?: return
        val info = SearchActionItemInfo(
            action.icon,
            target.packageName,
            target.userHandle,
            action.title,
            true
        )
        if (action.intent != null) {
            info.intent = action.intent
        }
        if (action.pendingIntent != null) {
            info.pendingIntent = action.pendingIntent
        }
        applyFromSearchActionItemInfo(info)
        notifyApplied(info)
    }

    private fun bindFromApp(componentName: ComponentName, user: UserHandle) {
        val appInfo = launcher.appsView.appsStore.getApp(ComponentKey(componentName, user))
        if (appInfo == null) {
            isVisible = false
            return
        }
        applyFromApplicationInfo(appInfo)
        notifyApplied(appInfo)
    }

    private fun notifyApplied(info: ItemInfoWithIcon) {
        callback?.invoke(info)
        callback = null
    }

    override fun onClick(v: View) {
        ItemClickHandler.INSTANCE.onClick(v)
    }

    override fun onLongClick(v: View): Boolean {
        if (!allowLongClick) {
            return false
        }
        return ItemLongClickListener.INSTANCE_ALL_APPS.onLongClick(v)
    }
}
