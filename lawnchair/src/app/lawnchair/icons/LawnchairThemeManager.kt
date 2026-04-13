package app.lawnchair.icons

import android.content.Context
import android.util.Log
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.icons.shape.PathShapeDelegate
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.EncryptionType
import com.android.launcher3.LauncherPrefChangeListener
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.backedUpItem
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.LooperExecutor
import com.patrykmichalik.opto.core.firstBlocking
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach

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
) : ThemeManager(
    context,
    uiExecutor,
    prefs,
    iconControllerFactory,
    lifecycle,
) {
    override var iconState = parseIconStateV2(null)

    init {
        val scope = MainScope()
        merge(
            prefs2.iconShape.get(),
            prefs2.customIconShape.get(),
        ).onEach { verifyIconState() }
            .launchIn(scope)

        // Listen for specific Lawnchair SharedPreferences it's easier than trying to make prefs1 work with listener
        val drawerThemedIcons = backedUpItem("drawer_themed_icons", false, EncryptionType.DEVICE_PROTECTED)
        val forceMonochrome = backedUpItem("pref_forceIconMonochrome", false, EncryptionType.DEVICE_PROTECTED)
        val keys = listOf(drawerThemedIcons, forceMonochrome)
        val keysArray = keys.toTypedArray()
        val prefKeySet = keys.map { it.sharedPrefKey }

        val prefListener = LauncherPrefChangeListener { key ->
            if (prefKeySet.contains(key)) verifyIconState()
        }
        prefs.addListener(prefListener, *keysArray)

        lifecycle.addCloseable {
            prefs.removeListener(prefListener, *keysArray)
            scope.cancel()
        }
    }

    override fun verifyIconState() {
        val newState = parseIconStateV2(iconState)
        if (newState == iconState) return
        iconState = newState

        listeners.forEach { it.onThemeChanged() }
    }

    private fun parseIconStateV2(oldState: IconState?): IconState {
        val currentAppShape: IconShape = try {
            prefs2.iconShape.firstBlocking()
        } catch (e: Exception) {
            Log.d(TAG, "Error getting icon shape", e)
            IconShape.Circle
        }

        val currentFolderShape: IconShape = try {
            prefs2.folderShape.firstBlocking()
        } catch (e: Exception) {
            Log.d(TAG, "Error getting icon shape", e)
            IconShape.Circle
        }

        val appShapeKey = currentAppShape.getHashString()
        val folderShapeKey = currentFolderShape.getHashString()

        val appShape =
            if (oldState != null && oldState.iconMask == appShapeKey) {
                oldState.iconShape
            } else {
                PathShapeDelegate(currentAppShape)
            }

        val folderShape =
            if (oldState != null && oldState.iconMask == folderShapeKey) {
                oldState.iconShape
            } else {
                PathShapeDelegate(currentFolderShape)
            }

        return IconState(
            iconMask = appShapeKey,
            folderRadius = 1f,
            shapeRadius = 1f,
            themeController = iconControllerFactory.createThemeController(),
            iconShape = appShape,
            folderShape = folderShape,
        )
    }
}

private const val TAG = "LawnchairThemeManager"
