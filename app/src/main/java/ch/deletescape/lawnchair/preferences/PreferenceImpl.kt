package ch.deletescape.lawnchair.preferences

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.view.View
import ch.deletescape.lawnchair.BuildConfig
import ch.deletescape.lawnchair.Launcher
import ch.deletescape.lawnchair.LauncherFiles
import ch.deletescape.lawnchair.Utilities
import ch.deletescape.lawnchair.config.FeatureFlags
import ch.deletescape.lawnchair.dynamicui.ExtractedColors
import java.io.File
import kotlin.reflect.KProperty

open class PreferenceImpl(context: Context) : IPreferenceProvider {
    val context = context.applicationContext!!

    override val workSpaceLabelColor by IntPref(PreferenceFlags.KEY_PREF_WS_LABEL_COLOR, Color.WHITE)
    override val allAppsLabelColor by IntPref(PreferenceFlags.KEY_PREF_ALL_APPS_LABEL_COLOR, Color.BLACK)

    override fun showSettings(launcher: Launcher, view: View) {
        val intent = Intent(Intent.ACTION_APPLICATION_PREFERENCES)
                .setPackage(launcher.packageName)
        intent.sourceBounds = launcher.getViewBounds(view)
        launcher.startActivity(intent, launcher.getActivityLaunchOptions(view))
    }

    override fun numRowsDrawer(default: String): String {
        return getString(PreferenceFlags.KEY_PREF_NUM_ROWS_DRAWER, default)
    }

    override fun numHotseatIcons(default: String): String {
        return getString(PreferenceFlags.KEY_PREF_NUM_HOTSEAT_ICONS, default)
    }

    override val hotseatIconScale by FloatPref(PreferenceFlags.KEY_PREF_HOTSEAT_ICON_SCALE, 1f)
    override val hotseatHeightScale by FloatPref(PreferenceFlags.KEY_PREF_HOTSEAT_HEIGHT_SCALE, 1f)
    override val hotseatCustomOpacity by FloatPref(PreferenceFlags.KEY_PREF_HOTSEAT_CUSTOM_OPACITY, .5f)
    override val allAppsIconScale by FloatPref(PreferenceFlags.KEY_PREF_ALL_APPS_ICON_SCALE, 1f)
    override val allAppsIconTextScale by FloatPref(PreferenceFlags.KEY_PREF_ALL_APPS_ICON_TEXT_SCALE, 1f)
    override val allAppsIconPaddingScale by FloatPref(PreferenceFlags.KEY_PREF_ALL_APPS_ICON_PADDING_SCALE, 1f)
    override val hotseatShouldUseCustomOpacity by BooleanPref(PreferenceFlags.KEY_HOTSEAT_SHOULD_USE_CUSTOM_OPACITY, false)
    override val useCustomAllAppsTextColor by BooleanPref(PreferenceFlags.KEY_PREF_DRAWER_CUSTOM_LABEL_COLOR, false)
    override val verticalDrawerLayout by BooleanPref(PreferenceFlags.KEY_PREF_DRAWER_VERTICAL_LAYOUT, false)
    override val iconLabelsInTwoLines by BooleanPref(PreferenceFlags.KEY_ICON_LABELS_IN_TWO_LINES, false)
    override val animatedClockIconAlternativeClockApps by BooleanPref(PreferenceFlags.KEY_ANIMATED_CLOCK_ICON_ALTERNATIVE_CLOCK_APPS, false)
    override val enablePhysics by BooleanPref(PreferenceFlags.KEY_ENABLE_PHYSICS, true)
    override val snowflakeSizeScale by FloatPref(PreferenceFlags.KEY_PREF_SNOWFLAKE_SIZE_SCALE, 1f)
    override val snowflakesNum by StringPref(PreferenceFlags.KEY_PREF_SNOWFLAKES_NUM, "200")

    override fun lightStatusBarKeyCache(default: Boolean): Boolean {
        return getBoolean(PreferenceFlags.KEY_LIGHT_STATUS_BAR, default)
    }

    override fun lightStatusBarKeyCache(value: Boolean, commit: Boolean) {
        setBoolean(PreferenceFlags.KEY_LIGHT_STATUS_BAR, value, commit)
    }

    override val weatherUnit by StringPref(PreferenceFlags.KEY_WEATHER_UNITS, PreferenceFlags.PREF_WEATHER_UNIT_METRIC)
    override val weatherCity by StringPref(PreferenceFlags.KEY_WEATHER_CITY, PreferenceFlags.PREF_WEATHER_DEFAULT_CITY)
    override val showHidden by BooleanPref(PreferenceFlags.KEY_SHOW_HIDDEN, false)

