package app.lawnchair.fold

import android.content.Context
import android.util.Log
import android.widget.Toast
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.ConstantItem
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.SCREEN
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.patrykmichalik.opto.core.setBlocking

/**
 * The two item sets of the "Independent" home layout, in one database.
 *
 * Cover items are stored as always. Items of the large (inner) screen use the same containers
 * (desktop and dock) with screen ids, and dock ranks, from [INNER_SCREEN_BASE] up, so every table,
 * backup and existing database path still sees one ordinary file. Folder contents point at their
 * folder and follow its set. The launcher presents the active set with screen ids from 0, see
 * `CellPosMapper`; the other set stays in the model, never bound and never deleted.
 */
object FoldItemSets {

    private const val TAG = "LC-FoldItemSets"

    /** First model screen id (and dock rank) of the inner set. */
    const val INNER_SCREEN_BASE = 1_000_000

    /**
     * The model screen id of page (or dock rank) [presenterId] of the inner set if [inner], else
     * of the cover set. Negative ids (no page) are left as they are.
     */
    @JvmStatic
    fun toModelScreenId(presenterId: Int, inner: Boolean): Int = if (inner && presenterId >= 0) presenterId + INNER_SCREEN_BASE else presenterId

    /** The page (or dock rank) within its item set of a desktop or dock item's model screen id. */
    @JvmStatic
    fun toPresenterScreenId(modelId: Int): Int = if (modelId >= INNER_SCREEN_BASE) modelId - INNER_SCREEN_BASE else modelId

    /**
     * SQL condition selecting the desktop and dock rows of the inner set if [inner], else of the
     * cover set; combine it with a container condition. Folder contents follow their folder.
     */
    @JvmStatic
    fun whereItemSet(inner: Boolean): String = "$SCREEN ${if (inner) ">=" else "<"} $INNER_SCREEN_BASE"

    /** The persisted page order of the inner set if [inner], else of the cover set. */
    @JvmStatic
    fun screenOrderPref(inner: Boolean): ConstantItem<String> = if (inner) LauncherPrefs.INNER_WORKSPACE_SCREEN_ORDER else LauncherPrefs.WORKSPACE_SCREEN_ORDER

    /** Whether this is a top-level desktop or dock item of the inner set. */
    @JvmStatic
    fun isInnerItem(item: ItemInfo): Boolean = (item.container == CONTAINER_DESKTOP || item.container == CONTAINER_HOTSEAT) &&
        item.screenId >= INNER_SCREEN_BASE

    /**
     * Whether a top-level desktop or dock item belongs to the active set. Other containers (folder
     * contents, predictions) are not filtered here.
     */
    @JvmStatic
    fun isInActiveSet(item: ItemInfo, innerSetActive: Boolean): Boolean {
        if (item.container != CONTAINER_DESKTOP && item.container != CONTAINER_HOTSEAT) return true
        return isInnerItem(item) == innerSetActive
    }

    /**
     * Whether this device has the two item sets: a foldable with the "Independent" home layout.
     * A backup restored on a phone or tablet keeps the preference, but has one set only.
     */
    @JvmStatic
    fun isIndependent(context: Context): Boolean = InvariantDeviceProfile.deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY &&
        FoldLayout.isHomeIndependent(PreferenceManager2.INSTANCE.get(context))

    /**
     * Whether the launcher shows the inner set, as of its last bind. Set by `ModelCallbacks` on
     * every bind (a fold or unfold rebinds) from `CellPosMapper.isInnerItemSet`, so model tasks
     * read it on any thread without asking the activity, and it outlives a launcher being
     * recreated. Until a bind sets it, it is the set of the pages on screen, which
     * `ModelCallbacks.getPagesToBindSynchronously` compares with the set about to be bound.
     */
    @JvmStatic
    @Volatile
    var isInnerSetShown = false

    /**
     * Creates the inner set from the cover layout when "Independent" is selected and the inner
     * set has no items yet (the first time, or after the user emptied it). Runs on the model
     * thread after pending writes, copies in one transaction (see
     * `DatabaseHelper.cloneCoverItemSetToInnerSet`), gives the inner set the cover's page order,
     * then reloads the model once so the copies (and pending widgets) are loaded and bound. An
     * inner set that already has items is left as it is; nothing is ever deleted. If the copy
     * fails, the home layout goes back to [revertTo] and a toast says why, so "Independent" is
     * never left on with an empty inner screen.
     */
    @JvmStatic
    fun createInnerSetIfEmpty(context: Context, revertTo: FoldHomeLayout) {
        val appContext = context.applicationContext
        MODEL_EXECUTOR.execute {
            val model = LauncherAppState.getInstance(appContext).model
            val copied = try {
                model.modelDbController.cloneCoverItemSetToInnerSet()
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't copy the cover layout into the inner set, back to $revertTo", e)
                PreferenceManager2.INSTANCE.get(appContext).foldHomeLayout.setBlocking(revertTo)
                MAIN_EXECUTOR.execute {
                    Toast.makeText(appContext, R.string.fold_independent_copy_failed, Toast.LENGTH_LONG).show()
                }
                return@execute
            }
            if (copied <= 0) return@execute
            val prefs = LauncherPrefs.get(appContext)
            prefs.putSync(LauncherPrefs.INNER_WORKSPACE_SCREEN_ORDER.to(prefs.get(LauncherPrefs.WORKSPACE_SCREEN_ORDER)))
            Log.i(TAG, "Inner set created from the cover layout: $copied items")
            MAIN_EXECUTOR.execute { model.forceReload() }
        }
    }
}
