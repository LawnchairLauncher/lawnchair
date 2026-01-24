package app.lawnchair.theme.drawable

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import androidx.appcompat.content.res.AppCompatResources
import app.lawnchair.theme.color.tokens.ColorTokens
import com.android.launcher3.R

object DrawableTokens {

    @JvmField
    val BgCellLayout = ResourceDrawableToken<Drawable>(R.drawable.bg_celllayout)
        .setTint(ColorTokens.ColorAccent)

    // pE-TODO(QPR1): Investigate
    @JvmField
    val BgOverviewClearAllButton = ResourceDrawableToken<RippleDrawable>(R.drawable.overview_action_button_background)
        .mutate { context, scheme, uiColorMode ->
            val background = getDrawable(0) as GradientDrawable
            background.setColor(ColorTokens.ColorBackground.resolveColor(context, scheme, uiColorMode))
        }

    @JvmField
    val BgWidgetsFullSheet = ResourceDrawableToken<GradientDrawable>(R.drawable.bg_widgets_full_sheet)
        .setColor(ColorTokens.ColorBackground)

    @JvmField
    val BgWidgetsSearchbox = ResourceDrawableToken<GradientDrawable>(R.drawable.bg_widgets_searchbox)
        .setColor(ColorTokens.Surface)

    @JvmField
    val DropTargetBackground = ResourceDrawableToken<Drawable>(R.drawable.drop_target_background)
        .setTint(ColorTokens.WorkspaceAccentColor)

    @JvmField
    val MiddleItemPrimary = ResourceDrawableToken<GradientDrawable>(R.drawable.middle_item_primary)
        .setColor(ColorTokens.PopupColorPrimary)

    @JvmField
    val PopupItemBackgroundBorderless = AttributeDrawableToken<Drawable>(android.R.attr.selectableItemBackgroundBorderless)
        .mutate { context, scheme, uiColorMode ->
            if (this is RippleDrawable) {
                val color = ColorTokens.PopupColorTertiary.resolveColor(context, scheme, uiColorMode)
                setColor(ColorStateList.valueOf(color))
            }
        }

    @JvmField
    val RoundRectFolder = ResourceDrawableToken<GradientDrawable>(R.drawable.round_rect_folder)
        .setColor(ColorTokens.FolderBackgroundColor)

    @JvmField
    val RoundRectPrimary = ResourceDrawableToken<GradientDrawable>(R.drawable.round_rect_primary)
        .setColor(ColorTokens.ColorPrimary)

    @JvmField
    val SearchInputFg = ResourceDrawableToken<LayerDrawable>(R.drawable.search_input_fg)
        .mutate { context, scheme, darkTheme ->
            val shape = getDrawable(0) as GradientDrawable
            shape.setColor(ColorTokens.SearchboxHighlight.resolveColor(context, scheme, darkTheme))
        }

    @JvmField
    val SingleItemPrimary = ResourceDrawableToken<GradientDrawable>(R.drawable.single_item_primary)
        .setColor(ColorTokens.PopupColorPrimary)

    @JvmField
    val TaskMenuItemBg = ResourceDrawableToken<GradientDrawable>(R.drawable.task_menu_item_bg)
        .setColor(ColorTokens.ColorPrimary)

    @JvmField
    val WidgetsBottomSheetBackground = ResourceDrawableToken<GradientDrawable>(R.drawable.bg_rounded_corner_bottom_sheet)
        .setColor(ColorTokens.Surface)

    @JvmField
    val WidgetsListBackground = NewDrawable { context, scheme, uiColorMode ->
        val list = StateListDrawable()
        list.setEnterFadeDuration(100)

        val unselected = AppCompatResources.getDrawable(
            context,
            R.drawable.bg_widgets_header,
        )
        unselected?.setTint(ColorTokens.Surface.resolveColor(context, scheme, uiColorMode))

        val selected = AppCompatResources.getDrawable(
            context,
            R.drawable.bg_widgets_header,
        )
        selected?.setTint(ColorTokens.WidgetListRowColor.resolveColor(context, scheme, uiColorMode))

        list.addState(intArrayOf(-android.R.attr.state_selected), unselected)
        list.addState(intArrayOf(android.R.attr.state_selected), selected)

        list
    }

