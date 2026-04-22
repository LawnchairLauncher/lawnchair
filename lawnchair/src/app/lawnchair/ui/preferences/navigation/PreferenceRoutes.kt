package app.lawnchair.ui.preferences.navigation

import app.lawnchair.ui.preferences.components.search.SearchProviderId
import app.lawnchair.ui.preferences.destinations.SearchRoute
import app.lawnchair.ui.preferences.destinations.ShapeRoute
import kotlinx.serialization.Serializable

private const val URI = "lawnchair://settings"

/**
 * Represents a route in the Lawnchair preferences navigation graph.
 *
 * This sealed interface is the base for all navigation destinations within the preferences.
 * Each implementing object or data class defines a specific screen or action.
 *
 * The `@Serializable` annotation indicates that this interface and its implementations
 * can be serialized, which is useful for state saving and deep linking.
 */
@Serializable
sealed interface PreferenceRoute

/**
 * determines whether this is one of the root routes shown in the preference dashboard
 */
@Serializable
sealed interface PreferenceRootRoute : PreferenceRoute

@Serializable
sealed interface PreferenceDeepLink {
    val deepLink: String
}

// Misc routes

@Serializable
data object Root : PreferenceRootRoute

@Serializable
data object Dummy : PreferenceRootRoute

// Top-level destinations
@Serializable
data object General : PreferenceRootRoute, PreferenceDeepLink {
    override val deepLink = "$URI/general"
}

@Serializable
data object HomeScreen : PreferenceRootRoute, PreferenceDeepLink {
    override val deepLink = "$URI/home-screen"
}

@Serializable
data object Dock : PreferenceRootRoute, PreferenceDeepLink {
    override val deepLink = "$URI/dock"
}

@Serializable
data object AppDrawer : PreferenceRootRoute, PreferenceDeepLink {
    override val deepLink = "$URI/app-drawer"
}

// technically the search screen, selectedId selects the default tab inside this
@Serializable
data class Search(val selectedId: SearchRoute = SearchRoute.DOCK_SEARCH) :
    PreferenceRootRoute,
    PreferenceDeepLink {
    override val deepLink = "$URI/search"
}

@Serializable
data object Folders : PreferenceRootRoute, PreferenceDeepLink {
    override val deepLink = "$URI/folders"
}

@Serializable
data object Quickstep : PreferenceRootRoute, PreferenceDeepLink {
    override val deepLink = "$URI/quickstep"
}

@Serializable
data object BackupAndRestore : PreferenceRootRoute, PreferenceDeepLink {
    override val deepLink = "$URI/backup-restore"
}

@Serializable
data object Gestures : PreferenceRootRoute, PreferenceDeepLink {
    override val deepLink = "$URI/gestures"
}

@Serializable
data object Smartspace : PreferenceRootRoute, PreferenceDeepLink {
    override val deepLink = "$URI/smartspace"
}

@Serializable
data object About : PreferenceRootRoute, PreferenceDeepLink {
    override val deepLink = "$URI/about"
}

@Serializable
data object ExperimentalFeatures : PreferenceRootRoute, PreferenceDeepLink {
    override val deepLink = "$URI/experimental-features"
}

@Serializable
data object DebugMenu : PreferenceRootRoute

@Serializable
data object FeatureFlags : PreferenceRoute

// General section routes
@Serializable
data class GeneralFontSelection(val prefKey: String) : PreferenceRoute

@Serializable
data object GeneralIconPack : PreferenceRoute, PreferenceDeepLink {
    override val deepLink = "$URI/general-iconpack"
}

@Serializable
data class GeneralIconShape(val selectedId: ShapeRoute = ShapeRoute.APP_SHAPE) : PreferenceRoute

@Serializable
data object GeneralCustomIconShapeCreator : PreferenceRoute, PreferenceDeepLink {
    override val deepLink = "$URI/general-icon-shape-creator"
}

// Home Screen section routes
@Serializable
data object HomeScreenGrid : PreferenceRoute, PreferenceDeepLink {
    override val deepLink = "$URI/home-screen-grid"
}

@Serializable
data object HomeScreenPopupEditor : PreferenceRoute, PreferenceDeepLink {
    override val deepLink = "$URI/home-screen-popup-editor"
}

// Dock section routes
@Serializable
data object DockSearchProvider : PreferenceRoute, PreferenceDeepLink {
    override val deepLink = "$URI/dock-search-provider"
}

// App Drawer section routes
@Serializable
data object AppDrawerHiddenApps : PreferenceRoute, PreferenceDeepLink {
    override val deepLink = "$URI/app-drawer-hidden-apps"
}

@Serializable
data object AppDrawerFolder : PreferenceRoute, PreferenceDeepLink {
    override val deepLink = "$URI/app-drawer-folder"
}

@Serializable
data class AppDrawerAppListToFolder(val id: Int) : PreferenceRoute

// Search section routes
@Serializable
data class SearchProviderPreference(val id: SearchProviderId) :
    PreferenceRoute,
    PreferenceDeepLink {
    override val deepLink = "$URI/search-provider"
}

// Smartspace section routes
@Serializable
data object SmartspaceWidget : PreferenceRoute

// Gestures section routes
@Serializable
data object GesturesPickApp : PreferenceRoute

// About section routes
@Serializable
data object AboutLicenses : PreferenceRoute, PreferenceDeepLink {
    override val deepLink = "$URI/about-licenses"
}

// Data/Action oriented routes (might be used across sections or are specific actions)
// These are intentionally not prefixed as per your instruction,
// as they might be used across different sections or are standalone actions.
@Serializable
data class SelectIcon(
    // assuming componentKey is a ComponentKey.toString()
    val componentKey: String,
) : PreferenceRoute

// default to empty
@Serializable
data class IconPicker(val packageName: String = "") : PreferenceRoute

@Serializable
data class ColorSelection(val prefKey: String) : PreferenceRoute

@Serializable
data object CreateBackup : PreferenceRoute, PreferenceDeepLink {
    override val deepLink = "$URI/create-backup"
}

@Serializable
data class RestoreBackup(val base64Uri: String) : PreferenceRoute
