package ch.deletescape.lawnchair.preferences

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.View
import ch.deletescape.lawnchair.Launcher
import ch.deletescape.lawnchair.LauncherFiles
import ch.deletescape.lawnchair.config.FeatureFlags
import ch.deletescape.lawnchair.config.PreferenceProvider
import ch.deletescape.lawnchair.dynamicui.ExtractedColors

open class PreferenceImpl : IPreferenceProvider {

    override fun showSettings(launcher: Launcher, view: View) {
        val intent = Intent(Intent.ACTION_APPLICATION_PREFERENCES)
                .setPackage(launcher.getPackageName())
        intent.sourceBounds = launcher.getViewBounds(view)
        launcher.startActivity(intent, launcher.getActivityLaunchOptions(view))
    }

    override fun numRowsDrawer(default: String): String {
        return getString(PreferenceFlags.KEY_PREF_NUM_ROWS_DRAWER, default)
    }

    override fun numRowsDrawer(value: String, commit: Boolean) {
        setString(PreferenceFlags.KEY_PREF_NUM_ROWS_DRAWER, value, commit)
    }

    override fun numHotseatIcons(default: String): String {
        return getString(PreferenceFlags.KEY_PREF_NUM_HOTSEAT_ICONS, default)
    }

    override fun numHotseatIcons(value: String, commit: Boolean) {
        setString(PreferenceFlags.KEY_PREF_NUM_HOTSEAT_ICONS, value, commit)
    }

    override fun hotseatIconScale(): Float {
        return getFloat(PreferenceFlags.KEY_PREF_HOTSEAT_ICON_SCALE, 1f)
    }

    override fun hotseatIconScale(value: Float, commit: Boolean) {
       setFloat(PreferenceFlags.KEY_PREF_HOTSEAT_ICON_SCALE, value, commit)
    }

    override fun allAppsIconScale(): Float {
        return getFloat(PreferenceFlags.KEY_PREF_ALL_APPS_ICON_SCALE, 1f)
    }

    override fun allAppsIconScale(value: Float, commit: Boolean) {
        setFloat(PreferenceFlags.KEY_PREF_ALL_APPS_ICON_SCALE, value, commit)
    }

    override fun alllAppsIconTextScale(): Float {
        return getFloat(PreferenceFlags.KEY_PREF_ALL_APPS_ICON_TEXT_SCALE, 1f)
    }

    override fun alllAppsIconTextScale(value: Float, commit: Boolean) {
        setFloat(PreferenceFlags.KEY_PREF_ALL_APPS_ICON_TEXT_SCALE, value, commit)
    }

    override fun useCustomAllAppsTextColor(context: Context): Boolean {
        return getBoolean(PreferenceFlags.KEY_PREF_DRAWER_CUSTOM_LABEL_COLOR, false)
    }

    override fun useCustomAllAppsTextColor(value: Boolean, commit: Boolean) {
        setBoolean(PreferenceFlags.KEY_PREF_DRAWER_CUSTOM_LABEL_COLOR, value, commit)
    }


    override fun hideAllAppsAppLabels(): Boolean {
        return getBoolean(PreferenceFlags.KEY_PREF_HIDE_ALL_APPS_APP_LABELS, false)
    }

    override fun hideAllAppsAppLabels(value: Boolean, commit: Boolean) {
        setBoolean(PreferenceFlags.KEY_PREF_HIDE_ALL_APPS_APP_LABELS, value, commit)
    }

    override fun animateClockIconAlternativeClockApps(): Boolean {
        return false
    }

    override fun lightStatusBarKeyCache(default: Boolean): Boolean {
        return getBoolean(PreferenceFlags.KEY_LIGHT_STATUS_BAR, default)
    }

    override fun lightStatusBarKeyCache(value: Boolean, commit: Boolean) {
        setBoolean(PreferenceFlags.KEY_LIGHT_STATUS_BAR, value, commit)
    }

    override fun weatherUnit(): String {
        return getString(PreferenceFlags.KEY_WEATHER_UNITS, PreferenceFlags.PREF_WEATHER_UNIT_METRIC)
    }