    override fun migrationSrcHotseatCount(default: Int): Int {
        return getInt(PreferenceFlags.KEY_MIGRATION_SRC_HOTSEAT_COUNT, default)
    }

    override fun migrationSrcHotseatCount(value: Int, commit: Boolean) {
        setInt(PreferenceFlags.KEY_MIGRATION_SRC_HOTSEAT_COUNT, value, commit)
    }

    override fun migrationSrcWorkspaceSize(default: String): String {
        return getString(PreferenceFlags.KEY_MIGRATION_SRC_WORKSPACE_SIZE, default)
    }

    override fun migrationSrcWorkspaceSize(value: String, commit: Boolean) {
        setString(PreferenceFlags.KEY_MIGRATION_SRC_WORKSPACE_SIZE, value, commit)
    }

    override val allAppsOpacity by FloatPref(PreferenceFlags.KEY_PREF_ALL_APPS_OPACITY, 1f)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    override var restoreTaskPending by MutableBooleanPref(PreferenceFlags.RESTORE_TASK_PENDING, false)
    override var appsPendingInstalls by MutableStringSetPref(PreferenceFlags.APPS_PENDING_INSTALL, null)

    override fun userCreationTimeKeyExists(key: Long): Boolean {
        return sharedPrefs.contains(PreferenceFlags.KEY_USER_CREATION_TIME_KEY_PREFIX + key)
    }

    override fun userCreationTimeKey(key: Long): Long {
        return getLong(PreferenceFlags.KEY_USER_CREATION_TIME_KEY_PREFIX + key, 0)
    }

    override fun userCreationTimeKey(key: Long, value: Long, commit: Boolean) {
        setLong(PreferenceFlags.KEY_USER_CREATION_TIME_KEY_PREFIX + key, value, commit)
    }

    override val iconPackPackage by StringPref(PreferenceFlags.KEY_ICON_PACK_PACKAGE, "")
    override var emptyDatabaseCreated by MutableBooleanPref(PreferenceFlags.EMPTY_DATABASE_CREATED, false)

    override fun removeEmptyDatabaseCreated() {
        return remove(PreferenceFlags.EMPTY_DATABASE_CREATED, false)
    }

    override var wallpaperId by MutableIntPref(PreferenceFlags.KEY_WALLPAPER_ID_PREFERENCE, -1)

    override fun alternateIcon(key: String): String? {
        return sharedPrefs.getString(PreferenceFlags.KEY_ALTERNATE_ICON_PREFIX + key, null)
    }

    override fun numRows(default: String): String {
        return getString(PreferenceFlags.KEY_NUM_ROWS, default)
    }

    override fun numCols(default: String): String {
        return getString(PreferenceFlags.KEY_NUM_COLS, default)
    }

    override fun numColsDrawer(default: String): String {
        return getString(PreferenceFlags.KEY_NUM_COLS_DRAWER, default)
    }

    override val iconScaleSB by FloatPref(PreferenceFlags.KEY_ICON_SCALE_SB, 1f)
    override val iconTextScaleSB by FloatPref(PreferenceFlags.KEY_ICON_TEXT_SCALE_SB, 1f)

    override var extractedColorsPreference
            by MutableStringPref(PreferenceFlags.KEY_EXTRACTED_COLORS_PREFERENCE, ExtractedColors.VERSION.toString())

    override fun itemAlias(key: String, default: String): String {
        return getString(PreferenceFlags.KEY_ITEM_ALIAS_PREFIX + key, default)
    }

    override fun itemAlias(key: String, value: String, commit: Boolean) {
        setString(PreferenceFlags.KEY_ITEM_ALIAS_PREFIX + key, value, commit)
    }

    override val weatherApiKey by StringPref(PreferenceFlags.KEY_WEATHER_API_KEY, PreferenceFlags.PREF_DEFAULT_WEATHER_API_KEY)

    override fun getIntPref(key: String, default: Int): Int {
        return sharedPrefs.getInt(key, default)
    }

    override fun setIntPref(key: String, value: Int, commit: Boolean) {
        setInt(key, value, commit)
    }

    override fun removeOverrideIconShape() {
        remove(PreferenceFlags.KEY_OVERRIDE_ICON_SHAPE, false)
    }

    override var overrideIconShape by MutableStringPref(PreferenceFlags.KEY_OVERRIDE_ICON_SHAPE, "")
    override val backportAdaptiveIcons = Utilities.ATLEAST_NOUGAT
    override val weatherProvider by StringPref(PreferenceFlags.KEY_WEATHER_PROVIDER, PreferenceFlags.PREF_WEATHER_PROVIDER_AWARENESS)
    override var previousBuildNumber by MutableIntPref(PreferenceFlags.KEY_PREVIOUS_BUILD_NUMBER, 0)

