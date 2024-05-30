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

import static android.animation.ValueAnimator.areAnimatorsEnabled;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
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
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.util.CancellableTask;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;

/**
 * A UI represents a header of an app shown in the full widgets tray.
 *
 * It is a {@link LinearLayout} which contains an app icon, an app name, a subtitle and a checkbox
 * which indicates if the widgets content view underneath this header should be shown.
 */
public final class WidgetsListHeader extends LinearLayout implements ItemInfoUpdateReceiver {

    private static final int[] EXPANDED_DRAWABLE_STATE = new int[] {android.R.attr.state_expanded};

    private final int mIconSize;
    /**
     * Indicates if the header is collapsable. For example, when displayed in a two pane layout,
     * widget apps aren't collapsable.
    */
    private final boolean mIsCollapsable;
    @Nullable private CancellableTask mIconLoadRequest;
    @Nullable private Drawable mIconDrawable;
    @Nullable private WidgetsListDrawableState mListDrawableState;
    private ImageView mAppIcon;
    private TextView mTitle;
    private TextView mSubtitle;
    private boolean mEnableIconUpdateAnimation = false;
    private boolean mIsExpanded = false;

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
        mIsCollapsable = a.getBoolean(R.styleable.WidgetsListRowHeader_collapsable, true);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAppIcon = findViewById(R.id.app_icon);
        mTitle = findViewById(R.id.app_title);
        mSubtitle = findViewById(R.id.app_subtitle);
        // Lists that cannot collapse, don't need EXPAND or COLLAPSE accessibility actions.
        if (mIsCollapsable) {
            setAccessibilityDelegate(new AccessibilityDelegate() {

                @Override
                public void onInitializeAccessibilityNodeInfo(View host,
                        AccessibilityNodeInfo info) {
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
    }

    /** Sets the expand toggle to expand / collapse. */
    @UiThread
    public void setExpanded(boolean isExpanded) {
        this.mIsExpanded = isExpanded;
        refreshDrawableState();
    }

    /** @return true if this header is expanded. */
    public boolean isExpanded() {
        return mIsExpanded;
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
        PackageItemInfo info = entry.mPkgItem;
        setIcon(info.newIcon(getContext()));
        setTitles(entry);
        setExpanded(entry.isWidgetListShown());

        super.setTag(info);

        verifyHighRes();
    }

    void setIcon(Drawable icon) {
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

        String subtitle = entry.getSubtitle(getContext());
        if (TextUtils.isEmpty(subtitle)) {
            mSubtitle.setVisibility(GONE);
        } else {
            mSubtitle.setText(subtitle);
            mSubtitle.setVisibility(VISIBLE);
        }
    }

    @Override
    public void reapplyItemInfo(ItemInfoWithIcon info) {
        if (getTag() == info) {
            mIconLoadRequest = null;
            mEnableIconUpdateAnimation = areAnimatorsEnabled();

            // Optimization: Starting in N, pre-uploads the bitmap to RenderThread.
            info.bitmap.icon.prepareToDraw();

            setIcon(info.newIcon(getContext()));

            mEnableIconUpdateAnimation = false;
        }
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        // We create a drawable state with an additional two spaces to be able to fit expanded state
        // and the list drawable state.
        int[] drawableState = super.onCreateDrawableState(extraSpace + 2);
        if (mIsExpanded) {
            mergeDrawableStates(drawableState, EXPANDED_DRAWABLE_STATE);
        }
        if (mListDrawableState != null) {
            mergeDrawableStates(drawableState, mListDrawableState.mStateSet);
        }
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
}
