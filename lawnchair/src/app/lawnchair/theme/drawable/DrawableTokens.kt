package app.lawnchair.theme.drawable

import android.content.res.ColorStateList
import android.graphics.drawable.*
import androidx.appcompat.content.res.AppCompatResources
import app.lawnchair.theme.color.ColorTokens
import com.android.launcher3.R

object DrawableTokens {

    @JvmField
    val BgCellLayout = ResourceDrawableToken<Drawable>(R.drawable.bg_celllayout)
        .setTint(ColorTokens.ColorAccent)

    @JvmField
    val BgWidgetsPickerHandle = ResourceDrawableToken<LayerDrawable>(R.drawable.bg_widgets_picker_handle)
        .mutate { context, scheme, darkTheme ->
            val shape = getDrawable(0) as GradientDrawable
            shape.setColor(ColorTokens.ColorBackground.resolveColor(context, scheme, darkTheme))
        }

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
        .setColor(ColorTokens.FolderFillColor)

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
    val WidgetsBottomSheetBackground = ResourceDrawableToken<GradientDrawable>(R.drawable.widgets_bottom_sheet_background)
        .setColor(ColorTokens.Surface)

    @JvmField
    val WidgetsRecommendationBackground = ResourceDrawableToken<GradientDrawable>(R.drawable.widgets_recommendation_background)
        .setColor(ColorTokens.Surface)

    @JvmField
    val WidgetResizeFrame = ResourceDrawableToken<GradientDrawable>(R.drawable.widget_resize_frame)
        .setTint(ColorTokens.WorkspaceAccentColor)

    @JvmField
    val AllAppsTabsBackground = NewDrawable { context, scheme, uiColorMode ->
        val list = StateListDrawable()
        list.setEnterFadeDuration(100)

        val cornerRadius = context.resources
            .getDimensionPixelSize(R.dimen.all_apps_header_pill_corner_radius).toFloat()

        val unselected = GradientDrawable()
        unselected.shape = GradientDrawable.RECTANGLE
        unselected.cornerRadius = cornerRadius
        unselected.setColor(ColorTokens.Surface.resolveColor(context, scheme, uiColorMode))

        val selected = GradientDrawable()
        selected.shape = GradientDrawable.RECTANGLE
        selected.cornerRadius = cornerRadius
        selected.setColor(ColorTokens.AllAppsTabBackgroundSelected.resolveColor(context, scheme, uiColorMode))

        list.addState(intArrayOf(-android.R.attr.state_selected), unselected)
        list.addState(intArrayOf(android.R.attr.state_selected), selected)

        list
    }

    @JvmField
    val WorkAppsToggleBackground = NewDrawable { context, scheme, uiColorMode ->
        val list = StateListDrawable()

        val disabled = AppCompatResources.getDrawable(
            context, R.drawable.work_apps_toggle_background_shape)

        val enabled = AppCompatResources.getDrawable(
            context, R.drawable.work_apps_toggle_background_shape) as GradientDrawable
        enabled.setColor(ColorTokens.AllAppsTabBackgroundSelected.resolveColor(context, scheme, uiColorMode))

        list.addState(intArrayOf(-android.R.attr.state_enabled), disabled)
        list.addState(intArrayOf(), enabled)

        list
    }

    @JvmField val WorkCard = ResourceDrawableToken<GradientDrawable>(R.drawable.work_card)
        .setColor(ColorTokens.Surface)
}