    override var hiddenAppsSet: Set<String>
        get() {
            // We need to copy the set, as SharedPreferences doesn't return a copy of the Set object
            return HashSet<String>(sharedPrefs.getStringSet(PreferenceFlags.KEY_HIDDEN_APPS_SET, HashSet<String>()))
        }
        set(value) {
            sharedPrefs.edit().putStringSet(PreferenceFlags.KEY_HIDDEN_APPS_SET, value).apply()
        }

    override fun alternateIcon(key: String, alternateIcon: String, commit: Boolean) {
        commitOrApply(sharedPrefs.edit().putString(PreferenceFlags.KEY_ALTERNATE_ICON_PREFIX + key, alternateIcon), commit)
    }

    override fun removeAlternateIcon(key: String) {
        sharedPrefs.edit().remove(PreferenceFlags.KEY_ALTERNATE_ICON_PREFIX + key).apply()
    }

    override val blurMode by IntPref(PreferenceFlags.KEY_BLUR_MODE, (1 shl 30) - 1)
    override val blurRadius by FloatPref(PreferenceFlags.KEY_BLUR_RADIUS, 75f)

    override var appsViewShown by MutableBooleanPref(PreferenceFlags.APPS_VIEW_SHOWN, false)

    override val darkTheme: Boolean by BooleanPref(FeatureFlags.KEY_PREF_DARK_THEME, false)
    override val pulldownAction by StringPref(FeatureFlags.KEY_PREF_PULLDOWN_ACTION, "1")
    val pulldownNotis by BooleanPref(FeatureFlags.KEY_PREF_PULLDOWN_NOTIS, true)

    override val themeMode by IntPref(FeatureFlags.KEY_PREF_THEME_MODE, (1 shl 30) - 1)
    override val theme by StringPref(FeatureFlags.KEY_PREF_THEME, "0")
    override val enableVibrancy: Boolean
        get() = true
    override val useRoundSearchBar by BooleanPref(FeatureFlags.KEY_PREF_ROUND_SEARCH_BAR, false)
    override val enableBackportShortcuts by BooleanPref(FeatureFlags.KEY_PREF_ENABLE_BACKPORT_SHORTCUTS, false)
    override val showTopShadow by BooleanPref(FeatureFlags.KEY_PREF_SHOW_TOP_SHADOW, true)
    override val hideHotseat by BooleanPref(FeatureFlags.KEY_PREF_HIDE_HOTSEAT, false)
    override val enablePlanes by BooleanPref(FeatureFlags.KEY_PREF_PLANE, false)
    override val showWeather by BooleanPref(FeatureFlags.KEY_PREF_WEATHER, false)
    override val lockDesktop by BooleanPref(FeatureFlags.KEY_PREF_LOCK_DESKTOP, false)
    override val animatedClockIcon by BooleanPref(FeatureFlags.KEY_PREF_ANIMATED_CLOCK_ICON, false)
    override val enableSnowfall by BooleanPref(FeatureFlags.KEY_PREF_SNOWFALL, false)

    override val pinchToOverview by BooleanPref(FeatureFlags.KEY_PREF_PINCH_TO_OVERVIEW, true)
    override val centerWallpaper by BooleanPref(PreferenceFlags.KEY_CENTER_WALLPAPER, true)
    override val popupCardTheme = false
    override val lightStatusBar by BooleanPref(FeatureFlags.KEY_PREF_LIGHT_STATUS_BAR, false)
    override val hotseatShouldUseExtractedColors by BooleanPref(FeatureFlags.KEY_PREF_HOTSEAT_EXTRACTED_COLORS, true)
    override val hotseatShowArrow by BooleanPref(PreferenceFlags.KEY_PREF_HOTSEAT_SHOW_ARROW, true)
    override val hotseatShowPageIndicator by BooleanPref(PreferenceFlags.KEY_PREF_HOTSEAT_SHOW_PAGE_INDICATOR, true)
    override val twoRowDock by BooleanPref(PreferenceFlags.KEY_TWO_ROW_DOCK, false)

    override fun hotseatShouldUseExtractedColorsCache(default: Boolean): Boolean {
        return getBoolean(PreferenceFlags.KEY_HOTSEAT_SHOULD_USE_EXTRACTED_COLORS_CACHE, default)
    }

