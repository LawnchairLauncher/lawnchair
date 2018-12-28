package com.google.android.apps.nexuslauncher;

import android.content.Context;
import android.support.annotation.Keep;
import android.view.View;
import ch.deletescape.lawnchair.override.CustomInfoProvider;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.popup.SystemShortcut;

@Keep
public class CustomEditShortcut extends SystemShortcut.Custom {
    public CustomEditShortcut(Context context) {
        super();
    }

    @Override
    public View.OnClickListener getOnClickListener(final Launcher launcher, final ItemInfo itemInfo) {
        boolean enabled = CustomInfoProvider.Companion.isEditable(itemInfo);
        return enabled ? new View.OnClickListener() {
            private boolean mOpened = false;

            @Override
            public void onClick(View view) {
                if (!mOpened) {
                    mOpened = true;
                    AbstractFloatingView.closeAllOpenViews(launcher);
                    CustomBottomSheet.show(launcher, itemInfo);
                }
            }
        } : null;
    }
}
