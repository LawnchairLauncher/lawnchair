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

package ch.deletescape.lawnchair.animations

import android.animation.*
import android.annotation.TargetApi
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Build
import android.support.annotation.Keep
import android.util.Pair
import android.view.View
import android.view.animation.PathInterpolator
import ch.deletescape.lawnchair.LawnchairLauncher
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.findInViews
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.util.InvertedMultiValueAlpha
import ch.deletescape.lawnchair.views.LawnchairBackgroundView
import com.android.launcher3.*
import com.android.launcher3.BaseActivity.INVISIBLE_BY_APP_TRANSITIONS
import com.android.launcher3.LauncherState.*
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.shortcuts.DeepShortcutView
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.MultiValueAlpha
import com.android.quickstep.TaskUtils
import com.android.quickstep.util.MultiValueUpdateListener
import com.android.quickstep.util.RemoteAnimationProvider
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.TaskView
import com.android.systemui.shared.recents.model.RecentsTaskLoadPlan
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.system.ActivityCompat
import com.android.systemui.shared.system.RemoteAnimationDefinitionCompat
import com.android.systemui.shared.system.RemoteAnimationTargetCompat
import com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_CLOSING
import com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_OPENING
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplier
import com.google.android.apps.nexuslauncher.allapps.PredictionsFloatingHeader

@Keep
@TargetApi(Build.VERSION_CODES.O)
class LawnchairAppTransitionManagerImpl(context: Context) : LauncherAppTransitionManagerImpl(context), LawnchairPreferences.OnPreferenceChangeListener {

    private val launcher = Launcher.getLauncher(context)
    private val prefsToListen = arrayOf("pref_useScaleAnim", "pref_useWindowToIcon")
    private var useWindowToIcon = false

    private val animationType by context.lawnchairPrefs.StringBasedPref("pref_animationType",
            AnimationType.DefaultAnimation(), { },
            AnimationType.Companion::fromString,
            AnimationType.Companion::toString) { registerRemoteAnimations() }

    init {
        Utilities.getLawnchairPrefs(launcher).addOnPreferenceChangeListener(this, *prefsToListen)
        registerRemoteAnimations()
    }

    override fun registerRemoteAnimations() {
        if (animationType.allowWallpaperOpenRemoteAnimation) {
            super.registerRemoteAnimations()
        } else if (hasControlRemoteAppTransitionPermission()) {
            ActivityCompat(launcher).registerRemoteAnimations(RemoteAnimationDefinitionCompat())
        }
    }

    override fun getActivityLaunchOptions(launcher: Launcher, v: View): ActivityOptions? {
        if (isLaunchingFromRecents(launcher, v)) {
            return super.getActivityLaunchOptions(launcher, v)
        }
        return animationType.getActivityLaunchOptions(launcher, v) ?: super.getActivityLaunchOptions(launcher, v)
    }

    private fun isLaunchingFromRecents(launcher: Launcher, v: View?): Boolean {
        return launcher.stateManager.state.overviewUi && v is TaskView
    }

    fun playLaunchAnimation(launcher: Launcher, v: View?, intent: Intent) {
        if (!isLaunchingFromRecents(launcher, v)) {
            animationType.playLaunchAnimation(launcher, v, intent, this)
        }
    }

    fun overrideResumeAnimation(launcher: Launcher) {
        animationType.overrideResumeAnimation(launcher)
    }

    override fun destroy() {
        super.destroy()
        Utilities.getLawnchairPrefs(launcher).removeOnPreferenceChangeListener(this, *prefsToListen)
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        useScaleAnim = prefs.useScaleAnim
        useWindowToIcon = prefs.useWindowToIcon
    }