    override fun hotseatShouldUseExtractedColorsCache(value: Boolean, commit: Boolean) {
        setBoolean(PreferenceFlags.KEY_HOTSEAT_SHOULD_USE_EXTRACTED_COLORS_CACHE, value, commit)
    }

    override val keepScrollState by BooleanPref(FeatureFlags.KEY_PREF_KEEP_SCROLL_STATE, false)
    override val useFullWidthSearchBar by BooleanPref(FeatureFlags.KEY_FULL_WIDTH_SEARCHBAR, false)
    override val showVoiceSearchButton by BooleanPref(FeatureFlags.KEY_SHOW_VOICE_SEARCH_BUTTON, false)
    override val showPixelBar by BooleanPref(FeatureFlags.KEY_SHOW_PIXEL_BAR, true)
    override val homeOpensDrawer by BooleanPref(FeatureFlags.KEY_HOME_OPENS_DRAWER, true)
    override val usePixelIcons by BooleanPref(FeatureFlags.KEY_PREF_PIXEL_STYLE_ICONS, true)
    override val enableScreenRotation by BooleanPref(FeatureFlags.KEY_PREF_ENABLE_SCREEN_ROTATION, false)
    override val hideAppLabels by BooleanPref(FeatureFlags.KEY_PREF_HIDE_APP_LABELS, false)
    override val hideAllAppsAppLabels by BooleanPref(PreferenceFlags.KEY_PREF_HIDE_ALL_APPS_APP_LABELS, false)
    override val allowFullWidthWidgets by BooleanPref(FeatureFlags.KEY_PREF_FULL_WIDTH_WIDGETS, false)
    override val showGoogleNowTab by BooleanPref(FeatureFlags.KEY_PREF_SHOW_NOW_TAB, !BuildConfig.ENABLE_LAWNFEED)
    override val transparentHotseat by BooleanPref(FeatureFlags.KEY_PREF_TRANSPARENT_HOTSEAT, false)
    override val enableDynamicUi by BooleanPref(FeatureFlags.KEY_PREF_ENABLE_DYNAMIC_UI, false)
    override val enableBlur by BooleanPref(FeatureFlags.KEY_PREF_ENABLE_BLUR, false)
    override fun enableBlur(enable: Boolean) {
        sharedPrefs.edit()
                .putBoolean(FeatureFlags.KEY_PREF_ENABLE_BLUR, enable)
                .apply()
    }
    override val useWhiteGoogleIcon by BooleanPref(FeatureFlags.KEY_PREF_WHITE_GOOGLE_ICON, false)
    override val ayyMatey by BooleanPref(PreferenceFlags.KEY_AYY_MATEY, false)
    override fun migrateThemePref(context: Context) {
        val darkTheme = PreferenceProvider.getPreferences(context).darkTheme
        if (darkTheme) {
            sharedPrefs.edit()
                    .remove(FeatureFlags.KEY_PREF_DARK_THEME)
                    .putString(FeatureFlags.KEY_PREF_THEME, "1")
                    .apply()
        }
    }

    override fun migratePullDownPref(context: Context) {
        val pulldownNotis = pulldownNotis
        if (!pulldownNotis) {
            sharedPrefs.edit()
                    .remove(FeatureFlags.KEY_PREF_PULLDOWN_NOTIS)
                    .putString(FeatureFlags.KEY_PREF_PULLDOWN_ACTION, "0")
                    .apply()
        }
    }

    // ----------------
    // Helper functions and class
    // ----------------

    private val sharedPrefs: SharedPreferences = getSharedPrefs()

    private fun setBoolean(pref: String, value: Boolean, commit: Boolean) {
        commitOrApply(sharedPrefs.edit().putBoolean(pref, value), commit)
    }

    private fun getBoolean(pref: String, default: Boolean): Boolean {
        return sharedPrefs.getBoolean(pref, default)
    }

    private fun setString(pref: String, value: String, commit: Boolean) {
        commitOrApply(sharedPrefs.edit().putString(pref, value), commit)
    }

    private fun getString(pref: String, default: String): String {
        return sharedPrefs.getString(pref, default)
    }

    private fun setInt(pref: String, value: Int, commit: Boolean) {
        commitOrApply(sharedPrefs.edit().putInt(pref, value), commit)
    }

    private fun getInt(pref: String, default: Int): Int {
        return sharedPrefs.getInt(pref, default)
    }

    private fun setFloat(pref: String, value: Float, commit: Boolean) {
        commitOrApply(sharedPrefs.edit().putFloat(pref, value), commit)
    }

    private fun getFloat(pref: String, default: Float): Float {
        return sharedPrefs.getFloat(pref, default)
    }

