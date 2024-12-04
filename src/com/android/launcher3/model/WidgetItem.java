package com.android.launcher3.model;

import static android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN;
import static android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD;
import static android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX;

import static com.android.launcher3.Utilities.ATLEAST_S;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.SparseArray;
import android.widget.RemoteViews;

import androidx.core.os.BuildCompat;

import com.android.launcher3.Flags;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.pm.ShortcutConfigActivityInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.WidgetManagerHelper;

/**
 * An wrapper over various items displayed in a widget picker,
 * {@link LauncherAppWidgetProviderInfo} & {@link ActivityInfo}. This provides easier access to
 * common attributes like spanX and spanY.
 */
public class WidgetItem extends ComponentKey {

    public final LauncherAppWidgetProviderInfo widgetInfo;
    public final ShortcutConfigActivityInfo activityInfo;

    public BitmapInfo bitmap = BitmapInfo.LOW_RES_INFO;
    public final String label;
    public final CharSequence description;
    public final int spanX, spanY;
    public final SparseArray<RemoteViews> generatedPreviews;

    public WidgetItem(LauncherAppWidgetProviderInfo info,
            InvariantDeviceProfile idp, IconCache iconCache, Context context,
            WidgetManagerHelper helper) {
        super(info.provider, info.getProfile());

        label = iconCache.getTitleNoCache(info);
        description = ATLEAST_S ? info.loadDescription(context) : null;
        widgetInfo = info;
        activityInfo = null;

        spanX = Math.min(info.spanX, idp.numColumns);
        spanY = Math.min(info.spanY, idp.numRows);

        if (BuildCompat.isAtLeastV() && Flags.enableGeneratedPreviews()) {
            generatedPreviews = new SparseArray<>(3);
            for (int widgetCategory : new int[] {
                    WIDGET_CATEGORY_HOME_SCREEN,
                    WIDGET_CATEGORY_KEYGUARD,
                    WIDGET_CATEGORY_SEARCHBOX,
            }) {
                if ((widgetCategory & widgetInfo.generatedPreviewCategories) != 0) {
                    generatedPreviews.put(widgetCategory,
                            helper.loadGeneratedPreview(widgetInfo, widgetCategory));
                }
            }
        } else {
            generatedPreviews = null;
        }
    }

    public WidgetItem(LauncherAppWidgetProviderInfo info,
            InvariantDeviceProfile idp, IconCache iconCache, Context context) {
        this(info, idp, iconCache, context, new WidgetManagerHelper(context));
    }

    public WidgetItem(ShortcutConfigActivityInfo info, IconCache iconCache, PackageManager pm) {
        super(info.getComponent(), info.getUser());
        label = info.isPersistable() ? iconCache.getTitleNoCache(info) :
                Utilities.trim(info.getLabel(pm));
        description = null;
        widgetInfo = null;
        activityInfo = info;
        spanX = spanY = 1;
        generatedPreviews = null;
    }

    /**
     * Returns {@code true} if this {@link WidgetItem} has the same type as the given
     * {@code otherItem}.
     *
     * For example, both items are widgets or both items are shortcuts.
     */
    public boolean hasSameType(WidgetItem otherItem) {
        if (widgetInfo != null && otherItem.widgetInfo != null) {
            return true;
        }
        if (activityInfo != null && otherItem.activityInfo != null) {
            return true;
        }
        return false;
    }

    /** Returns whether this {@link WidgetItem} has a preview layout that can be used. */
    @SuppressLint("NewApi") // Already added API check.
    public boolean hasPreviewLayout() {
        return ATLEAST_S && widgetInfo != null && widgetInfo.previewLayout != Resources.ID_NULL;
    }

    /** Returns whether this {@link WidgetItem} is for a shortcut rather than an app widget. */
    public boolean isShortcut() {
        return activityInfo != null;
    }

    /**
     * Returns whether this {@link WidgetItem} has a generated preview for the given widget
     * category.
     */
    public boolean hasGeneratedPreview(int widgetCategory) {
        if (!Flags.enableGeneratedPreviews() || generatedPreviews == null) {
            return false;
        }
        return generatedPreviews.contains(widgetCategory)
                && generatedPreviews.get(widgetCategory) != null;
    }
}
