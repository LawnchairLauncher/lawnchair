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
package com.android.launcher3.keyboard;

import static android.app.Activity.DEFAULT_KEYS_SEARCH_LOCAL;

import static com.android.launcher3.LauncherState.EDIT_MODE;
import static com.android.launcher3.LauncherState.SPRING_LOADED;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.PagedView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.accessibility.DragAndDropAccessibilityDelegate;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.statemanager.StateManager.StateListener;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.Themes;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;

/**
 * A floating view to allow keyboard navigation across virtual nodes
 */
public class KeyboardDragAndDropView extends AbstractFloatingView
        implements Insettable, StateListener<LauncherState> {

    private static final long MINOR_AXIS_WEIGHT = 13;

    private final ArrayList<Integer> mIntList = new ArrayList<>();
    private final ArrayList<DragAndDropAccessibilityDelegate> mDelegates = new ArrayList<>();
    private final ArrayList<VirtualNodeInfo> mNodes = new ArrayList<>();

    private final Rect mTempRect = new Rect();
    private final Rect mTempRect2 = new Rect();
    private final AccessibilityNodeInfoCompat mTempNodeInfo = AccessibilityNodeInfoCompat.obtain();

    private final RectFocusIndicator mFocusIndicator;

    private final Launcher mLauncher;
    private VirtualNodeInfo mCurrentSelection;


    public KeyboardDragAndDropView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyboardDragAndDropView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(context);
        mFocusIndicator = new RectFocusIndicator(this);
        setWillNotDraw(false);
    }

    @Override
    protected void handleClose(boolean animate) {
        mLauncher.getDragLayer().removeView(this);
        mLauncher.getStateManager().removeStateListener(this);
        mLauncher.setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);
        mIsOpen = false;
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_DRAG_DROP_POPUP) != 0;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        // Consume all touch
        return true;
    }

    @Override
    public void setInsets(Rect insets) {
        setPadding(insets.left, insets.top, insets.right, insets.bottom);
    }

    @Override
    public void onStateTransitionStart(LauncherState toState) {
        if (toState != SPRING_LOADED && toState != EDIT_MODE) {
            close(false);
        }
    }

    @Override
    public void onStateTransitionComplete(LauncherState finalState) {
        if (mCurrentSelection != null) {
            setCurrentSelection(mCurrentSelection);
        }
    }

    private void setCurrentSelection(VirtualNodeInfo nodeInfo) {
        mCurrentSelection = nodeInfo;
        ((TextView) findViewById(R.id.label))
                .setText(nodeInfo.populate(mTempNodeInfo).getContentDescription());

        Rect bounds = new Rect();
        mTempNodeInfo.getBoundsInParent(bounds);
        View host = nodeInfo.delegate.getHost();
        ViewParent parent = host.getParent();
        if (parent instanceof PagedView) {
            PagedView pv = (PagedView) parent;
            int pageIndex = pv.indexOfChild(host);

            pv.setCurrentPage(pageIndex);
            bounds.offset(pv.getScrollX() - pv.getScrollForPage(pageIndex), 0);
        }
        float[] pos = new float[] {bounds.left, bounds.top, bounds.right, bounds.bottom};
        Utilities.getDescendantCoordRelativeToAncestor(host, mLauncher.getDragLayer(), pos, true);

        new RectF(pos[0], pos[1], pos[2], pos[3]).roundOut(bounds);
        mFocusIndicator.changeFocus(bounds, true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mFocusIndicator.draw(canvas);
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        VirtualNodeInfo nodeInfo = getNextSelection(direction);
        if (nodeInfo == null) {
            return false;
        }
        setCurrentSelection(nodeInfo);
        return true;
    }

    /**
     * Focus finding logic:
     * Collect all virtual nodes in reading order (used for forward and backwards).
     * Then find the closest view by comparing the distances spatially. Since it is a move
     * operation. consider all cell sizes to be approximately of the same size.
     */
    private VirtualNodeInfo getNextSelection(int direction) {
        // Collect all virtual nodes
        mDelegates.clear();
        mNodes.clear();

        Folder openFolder = Folder.getOpen(mLauncher);
        PagedView pv = openFolder == null ? mLauncher.getWorkspace() : openFolder.getContent();
        int count = pv.getPageCount();
        for (int i = 0; i < count; i++) {
            mDelegates.add(((CellLayout) pv.getChildAt(i)).getDragAndDropAccessibilityDelegate());
        }
        if (openFolder == null) {
            mDelegates.add(pv.getNextPage() + 1,
                    mLauncher.getHotseat().getDragAndDropAccessibilityDelegate());
        }
        mDelegates.forEach(delegate -> {
            mIntList.clear();
            delegate.getVisibleVirtualViews(mIntList);
            mIntList.forEach(id -> mNodes.add(new VirtualNodeInfo(delegate, id)));
        });

        if (mNodes.isEmpty()) {
            return null;
        }
        int index = mNodes.indexOf(mCurrentSelection);
        if (mCurrentSelection == null || index < 0) {
            return null;
        }
        int totalNodes = mNodes.size();

        final ToIntBiFunction<Rect, Rect> majorAxis;
        final ToIntFunction<Rect> minorAxis;

        switch (direction) {
            case View.FOCUS_RIGHT:
                majorAxis = (source, dest) -> dest.left - source.left;
                minorAxis = Rect::centerY;
                break;
            case View.FOCUS_LEFT:
                majorAxis = (source, dest) -> source.left - dest.left;
                minorAxis = Rect::centerY;
                break;
            case View.FOCUS_UP:
                majorAxis = (source, dest) -> source.top - dest.top;
                minorAxis = Rect::centerX;
                break;
            case View.FOCUS_DOWN:
                majorAxis = (source, dest) -> dest.top - source.top;
                minorAxis = Rect::centerX;
                break;
            case View.FOCUS_FORWARD:
                return mNodes.get((index + 1) % totalNodes);
            case View.FOCUS_BACKWARD:
                return mNodes.get((index + totalNodes - 1) % totalNodes);
            default:
                // Unknown direction
                return null;
        }
        mCurrentSelection.populate(mTempNodeInfo).getBoundsInScreen(mTempRect);

        float minWeight = Float.MAX_VALUE;
        VirtualNodeInfo match = null;
        for (int i = 0; i < totalNodes; i++) {
            VirtualNodeInfo node = mNodes.get(i);
            node.populate(mTempNodeInfo).getBoundsInScreen(mTempRect2);

            int majorAxisWeight = majorAxis.applyAsInt(mTempRect, mTempRect2);
            if (majorAxisWeight <= 0) {
                continue;
            }
            int minorAxisWeight = minorAxis.applyAsInt(mTempRect2)
                    - minorAxis.applyAsInt(mTempRect);

            float weight = majorAxisWeight * majorAxisWeight
                    + minorAxisWeight * minorAxisWeight * MINOR_AXIS_WEIGHT;
            if (weight < minWeight) {
                minWeight = weight;
                match = node;
            }
        }
        return match;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER && mCurrentSelection != null) {
            mCurrentSelection.delegate.onPerformActionForVirtualView(
                    mCurrentSelection.id, AccessibilityNodeInfoCompat.ACTION_CLICK, null);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Shows the keyboard drag popup for the provided view
     */
    public void showForIcon(View icon, ItemInfo item, DragOptions dragOptions) {
        mIsOpen = true;
        mLauncher.getDragLayer().addView(this);
        mLauncher.getStateManager().addStateListener(this);

        // Find current selection
        CellLayout currentParent = (CellLayout) icon.getParent().getParent();
        float[] iconPos = new float[] {currentParent.getCellWidth() / 2,
                currentParent.getCellHeight() / 2};
        Utilities.getDescendantCoordRelativeToAncestor(icon, currentParent, iconPos, false);

        ItemLongClickListener.beginDrag(icon, mLauncher, item, dragOptions);

        DragAndDropAccessibilityDelegate dndDelegate =
                currentParent.getDragAndDropAccessibilityDelegate();
        setCurrentSelection(new VirtualNodeInfo(
                dndDelegate, dndDelegate.getVirtualViewAt(iconPos[0], iconPos[1])));

        mLauncher.setDefaultKeyMode(Activity.DEFAULT_KEYS_DISABLE);
        requestFocus();
    }

    private static class VirtualNodeInfo {
        public final DragAndDropAccessibilityDelegate delegate;
        public final int id;

        VirtualNodeInfo(DragAndDropAccessibilityDelegate delegate, int id) {
            this.id = id;
            this.delegate = delegate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof VirtualNodeInfo)) {
                return false;
            }
            VirtualNodeInfo that = (VirtualNodeInfo) o;
            return id == that.id && delegate.equals(that.delegate);
        }

        public AccessibilityNodeInfoCompat populate(AccessibilityNodeInfoCompat nodeInfo) {
            delegate.onPopulateNodeForVirtualView(id, nodeInfo);
            return nodeInfo;
        }

        public void getBounds(AccessibilityNodeInfoCompat nodeInfo, Rect out) {
            delegate.onPopulateNodeForVirtualView(id, nodeInfo);
            nodeInfo.getBoundsInScreen(out);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, delegate);
        }
    }

    private static class RectFocusIndicator extends ItemFocusIndicatorHelper<Rect> {

        RectFocusIndicator(View container) {
            super(container, Themes.getColorAccent(container.getContext()));
            mPaint.setStrokeWidth(container.getResources()
                    .getDimension(R.dimen.keyboard_drag_stroke_width));
            mPaint.setStyle(Style.STROKE);
        }

        @Override
        public void viewToRect(Rect item, Rect outRect) {
            outRect.set(item);
        }
    }
}
