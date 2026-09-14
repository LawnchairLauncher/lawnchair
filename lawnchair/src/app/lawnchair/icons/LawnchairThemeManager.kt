package app.lawnchair.icons

import android.content.Context
import android.util.Log
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.icons.shape.PathShapeDelegate
import app.lawnchair.preferences.PreferenceChangeListener
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.mono.MonoIconThemeController
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.LooperExecutor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus

@LauncherAppSingleton
class LawnchairThemeManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @Ui private val uiExecutor: LooperExecutor,
    private val prefs: LauncherPrefs,
    private val iconControllerFactory: IconControllerFactory,
    private val lifecycle: DaggerSingletonTracker,
    private val prefs2: PreferenceManager2,
    private val prefs1: PreferenceManager,
) : ThemeManager(
    context,
    uiExecutor,
    prefs,
    iconControllerFactory,
    lifecycle,
) {
    private val statePrefs1 = listOf(
        prefs1.wrapAdaptiveIcons,
        prefs1.maskOnlyIcons,
        prefs1.transparentIconBackground,
        prefs1.shadowBGIcons,
        prefs1.coloredBackgroundLightness,
        prefs1.forceIconMonochrome,
    )

    private val prefListener = PreferenceChangeListener {
        uiExecutor.execute { verifyIconState() }
    }

    override var iconState = parseIconStateV2(null)

    init {
        val scope = MainScope() + CoroutineName("LawnchairThemeManager")
        merge(
            prefs2.iconShape.get(),
            prefs2.customIconShape.get(),
            prefs2.folderShape.get(),
            prefs2.customFolderShape.get(),
        ).onEach { verifyIconState() }
            .launchIn(scope)

        statePrefs1.forEach { it.addListener(prefListener) }

        lifecycle.addCloseable {
            scope.cancel()
            statePrefs1.forEach { it.removeListener(prefListener) }
        }
    }

    override fun verifyIconState() {
        val newState = parseIconStateV2(iconState)
        if (newState == iconState) return
        iconState = newState

        listeners.forEach { it.onThemeChanged() }
    }

    private fun prefs1State(): String = statePrefs1.joinToString(",") { it.get().toString() }

    private fun parseIconStateV2(oldState: IconState?): IconState {
        val currentAppShape: IconShape = try {
            prefs2.iconShape.firstCached()
        } catch (e: Exception) {
            Log.d(TAG, "Error getting icon shape", e)
            IconShape.Circle
        }

        // Sora: folders wear the icon shape, full stop. Unified where the shape
        // is read rather than by copying one setting into the other on write:
        // a copy can only ever be as reliable as the order the two writes land
        // in, and it leaves every existing install still carrying whatever
        // mismatched folder shape it had already saved.
        val currentFolderShape: IconShape = currentAppShape

        val currentPrefs1State = prefs1State()
        val appShapeKey = currentAppShape.getHashString() + currentPrefs1State
        val folderShapeKey = currentFolderShape.getHashString() + currentPrefs1State
        val combinedKey = "$appShapeKey:$folderShapeKey:$ICON_GENERATION"

        val appShape =
            if (oldState != null && (oldState.iconShape as? PathShapeDelegate)?.iconShape == currentAppShape) {
                oldState.iconShape
            } else {
                PathShapeDelegate(currentAppShape)
            }

        val folderShape =
            if (oldState != null && (oldState.folderShape as? PathShapeDelegate)?.iconShape == currentFolderShape) {
                oldState.folderShape
            } else {
                PathShapeDelegate(currentFolderShape)
            }

        val themeController = iconControllerFactory.createThemeController()?.let {
            if (prefs1.forceIconMonochrome.get()) {
                FORCED_MONO_THEME_CONTROLLER
            } else {
                MONO_THEME_CONTROLLER
            }
        }

        return IconState(
            iconMask = combinedKey,
            folderRadius = 1f,
            shapeRadius = 1f,
            themeController = themeController,
            iconShape = appShape,
            folderShape = folderShape,
        )
    }
}

// Reuse controllers to allow the equality check in verifyIconState.
private val MONO_THEME_CONTROLLER = MonoIconThemeController()
private val FORCED_MONO_THEME_CONTROLLER = MonoIconThemeController(shouldForceThemeIcon = true)
private const val TAG = "LawnchairThemeManager"

/**
 * Sora: bumped whenever the way an icon bitmap is *drawn* changes.
 *
 * Icon bitmaps are cached and keyed on the state this builds. A change to how
 * they are generated leaves that key untouched, so the cache goes on serving
 * bitmaps made by the old code and the change appears only on icons that happen
 * to be regenerated for some other reason. Baking the rim light in first showed
 * up on exactly one icon for that reason: the app that had just been reinstalled.
 */
private const val ICON_GENERATION = "rim8"
