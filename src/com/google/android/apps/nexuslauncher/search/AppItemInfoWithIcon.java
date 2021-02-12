package com.google.android.apps.nexuslauncher.search;

import android.content.Intent;

import androidx.annotation.NonNull;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.util.ComponentKey;

public class AppItemInfoWithIcon extends ItemInfoWithIcon {
    public Intent mIntent;

    public AppItemInfoWithIcon(AppItemInfoWithIcon info) {
        super(info);
        this.mIntent = info.mIntent;
    }

    public AppItemInfoWithIcon(final ComponentKey componentKey) {
        mIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(componentKey.componentName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        user = componentKey.user;
        itemType = 0;
    }

    public Intent getIntent() {
        return this.mIntent;
    }

    @NonNull
    @Override
    public ItemInfoWithIcon clone() {
        return new AppItemInfoWithIcon(this);
    }
}