    override fun getClosingWindowAnimators(targets: Array<out RemoteAnimationTargetCompat>): Animator {
        if (allowWindowIconTransition(targets)) {
            targets.forEach {
                if (it.mode == MODE_CLOSING) {
                    loadTask(it.taskId)?.let { task ->
                        val component = TaskUtils.getLaunchComponentKeyForTask(task.key)
                        findIconForComponent(component, useScaleAnim)?.let { icon ->
                            val v = if (icon is FolderIcon && icon.isCoverMode)
                                icon.folderName else icon

                            iconBounds = getViewBounds(v)
                            val anim = AnimatorSet()

                            val windowSourceBounds = getWindowSourceBounds(targets)
                            playIconAnimators(anim, v, windowSourceBounds, true)
                            anim.play(getOpeningWindowAnimators(v, targets, windowSourceBounds, true))

                            return anim
                        }
                    }
                }
            }
            iconBounds = null
        }
        if (useScaleAnim) {
            val anim = AnimatorSet()
            anim.play(getOpeningWindowAnimators(null, targets, null, true))
            return anim
        } else {
            return super.getClosingWindowAnimators(targets)
        }
    }

    override fun playIconAnimators(appOpenAnimator: AnimatorSet, v: View, windowTargetBounds: Rect, reversed: Boolean) {
        if (useScaleAnim) {
            iconBounds = getViewBounds(v)
        } else {
            super.playIconAnimators(appOpenAnimator, v, windowTargetBounds, reversed)
        }
    }

    private fun resetPivot() {
        if (iconBounds != null) {
            dragLayer.pivotX = iconBounds.exactCenterX()
            dragLayer.pivotY = iconBounds.exactCenterY()
        } else {
            dragLayer.pivotX = dragLayer.width / 2f
            dragLayer.pivotY = dragLayer.height / 2f
        }
    }

