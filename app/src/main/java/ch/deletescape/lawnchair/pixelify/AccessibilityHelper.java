package ch.deletescape.lawnchair.pixelify;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;

import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.R;

public class AccessibilityHelper extends AccessibilityDelegate {
    @Override
    public void onInitializeAccessibilityNodeInfo(View view, AccessibilityNodeInfo accessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(view, accessibilityNodeInfo);
        if (Launcher.getLauncher(view.getContext()).isClientConnected()) {
            accessibilityNodeInfo.addAction(new AccessibilityAction(R.string.title_show_google_app, AccessibilityHelper.getShowGoogleAppText(view.getContext())));
        }
    }

    @Override
    public boolean performAccessibilityAction(View view, int i, Bundle bundle) {
        if (i != R.string.title_show_google_app) {
            return super.performAccessibilityAction(view, i, bundle);
        }
        Launcher.getLauncher(view.getContext()).getClient().openOverlay(true);
        return true;
    }

    public static String getShowGoogleAppText(Context context) {
        try {
            Resources res = context.getPackageManager().getResourcesForApplication("com.google.android.googlequicksearchbox");
            int id = res.getIdentifier("title_google_home_screen", "string", "com.google.android.googlequicksearchbox");
            if (id != 0) {
                if (TextUtils.isEmpty(res.getString(id))) {
                    return context.getString(R.string.title_show_google_app, res.getString(id));
                }
            }
        } catch (NameNotFoundException e) {
        }
        return context.getString(R.string.title_show_google_app_default);
    }
}