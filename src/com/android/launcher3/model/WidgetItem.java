package com.android.launcher3.model;

import static com.android.launcher3.Utilities.ATLEAST_S;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.pm.ShortcutConfigActivityInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;

/**
 * An wrapper over various items displayed in a widget picker,
 * {@link LauncherAppWidgetProviderInfo} & {@link ActivityInfo}. This provides easier access to
 * common attributes like spanX and spanY.
 */
public class WidgetItem extends ComponentKey {

    public final LauncherAppWidgetProviderInfo widgetInfo;
    public final ShortcutConfigActivityInfo activityInfo;

    public final String label;
    public final CharSequence description;
    public final int spanX, spanY;

    public WidgetItem(LauncherAppWidgetProviderInfo info,
            InvariantDeviceProfile idp, IconCache iconCache, Context context) {
        super(info.provider, info.getProfile());

        label = iconCache.getTitleNoCache(info);
        description = ATLEAST_S ? info.loadDescription(context) : null;
        widgetInfo = info;
        activityInfo = null;

        spanX = Math.min(info.spanX, idp.numColumns);
        spanY = Math.min(info.spanY, idp.numRows);
    }

    public WidgetItem(ShortcutConfigActivityInfo info, IconCache iconCache, PackageManager pm) {
        super(info.getComponent(), info.getUser());
        label = info.isPersistable() ? iconCache.getTitleNoCache(info) :
                Utilities.trim(info.getLabel(pm));
        description = null;
        widgetInfo = null;
        activityInfo = info;
        spanX = spanY = 1;
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
}