    override fun createLauncherResumeAnimation(anim: AnimatorSet) {
        if (!useScaleAnim) {
            super.createLauncherResumeAnimation(anim)
            return
        }

        if (launcher.isInState(LauncherState.ALL_APPS)) run {
            val contentAnimator = getLauncherContentAnimator(false /* isAppOpening */)
            anim.play(contentAnimator.first)
            anim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    contentAnimator.second.run()
                }
            })
        }

        resetPivot()

        val workspaceAnimator = AnimatorSet()

        dragLayer.scaleX = APP_CLOSE_HOME_ENTER_SCALE_FROM
        dragLayer.scaleY = APP_CLOSE_HOME_ENTER_SCALE_FROM

        val scaleAnim = ObjectAnimator.ofFloat(dragLayer, Utilities.VIEW_SCALE,
                APP_CLOSE_HOME_ENTER_SCALE_FROM, APP_CLOSE_HOME_ENTER_SCALE_TO)
        scaleAnim.duration = APP_CLOSE_HOME_ENTER_SCALE_DURATION
        scaleAnim.interpolator = APP_CLOSE_HOME_ENTER_SCALE_INTERPOLATOR
        workspaceAnimator.play(scaleAnim)

        dragLayerAlpha.value = APP_CLOSE_HOME_ENTER_ALPHA_FROM
        val alphaAnim = ObjectAnimator.ofFloat(dragLayerAlpha, MultiValueAlpha.VALUE,
                APP_CLOSE_HOME_ENTER_ALPHA_FROM, APP_CLOSE_HOME_ENTER_ALPHA_TO)
        alphaAnim.duration = APP_CLOSE_HOME_ENTER_ALPHA_DURATION
        alphaAnim.interpolator = APP_CLOSE_HOME_ENTER_ALPHA_INTERPOLATOR
        workspaceAnimator.play(alphaAnim)

        val background = LawnchairLauncher.getLauncher(launcher).background
        background.blurAlphas.getProperty(LawnchairBackgroundView.ALPHA_INDEX_TRANSITIONS).value = 1f
        val blurAlpha = ObjectAnimator.ofFloat<InvertedMultiValueAlpha.InvertedAlphaProperty>(background.blurAlphas.getProperty(
                LawnchairBackgroundView.ALPHA_INDEX_TRANSITIONS),
                InvertedMultiValueAlpha.VALUE, 1f, 0f)
        blurAlpha.duration = APP_CLOSE_WALLPAPER_ENTER_SCALE_DURATION
        blurAlpha.interpolator = APP_CLOSE_WALLPAPER_ENTER_SCALE_INTERPOLATOR
        workspaceAnimator.play(blurAlpha)

        dragLayer.scrim.hideSysUiScrim(true)

        // Pause page indicator animations as they lead to layer trashing.
        launcher.workspace.pageIndicator.pauseAnimations()
        dragLayer.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        workspaceAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                resetContentView()
            }
        })
        anim.play(workspaceAnimator)
    }

    public override fun getLauncherContentAnimator(isAppOpening: Boolean): Pair<AnimatorSet, Runnable> {
        if (!useScaleAnim) {
            return super.getLauncherContentAnimator(isAppOpening)
        }

        resetPivot()

        val launcherAnimator = AnimatorSet()

        resetPivot()

        val blurAlphaFrom = if (isAppOpening) 0f else 1f
        val blurAlphaTo = if (isAppOpening) 1f else 0f

        val alphaFrom = if (isAppOpening) APP_OPEN_HOME_EXIT_ALPHA_FROM else APP_CLOSE_HOME_ENTER_SCALE_FROM
        val alphaTo = if (isAppOpening) APP_OPEN_HOME_EXIT_ALPHA_TO else APP_CLOSE_HOME_ENTER_SCALE_TO

        val alpha = if (launcher.isInState(ALL_APPS)) {
            val appsView = launcher.appsView
            appsView.alpha = alphaFrom
            ObjectAnimator.ofFloat(appsView, View.ALPHA, alphaFrom, alphaTo)
        } else {
            dragLayerAlpha.value = alphaFrom
            ObjectAnimator.ofFloat(dragLayerAlpha, MultiValueAlpha.VALUE,
                    alphaFrom, alphaTo)
        }
        alpha.duration = if (isAppOpening) APP_OPEN_HOME_EXIT_ALPHA_DURATION else APP_CLOSE_HOME_ENTER_ALPHA_DURATION
        alpha.interpolator = if (isAppOpening)
            APP_OPEN_HOME_EXIT_ALPHA_INTERPOLATOR else APP_CLOSE_HOME_ENTER_ALPHA_INTERPOLATOR
        launcherAnimator.play(alpha)

        val scaleFrom = if (isAppOpening) APP_OPEN_HOME_EXIT_SCALE_FROM else APP_CLOSE_HOME_ENTER_SCALE_FROM
        val scaleTo = if (isAppOpening) APP_OPEN_HOME_EXIT_SCALE_TO else APP_CLOSE_HOME_ENTER_SCALE_TO

        dragLayer.scaleX = scaleFrom
        dragLayer.scaleY = scaleTo

        val scaleAnim = ObjectAnimator.ofFloat(dragLayer, Utilities.VIEW_SCALE, scaleTo)
        scaleAnim.duration = if (isAppOpening) APP_OPEN_HOME_EXIT_SCALE_DURATION else APP_CLOSE_HOME_ENTER_SCALE_DURATION
        scaleAnim.interpolator = if (isAppOpening)
            APP_OPEN_HOME_EXIT_SCALE_INTERPOLATOR else APP_CLOSE_HOME_ENTER_SCALE_INTERPOLATOR
        launcherAnimator.play(scaleAnim)

        val background = LawnchairLauncher.getLauncher(launcher).background
        background.blurAlphas.getProperty(LawnchairBackgroundView.ALPHA_INDEX_TRANSITIONS).value = blurAlphaFrom
        val blurAlpha = ObjectAnimator.ofFloat<InvertedMultiValueAlpha.InvertedAlphaProperty>(background.blurAlphas.getProperty(
                LawnchairBackgroundView.ALPHA_INDEX_TRANSITIONS),
                InvertedMultiValueAlpha.VALUE, blurAlphaFrom, blurAlphaTo)
        blurAlpha.duration = APP_OPEN_WALLPAPER_EXIT_DURATION
        blurAlpha.interpolator = APP_OPEN_WALLPAPER_EXIT_INTERPOLATOR
        launcherAnimator.play(blurAlpha)

        dragLayer.scrim.hideSysUiScrim(true)
        // Pause page indicator animations as they lead to layer trashing.
        launcher.workspace.pageIndicator.pauseAnimations()
        dragLayer.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val endListener = if (launcher.isInState(ALL_APPS)) {
            Runnable {
                launcher.appsView.alpha = alphaFrom
                resetContentView()
            }
        } else {
            Runnable { resetContentView() }
        }

        return Pair(launcherAnimator, endListener)
    }

    override fun getOpeningWindowAnimators(v: View?, targets: Array<out RemoteAnimationTargetCompat>, windowTargetBounds: Rect?, isExit: Boolean): ValueAnimator {
        if (!useScaleAnim) {
            return super.getOpeningWindowAnimators(v, targets, windowTargetBounds, isExit)
        }

        val targetMode = if (isExit) MODE_CLOSING else MODE_OPENING

        val matrix = Matrix()

        val surfaceApplier = SyncRtSurfaceTransactionApplier(
                dragLayer)

        val appAnimator = ValueAnimator.ofFloat(0f, 1f)
        appAnimator.duration = APP_OPEN_APP_ENTER_SCALE_DURATION.toLong()
        appAnimator.addUpdateListener(object : MultiValueUpdateListener() {

            val scale = if (isExit) {
                FloatProp(
                        APP_CLOSE_APP_EXIT_SCALE_FROM,
                        APP_CLOSE_APP_EXIT_SCALE_TO,
                        0f, APP_CLOSE_APP_EXIT_SCALE_DURATION,
                        APP_CLOSE_APP_EXIT_SCALE_INTERPOLATOR)
            } else {
                FloatProp(
                        APP_OPEN_APP_ENTER_SCALE_FROM,
                        APP_OPEN_APP_ENTER_SCALE_TO,
                        0f, APP_OPEN_APP_ENTER_SCALE_DURATION,
                        APP_OPEN_APP_ENTER_SCALE_INTERPOLATOR)
            }

            val alpha = if (isExit) {
                FloatProp(
                        APP_CLOSE_APP_EXIT_ALPHA_FROM,
                        APP_CLOSE_APP_EXIT_ALPHA_TO,
                        APP_CLOSE_APP_EXIT_ALPHA_START_OFFSET,
                        APP_CLOSE_APP_EXIT_ALPHA_DURATION,
                        APP_CLOSE_APP_EXIT_ALPHA_INTERPOLATOR)
            } else {
                FloatProp(
                        APP_OPEN_APP_ENTER_ALPHA_FROM,
                        APP_OPEN_APP_ENTER_ALPHA_TO,
                        APP_OPEN_APP_ENTER_ALPHA_START_OFFSET,
                        APP_OPEN_APP_ENTER_ALPHA_DURATION,
                        APP_OPEN_APP_ENTER_ALPHA_INTERPOLATOR)
            }

            override fun onUpdate(percent: Float) {
                val params = arrayOfNulls<SyncRtSurfaceTransactionApplier.SurfaceParams>(targets.size)
                for (i in (0 until targets.size).reversed()) {
                    val target = targets[i]

                    val targetCrop = target.sourceContainerBounds
                    val alpha: Float
                    if (target.mode == targetMode) {
                        alpha = this.alpha.value
                        matrix.setTranslate(target.position.x.toFloat(), target.position.y.toFloat())
                        matrix.postScale(scale.value, scale.value,
                                getTargetBounds(target).exactCenterX(),
                                getTargetBounds(target).exactCenterY())
                    } else {
                        alpha = 1f
                        matrix.setTranslate(target.position.x.toFloat(), target.position.y.toFloat())
                    }

                    params[i] = SyncRtSurfaceTransactionApplier.SurfaceParams(target.leash, alpha,
                            matrix, targetCrop, RemoteAnimationProvider.getLayer(target, MODE_OPENING))
                }
                surfaceApplier.scheduleApply(*params)
            }
        })

        return appAnimator
    }

    private fun getViewBounds(v: View): Rect {
        val bounds = Rect()
        when {
            v.parent is DeepShortcutView -> {
                val view = v.parent as DeepShortcutView
                dragLayer.getDescendantRectRelativeToSelf(view.iconView, bounds)
            }
            v is BubbleTextView -> {
                val pos = Rect()
                dragLayer.getDescendantRectRelativeToSelf(v, pos)

                v.getIconBounds(bounds)
                bounds.set(pos.left + bounds.left, pos.top + bounds.top, pos.left + bounds.right, pos.top + bounds.bottom)
            }
            else -> dragLayer.getDescendantRectRelativeToSelf(v, bounds)
        }
        return bounds
    }

    private fun loadTask(taskId: Int): Task? {
        val recentsView = launcher.getOverviewPanel<RecentsView<*>>()
        val plan = RecentsTaskLoadPlan(launcher)
        val launchOpts = RecentsTaskLoadPlan.Options()
        launchOpts.runningTaskId = taskId
        launchOpts.numVisibleTasks = 1
        launchOpts.onlyLoadForCache = true
        launchOpts.onlyLoadPausedActivities = false
        launchOpts.loadThumbnails = false
        val preloadOpts = RecentsTaskLoadPlan.PreloadOptions()
        preloadOpts.loadTitles = false
        val recentsTaskLoader = recentsView.model?.recentsTaskLoader ?: return null
        plan.preloadPlan(preloadOpts, recentsTaskLoader, -1, Utilities.getUserId())
        recentsTaskLoader.loadTasks(plan, launchOpts)
        return plan.taskStack.findTaskWithId(taskId)
    }

    private fun allowWindowIconTransition(targets: Array<out RemoteAnimationTargetCompat>): Boolean {
        if (!useWindowToIcon) return false
        if (!launcher.isInState(NORMAL) && !launcher.isInState(ALL_APPS)) return false
        if (launcher.hasSomeInvisibleFlag(INVISIBLE_BY_APP_TRANSITIONS)) return false
        return launcherIsATargetWithMode(targets, MODE_OPENING) || launcher.isForceInvisible
    }

    private fun getWindowSourceBounds(targets: Array<out RemoteAnimationTargetCompat>): Rect {
        val bounds = Rect(0, 0, mDeviceProfile.widthPx, mDeviceProfile.heightPx)
        if (launcher.isInMultiWindowModeCompat) {
            for (target in targets) {
                if (target.mode == MODE_CLOSING) {
                    bounds.set(target.sourceContainerBounds)
                    bounds.offsetTo(target.position.x, target.position.y)
                    return bounds
                }
            }
        }
        return bounds
    }

    private fun findIconForComponent(component: ComponentKey, allowFolder: Boolean): View? {
        return when {
            launcher.isInState(NORMAL) -> findWorkspaceIconForComponent(component, allowFolder)
            launcher.isInState(ALL_APPS) -> findAllAppsIconForComponent(component)
            else -> null
        }
    }

    private fun findWorkspaceIconForComponent(component: ComponentKey, allowFolder: Boolean = false): View? {
        return findInViews(Workspace.ItemOperator { info, _ ->
            matchesComponent(info, component, allowFolder)
        }, launcher.workspace.currentContainer, launcher.hotseat.layout.shortcutsAndWidgets)
    }

    private fun findAllAppsIconForComponent(component: ComponentKey): View? {
        val appsView = launcher.allAppsController.appsView
        val predictions = (appsView.floatingHeaderView as PredictionsFloatingHeader).predictionRowView
        return findInViews(Workspace.ItemOperator { info, _ ->
            matchesComponent(info, component, false)
        }, appsView.activeRecyclerView, predictions)
    }

    private fun matchesComponent(info: ItemInfo?, component: ComponentKey, allowFolder: Boolean): Boolean {
        if (info == null) {
            return false
        }

        if (info is FolderInfo) {
            if (info.isCoverMode) {
                return matchesComponent(info.coverInfo, component, false)
            } else if (allowFolder) {
                return info.contents.any { matchesComponent(it, component, allowFolder) }
            }
        }

        return info.targetComponent?.packageName == component.componentName.packageName && info.user == component.user
    }

    companion object {

        private const val TAG = "LawnchairTransition"

        private val APP_OPEN_APP_ENTER_SCALE_INTERPOLATOR = PathInterpolator(0.2f, 0.5f, 0.2f, 1.0f)
        private const val APP_OPEN_APP_ENTER_SCALE_FROM = 0.3f
        private const val APP_OPEN_APP_ENTER_SCALE_TO = 1.0f
        private const val APP_OPEN_APP_ENTER_SCALE_DURATION = 350f

        private val APP_OPEN_APP_ENTER_ALPHA_INTERPOLATOR = PathInterpolator(0.33f, 0.0f, 0.3f, 1.0f)
        private const val APP_OPEN_APP_ENTER_ALPHA_FROM = 0.0f
        private const val APP_OPEN_APP_ENTER_ALPHA_TO = 1.0f
        private const val APP_OPEN_APP_ENTER_ALPHA_DURATION = 233f
        private const val APP_OPEN_APP_ENTER_ALPHA_START_OFFSET = 30f

        private val APP_OPEN_HOME_EXIT_SCALE_INTERPOLATOR = PathInterpolator(0.2f, 0.5f, 0.2f, 1.0f)
        private const val APP_OPEN_HOME_EXIT_SCALE_FROM = 1.0f
        private const val APP_OPEN_HOME_EXIT_SCALE_TO = 1.5f
        private const val APP_OPEN_HOME_EXIT_SCALE_DURATION = 250L

        private val APP_OPEN_HOME_EXIT_ALPHA_INTERPOLATOR = PathInterpolator(0.33f, 0.0f, 0.3f, 1.0f)
        private const val APP_OPEN_HOME_EXIT_ALPHA_FROM = 1.0f
        private const val APP_OPEN_HOME_EXIT_ALPHA_TO = 0.0f
        private const val APP_OPEN_HOME_EXIT_ALPHA_DURATION = 250L

        private val APP_OPEN_WALLPAPER_EXIT_INTERPOLATOR = PathInterpolator(0.2f, 0.5f, 0.2f, 1.0f)
        private const val APP_OPEN_WALLPAPER_EXIT_DURATION = 250L

        private val APP_CLOSE_APP_EXIT_SCALE_INTERPOLATOR = PathInterpolator(0.33f, 0.0f, 0.2f, 1.0f)
        private const val APP_CLOSE_APP_EXIT_SCALE_FROM = 1.0f
        private const val APP_CLOSE_APP_EXIT_SCALE_TO = 0.1f
        private const val APP_CLOSE_APP_EXIT_SCALE_DURATION = 300f

        private val APP_CLOSE_APP_EXIT_ALPHA_INTERPOLATOR = PathInterpolator(0.33f, 0.0f, 0.1f, 1.0f)
        private const val APP_CLOSE_APP_EXIT_ALPHA_FROM = 1.0f
        private const val APP_CLOSE_APP_EXIT_ALPHA_TO = 0.0f
        private const val APP_CLOSE_APP_EXIT_ALPHA_DURATION = 200f
        private const val APP_CLOSE_APP_EXIT_ALPHA_START_OFFSET = 20f

        private val APP_CLOSE_HOME_ENTER_SCALE_INTERPOLATOR = PathInterpolator(0.33f, 0.0f, 0.2f, 1.0f)
        private const val APP_CLOSE_HOME_ENTER_SCALE_FROM = 1.5f
        private const val APP_CLOSE_HOME_ENTER_SCALE_TO = 1.0f
        private const val APP_CLOSE_HOME_ENTER_SCALE_DURATION = 250L

        private val APP_CLOSE_HOME_ENTER_ALPHA_INTERPOLATOR = PathInterpolator(0.33f, 0.0f, 0.3f, 1.0f)
        private const val APP_CLOSE_HOME_ENTER_ALPHA_FROM = 0.0f
        private const val APP_CLOSE_HOME_ENTER_ALPHA_TO = 1.0f
        private const val APP_CLOSE_HOME_ENTER_ALPHA_DURATION = 100L

        private val APP_CLOSE_WALLPAPER_ENTER_SCALE_INTERPOLATOR = PathInterpolator(0.33f, 0.0f, 0.2f, 1.0f)
        private const val APP_CLOSE_WALLPAPER_ENTER_SCALE_DURATION = 250L
    }
}
