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

package ch.deletescape.lawnchair.shortcuts;

import android.content.Context;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ch.deletescape.lawnchair.AbstractFloatingView;
import ch.deletescape.lawnchair.BubbleTextView;
import ch.deletescape.lawnchair.ItemInfo;
import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.config.FeatureFlags;
import ch.deletescape.lawnchair.dragndrop.DragOptions;
import ch.deletescape.lawnchair.dragndrop.DragView;
import ch.deletescape.lawnchair.popup.PopupContainerWithArrow;
import ch.deletescape.lawnchair.popup.PopupItemView;
import ch.deletescape.lawnchair.popup.PopupPopulator;
import ch.deletescape.lawnchair.popup.SystemShortcut;

/**
 * A {@link PopupItemView} that contains all of the {@link DeepShortcutView}s for an app,
 * as well as the system shortcuts such as Widgets and App Info.
 */
public class ShortcutsItemView extends PopupItemView implements View.OnLongClickListener,
        View.OnTouchListener {

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
        if (!mLauncher.isDraggingEnabled() || mLauncher.isEditingDisabled()) return false;
        // Return early if an item is already being dragged (e.g. when long-pressing two shortcuts)
        if (mLauncher.getDragController().isDragging()) return false;

        // Long clicked on a shortcut.
        DeepShortcutView sv = (DeepShortcutView) v.getParent();
        sv.setWillDrawIcon(false);

        // Move the icon to align with the center-top of the touch point
        mIconShift.x = mIconLastTouchPos.x - sv.getIconCenter().x;
        mIconShift.y = mIconLastTouchPos.y - mLauncher.getDeviceProfile().iconSizePx;

        DragView dv = mLauncher.getWorkspace().beginDragShared(sv.getIconView(),
                getContainer(), sv.getFinalInfo(),
                new ShortcutDragPreviewProvider(sv.getIconView(), mIconShift), new DragOptions());
        dv.animateShift(-mIconShift.x, -mIconShift.y);
        mLauncher.closeFolder(true);
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
                LayoutInflater layoutInflater = LayoutInflater.from(FeatureFlags.INSTANCE.applyDarkTheme(mLauncher, FeatureFlags.DARK_SHORTCUTS));
                mSystemShortcutIcons = (LinearLayout) layoutInflater.inflate(
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
                getContainer().close(false);
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
    public int getArrowColor(boolean isArrowAttachedToBottom) {
        return Utilities.resolveAttributeData(getContext(),
                isArrowAttachedToBottom || mSystemShortcutIcons == null
                        ? R.attr.popupColorPrimary
                        : R.attr.appPopupHeaderBgColor);
    }
}
