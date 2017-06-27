package ch.deletescape.lawnchair.model;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.support.annotation.NonNull;

import java.text.Collator;

import ch.deletescape.lawnchair.InvariantDeviceProfile;
import ch.deletescape.lawnchair.LauncherAppWidgetProviderInfo;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.compat.ShortcutConfigActivityInfo;
import ch.deletescape.lawnchair.util.ComponentKey;

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

    public WidgetItem(LauncherAppWidgetProviderInfo launcherAppWidgetProviderInfo, PackageManager packageManager, InvariantDeviceProfile invariantDeviceProfile) {
        super(launcherAppWidgetProviderInfo.provider, launcherAppWidgetProviderInfo.getProfile());
        this.label = Utilities.trim(launcherAppWidgetProviderInfo.getLabel(packageManager));
        this.widgetInfo = launcherAppWidgetProviderInfo;
        this.activityInfo = null;
        this.spanX = Math.min(launcherAppWidgetProviderInfo.spanX, invariantDeviceProfile.numColumns);
        this.spanY = Math.min(launcherAppWidgetProviderInfo.spanY, invariantDeviceProfile.numRows);
    }

    public WidgetItem(ShortcutConfigActivityInfo shortcutConfigActivityInfo) {
        super(shortcutConfigActivityInfo.getComponent(), shortcutConfigActivityInfo.getUser());
        this.label = Utilities.trim(shortcutConfigActivityInfo.getLabel());
        this.widgetInfo = null;
        this.activityInfo = shortcutConfigActivityInfo;
        this.spanY = 1;
        this.spanX = 1;
    }

    @Override
    public int compareTo(@NonNull WidgetItem another) {
        if (sMyUserHandle == null) {
            // Delay these object creation until required.
            sMyUserHandle = Utilities.myUserHandle();
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
