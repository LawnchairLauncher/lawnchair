package app.lawnchair.fold

import androidx.annotation.StringRes
import com.android.launcher3.R

/**
 * An option of a foldable-screen preference.
 *
 * [key] is the stable string written to the datastore, so it must never change even if the enum
 * constant or its labels are renamed. An unknown or missing key reads as the preference's default
 * (`PreferenceManager2.foldOptionPreference`).
 */
interface FoldOption {
    val key: String

    @get:StringRes
    val labelResourceId: Int

    @get:StringRes
    val descriptionResourceId: Int
}

/** Home screen layout on the large (unfolded) screen. */
enum class FoldHomeLayout(
    override val key: String,
    @StringRes override val labelResourceId: Int,
    @StringRes override val descriptionResourceId: Int,
) : FoldOption {
    PAGES(
        key = "pages",
        labelResourceId = R.string.fold_home_pages,
        descriptionResourceId = R.string.fold_home_pages_description,
    ),
    EXPAND(
        key = "expand",
        labelResourceId = R.string.fold_home_expand,
        descriptionResourceId = R.string.fold_home_expand_description,
    ),
    MIRROR(
        key = "mirror",
        labelResourceId = R.string.fold_home_mirror,
        descriptionResourceId = R.string.fold_home_mirror_description,
    ),
    INDEPENDENT(
        key = "independent",
        labelResourceId = R.string.fold_home_independent,
        descriptionResourceId = R.string.fold_home_independent_description,
    ),
}

/**
 * Order of the home pages on the large screen with [FoldHomeLayout.PAGES] and
 * [FoldHomeLayout.EXPAND]; the other layouts keep [OPENING]. See [FoldPagePairs]. Also the order of
 * the [FoldDrawerLayout.PAGES] drawer's two pages, whatever the home layout ([FoldDrawerLayouts]).
 */
enum class FoldPageOrder(
    override val key: String,
    @StringRes override val labelResourceId: Int,
    @StringRes override val descriptionResourceId: Int,
) : FoldOption {
    OPENING(
        key = "opening",
        labelResourceId = R.string.fold_page_order_opening,
        descriptionResourceId = R.string.fold_page_order_opening_description,
    ),
    READING(
        key = "reading",
        labelResourceId = R.string.fold_page_order_reading,
        descriptionResourceId = R.string.fold_page_order_reading_description,
    ),
}

/** App drawer layout on the large (unfolded) screen. */
enum class FoldDrawerLayout(
    override val key: String,
    @StringRes override val labelResourceId: Int,
    @StringRes override val descriptionResourceId: Int,
) : FoldOption {
    EXPAND(
        key = "expand",
        labelResourceId = R.string.fold_drawer_expand,
        descriptionResourceId = R.string.fold_drawer_expand_description,
    ),
    PAGES(
        key = "pages",
        labelResourceId = R.string.fold_drawer_pages,
        descriptionResourceId = R.string.fold_drawer_pages_description,
    ),
    BIG_ICONS(
        key = "big_icons",
        labelResourceId = R.string.fold_drawer_big_icons,
        descriptionResourceId = R.string.fold_drawer_big_icons_description,
    ),
}

/** Animation played while folding and unfolding. */
enum class FoldTransitionStyle(
    override val key: String,
    @StringRes override val labelResourceId: Int,
    @StringRes override val descriptionResourceId: Int,
) : FoldOption {
    WINDOW(
        key = "window",
        labelResourceId = R.string.fold_transition_window,
        descriptionResourceId = R.string.fold_transition_window_description,
    ),
    WAVE(
        key = "wave",
        labelResourceId = R.string.fold_transition_wave,
        descriptionResourceId = R.string.fold_transition_wave_description,
    ),
    BOOK(
        key = "book",
        labelResourceId = R.string.fold_transition_book,
        descriptionResourceId = R.string.fold_transition_book_description,
    ),
    SIMPLE(
        key = "simple",
        labelResourceId = R.string.fold_transition_simple,
        descriptionResourceId = R.string.fold_transition_simple_description,
    ),
}

/** Which page the cover screen shows after folding. */
enum class FoldAfterFold(
    override val key: String,
    @StringRes override val labelResourceId: Int,
    @StringRes override val descriptionResourceId: Int,
) : FoldOption {
    LAST_PANEL(
        key = "last_panel",
        labelResourceId = R.string.fold_after_fold_last_panel,
        descriptionResourceId = R.string.fold_after_fold_last_panel_description,
    ),
    FIRST_PAGE(
        key = "first_page",
        labelResourceId = R.string.fold_after_fold_first_page,
        descriptionResourceId = R.string.fold_after_fold_first_page_description,
    ),
}

/** Dock behavior on the large (unfolded) screen. */
enum class FoldDock(
    override val key: String,
    @StringRes override val labelResourceId: Int,
    @StringRes override val descriptionResourceId: Int,
) : FoldOption {
    MERGE(
        key = "merge",
        labelResourceId = R.string.fold_dock_merge,
        descriptionResourceId = R.string.fold_dock_merge_description,
    ),
    SAME(
        key = "same",
        labelResourceId = R.string.fold_dock_same,
        descriptionResourceId = R.string.fold_dock_same_description,
    ),
    SIDE(
        key = "side",
        labelResourceId = R.string.fold_dock_side,
        descriptionResourceId = R.string.fold_dock_side_description,
    ),
}

/** Layout used on the large screen in landscape. */
enum class FoldLandscape(
    override val key: String,
    @StringRes override val labelResourceId: Int,
    @StringRes override val descriptionResourceId: Int,
) : FoldOption {
    AUTO(
        key = "auto",
        labelResourceId = R.string.fold_landscape_auto,
        descriptionResourceId = R.string.fold_landscape_auto_description,
    ),
    OWN(
        key = "own",
        labelResourceId = R.string.fold_landscape_own,
        descriptionResourceId = R.string.fold_landscape_own_description,
    ),
}

/** Wallpaper behavior across the two screens. */
enum class FoldWallpaper(
    override val key: String,
    @StringRes override val labelResourceId: Int,
    @StringRes override val descriptionResourceId: Int,
) : FoldOption {
    SPAN(
        key = "span",
        labelResourceId = R.string.fold_wallpaper_span,
        descriptionResourceId = R.string.fold_wallpaper_span_description,
    ),
    EACH(
        key = "each",
        labelResourceId = R.string.fold_wallpaper_each,
        descriptionResourceId = R.string.fold_wallpaper_each_description,
        // The system's own per-screen wallpapers (Samsung), else Lawnchair's dual wallpaper
    ),
}