    override fun weatherUnit(value: String, commit: Boolean) {
        setString(PreferenceFlags.KEY_WEATHER_UNITS, value, commit)
    }

    override fun weatherCity(): String {
        return getString(PreferenceFlags.KEY_WEATHER_CITY, PreferenceFlags.PREF_WEATHER_DEFAULT_CITY)
    }

    override fun weatherCity(value: String, commit: Boolean) {
        setString(PreferenceFlags.KEY_WEATHER_CITY, value, commit)
    }

    override fun showHidden(): Boolean {
        return getBoolean(PreferenceFlags.KEY_SHOW_HIDDEN, false)
    }

    override fun showHidden(value: Boolean, commit: Boolean) {
        setBoolean(PreferenceFlags.KEY_SHOW_HIDDEN, value, commit)
    }

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

    override fun allAppsOpacity(): Float {
        return getFloat(PreferenceFlags.KEY_PREF_ALL_APPS_OPACITY, 1f)
    }

    override fun allAppsOpacity(value: Float, commit: Boolean) {
        setFloat(PreferenceFlags.KEY_PREF_ALL_APPS_OPACITY, value, commit)
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        mSharedPref.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        mSharedPref.unregisterOnSharedPreferenceChangeListener(listener)
    }

    override fun restoreTaskPending(): Boolean {
        return getBoolean(PreferenceFlags.RESTORE_TASK_PENDING, false)
    }

    override fun restoreTaskPending(value: Boolean, commit: Boolean) {
        setBoolean(PreferenceFlags.RESTORE_TASK_PENDING, value, commit)
    }

    override fun appsPendingInstalls(): Set<String>? {
        return mSharedPref.getStringSet(PreferenceFlags.APPS_PENDING_INSTALL, null)
    }

    override fun appsPendingInstalls(value: Set<String>, commit: Boolean) {
        commitOrApply(mSharedPref.edit().putStringSet(PreferenceFlags.APPS_PENDING_INSTALL, value), commit)
    }

    override fun userCreationTimeKeyExists(key: Long): Boolean {
        return mSharedPref.contains(PreferenceFlags.KEY_USER_CREATION_TIME_KEY_PREFIX + key)
    }

    override fun userCreationTimeKey(key: Long): Long {
        return getLong(PreferenceFlags.KEY_USER_CREATION_TIME_KEY_PREFIX + key, 0)
    }

    override fun userCreationTimeKey(key: Long, value: Long, commit: Boolean) {
        setLong(PreferenceFlags.KEY_USER_CREATION_TIME_KEY_PREFIX + key, value, commit)
    }

    override fun iconPackPackage(): String {
        return getString(PreferenceFlags.KEY_ICON_PACK_PACKAGE, "")
    }

    override fun iconPackPackage(value: String, commit: Boolean) {
        setString(PreferenceFlags.KEY_ICON_PACK_PACKAGE, value, commit)
    }

    override fun removeEmptyDatabaseCreated() {
        return remove(PreferenceFlags.EMPTY_DATABASE_CREATED, false)
    }

    override fun emptyDatabaseCreated(): Boolean {
        return getBoolean(PreferenceFlags.EMPTY_DATABASE_CREATED, false)
    }

    override fun emptyDatabaseCreated(value: Boolean, commit: Boolean) {
        setBoolean(PreferenceFlags.EMPTY_DATABASE_CREATED, value, commit)
    }

    override fun wallpaperId(): Int {
        return getInt(PreferenceFlags.KEY_WALLPAPER_ID_PREFERENCE, -1)
    }

    override fun wallpaperId(value: Int, commit: Boolean) {
        setInt(PreferenceFlags.KEY_WALLPAPER_ID_PREFERENCE, value, commit)
    }

    override fun alternateIcon(key: String): String? {
        return mSharedPref.getString(PreferenceFlags.KEY_ALTERNATE_ICON_PREFIX + key, null)
    }

    override fun numRows(default: String): String {
        return getString(PreferenceFlags.KEY_NUM_ROWS, default)
    }

    override fun numRows(value: String, commit: Boolean) {
        setString(PreferenceFlags.KEY_NUM_ROWS, value, commit)
    }

    override fun numCols(default: String): String {
        return getString(PreferenceFlags.KEY_NUM_COLS, default)
    }

