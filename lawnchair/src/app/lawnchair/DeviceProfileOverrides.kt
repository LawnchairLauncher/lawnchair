package app.lawnchair

import android.content.Context
import app.lawnchair.fold.FoldLayout
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.InvariantDeviceProfile.INDEX_DEFAULT
import com.android.launcher3.InvariantDeviceProfile.INDEX_LANDSCAPE
import com.android.launcher3.InvariantDeviceProfile.INDEX_TWO_PANEL_LANDSCAPE
import com.android.launcher3.InvariantDeviceProfile.INDEX_TWO_PANEL_PORTRAIT
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable
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

    fun getGridName(gridInfo: DBGridInfo): String {
        val grids = predefinedGrids
        val match = grids
            .firstOrNull { it.first.numRows >= gridInfo.numRows && it.first.numColumns >= gridInfo.numColumns }
            ?: grids.last()
        // LC-Note: Grids can have the same size (e.g. 4_by_6 and practical on foldables), so keep
        // the current one rather than always switching to the first of them.
        val currentGridName = LauncherPrefs.get(context).get(LauncherPrefs.GRID_NAME)
        val current = grids.firstOrNull { it.second == currentGridName }
        if (current != null && current.first.numRows == match.first.numRows &&
            current.first.numColumns == match.first.numColumns
        ) {
            return current.second
        }
        return match.second
    }

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
    )

    fun getTextFactors() = TextFactors(preferenceManager2)

    /**
     * The text factors of the profile of type [typeIndex]: the large screen of a foldable in
     * landscape has its own label size with [app.lawnchair.fold.FoldLandscape.OWN].
     */
    fun getTextFactors(typeIndex: Int): TextFactors {
        val textFactors = TextFactors(preferenceManager2)
        if (!isLandscapeOwn(typeIndex)) return textFactors
        val showLabels = preferenceManager2.showIconLabelsOnHomeScreen.firstCached()
        return textFactors.copy(
            iconTextSizeFactor = if (showLabels) FoldLayout.landscapeLabelSizeFactor(preferenceManager2) else 0f,
        )
    }

    /**
     * Scale of the responsive grids' icon size for the profile of type [typeIndex]. Responsive
     * grids take their icon size from their cell specs, not from the icon size factor, so the large
     * screen in landscape with [app.lawnchair.fold.FoldLandscape.OWN] is scaled relative to portrait.
     */
    fun getResponsiveIconScale(typeIndex: Int): Float {
        if (!isLandscapeOwn(typeIndex)) return 1f
        val portrait = preferenceManager2.homeIconSizeFactor.firstCached()
        if (portrait <= 0f) return 1f
        return FoldLayout.landscapeIconSizeFactor(preferenceManager2) / portrait
    }

    private fun isLandscapeOwn(typeIndex: Int) = typeIndex == INDEX_TWO_PANEL_LANDSCAPE &&
        InvariantDeviceProfile.deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY &&
        FoldLayout.isLandscapeOwn(preferenceManager2)
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

        val iconSizeFactor: Float,
        // The large screen of a foldable in landscape ([INDEX_TWO_PANEL_LANDSCAPE])
        val landscapeIconSizeFactor: Float,
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
        ) : this(
            numAllAppsColumns = prefs2.drawerColumns.firstCached(gridOption = defaultGrid),
            numFolderRows = prefs.folderRows.get(defaultGrid),
            numFolderColumns = prefs2.folderColumns.firstCached(gridOption = defaultGrid),
            folderGridInLandscape = deviceType == InvariantDeviceProfile.TYPE_TABLET ||
                deviceType == InvariantDeviceProfile.TYPE_DESKTOP,

            iconSizeFactor = prefs2.homeIconSizeFactor.firstCached(),
            landscapeIconSizeFactor =
            if (deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY && FoldLayout.isLandscapeOwn(prefs2)) {
                FoldLayout.landscapeIconSizeFactor(prefs2)
            } else {
                prefs2.homeIconSizeFactor.firstCached()
            },
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
                prefs.folderRowsUnfolded.get(defaultGrid)
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
            idp.iconSize[INDEX_DEFAULT] *= iconSizeFactor
            idp.iconSize[INDEX_LANDSCAPE] *= iconSizeFactor
            idp.iconSize[INDEX_TWO_PANEL_PORTRAIT] *= iconSizeFactor
            idp.iconSize[INDEX_TWO_PANEL_LANDSCAPE] *= landscapeIconSizeFactor

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
        ) : this(
            enableIconText = prefs2.showIconLabelsOnHomeScreen.firstCached(),
            iconTextSizeFactor = prefs2.homeIconLabelSizeFactor.firstCached(),
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
