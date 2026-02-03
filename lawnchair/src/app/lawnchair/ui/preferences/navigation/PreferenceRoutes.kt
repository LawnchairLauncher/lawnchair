package app.lawnchair.ui.preferences.navigation

import app.lawnchair.ui.preferences.components.search.SearchProviderId
import app.lawnchair.ui.preferences.destinations.SearchRoute
import app.lawnchair.ui.preferences.destinations.ShapeRoute
import kotlinx.serialization.Serializable

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

// Misc routes

@Serializable
data object Root : PreferenceRootRoute

@Serializable
data object Dummy : PreferenceRootRoute

// Top-level destinations
@Serializable
data object General : PreferenceRootRoute

@Serializable
data object HomeScreen : PreferenceRootRoute

@Serializable
data object Dock : PreferenceRootRoute

@Serializable
data object AppDrawer : PreferenceRootRoute

// technically the search screen, selectedId selects the default tab inside this
@Serializable
data class Search(val selectedId: SearchRoute = SearchRoute.DOCK_SEARCH) : PreferenceRootRoute

@Serializable
data object Folders : PreferenceRootRoute

@Serializable
data object Quickstep : PreferenceRootRoute

@Serializable
data object Gestures : PreferenceRootRoute

@Serializable
data object Smartspace : PreferenceRootRoute

@Serializable
data object About : PreferenceRootRoute

@Serializable
data object ExperimentalFeatures : PreferenceRootRoute

@Serializable
data object DebugMenu : PreferenceRootRoute

@Serializable
data object FeatureFlags : PreferenceRoute

// General section routes
@Serializable
data class GeneralFontSelection(val prefKey: String) : PreferenceRoute

@Serializable
data object GeneralIconPack : PreferenceRoute

@Serializable
data class GeneralIconShape(val selectedId: ShapeRoute = ShapeRoute.APP_SHAPE) : PreferenceRoute

@Serializable
data object GeneralCustomIconShapeCreator : PreferenceRoute

// Home Screen section routes
@Serializable
data object HomeScreenGrid : PreferenceRoute

@Serializable
data object HomeScreenPopupEditor : PreferenceRoute

// Dock section routes
@Serializable
data object DockSearchProvider : PreferenceRoute

// App Drawer section routes
@Serializable
data object AppDrawerHiddenApps : PreferenceRoute

@Serializable
data object AppDrawerFolder : PreferenceRoute

@Serializable
data class AppDrawerAppListToFolder(val id: Int) : PreferenceRoute

// Search section routes
@Serializable
data class SearchProviderPreference(val id: SearchProviderId) : PreferenceRoute

// Smartspace section routes
@Serializable
data object SmartspaceWidget : PreferenceRoute

// Gestures section routes
@Serializable
data object GesturesPickApp : PreferenceRoute

// About section routes
@Serializable
data object AboutLicenses : PreferenceRoute

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
data object CreateBackup : PreferenceRoute

@Serializable
data class RestoreBackup(val base64Uri: String) : PreferenceRoute