    override fun numCols(value: String, commit: Boolean) {
        setString(PreferenceFlags.KEY_NUM_COLS, value, commit)
    }

    override fun numColsDrawer(default: String): String {
        return getString(PreferenceFlags.KEY_NUM_COLS_DRAWER, default)
    }

    override fun numColsDrawer(value: String, commit: Boolean) {
        setString(PreferenceFlags.KEY_NUM_COLS_DRAWER, value, commit)
    }

    override fun hotseatIcons(default: String): String {
        return getString(PreferenceFlags.KEY_HOTSEAT_ICONS, default)
    }

    override fun hotseatIcons(value: String, commit: Boolean) {
        setString(PreferenceFlags.KEY_HOTSEAT_ICONS, value, commit)
    }

    override fun iconScaleSB(): Float {
        return getFloat(PreferenceFlags.KEY_ICON_SCALE_SB, 1f)
    }

    override fun iconScaleSB(value: Float, commit: Boolean) {
        setFloat(PreferenceFlags.KEY_ICON_SCALE_SB, value, commit)
    }

    override fun iconTextScaleSB(): Float {
        return getFloat(PreferenceFlags.KEY_ICON_TEXT_SCALE_SB, 1f)
    }

    override fun iconTextScaleSB(value: Float, commit: Boolean) {
        setFloat(PreferenceFlags.KEY_ICON_TEXT_SCALE_SB, value, commit)
    }

    override fun extractedColorsPreference(): String {
        return getString(PreferenceFlags.KEY_EXTRACTED_COLORS_PREFERENCE, ExtractedColors.VERSION.toString() + "")
    }

    override fun extractedColorsPreference(value: String, commit: Boolean) {
        setString(PreferenceFlags.KEY_EXTRACTED_COLORS_PREFERENCE, value, commit)
    }

    override fun itemAlias(key: String, default: String): String {
        return getString(PreferenceFlags.KEY_ITEM_ALIAS_PREFIX + key, default)
    }

    override fun itemAlias(key: String, value: String, commit: Boolean) {
        setString(PreferenceFlags.KEY_ITEM_ALIAS_PREFIX + key, value, commit)
    }

    override fun weatherApiKey(): String {
        return getString(PreferenceFlags.KEY_WEATHER_API_KEY, PreferenceFlags.PREF_DEFAULT_WEATHER_API_KEY)
    }

    override fun weatherApiKey(value: String, commit: Boolean) {
        setString(PreferenceFlags.KEY_WEATHER_API_KEY, value, commit)
    }

    override fun getIntPref(key: String, default: Int): Int {
        return mSharedPref.getInt(key, default)
    }

    override fun setIntPref(key: String, value: Int, commit: Boolean) {
        setInt(key, value, commit)
    }

    override fun removeOverrideIconShape(commit: Boolean) {
        remove(PreferenceFlags.KEY_OVERRIDE_ICON_SHAPE, commit)
    }

    override fun overrideIconShape(): String {
        return getString(PreferenceFlags.KEY_OVERRIDE_ICON_SHAPE, "")
    }

    override fun overrideIconShape(value: String, commit: Boolean) {
        setString(PreferenceFlags.KEY_OVERRIDE_ICON_SHAPE, value, commit)
    }

    override fun weatherProvider(): String {
        return getString(PreferenceFlags.KEY_WEATHER_PROVIDER, PreferenceFlags.PREF_WEATHER_PROVIDER_AWARENESS)
    }

    override fun weatherProvider(value: String, commit: Boolean) {
        setString(PreferenceFlags.KEY_WEATHER_PROVIDER, value, commit)
    }

    override fun previousBuildNumber(): Int {
        return getInt(PreferenceFlags.KEY_PREVIOUS_BUILD_NUMBER, 0)
    }

    override fun previousBuildNumber(value: Int, commit: Boolean) {
        commitOrApply(mSharedPref.edit().putInt(PreferenceFlags.KEY_PREVIOUS_BUILD_NUMBER, value), commit)
    }