    private fun setLong(pref: String, value: Long, commit: Boolean) {
        commitOrApply(sharedPrefs.edit().putLong(pref, value), commit)
    }

    private fun getLong(pref: String, default: Long): Long {
        return sharedPrefs.getLong(pref, default)
    }

    private fun remove(pref: String, commit: Boolean) {
        return commitOrApply(sharedPrefs.edit().remove(pref), commit)
    }

    fun commitOrApply(editor: SharedPreferences.Editor, commit: Boolean) {
        if (commit) {
            editor.commit()
        } else {
            editor.apply()
        }
    }

    var blockingEditing = false
    var bulkEditing = false
    var editor: SharedPreferences.Editor? = null

    override fun beginBlockingEdit() {
        blockingEditing = true
    }

    override fun endBlockingEdit() {
        blockingEditing = false
    }

    @SuppressLint("CommitPrefEdits")
    fun beginBulkEdit() {
        bulkEditing = true
        editor = sharedPrefs.edit()
    }

    fun endBulkEdit() {
        bulkEditing = false
        commitOrApply(editor!!, blockingEditing)
        editor = null
    }

    private inner class MutableStringPref(key: String? = null, defaultValue: String = "") :
            StringPref(key, defaultValue), MutablePrefDelegate<String> {
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            edit { putString(key ?: property.name, value) }
        }
    }

    private inner open class StringPref(key: String? = null, defaultValue: String = "") :
            PrefDelegate<String>(key, defaultValue) {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String = sharedPrefs.getString(key ?: property.name, defaultValue)
    }

    private inner class MutableStringSetPref(key: String? = null, defaultValue: Set<String>? = null) :
            StringSetPref(key, defaultValue), MutablePrefDelegate<Set<String>?> {
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Set<String>?) {
            edit { putStringSet(key ?: property.name, value) }
        }
    }

    private inner open class StringSetPref(key: String? = null, defaultValue: Set<String>? = null) :
            PrefDelegate<Set<String>?>(key, defaultValue) {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Set<String>? = sharedPrefs.getStringSet(key ?: property.name, defaultValue)
    }

    private inner class MutableIntPref(key: String? = null, defaultValue: Int = 0) :
            IntPref(key, defaultValue), MutablePrefDelegate<Int> {
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            edit { putInt(key ?: property.name, value) }
        }
    }

    private inner open class IntPref(key: String? = null, defaultValue: Int = 0) :
            PrefDelegate<Int>(key, defaultValue) {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int = sharedPrefs.getInt(key ?: property.name, defaultValue)
    }

    private inner class MutableFloatPref(key: String? = null, defaultValue: Float = 0f) :
            FloatPref(key, defaultValue), MutablePrefDelegate<Float> {
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
            edit { putFloat(key ?: property.name, value) }
        }
    }

    private inner open class FloatPref(key: String? = null, defaultValue: Float = 0f) :
            PrefDelegate<Float>(key, defaultValue) {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Float = sharedPrefs.getFloat(key ?: property.name, defaultValue)
    }

    private inner class MutableBooleanPref(key: String? = null, defaultValue: Boolean = false) :
            BooleanPref(key, defaultValue), MutablePrefDelegate<Boolean> {
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            edit { putBoolean(key ?: property.name, value) }
        }
    }

    private inner open class BooleanPref(key: String? = null, defaultValue: Boolean = false) :
            PrefDelegate<Boolean>(key, defaultValue) {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = sharedPrefs.getBoolean(key ?: property.name, defaultValue)
    }

    private interface MutablePrefDelegate<T> {
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T)
    }

    fun getSharedPrefs() : SharedPreferences {
        val dir = context.cacheDir.parent
        val oldFile = File(dir, "shared_prefs/" + LauncherFiles.OLD_SHARED_PREFERENCES_KEY + ".xml")
        val newFile = File(dir, "shared_prefs/" + LauncherFiles.SHARED_PREFERENCES_KEY + ".xml")
        if (oldFile.exists() && !newFile.exists()) {
            oldFile.renameTo(newFile)
            oldFile.delete()
        }
        return context.applicationContext.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
    }

    private abstract inner class PrefDelegate<T>(val key: String?, val defaultValue: T) {
        abstract operator fun getValue(thisRef: Any?, property: KProperty<*>): T

        protected inline fun edit(body: SharedPreferences.Editor.() -> Unit) {
            @SuppressLint("CommitPrefEdits")
            val editor = if (bulkEditing) editor!! else sharedPrefs.edit()
            body(editor)
            if (!bulkEditing)
                commitOrApply(editor, blockingEditing)
        }
    }

}
