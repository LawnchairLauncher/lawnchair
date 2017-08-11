package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.content.SharedPreferences

interface IPreferenceProvider {

    // -------------------
    // SORTED by Feature
    // -------------------

    // -------------------
    // 1) App Theme
    // -------------------

    fun theme(): String
    fun theme(value: String, commit: Boolean = false)
    fun darkTheme(): Boolean
    fun darkTheme(value: Boolean, commit: Boolean = false)
    fun themeMode(): Int
    fun themeMode(value: Int, commit: Boolean = false)

    fun migrateThemePref(context: Context)
    fun migratePullDownPref(context: Context)

    // -------------------
    // 2) All Apps Drawer
    // -------------------

    fun allAppsOpacity(): Float
    fun allAppsOpacity(value: Float, commit: Boolean = false)
    /*
     * defines if hidden apps should be shown in drawer for changing their hidden state
     */
    fun showHidden(): Boolean
    fun showHidden(value: Boolean, commit: Boolean = false)
    fun allAppsIconScale(): Float
    fun allAppsIconScale(value: Float, commit: Boolean = false)
    fun alllAppsIconTextScale(): Float
    fun alllAppsIconTextScale(value: Float, commit: Boolean = false)
    fun useCustomAllAppsTextColor(context: Context): Boolean
    fun useCustomAllAppsTextColor(value: Boolean, commit: Boolean)

    // -------------------
    // 3) Desktop
    // -------------------

    /*
     * defines if a pinch gesture opens the desktop edit page
     */
    fun pinchToOverview(): Boolean
    fun pinchToOverview(value: Boolean, commit: Boolean = false)

    // -------------------
    // 4) Weather
    // -------------------

    fun weatherProvider(): String
    fun weatherProvider(value: String, commit: Boolean = false)
    fun weatherApiKey(): String
    fun weatherApiKey(value: String, commit: Boolean = false)
    fun weatherCity(): String
    fun weatherCity(value: String, commit: Boolean = false)
    fun weatherUnit(): String
    fun weatherUnit(value: String, commit: Boolean = false)

    // --------------
    // Unsorted...
    // --------------

    // -----------------
    // FEATURES
    // -----------------

    // When enabled the status bar may show dark icons based on the top of the wallpaper.
    fun lightStatusBar(default: Boolean): Boolean
    fun lightStatusBar(value: Boolean, commit: Boolean = false)
    fun hotseatShouldUseExtractedColors(default: Boolean): Boolean
    fun hotseatShouldUseExtractedColors(value: Boolean, commit: Boolean = false)
    fun hotseatShouldUseExtractedColorsCache(default: Boolean): Boolean
    fun hotseatShouldUseExtractedColorsCache(value: Boolean, commit: Boolean = false)
    fun lightStatusBarKeyCache(default: Boolean): Boolean
    fun lightStatusBarKeyCache(value: Boolean, commit: Boolean = false)
    fun enableHapticFeedback(): Boolean
    fun enableHapticFeedback(value: Boolean, commit: Boolean = false)
    fun keepScrollState(): Boolean
    fun keepScrollState(value: Boolean, commit: Boolean = false)
    fun useFullWidthSearchbar(): Boolean
    fun useFullWidthSearchbar(value: Boolean, commit: Boolean = false)
    fun showVoiceSearchButton(): Boolean
    fun showVoiceSearchButton(value: Boolean, commit: Boolean = false)
    fun showPixelBar(): Boolean
    fun showPixelBar(value: Boolean, commit: Boolean = false)
    fun homeOpensDrawer(): Boolean
    fun homeOpensDrawer(value: Boolean, commit: Boolean = false)
    fun usePixelIcons(): Boolean
    fun usePixelIcons(value: Boolean, commit: Boolean = false)
    fun enableScreenRotation(): Boolean
    fun enableScreenRotation(value: Boolean, commit: Boolean = false)
    fun hideAppLabels(): Boolean
    fun hideAppLabels(value: Boolean, commit: Boolean = false)
    fun hideAllAppsAppLabels(): Boolean
    fun hideAllAppsAppLabels(value: Boolean, commit: Boolean = false)
    fun allowFullWidthWidgets(): Boolean
    fun allowFullWidthWidgets(value: Boolean, commit: Boolean = false)
    fun showGoogleNowTab(): Boolean
    fun showGoogleNowTab(value: Boolean, commit: Boolean = false)
    fun isTransparentHotseat(): Boolean
    fun isTransparentHotseat(value: Boolean, commit: Boolean = false)
    fun isDynamicUiEnabled(): Boolean
    fun isDynamicUiEnabled(value: Boolean, commit: Boolean = false)
    fun isBlurEnabled(): Boolean
    fun isBlurEnabled(value: Boolean, commit: Boolean = false)
    fun useWhiteGoogleIcon(): Boolean
    fun useWhiteGoogleIcon(value: Boolean, commit: Boolean = false)
    fun isVibrancyEnabled(): Boolean
    fun isVibrancyEnabled(value: Boolean, commit: Boolean = false)
    fun useRoundSearchBar(): Boolean
    fun useRoundSearchBar(value: Boolean, commit: Boolean = false)
    fun enableBackportShortcuts(): Boolean
    fun enableBackportShortcuts(value: Boolean, commit: Boolean = false)
    fun showTopShadow(): Boolean
    fun showTopShadow(value: Boolean, commit: Boolean = false)
    fun hideHotseat(): Boolean
    fun hideHotseat(value: Boolean, commit: Boolean = false)
    fun planes(): Boolean
    fun planes(value: Boolean, commit: Boolean = false)
    fun showWeather(): Boolean
    fun showWeather(value: Boolean, commit: Boolean = false)
    fun enableEditing(): Boolean
    fun enableEditing(value: Boolean, commit: Boolean = false)
    fun animatedClockIcon(): Boolean
    fun animatedClockIcon(value: Boolean, commit: Boolean = false)
    fun animateClockIconAlternativeClockApps(): Boolean