    override fun appVisibility(context: Context, key: String, visible: Boolean, commit: Boolean) {
        commitOrApply(mSharedPref.edit().putBoolean(PreferenceFlags.KEY_APP_VISIBILITY_PREFIX + key, visible), commit)
    }

    override fun appVisibility(context: Context, key: String): Boolean {
        return mSharedPref.getBoolean(PreferenceFlags.KEY_APP_VISIBILITY_PREFIX + key, true)
    }

    override fun alternateIcon(key: String, alternateIcon: String, commit: Boolean) {
        commitOrApply(mSharedPref.edit().putString(PreferenceFlags.KEY_ALTERNATE_ICON_PREFIX + key, alternateIcon), commit)
    }

    override fun removeAlternateIcon(key: String) {
        mSharedPref.edit().remove(PreferenceFlags.KEY_ALTERNATE_ICON_PREFIX + key).apply()
    }

    override fun labelColorHue(): String {
        return getString(PreferenceFlags.KEY_WS_LABEL_COLOR_HUE, "-3")
    }

    override fun labelColorHue(value: String, commit: Boolean) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun labelColorVariation(): String {
        return getString(PreferenceFlags.KEY_WS_LABEL_COLOR_VARIATION, "5")
    }

    override fun labelColorVariation(value: String, commit: Boolean) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun blurMode(): Int {
        return getInt(PreferenceFlags.KEY_BLUR_MODE, (1 shl 30) - 1)
    }

    override fun blurMode(value: Int, commit: Boolean) {
        setInt(PreferenceFlags.KEY_BLUR_MODE, value, commit)
    }

    override fun blurRadius(value: Float, commit: Boolean) {
        setFloat(PreferenceFlags.KEY_BLUR_RADIUS, value, false)
    }

    override fun blurRadius(): Float {
        return getFloat(PreferenceFlags.KEY_BLUR_RADIUS, 75f)
    }

    override fun appsViewShown(): Boolean {
        return getBoolean(PreferenceFlags.APPS_VIEW_SHOWN, false)
    }

    override fun appsViewShown(value: Boolean, commit: Boolean) {
        setBoolean(PreferenceFlags.APPS_VIEW_SHOWN, value, commit)
    }

    override fun requiresIconCacheReload(): Boolean {
        return getBoolean(PreferenceFlags.KEY_REQUIRES_ICON_CACHE_RELOAD, true)
    }

    override fun requiresIconCacheReload(value: Boolean, commit: Boolean) {
        setBoolean(PreferenceFlags.KEY_REQUIRES_ICON_CACHE_RELOAD, value, commit)
    }

