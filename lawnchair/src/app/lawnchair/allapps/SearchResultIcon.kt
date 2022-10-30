package app.lawnchair.allapps

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import app.lawnchair.launcher
import app.lawnchair.search.SearchTargetCompat
import app.lawnchair.util.runOnMainThread
import com.android.launcher3.BubbleTextView
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.IconProvider
import com.android.launcher3.icons.LauncherIcons
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.model.data.PackageItemInfo
import com.android.launcher3.model.data.SearchActionItemInfo
import com.android.launcher3.model.data.SearchActionItemInfo.*
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.touch.ItemClickHandler
import com.android.launcher3.touch.ItemLongClickListener
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors.MODEL_EXECUTOR

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

    override val isQuickLaunch get() = hasFlag(flags, SearchResultView.FLAG_QUICK_LAUNCH)
    override val titleText: CharSequence? get() = text

    override fun launch(): Boolean {
        ItemClickHandler.INSTANCE.onClick(this)
        return true
    }

    override fun bind(target: SearchTargetCompat, shortcuts: List<SearchTargetCompat>) {
        if (boundId == target.id) return
        boundId = target.id
        flags = getFlags(target.extras)
        reset()
        setForceHideDot(true)

        val extras = target.extras
        val iconComponentKey = extras.getString(SearchResultView.EXTRA_ICON_COMPONENT_KEY)
            ?.let { ComponentKey.fromString(it) }
        when {
            target.searchAction != null -> {
                allowLongClick = false
                bindFromAction(target, iconComponentKey == null)
            }
            target.shortcutInfo != null -> {
                allowLongClick = true
                bindFromShortcutInfo(target.shortcutInfo!!)
            }
            else -> {
                allowLongClick = true
                val className = extras.getString("class").orEmpty()
                val componentName = ComponentName(target.packageName, className)
                bindFromApp(componentName, target.userHandle)
            }
        }
        if (iconComponentKey != null) {
            bindIconComponentKey(iconComponentKey)
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

    private fun bindFromAction(target: SearchTargetCompat, bindIcon: Boolean) {
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
        val extras = action.extras
        if (extras != null) {
            if (extras.getBoolean("should_start_for_result") || target.resultType == 16 /* settings */) {
                info.setFlags(FLAG_SHOULD_START_FOR_RESULT)
            } else if (extras.getBoolean("should_start")) {
                info.setFlags(FLAG_SHOULD_START)
            }
            if (extras.getBoolean("badge_with_package")) {
                info.setFlags(FLAG_BADGE_WITH_PACKAGE)
            }
            if (extras.getBoolean("badge_with_component_name")) {
                info.setFlags(FLAG_BADGE_WITH_COMPONENT_NAME)
            }
            if (extras.getBoolean("primary_icon_from_title")) {
                info.setFlags(FLAG_PRIMARY_ICON_FROM_TITLE)
            }
        }
        notifyApplied(info)
        if (bindIcon) {
            MODEL_EXECUTOR.handler.postAtFrontOfQueue {
                populateSearchActionItemInfo(target, info)
                runOnMainThread { applyFromItemInfoWithIcon(info) }
            }
        }
    }

    private fun bindIconComponentKey(iconComponentKey: ComponentKey) {
        val appInfo = launcher.appsView.appsStore.getApp(iconComponentKey)
        if (appInfo == null) {
            isVisible = false
            return
        }
        icon = appInfo.newIcon(context, false)
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

    private fun bindFromShortcutInfo(shortcutInfo: ShortcutInfo) {
        val si = WorkspaceItemInfo(shortcutInfo, launcher)
        si.container = LauncherSettings.Favorites.CONTAINER_ALL_APPS
        applyFromWorkspaceItem(si)
        notifyApplied(si)
        val cache = LauncherAppState.getInstance(launcher).iconCache
        MODEL_EXECUTOR.handler.postAtFrontOfQueue {
            cache.getUnbadgedShortcutIcon(si, shortcutInfo)
            runOnMainThread { applyFromWorkspaceItem(si) }
        }
    }

    private fun notifyApplied(info: ItemInfoWithIcon) {
        callback?.invoke(info)
        callback = null
    }

    override fun onClick(v: View) {
        ItemClickHandler.INSTANCE.onClick(v)
    }

    private fun populateSearchActionItemInfo(
        target: SearchTargetCompat,
        info: SearchActionItemInfo
    ) {
        val action = target.searchAction!!
        LauncherIcons.obtain(context).use { li ->
            val icon = action.icon

            val packageIcon = getPackageIcon(target.packageName, target.userHandle)

            info.bitmap = when {
                info.hasFlags(FLAG_PRIMARY_ICON_FROM_TITLE) ->
                    li.createIconBitmap("${info.title[0]}", packageIcon.color)
                icon == null -> packageIcon
                else -> li.createBadgedIconBitmap(icon.loadDrawable(context), info.user, false)
            }
            if (info.hasFlags(FLAG_BADGE_WITH_COMPONENT_NAME) && target.extras.containsKey("class")) {
                try {
                    val iconProvider = IconProvider(context)
                    val componentName = ComponentName(target.packageName, target.extras.getString("class")!!)
                    val activityInfo = context.packageManager.getActivityInfo(componentName, 0)
                    val activityIcon = iconProvider.getIcon(activityInfo)
                    val bitmap = li.createIconBitmap(activityIcon, 1f, iconSize)
                    val bitmapInfo = BitmapInfo.of(bitmap, packageIcon.color)
                    info.bitmap = li.badgeBitmap(info.bitmap.icon, bitmapInfo)
                } catch (_: PackageManager.NameNotFoundException) {
                }
            } else if (info.hasFlags(FLAG_BADGE_WITH_PACKAGE) && info.bitmap != packageIcon) {
                info.bitmap = li.badgeBitmap(info.bitmap.icon, packageIcon)
            }
        }
    }

    private fun getPackageIcon(packageName: String, user: UserHandle): BitmapInfo {
        val las = LauncherAppState.getInstance(context)
        val info = PackageItemInfo(packageName, user)
        info.user = user
        las.iconCache.getTitleAndIconForApp(info, false)
        return info.bitmap
    }

    override fun onLongClick(v: View): Boolean {
        if (!allowLongClick) {
            return false
        }
        return ItemLongClickListener.INSTANCE_ALL_APPS.onLongClick(v)
    }

    fun hasFlag(flag: Int) = hasFlag(flags, flag)
}
