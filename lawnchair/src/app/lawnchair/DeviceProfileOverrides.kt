package app.lawnchair

import android.content.Context
import androidx.annotation.VisibleForTesting
import app.lawnchair.fold.FoldLandscape
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.InvariantDeviceProfile.COUNT_SIZES
import com.android.launcher3.InvariantDeviceProfile.INDEX_DEFAULT
import com.android.launcher3.InvariantDeviceProfile.INDEX_LANDSCAPE
import com.android.launcher3.InvariantDeviceProfile.INDEX_TWO_PANEL_LANDSCAPE
import com.android.launcher3.InvariantDeviceProfile.INDEX_TWO_PANEL_PORTRAIT
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable
import com.patrykmichalik.opto.core.PreferenceImpl
import javax.inject.Inject

@LauncherAppSingleton
class DeviceProfileOverrides @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {
    private val prefs = PreferenceManager.getInstance(context)
    private val preferenceManager2 = PreferenceManager2.getInstance(context)

    // LC-Note: The grid options depend on the device type, which InvariantDeviceProfile only sets
    // at the end of its first initGrid, while this class is first used during it. Parsing them
    // then kept the phone grids forever, so parse them again whenever the device type changes.
    @Volatile
    private var predefinedGridsCache: Pair<Int, List<Pair<DBGridInfo, String>>>? = null

    private val predefinedGrids: List<Pair<DBGridInfo, String>>
        get() {
            val deviceType = InvariantDeviceProfile.deviceType
            predefinedGridsCache?.let { (cachedDeviceType, grids) ->
                if (cachedDeviceType == deviceType) return grids
            }
            return InvariantDeviceProfile.parseAllGridOptions(context)
                .map { option ->
                    val gridInfo = DBGridInfo(
                        numHotseatColumns = option.numHotseatIcons,
                        numRows = option.numRows,
                        numColumns = option.numColumns,
                    )
                    gridInfo to option.name
                }
                .also { predefinedGridsCache = deviceType to it }
        }

    fun getGridInfo() = DBGridInfo(prefs)

    fun getGridInfo(gridName: String) = predefinedGrids
        .first { it.second == gridName }
        .first

    fun getGridName(gridInfo: DBGridInfo): String = pickGridName(predefinedGrids, gridInfo)

    fun getCurrentGridName() = getGridName(getGridInfo())

    fun setCurrentGrid(gridName: String) {
        val gridInfo = getGridInfo(gridName)
        prefs.workspaceRows.set(gridInfo.numRows)
        prefs.workspaceColumns.set(gridInfo.numColumns)
        prefs.hotseatColumns.set(gridInfo.numHotseatColumns)
    }

    fun getOverrides(
        defaultGrid: InvariantDeviceProfile.GridOption,
        deviceType: Int,
        previewOverrides: PreviewOverrides? = null,
    ) = Options(
        prefs = prefs,
        prefs2 = preferenceManager2,
        defaultGrid = defaultGrid,
        deviceType = deviceType,
        previewOverrides = previewOverrides ?: PreviewOverrides(),
        iconSizeFactors = List(COUNT_SIZES) { iconScaleFor(it) },
    )

    /** The text factors of the profile of type [typeIndex], with its label size ([labelScaleFor]). */
    fun getTextFactors(typeIndex: Int) = TextFactors(preferenceManager2, iconTextSizeFactor = labelScaleFor(typeIndex))

    /**
     * The home icon size factor of the profile of type [typeIndex]. The large screen of a foldable
     * in landscape ([INDEX_TWO_PANEL_LANDSCAPE]) has its own with [FoldLandscape.OWN] once it is
     * set; every other profile, and every other device, has the portrait one.
     */
    fun iconScaleFor(typeIndex: Int): Float = landscapeOwnFactor(typeIndex, preferenceManager2.foldLandscapeIconSizeFactor)
        ?: preferenceManager2.homeIconSizeFactor.firstCached(preferenceManager2)

    /** Like [iconScaleFor], for the home icon labels. */
    fun labelScaleFor(typeIndex: Int): Float = landscapeOwnFactor(typeIndex, preferenceManager2.foldLandscapeIconLabelSizeFactor)
        ?: preferenceManager2.homeIconLabelSizeFactor.firstCached(preferenceManager2)

