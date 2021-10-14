package app.lawnchair.theme.drawable

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import app.lawnchair.theme.color.ColorTokens
import com.android.launcher3.R

object DrawableTokens {
    @JvmField
    val SearchInputFg = ResourceDrawableToken<LayerDrawable>(R.drawable.search_input_fg)
        .mutate { context, scheme, darkTheme ->
            val shape = getDrawable(0) as GradientDrawable
            shape.setColor(ColorTokens.SearchboxHighlight.resolveColor(context, scheme, darkTheme))
        }

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
    val RoundRectFolder = ResourceDrawableToken<GradientDrawable>(R.drawable.round_rect_folder)
        .setColor(ColorTokens.FolderFillColor)

    @JvmField
    val SingleItemPrimary = ResourceDrawableToken<GradientDrawable>(R.drawable.single_item_primary)
        .setColor(ColorTokens.PopupColorPrimary)

    @JvmField
    val WidgetsBottomSheetBackground = ResourceDrawableToken<GradientDrawable>(R.drawable.widgets_bottom_sheet_background)
        .setColor(ColorTokens.Surface)

    @JvmField
    val WidgetResizeFrame = ResourceDrawableToken<GradientDrawable>(R.drawable.widget_resize_frame)
        .setStroke(2f, ColorTokens.WorkspaceAccentColor)
}