    @JvmField
    val WidgetsContentBackground = NewDrawable { context, scheme, uiColorMode ->
        val list = StateListDrawable()
        list.setEnterFadeDuration(100)

        val unselected = AppCompatResources.getDrawable(
            context,
            R.drawable.bg_widgets_content,
        )
        unselected?.setTint(ColorTokens.Surface.resolveColor(context, scheme, uiColorMode))

        val selected = AppCompatResources.getDrawable(
            context,
            R.drawable.bg_widgets_content,
        )
        selected?.setTint(ColorTokens.WidgetListRowColor.resolveColor(context, scheme, uiColorMode))

        list.addState(intArrayOf(-android.R.attr.state_selected), unselected)
        list.addState(intArrayOf(android.R.attr.state_selected), selected)

        list
    }

    @JvmField
    val WidgetsRecommendationBackground = ResourceDrawableToken<GradientDrawable>(R.drawable.widgets_surface_background)
        .setColor(ColorTokens.WidgetListRowColor)

    @JvmField
    val WidgetResizeFrame = ResourceDrawableToken<GradientDrawable>(R.drawable.widget_resize_frame)
        .setTint(ColorTokens.WorkspaceAccentColor)

    @JvmField
    val AllAppsTabsBackground = NewDrawable { context, scheme, uiColorMode ->
        val list = StateListDrawable()
        list.setEnterFadeDuration(100)

        val unselected = AppCompatResources.getDrawable(
            context,
            R.drawable.all_apps_tabs_background,
        )
        unselected?.setTint(ColorTokens.Surface.resolveColor(context, scheme, uiColorMode))

        val selected = AppCompatResources.getDrawable(
            context,
            R.drawable.all_apps_tabs_background,
        )
        selected?.setTint(ColorTokens.AllAppsTabBackgroundSelected.resolveColor(context, scheme, uiColorMode))

        list.addState(intArrayOf(-android.R.attr.state_selected), unselected)
        list.addState(intArrayOf(android.R.attr.state_selected), selected)

        list
    }

    @JvmField
    val AllAppsTabsMaskDrawable = NewDrawable { context, scheme, uiColorMode ->
        val mask = GradientDrawable()
        val radius = context.resources.getDimension(R.dimen.all_apps_header_pill_corner_radius)
        mask.cornerRadius = radius
        mask.setColor(ColorTokens.FocusHighlight.resolveColor(context, scheme, uiColorMode))

        mask
    }

    @JvmField
    val WorkAppsToggleBackground = NewDrawable { context, scheme, uiColorMode ->
        val list = StateListDrawable()

        val disabled = AppCompatResources.getDrawable(
            context,
            R.drawable.work_apps_toggle_background_shape,
        )

        val enabled = AppCompatResources.getDrawable(
            context,
            R.drawable.work_apps_toggle_background_shape,
        ) as GradientDrawable
        enabled.setColor(ColorTokens.AllAppsTabBackgroundSelected.resolveColor(context, scheme, uiColorMode))

        list.addState(intArrayOf(-android.R.attr.state_enabled), disabled)
        list.addState(intArrayOf(), enabled)

        list
    }

    @JvmField
    val WorkCard = ResourceDrawableToken<GradientDrawable>(R.drawable.work_card)
        .setColor(ColorTokens.SurfaceContainerHighest)

    @JvmField
    val WidgetAddButtonBackground = ResourceDrawableToken<InsetDrawable>(R.drawable.widget_cell_add_button_background)
        .setTint(ColorTokens.WidgetAddButtonBackgroundColor)
}
