/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.shortcuts;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.content.Context;
import android.graphics.Point;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.anim.PropertyListBuilder;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.logging.UserEventDispatcher.LogContainerProvider;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.popup.PopupItemView;
import com.android.launcher3.popup.PopupPopulator;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.userevent.nano.LauncherLogProto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link PopupItemView} that contains all of the {@link DeepShortcutView}s for an app,
 * as well as the system shortcuts such as Widgets and App Info.
 */
public class ShortcutsItemView extends PopupItemView implements View.OnLongClickListener,
        View.OnTouchListener, LogContainerProvider {

    private Launcher mLauncher;
    private LinearLayout mShortcutsLayout;
    private LinearLayout mSystemShortcutIcons;
    private final Point mIconShift = new Point();
    private final Point mIconLastTouchPos = new Point();
    private final List<DeepShortcutView> mDeepShortcutViews = new ArrayList<>();
    private final List<View> mSystemShortcutViews = new ArrayList<>();

    public ShortcutsItemView(Context context) {
        this(context, null, 0);
    }

    public ShortcutsItemView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ShortcutsItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mLauncher = Launcher.getLauncher(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mShortcutsLayout = findViewById(R.id.deep_shortcuts);
    }

    @Override
    public boolean onTouch(View v, MotionEvent ev) {
        // Touched a shortcut, update where it was touched so we can drag from there on long click.
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                mIconLastTouchPos.set((int) ev.getX(), (int) ev.getY());
                break;
        }
        return false;
    }

    @Override
    public boolean onLongClick(View v) {
        // Return early if this is not initiated from a touch or not the correct view
        if (!v.isInTouchMode() || !(v.getParent() instanceof DeepShortcutView)) return false;
        // Return early if global dragging is not enabled
        if (!mLauncher.isDraggingEnabled()) return false;
        // Return early if an item is already being dragged (e.g. when long-pressing two shortcuts)
        if (mLauncher.getDragController().isDragging()) return false;

        // Long clicked on a shortcut.
        DeepShortcutView sv = (DeepShortcutView) v.getParent();
        sv.setWillDrawIcon(false);

        // Move the icon to align with the center-top of the touch point
        mIconShift.x = mIconLastTouchPos.x - sv.getIconCenter().x;
        mIconShift.y = mIconLastTouchPos.y - mLauncher.getDeviceProfile().iconSizePx;

        DragView dv = mLauncher.getWorkspace().beginDragShared(sv.getIconView(),
                (PopupContainerWithArrow) getParent(), sv.getFinalInfo(),
                new ShortcutDragPreviewProvider(sv.getIconView(), mIconShift), new DragOptions());
        dv.animateShift(-mIconShift.x, -mIconShift.y);

        // TODO: support dragging from within folder without having to close it
        AbstractFloatingView.closeOpenContainer(mLauncher, AbstractFloatingView.TYPE_FOLDER);
        return false;
    }

    public void addShortcutView(View shortcutView, PopupPopulator.Item shortcutType) {
        addShortcutView(shortcutView, shortcutType, -1);
    }

    private void addShortcutView(View shortcutView, PopupPopulator.Item shortcutType, int index) {
        if (shortcutType == PopupPopulator.Item.SHORTCUT) {
            mDeepShortcutViews.add((DeepShortcutView) shortcutView);
        } else {
            mSystemShortcutViews.add(shortcutView);
        }
        if (shortcutType == PopupPopulator.Item.SYSTEM_SHORTCUT_ICON) {
            // System shortcut icons are added to a header that is separate from the full shortcuts.
            if (mSystemShortcutIcons == null) {
                mSystemShortcutIcons = (LinearLayout) mLauncher.getLayoutInflater().inflate(
                        R.layout.system_shortcut_icons, mShortcutsLayout, false);
                mShortcutsLayout.addView(mSystemShortcutIcons, 0);
            }
            mSystemShortcutIcons.addView(shortcutView, index);
        } else {
            if (mShortcutsLayout.getChildCount() > 0) {
                View prevChild = mShortcutsLayout.getChildAt(mShortcutsLayout.getChildCount() - 1);
                if (prevChild instanceof DeepShortcutView) {
                    prevChild.findViewById(R.id.divider).setVisibility(VISIBLE);
                }
            }
            mShortcutsLayout.addView(shortcutView, index);
        }
    }

    public List<DeepShortcutView> getDeepShortcutViews(boolean reverseOrder) {
        if (reverseOrder) {
            Collections.reverse(mDeepShortcutViews);
        }
        return mDeepShortcutViews;
    }

    public List<View> getSystemShortcutViews(boolean reverseOrder) {
        // Always reverse system shortcut icons (in the header)
        // so they are in priority order from right to left.
        if (reverseOrder || mSystemShortcutIcons != null) {
            Collections.reverse(mSystemShortcutViews);
        }
        return mSystemShortcutViews;
    }

    /**
     * Adds a {@link SystemShortcut.Widgets} item if there are widgets for the given ItemInfo.
     */
    public void enableWidgetsIfExist(final BubbleTextView originalIcon) {
        ItemInfo itemInfo = (ItemInfo) originalIcon.getTag();
        SystemShortcut widgetInfo = new SystemShortcut.Widgets();
        View.OnClickListener onClickListener = widgetInfo.getOnClickListener(mLauncher, itemInfo);
        View widgetsView = null;
        for (View systemShortcutView : mSystemShortcutViews) {
            if (systemShortcutView.getTag() instanceof SystemShortcut.Widgets) {
                widgetsView = systemShortcutView;
                break;
            }
        }
        final PopupPopulator.Item widgetsItem = mSystemShortcutIcons == null
                ? PopupPopulator.Item.SYSTEM_SHORTCUT
                : PopupPopulator.Item.SYSTEM_SHORTCUT_ICON;
        if (onClickListener != null && widgetsView == null) {
            // We didn't have any widgets cached but now there are some, so enable the shortcut.
            widgetsView = mLauncher.getLayoutInflater().inflate(widgetsItem.layoutId, this, false);
            PopupPopulator.initializeSystemShortcut(getContext(), widgetsView, widgetInfo);
            widgetsView.setOnClickListener(onClickListener);
            if (widgetsItem == PopupPopulator.Item.SYSTEM_SHORTCUT_ICON) {
                addShortcutView(widgetsView, widgetsItem, 0);
            } else {
                // If using the expanded system shortcut (as opposed to just the icon), we need to
                // reopen the container to ensure measurements etc. all work out. While this could
                // be quite janky, in practice the user would typically see a small flicker as the
                // animation restarts partway through, and this is a very rare edge case anyway.
                ((PopupContainerWithArrow) getParent()).close(false);
                PopupContainerWithArrow.showForIcon(originalIcon);
            }
        } else if (onClickListener == null && widgetsView != null) {
            // No widgets exist, but we previously added the shortcut so remove it.
            if (widgetsItem == PopupPopulator.Item.SYSTEM_SHORTCUT_ICON) {
                mSystemShortcutViews.remove(widgetsView);
                mSystemShortcutIcons.removeView(widgetsView);
            } else {
                ((PopupContainerWithArrow) getParent()).close(false);
                PopupContainerWithArrow.showForIcon(originalIcon);
            }
        }
    }

    @Override
    public Animator createOpenAnimation(boolean isContainerAboveIcon, boolean pivotLeft) {
        AnimatorSet openAnimation = LauncherAnimUtils.createAnimatorSet();
        openAnimation.play(super.createOpenAnimation(isContainerAboveIcon, pivotLeft));
        for (int i = 0; i < mShortcutsLayout.getChildCount(); i++) {
            if (!(mShortcutsLayout.getChildAt(i) instanceof DeepShortcutView)) {
                continue;
            }
            DeepShortcutView shortcutView = ((DeepShortcutView) mShortcutsLayout.getChildAt(i));
            View deepShortcutIcon = shortcutView.getIconView();
            deepShortcutIcon.setScaleX(0);
            deepShortcutIcon.setScaleY(0);
            openAnimation.play(LauncherAnimUtils.ofPropertyValuesHolder(
                    deepShortcutIcon, new PropertyListBuilder().scale(1).build()));
        }
        return openAnimation;
    }

    @Override
    public Animator createCloseAnimation(boolean isContainerAboveIcon, boolean pivotLeft,
            long duration) {
        AnimatorSet closeAnimation = LauncherAnimUtils.createAnimatorSet();
        closeAnimation.play(super.createCloseAnimation(isContainerAboveIcon, pivotLeft, duration));
        for (int i = 0; i < mShortcutsLayout.getChildCount(); i++) {
            if (!(mShortcutsLayout.getChildAt(i) instanceof DeepShortcutView)) {
                continue;
            }
            DeepShortcutView shortcutView = ((DeepShortcutView) mShortcutsLayout.getChildAt(i));
            View deepShortcutIcon = shortcutView.getIconView();
            deepShortcutIcon.setScaleX(1);
            deepShortcutIcon.setScaleY(1);
            closeAnimation.play(LauncherAnimUtils.ofPropertyValuesHolder(
                    deepShortcutIcon, new PropertyListBuilder().scale(0).build()));
        }
        return closeAnimation;
    }

    @Override
    public int getArrowColor(boolean isArrowAttachedToBottom) {
        return ContextCompat.getColor(getContext(),
                isArrowAttachedToBottom || mSystemShortcutIcons == null
                        ? R.color.popup_background_color
                        : R.color.popup_header_background_color);
    }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, LauncherLogProto.Target target,
            LauncherLogProto.Target targetParent) {
        target.itemType = LauncherLogProto.ItemType.DEEPSHORTCUT;
        target.rank = info.rank;
        targetParent.containerType = LauncherLogProto.ContainerType.DEEPSHORTCUTS;
    }
}
