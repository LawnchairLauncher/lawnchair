/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
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

package ch.deletescape.lawnchair.settings.ui.preview

import android.content.Context
import android.os.Handler
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.FrameLayout
import ch.deletescape.lawnchair.runOnMainThread
import ch.deletescape.lawnchair.runOnThread
import ch.deletescape.lawnchair.theme.ThemeOverride
import com.android.launcher3.*
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.BaseDragLayer
import kotlinx.android.synthetic.lawnchair.desktop_icons_preview.view.*

class DesktopIconsPreview(context: Context, attrs: AttributeSet?) :
        FrameLayout(PreviewContext(context), attrs), WorkspaceLayoutManager,
        InvariantDeviceProfile.OnIDPChangeListener {

    private val previewContext = this.context as PreviewContext
    private val previewApps = IconPreviewUtils.getPreviewAppInfos(context)
    private var iconsLoaded = false

    private val idp = previewContext.idp

    private val homeElementInflater = LayoutInflater.from(ContextThemeWrapper(previewContext, R.style.HomeScreenElementTheme))

    init {
        runOnThread(Handler(LauncherModel.getWorkerLooper())) {
            val iconCache = LauncherAppState.getInstance(context).iconCache
            previewApps.forEach { iconCache.getTitleAndIcon(it, false) }
            iconsLoaded = true
            runOnMainThread(::populatePreview)
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        populatePreview()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        idp.addOnChangeListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        idp.removeOnChangeListener(this)
    }

    override fun onIdpChanged(changeFlags: Int, profile: InvariantDeviceProfile) {
        populatePreview()
    }

    private fun populatePreview() {
        val dp = idp.getDeviceProfile(previewContext)
        val leftPadding = dp.workspacePadding.left + dp.cellLayoutPaddingLeftRightPx
        val rightPadding = dp.workspacePadding.right + dp.cellLayoutPaddingLeftRightPx
        val verticalPadding = (leftPadding + rightPadding) / 2 + dp.iconDrawablePaddingPx
        layoutParams.height = dp.cellHeightPx + verticalPadding * 2

        if (!iconsLoaded || !isAttachedToWindow) return

        workspace.removeAllViews()
        workspace.setGridSize(idp.numColumns, 1)
        workspace.setPadding(leftPadding,
                             verticalPadding,
                             rightPadding,
                             verticalPadding)

        previewApps.take(idp.numColumns).forEachIndexed { index, info ->
            info.container = LauncherSettings.Favorites.CONTAINER_DESKTOP
            info.screenId = 0
            info.cellX = index
            info.cellY = 0
            inflateAndAddIcon(info)
        }
    }

    private fun inflateAndAddIcon(info: AppInfo) {
        val icon = homeElementInflater.inflate(
                R.layout.app_icon, workspace, false) as BubbleTextView
        icon.applyFromApplicationInfo(info)
        addInScreenFromBind(icon, info)
    }

    override fun getScreenWithId(screenId: Int) = workspace!!

    override fun getHotseat() = null

    private class PreviewContext(base: Context) : ContextThemeWrapper(
            base, ThemeOverride.Launcher().getTheme(base)), ActivityContext {

        val idp = LauncherAppState.getIDP(this)!!
        val dp get() = idp.getDeviceProfile(this)!!

        override fun getDeviceProfile(): DeviceProfile {
            return dp
        }

        override fun getDragLayer(): BaseDragLayer<*> {
            throw UnsupportedOperationException()
        }
    }
}
