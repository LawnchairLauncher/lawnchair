package ch.deletescape.lawnchair;

import android.content.ComponentName;
import android.content.Context;

import java.util.HashSet;
import java.util.Set;

public class StringSetAppFilter extends PreferenceAppFilter {
    // Blacklisted APKs which will be hidden, these include simple regex formatting, without
    // full regex formatting (e.g. com.android. will block everything that starts with com.android.)
    // Taken from: https://github.com/substratum/template/blob/kt-n/app/src/main/kotlin/substratum/theme/template/Constants.kt
    private static final String[] CARNT_PACKAGES = {"com.android.vending.billing.InAppBillingService.LOCK",
            "com.android.vending.billing.InAppBillingService.LACK",
            "cc.madkite.freedom",
            "zone.jasi2169.uretpatcher",
            "uret.jasi2169.patcher",
            "com.dimonvideo.luckypatcher",
            "com.chelpus.lackypatch",
            "com.forpda.lp",
            "com.android.vending.billing.InAppBillingService.LUCK",
            "com.android.protips",
            "com.android.vending.billing.InAppBillingService.CLON",
            "com.android.vendinc"};
    private Set<ComponentName> mHiddenComponents = new HashSet<>();

    StringSetAppFilter() {
        mHiddenComponents.add(ComponentName.unflattenFromString("com.google.android.apps.wallpaper/.picker.CategoryPickerActivity"));
        mHiddenComponents.add(ComponentName.unflattenFromString("com.google.android.googlequicksearchbox/.VoiceSearchActivity"));
    }

    @Override
    public boolean shouldShowApp(ComponentName app, Context context) {
        for (String carntPackage : CARNT_PACKAGES) {
            if (app.getPackageName().startsWith(carntPackage)) {
                return false;
            }
        }
        return !mHiddenComponents.contains(app) && super.shouldShowApp(app, context);
    }
}
