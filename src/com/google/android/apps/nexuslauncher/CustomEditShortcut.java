package com.google.android.apps.nexuslauncher;

import android.content.Context;
import android.view.View;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.graphics.DrawableFactory;
import com.android.launcher3.popup.SystemShortcut;

public class CustomEditShortcut extends SystemShortcut.Custom {
    public CustomEditShortcut(Context context) {
        super();
    }

    @Override
    public View.OnClickListener getOnClickListener(final Launcher launcher, final ItemInfo itemInfo) {
        if (CustomIconUtils.usingValidPack(launcher)) {
            CustomDrawableFactory factory = (CustomDrawableFactory) DrawableFactory.get(launcher);
            factory.ensureInitialLoadComplete();

            return new View.OnClickListener() {
                private boolean mOpened = false;

                @Override
                public void onClick(View view) {
                    if (!mOpened) {
                        mOpened = true;
                        AbstractFloatingView.closeAllOpenViews(launcher);
                        CustomBottomSheet cbs = (CustomBottomSheet) launcher.getLayoutInflater()
                                .inflate(R.layout.app_edit_bottom_sheet, launcher.getDragLayer(), false);
                        cbs.populateAndShow(itemInfo);
                    }
                }
            };
        }

        return null;
    }
}
