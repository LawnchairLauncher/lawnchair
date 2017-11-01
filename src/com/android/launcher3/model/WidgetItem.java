package com.android.launcher3.model;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.UserHandle;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.ShortcutConfigActivityInfo;
import com.android.launcher3.util.ComponentKey;

import java.text.Collator;

/**
 * An wrapper over various items displayed in a widget picker,
 * {@link LauncherAppWidgetProviderInfo} & {@link ActivityInfo}. This provides easier access to
 * common attributes like spanX and spanY.
 */
public class WidgetItem extends ComponentKey implements Comparable<WidgetItem> {

    private static UserHandle sMyUserHandle;
    private static Collator sCollator;

    public final LauncherAppWidgetProviderInfo widgetInfo;
    public final ShortcutConfigActivityInfo activityInfo;

    public final String label;
    public final int spanX, spanY;

    public WidgetItem(LauncherAppWidgetProviderInfo info, PackageManager pm,
            InvariantDeviceProfile idp) {
        super(info.provider, info.getProfile());

        label = Utilities.trim(info.getLabel(pm));
        widgetInfo = info;
        activityInfo = null;

        spanX = Math.min(info.spanX, idp.numColumns);
        spanY = Math.min(info.spanY, idp.numRows);
    }

    public WidgetItem(ShortcutConfigActivityInfo info) {
        super(info.getComponent(), info.getUser());
        label = Utilities.trim(info.getLabel());
        widgetInfo = null;
        activityInfo = info;
        spanX = spanY = 1;
    }

    @Override
    public int compareTo(WidgetItem another) {
        if (sMyUserHandle == null) {
            // Delay these object creation until required.
            sMyUserHandle = Process.myUserHandle();
            sCollator = Collator.getInstance();
        }

        // Independent of how the labels compare, if only one of the two widget info belongs to
        // work profile, put that one in the back.
        boolean thisWorkProfile = !sMyUserHandle.equals(user);
        boolean otherWorkProfile = !sMyUserHandle.equals(another.user);
        if (thisWorkProfile ^ otherWorkProfile) {
            return thisWorkProfile ? 1 : -1;
        }

        int labelCompare = sCollator.compare(label, another.label);
        if (labelCompare != 0) {
            return labelCompare;
        }

        // If the label is same, put the smaller widget before the larger widget. If the area is
        // also same, put the widget with smaller height before.
        int thisArea = spanX * spanY;
        int otherArea = another.spanX * another.spanY;
        return thisArea == otherArea
                ? Integer.compare(spanY, another.spanY)
                : Integer.compare(thisArea, otherArea);
    }
}
