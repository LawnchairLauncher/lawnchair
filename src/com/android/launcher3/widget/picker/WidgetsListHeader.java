/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.widget.picker;

import static com.android.launcher3.widget.WidgetSections.NO_CATEGORY;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.icons.IconCache.ItemInfoUpdateReceiver;
import com.android.launcher3.icons.PlaceHolderIconDrawable;
import com.android.launcher3.icons.cache.HandlerRunnable;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.util.PluralMessageFormat;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.WidgetSections;
import com.android.launcher3.widget.WidgetSections.WidgetSection;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;
import com.android.launcher3.widget.model.WidgetsListSearchHeaderEntry;

import java.util.stream.Collectors;

/**
 * A UI represents a header of an app shown in the full widgets tray.
 *
 * It is a {@link LinearLayout} which contains an app icon, an app name, a subtitle and a checkbox
 * which indicates if the widgets content view underneath this header should be shown.
 */
public final class WidgetsListHeader extends LinearLayout implements ItemInfoUpdateReceiver {

    private boolean mEnableIconUpdateAnimation = false;

    @Nullable private HandlerRunnable mIconLoadRequest;
    @Nullable private Drawable mIconDrawable;
    private final int mIconSize;

    private ImageView mAppIcon;
    private TextView mTitle;
    private TextView mSubtitle;

    private CheckBox mExpandToggle;
    private boolean mIsExpanded = false;
    @Nullable private WidgetsListDrawableState mListDrawableState;

    public WidgetsListHeader(Context context) {
        this(context, /* attrs= */ null);
    }