    fun pulldownAction(): String
    fun pulldownAction(value: String, commit: Boolean = false)
    fun pulldownNotis(): Boolean
    fun pulldownNotis(value: Boolean, commit: Boolean = false)

    // -----------------
    // PREFERENCES
    // -----------------

    fun blurRadius() : Float
    fun blurRadius(value: Float, commit: Boolean = false)
    fun blurMode() : Int
    fun blurMode(value: Int, commit: Boolean = false)
    fun labelColorHue(): String
    fun labelColorHue(value: String, commit: Boolean = false)
    fun labelColorVariation(): String
    fun labelColorVariation(value: String, commit: Boolean = false)
    fun alternateIcon(key: String): String?
    fun alternateIcon(key: String, alternateIcon: String, commit: Boolean = false)
    fun removeAlternateIcon(key: String)
    fun appVisibility(context: Context, key: String, visible: Boolean, commit: Boolean = false)
    fun appVisibility(context: Context, key: String): Boolean
    fun previousBuildNumber() : Int
    fun previousBuildNumber(value: Int, commit: Boolean = false)
    fun overrideIconShape(): String
    fun overrideIconShape(value: String, commit: Boolean = false)
    fun removeOverrideIconShape(commit: Boolean = false)
    fun itemAlias(key: String, default: String): String
    fun itemAlias(key: String, value: String, commit: Boolean = false)
    fun extractedColorsPreference(): String
    fun extractedColorsPreference(value: String, commit: Boolean = false)
    fun wallpaperId(): Int
    fun wallpaperId(value: Int, commit: Boolean = false)
    fun numRows(default: String): String
    fun numRows(value: String, commit: Boolean = false)
    fun numCols(default: String): String
    fun numCols(value: String, commit: Boolean = false)
    fun numColsDrawer(default: String): String
    fun numColsDrawer(value: String, commit: Boolean = false)
    fun numRowsDrawer(default: String): String
    fun numRowsDrawer(value: String, commit: Boolean = false)
    fun numHotseatIcons(default: String): String
    fun numHotseatIcons(value: String, commit: Boolean = false)
    fun hotseatIcons(default: String): String
    fun hotseatIcons(value: String, commit: Boolean = false)
    fun iconScaleSB(): Float
    fun iconScaleSB(value: Float, commit: Boolean = false)
    fun iconTextScaleSB(): Float
    fun iconTextScaleSB(value: Float, commit: Boolean = false)
    fun iconPackPackage(): String
    fun iconPackPackage(value: String, commit: Boolean = false)
    fun hotseatIconScale(): Float
    fun hotseatIconScale(value: Float, commit: Boolean = false)

    // -----------------
    // GENERAL - BITS
    // -----------------

    fun getIntPref(key: String, default: Int) : Int
    fun setIntPref(key: String, value: Int, commit: Boolean = false)

    // -----------------
    // STATES
    // -----------------

    fun requiresIconCacheReload(): Boolean
    fun requiresIconCacheReload(value: Boolean, commit: Boolean = false)
    fun emptyDatabaseCreated(): Boolean
    fun emptyDatabaseCreated(value: Boolean, commit: Boolean = false)
    fun removeEmptyDatabaseCreated()
    fun userCreationTimeKeyExists(key: Long): Boolean
    fun userCreationTimeKey(key: Long): Long
    fun userCreationTimeKey(key: Long, value: Long, commit: Boolean = false)
    fun appsPendingInstalls(): Set<String>?
    fun appsPendingInstalls(value:  Set<String>, commit: Boolean = false)
    fun restoreTaskPending(): Boolean
    fun restoreTaskPending(value: Boolean, commit: Boolean = false)
    fun migrationSrcWorkspaceSize(default: String): String
    fun migrationSrcWorkspaceSize(value: String, commit: Boolean = false)
    fun migrationSrcHotseatCount(default: Int): Int
    fun migrationSrcHotseatCount(value: Int, commit: Boolean = false)

    // -----------------
    // LAUNCHER
    // -----------------

    fun appsViewShown(): Boolean
    fun appsViewShown(value: Boolean, commit: Boolean = false)

    // -----------------
    // LISTENER
    // -----------------

    fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener)
    fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener)
}