    override fun darkTheme(): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_DARK_THEME, false)
    }

    override fun darkTheme(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_DARK_THEME, value, commit)
    }

    override fun pulldownAction(): String {
        return getString(FeatureFlags.KEY_PREF_PULLDOWN_ACTION, "1")
    }

    override fun pulldownAction(value: String, commit: Boolean) {
        setString(FeatureFlags.KEY_PREF_PULLDOWN_ACTION, value, commit)
    }

    override fun pulldownNotis(): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_PULLDOWN_NOTIS, true)
    }

    override fun pulldownNotis(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_PULLDOWN_NOTIS, value, commit)
    }

    override fun themeMode(): Int {
        return getInt(FeatureFlags.KEY_PREF_THEME_MODE, (1 shl 30) - 1)
    }

    override fun themeMode(value: Int, commit: Boolean) {
        setInt(FeatureFlags.KEY_PREF_THEME_MODE, value, commit)
    }

    override fun theme(): String {
        return getString(FeatureFlags.KEY_PREF_THEME, "0")
    }

    override fun theme(value: String, commit: Boolean) {
        setString(FeatureFlags.KEY_PREF_THEME, value, commit)
    }

    override fun enableHapticFeedback(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_HAPTIC_FEEDBACK, value, commit)
    }

    override fun keepScrollState(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_KEEP_SCROLL_STATE, value, commit)
    }

    override fun useFullWidthSearchbar(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_FULL_WIDTH_WIDGETS, value, commit)
    }

    override fun showVoiceSearchButton(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_SHOW_VOICE_SEARCH_BUTTON, value, commit)
    }

    override fun showPixelBar(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_SHOW_PIXEL_BAR, value, commit)
    }

    override fun homeOpensDrawer(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_HOME_OPENS_DRAWER, value, commit)
    }

    override fun usePixelIcons(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_PIXEL_STYLE_ICONS, value, commit)
    }

    override fun enableScreenRotation(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_ENABLE_SCREEN_ROTATION, value, commit)
    }

    override fun hideAppLabels(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_HIDE_APP_LABELS, value, commit)
    }

    override fun allowFullWidthWidgets(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_FULL_WIDTH_WIDGETS, value, commit)
    }

    override fun showGoogleNowTab(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_SHOW_NOW_TAB, value, commit)
    }

    override fun isTransparentHotseat(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_TRANSPARENT_HOTSEAT, value, commit)
    }

    override fun isDynamicUiEnabled(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_ENABLE_DYNAMIC_UI, value, commit)
    }

    override fun isBlurEnabled(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_ENABLE_BLUR, value, commit)
    }

    override fun useWhiteGoogleIcon(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_WHITE_GOOGLE_ICON, value, commit)
    }

    override fun isVibrancyEnabled(): Boolean {
        return true
    }

    override fun isVibrancyEnabled(value: Boolean, commit: Boolean) {
        TODO("not implemented")
    }

    override fun useRoundSearchBar(): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_ROUND_SEARCH_BAR, false)
    }

    override fun useRoundSearchBar(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_ROUND_SEARCH_BAR, value, commit)
    }

    override fun enableBackportShortcuts(): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_ENABLE_BACKPORT_SHORTCUTS, false)
    }

    override fun enableBackportShortcuts(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_ENABLE_BACKPORT_SHORTCUTS, value, commit)
    }

    override fun showTopShadow(): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_SHOW_TOP_SHADOW, true)
    }

    override fun showTopShadow(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_SHOW_TOP_SHADOW, value, commit)
    }

    override fun hideHotseat(): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_HIDE_HOTSEAT, false)
    }

    override fun hideHotseat(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_HIDE_HOTSEAT, value, commit)
    }

    override fun planes(): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_PLANE, false)
    }

    override fun planes(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_PLANE, value, commit)
    }

    override fun showWeather(): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_WEATHER, false)
    }

    override fun showWeather(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_WEATHER, value, commit)
    }

    override fun enableEditing(): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_ENABLE_EDITING, true)
    }

    override fun enableEditing(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_ENABLE_EDITING, value, commit)
    }

    override fun animatedClockIcon(): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_ANIMATED_CLOCK_ICON, false)
    }

    override fun animatedClockIcon(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_ANIMATED_CLOCK_ICON, value, commit)
    }

    override fun pinchToOverview(): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_PINCH_TO_OVERVIEW, true)
    }

    override fun pinchToOverview(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_PINCH_TO_OVERVIEW, value, commit)
    }

    override fun lightStatusBar(default: Boolean): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_LIGHT_STATUS_BAR, default)
    }

    override fun lightStatusBar(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_LIGHT_STATUS_BAR, value, commit)
    }

    override fun hotseatShouldUseExtractedColors(default: Boolean): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_HOTSEAT_EXTRACTED_COLORS, default)
    }

    override fun hotseatShouldUseExtractedColors(value: Boolean, commit: Boolean) {
        setBoolean(FeatureFlags.KEY_PREF_HOTSEAT_EXTRACTED_COLORS, value, commit)
    }

    override fun hotseatShouldUseExtractedColorsCache(default: Boolean): Boolean {
        return getBoolean(PreferenceFlags.KEY_HOTSEAT_SHOULD_USE_EXTRACTED_COLORS_CACHE, default)
    }

    override fun hotseatShouldUseExtractedColorsCache(value: Boolean, commit: Boolean) {
        setBoolean(PreferenceFlags.KEY_HOTSEAT_SHOULD_USE_EXTRACTED_COLORS_CACHE, value, commit)
    }

    override fun enableHapticFeedback(): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_HAPTIC_FEEDBACK, false)
    }

    override fun keepScrollState(): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_KEEP_SCROLL_STATE, false)
    }

    override fun useFullWidthSearchbar(): Boolean {
        return getBoolean(FeatureFlags.KEY_FULL_WIDTH_SEARCHBAR, false)
    }

    override fun showVoiceSearchButton(): Boolean {
        return getBoolean(FeatureFlags.KEY_SHOW_VOICE_SEARCH_BUTTON, false)
    }

    override fun showPixelBar(): Boolean {
        return getBoolean(FeatureFlags.KEY_SHOW_PIXEL_BAR, true)
    }

    override fun homeOpensDrawer(): Boolean {
        return getBoolean(FeatureFlags.KEY_HOME_OPENS_DRAWER, true)
    }

    override fun usePixelIcons(): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_PIXEL_STYLE_ICONS, true)
    }

    override fun enableScreenRotation(): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_ENABLE_SCREEN_ROTATION, false)
    }

    override fun hideAppLabels(): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_HIDE_APP_LABELS, false)
    }

    override fun allowFullWidthWidgets(): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_FULL_WIDTH_WIDGETS, false)
    }

    override fun showGoogleNowTab(): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_SHOW_NOW_TAB, true)
    }

    override fun isTransparentHotseat(): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_TRANSPARENT_HOTSEAT, false)
    }

    override fun isDynamicUiEnabled(): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_ENABLE_DYNAMIC_UI, false)
    }

    override fun isBlurEnabled(): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_ENABLE_BLUR, false)
    }

    override fun useWhiteGoogleIcon(): Boolean {
        return getBoolean(FeatureFlags.KEY_PREF_WHITE_GOOGLE_ICON, false)
    }

    override fun migrateThemePref(context: Context) {
        val darkTheme = PreferenceProvider.getPreferences(context).darkTheme()
        if (darkTheme) {
            mSharedPref.edit()
                    .remove(FeatureFlags.KEY_PREF_DARK_THEME)
                    .putString(FeatureFlags.KEY_PREF_THEME, "1")
                    .apply()
        }
    }

    override fun migratePullDownPref(context: Context) {
        val pulldownNotis = PreferenceProvider.getPreferences(context).pulldownNotis()
        if (!pulldownNotis) {
            mSharedPref.edit()
                    .remove(FeatureFlags.KEY_PREF_PULLDOWN_NOTIS)
                    .putString(FeatureFlags.KEY_PREF_PULLDOWN_ACTION, "0")
                    .apply()
        }
    }

    // ----------------
    // Helper functions and class
    // ----------------

    private var mSharedPref: SharedPreferences

    constructor(context: Context) {
        mSharedPref = context.getApplicationContext().getSharedPreferences(
                LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
    }

    private fun setBoolean(pref: String, value: Boolean, commit: Boolean) {
        commitOrApply(mSharedPref.edit().putBoolean(pref, value), commit)
    }

    private fun getBoolean(pref: String, default: Boolean): Boolean {
        return mSharedPref.getBoolean(pref, default)
    }

    private fun setString(pref: String, value: String, commit: Boolean) {
        commitOrApply(mSharedPref.edit().putString(pref, value), commit)
    }

    private fun getString(pref: String, default: String): String {
        return mSharedPref.getString(pref, default)
    }

    private fun setInt(pref: String, value: Int, commit: Boolean) {
        commitOrApply(mSharedPref.edit().putInt(pref, value), commit)
    }

    private fun getInt(pref: String, default: Int): Int {
        return mSharedPref.getInt(pref, default)
    }

    private fun setFloat(pref: String, value: Float, commit: Boolean) {
        commitOrApply(mSharedPref.edit().putFloat(pref, value), commit)
    }

    private fun getFloat(pref: String, default: Float): Float {
        return mSharedPref.getFloat(pref, default)
    }

    private fun setLong(pref: String, value: Long, commit: Boolean) {
        commitOrApply(mSharedPref.edit().putLong(pref, value), commit)
    }

    private fun getLong(pref: String, default: Long): Long {
        return mSharedPref.getLong(pref, default)
    }

    private fun remove(pref: String, commit: Boolean) {
        return commitOrApply(mSharedPref.edit().remove(pref), commit)
    }

    private fun commitOrApply(editor: SharedPreferences.Editor, commit: Boolean) {
        if (commit) {
            editor.commit()
        } else {
            editor.apply()
        }
    }
}