    public WidgetsListHeader(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, /* defStyle= */ 0);
    }

    public WidgetsListHeader(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        ActivityContext activity = ActivityContext.lookupContext(context);
        DeviceProfile grid = activity.getDeviceProfile();
        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.WidgetsListRowHeader, defStyleAttr, /* defStyleRes= */ 0);
        mIconSize = a.getDimensionPixelSize(R.styleable.WidgetsListRowHeader_appIconSize,
                grid.iconSizePx);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAppIcon = findViewById(R.id.app_icon);
        mTitle = findViewById(R.id.app_title);
        mSubtitle = findViewById(R.id.app_subtitle);
        mExpandToggle = findViewById(R.id.toggle);
        findViewById(R.id.app_container).setAccessibilityDelegate(new AccessibilityDelegate() {

            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                if (mIsExpanded) {
                    info.removeAction(AccessibilityNodeInfo.ACTION_EXPAND);
                    info.addAction(AccessibilityNodeInfo.ACTION_COLLAPSE);
                } else {
                    info.removeAction(AccessibilityNodeInfo.ACTION_COLLAPSE);
                    info.addAction(AccessibilityNodeInfo.ACTION_EXPAND);
                }
                super.onInitializeAccessibilityNodeInfo(host, info);
            }

            @Override
            public boolean performAccessibilityAction(View host, int action, Bundle args) {
                switch (action) {
                    case AccessibilityNodeInfo.ACTION_EXPAND:
                    case AccessibilityNodeInfo.ACTION_COLLAPSE:
                        callOnClick();
                        return true;
                    default:
                        return super.performAccessibilityAction(host, action, args);
                }
            }
        });
    }

    /**
     * Sets a {@link OnExpansionChangeListener} to get a callback when this app widgets section
     * expands / collapses.
     */
    @UiThread
    public void setOnExpandChangeListener(
            @Nullable OnExpansionChangeListener onExpandChangeListener) {
        // Use the entire touch area of this view to expand / collapse an app widgets section.
        setOnClickListener(view -> {
            setExpanded(!mIsExpanded);
            if (onExpandChangeListener != null) {
                onExpandChangeListener.onExpansionChange(mIsExpanded);
            }
        });
    }

    /** Sets the expand toggle to expand / collapse. */
    @UiThread
    public void setExpanded(boolean isExpanded) {
        this.mIsExpanded = isExpanded;
        mExpandToggle.setChecked(isExpanded);
    }

    /** Sets the {@link WidgetsListDrawableState} and refreshes the background drawable. */
    @UiThread
    public void setListDrawableState(WidgetsListDrawableState state) {
        if (state == mListDrawableState) return;
        this.mListDrawableState = state;
        refreshDrawableState();
    }

    /** Apply app icon, labels and tag using a generic {@link WidgetsListHeaderEntry}. */
    @UiThread
    public void applyFromItemInfoWithIcon(WidgetsListHeaderEntry entry) {
        applyIconAndLabel(entry);
    }

    @UiThread
    private void applyIconAndLabel(WidgetsListHeaderEntry entry) {
        PackageItemInfo info = entry.mPkgItem;
        setIcon(info);
        setTitles(entry);
        setExpanded(entry.isWidgetListShown());

        super.setTag(info);

        verifyHighRes();
    }

    private void setIcon(PackageItemInfo info) {
        Drawable icon;
        if (info.widgetCategory == NO_CATEGORY) {
            icon = info.newIcon(getContext());
        } else {
            WidgetSection widgetSection = WidgetSections.getWidgetSections(getContext())
                    .get(info.widgetCategory);
            icon = getContext().getDrawable(widgetSection.mSectionDrawable);
        }
        applyDrawables(icon);
        mIconDrawable = icon;
        if (mIconDrawable != null) {
            mIconDrawable.setVisible(
                    /* visible= */ getWindowVisibility() == VISIBLE && isShown(),
                    /* restart= */ false);
        }
    }

    private void applyDrawables(Drawable icon) {
        icon.setBounds(0, 0, mIconSize, mIconSize);

        LinearLayout.LayoutParams layoutParams =
                (LinearLayout.LayoutParams) mAppIcon.getLayoutParams();
        layoutParams.width = mIconSize;
        layoutParams.height = mIconSize;
        mAppIcon.setLayoutParams(layoutParams);
        mAppIcon.setImageDrawable(icon);

        // If the current icon is a placeholder color, animate its update.
        if (mIconDrawable != null
                && mIconDrawable instanceof PlaceHolderIconDrawable
                && mEnableIconUpdateAnimation) {
            ((PlaceHolderIconDrawable) mIconDrawable).animateIconUpdate(icon);
        }
    }

    private void setTitles(WidgetsListHeaderEntry entry) {
        mTitle.setText(entry.mPkgItem.title);

        Resources resources = getContext().getResources();
        if (entry.widgetsCount == 0 && entry.shortcutsCount == 0) {
            mSubtitle.setVisibility(GONE);
            return;
        }

        String subtitle;
        if (entry.widgetsCount > 0 && entry.shortcutsCount > 0) {
            String widgetsCount = PluralMessageFormat.getIcuPluralString(getContext(),
                    R.string.widgets_count, entry.widgetsCount);
            String shortcutsCount = PluralMessageFormat.getIcuPluralString(getContext(),
                    R.string.shortcuts_count, entry.shortcutsCount);
            subtitle = resources.getString(R.string.widgets_and_shortcuts_count, widgetsCount,
                    shortcutsCount);
        } else if (entry.widgetsCount > 0) {
            subtitle = PluralMessageFormat.getIcuPluralString(getContext(),
                    R.string.widgets_count, entry.widgetsCount);
        } else {
            subtitle = PluralMessageFormat.getIcuPluralString(getContext(),
                    R.string.shortcuts_count, entry.shortcutsCount);
        }
        mSubtitle.setText(subtitle);
        mSubtitle.setVisibility(VISIBLE);
    }

    /** Apply app icon, labels and tag using a generic {@link WidgetsListSearchHeaderEntry}. */
    @UiThread
    public void applyFromItemInfoWithIcon(WidgetsListSearchHeaderEntry entry) {
        applyIconAndLabel(entry);
    }

    @UiThread
    private void applyIconAndLabel(WidgetsListSearchHeaderEntry entry) {
        PackageItemInfo info = entry.mPkgItem;
        setIcon(info);
        setTitles(entry);
        setExpanded(entry.isWidgetListShown());

        super.setTag(info);

        verifyHighRes();
    }

    private void setTitles(WidgetsListSearchHeaderEntry entry) {
        mTitle.setText(entry.mPkgItem.title);

        mSubtitle.setText(entry.mWidgets.stream()
                .map(item -> item.label).sorted().collect(Collectors.joining(", ")));
        mSubtitle.setVisibility(VISIBLE);
    }

    @Override
    public void reapplyItemInfo(ItemInfoWithIcon info) {
        if (getTag() == info) {
            mIconLoadRequest = null;
            mEnableIconUpdateAnimation = true;

            // Optimization: Starting in N, pre-uploads the bitmap to RenderThread.
            info.bitmap.icon.prepareToDraw();

            setIcon((PackageItemInfo) info);

            mEnableIconUpdateAnimation = false;
        }
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        if (mListDrawableState == null) return super.onCreateDrawableState(extraSpace);
        // Augment the state set from the super implementation with the custom states from
        // mListDrawableState.
        int[] drawableState =
                super.onCreateDrawableState(extraSpace + mListDrawableState.mStateSet.length);
        mergeDrawableStates(drawableState, mListDrawableState.mStateSet);
        return drawableState;
    }

    /** Verifies that the current icon is high-res otherwise posts a request to load the icon. */
    public void verifyHighRes() {
        if (mIconLoadRequest != null) {
            mIconLoadRequest.cancel();
            mIconLoadRequest = null;
        }
        if (getTag() instanceof ItemInfoWithIcon) {
            ItemInfoWithIcon info = (ItemInfoWithIcon) getTag();
            if (info.usingLowResIcon()) {
                mIconLoadRequest = LauncherAppState.getInstance(getContext()).getIconCache()
                        .updateIconInBackground(this, info);
            }
        }
    }

    /** A listener for the widget section expansion / collapse events. */
    public interface OnExpansionChangeListener {
        /** Notifies that the widget section is expanded or collapsed. */
        void onExpansionChange(boolean isExpanded);
    }
}