    /** The value of [ownFactor] for the profile of type [typeIndex], if it has its own (a negative value is unset). */
    private fun landscapeOwnFactor(typeIndex: Int, ownFactor: PreferenceImpl<Float, Float>): Float? {
        val isOwn = typeIndex == INDEX_TWO_PANEL_LANDSCAPE &&
            InvariantDeviceProfile.deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY &&
            preferenceManager2.foldLandscape.firstCached(preferenceManager2) == FoldLandscape.OWN
        return if (isOwn) ownFactor.firstCached(preferenceManager2).takeIf { it >= 0f } else null
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    data class DBGridInfo(
        val numHotseatColumns: Int,
        val numRows: Int,
        val numColumns: Int,
    ) {
        val dbFile get() = "launcher_${numRows}_${numColumns}_$numHotseatColumns.db"

        constructor(prefs: PreferenceManager) : this(
            numHotseatColumns = prefs.hotseatColumns.get(),
            numRows = prefs.workspaceRows.get(),
            numColumns = prefs.workspaceColumns.get(),
        )
    }

    /** Override for any other value that's not a DBGridInfo, extends this value when needed */
    data class PreviewOverrides(
        val foldableDatabaseHotseatIcons: Int? = null,
    )

    data class Options(
        val numAllAppsColumns: Int,
        val numFolderRows: Int,
        val numFolderColumns: Int,
        // Tablets and desktops use the folder grid in landscape too (phones keep the grid option's)
        val folderGridInLandscape: Boolean = false,

        // The home icon size factor of each profile type ([iconScaleFor])
        val iconSizeFactors: List<Float>,
        val allAppsIconSizeFactor: Float,
        val allAppsIconTextSizeFactor: Float,

        val enableTaskbarOnPhone: Boolean,

        val numHotseatRows: Int = 1,
        val numDockPages: Int = 1,

        // Foldable overrides (-1 means don't override)
        val foldableShownHotseatIcons: Int = -1,
        val foldableDatabaseHotseatIcons: Int = -1,
        val foldableDatabaseAllAppsColumns: Int = -1,
        val foldableFolderRows: Int = -1,
        val foldableFolderColumns: Int = -1,
    ) {
        constructor(
            prefs: PreferenceManager,
            prefs2: PreferenceManager2,
            defaultGrid: InvariantDeviceProfile.GridOption,
            deviceType: Int,
            previewOverrides: PreviewOverrides,
            iconSizeFactors: List<Float>,
        ) : this(
            numAllAppsColumns = prefs2.drawerColumns.firstCached(gridOption = defaultGrid),
            numFolderRows = prefs.folderRows.get(defaultGrid),
            numFolderColumns = prefs2.folderColumns.firstCached(gridOption = defaultGrid),
            folderGridInLandscape = deviceType == InvariantDeviceProfile.TYPE_TABLET ||
                deviceType == InvariantDeviceProfile.TYPE_DESKTOP,

            iconSizeFactors = iconSizeFactors,
            allAppsIconSizeFactor = prefs2.drawerIconSizeFactor.firstCached(),
            allAppsIconTextSizeFactor =
            if (prefs2.showIconLabelsInDrawer.firstCached()) {
                prefs2.drawerIconLabelSizeFactor.firstCached()
            } else {
                0f
            },

            enableTaskbarOnPhone = prefs2.enableTaskbarOnPhone.firstCached(),

            numHotseatRows = prefs.hotseatRows.get().coerceIn(1, 2),
            numDockPages = prefs.dockPages.get().coerceIn(1, 5),

            foldableShownHotseatIcons = if (deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY) {
                val folded = prefs.hotseatColumns.get()
                val unfolded = previewOverrides.foldableDatabaseHotseatIcons ?: prefs.hotseatColumnsUnfolded.get()
                folded.coerceAtMost(unfolded)
            } else {
                -1
            },
            foldableDatabaseHotseatIcons = if (deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY) {
                previewOverrides.foldableDatabaseHotseatIcons ?: prefs.hotseatColumnsUnfolded.get()
            } else {
                -1
            },
            foldableDatabaseAllAppsColumns = if (deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY) {
                val folded = prefs2.drawerColumns.firstCached(gridOption = defaultGrid)
                val unfolded = prefs2.drawerColumnsUnfolded.firstCached(gridOption = defaultGrid)
                folded.coerceAtLeast(unfolded)
            } else {
                -1
            },
            foldableFolderRows = if (deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY) {
                prefs2.folderRowsUnfolded.firstCached(gridOption = defaultGrid)
            } else {
                -1
            },
            foldableFolderColumns = if (deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY) {
                prefs2.folderColumnsUnfolded.firstCached(gridOption = defaultGrid)
            } else {
                -1
            },
        )

        fun applyUi(idp: InvariantDeviceProfile) {
            // apply grid size
            idp.numAllAppsColumns = numAllAppsColumns
            idp.numDatabaseAllAppsColumns = numAllAppsColumns
            idp.numFolderRows[INDEX_DEFAULT] = numFolderRows
            idp.numFolderColumns[INDEX_DEFAULT] = numFolderColumns
            // The other profiles took their folder grid from the grid option only, so the folder
            // settings didn't apply in landscape or on the unfolded screen. Tablets in landscape:
            if (folderGridInLandscape) {
                idp.numFolderRows[INDEX_LANDSCAPE] = numFolderRows
                idp.numFolderColumns[INDEX_LANDSCAPE] = numFolderColumns
            }
            // Foldables, the two-panel profiles (unfolded, in both orientations):
            if (foldableFolderRows > 0) {
                idp.numFolderRows[INDEX_TWO_PANEL_PORTRAIT] = foldableFolderRows
                idp.numFolderRows[INDEX_TWO_PANEL_LANDSCAPE] = foldableFolderRows
            }
            if (foldableFolderColumns > 0) {
                idp.numFolderColumns[INDEX_TWO_PANEL_PORTRAIT] = foldableFolderColumns
                idp.numFolderColumns[INDEX_TWO_PANEL_LANDSCAPE] = foldableFolderColumns
            }

            // Foldable overrides for hotseat and allapps columns
            if (foldableShownHotseatIcons > 0) {
                idp.numShownHotseatIcons = foldableShownHotseatIcons
            }
            if (foldableDatabaseHotseatIcons > 0) {
                idp.numDatabaseHotseatIcons = foldableDatabaseHotseatIcons
            }
            if (foldableDatabaseAllAppsColumns > 0) {
                idp.numDatabaseAllAppsColumns = foldableDatabaseAllAppsColumns
            }

            // Ensure database can hold enough icons for multi-row / multi-page dock
            val requiredSlots = idp.numShownHotseatIcons * numHotseatRows * numDockPages
            if (idp.numDatabaseHotseatIcons < requiredSlots) {
                idp.numDatabaseHotseatIcons = requiredSlots
            }

            // apply icon and text size
            iconSizeFactors.forEachIndexed { typeIndex, factor -> idp.iconSize[typeIndex] *= factor }

            idp.allAppsIconSize[INDEX_DEFAULT] *= allAppsIconSizeFactor
            idp.allAppsIconSize[INDEX_LANDSCAPE] *= allAppsIconSizeFactor
            idp.allAppsIconSize[INDEX_TWO_PANEL_PORTRAIT] *= allAppsIconSizeFactor
            idp.allAppsIconSize[INDEX_TWO_PANEL_LANDSCAPE] *= allAppsIconSizeFactor

            idp.allAppsIconTextSize[INDEX_DEFAULT] *= allAppsIconTextSizeFactor
            idp.allAppsIconTextSize[INDEX_LANDSCAPE] *= allAppsIconTextSizeFactor
            idp.allAppsIconTextSize[INDEX_TWO_PANEL_PORTRAIT] *= allAppsIconTextSizeFactor
            idp.allAppsIconTextSize[INDEX_TWO_PANEL_LANDSCAPE] *= allAppsIconTextSizeFactor
        }
    }

    data class TextFactors(
        val iconTextSizeFactor: Float,
        val allAppsIconTextSizeFactor: Float,
        val iconFolderTextSizeFactor: Float,
    ) {
        constructor(
            prefs2: PreferenceManager2,
            iconTextSizeFactor: Float,
        ) : this(
            enableIconText = prefs2.showIconLabelsOnHomeScreen.firstCached(),
            iconTextSizeFactor = iconTextSizeFactor,
            enableIconTextFolder = prefs2.showIconLabelsOnHomeScreenFolder.firstCached(),
            iconFolderTextSizeFactor = prefs2.homeIconLabelFolderSizeFactor.firstCached(),
            enableAllAppsIconText = prefs2.showIconLabelsInDrawer.firstCached(),
            allAppsIconTextSizeFactor = prefs2.drawerIconLabelSizeFactor.firstCached(),
        )

        constructor(
            enableIconText: Boolean,
            iconTextSizeFactor: Float,
            enableIconTextFolder: Boolean,
            iconFolderTextSizeFactor: Float,
            enableAllAppsIconText: Boolean,
            allAppsIconTextSizeFactor: Float,
        ) : this(
            iconTextSizeFactor = if (enableIconText) iconTextSizeFactor else 0f,
            allAppsIconTextSizeFactor = if (enableAllAppsIconText) allAppsIconTextSizeFactor else 0f,
            iconFolderTextSizeFactor = if (enableIconTextFolder) iconFolderTextSizeFactor else 0f,
        )
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getDPO)
    }
}

/**
 * The name of the first of [grids] (in their XML order) that holds [gridInfo]'s rows and columns,
 * else of the largest, the last one. Grids can have the same size: on foldables the regular 4_by_6
 * and the responsive practical, whose fixed cell sizes ignore the row and column settings. The
 * first of them is always used, so foldables get 4_by_6 like upstream (and like phones), also with
 * more rows or columns than any grid has.
 */
@VisibleForTesting
internal fun pickGridName(grids: List<Pair<DeviceProfileOverrides.DBGridInfo, String>>, gridInfo: DeviceProfileOverrides.DBGridInfo): String {
    val largest = grids.last().first
    return grids.firstOrNull { it.first.numRows >= gridInfo.numRows && it.first.numColumns >= gridInfo.numColumns }?.second
        ?: grids.first { it.first.numRows == largest.numRows && it.first.numColumns == largest.numColumns }.second
}
