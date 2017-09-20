/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3.widget;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.graphics.Point;
import android.support.v7.widget.LinearLayoutManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.launcher3.BaseContainerView;
import com.android.launcher3.DeleteDropTarget;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.AlphabeticIndexCompat;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.model.PackageItemInfo;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.util.MultiHashMap;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Thunk;

import java.util.List;

/**
 * The widgets list view container.
 */
public class WidgetsContainerView extends BaseContainerView
        implements View.OnLongClickListener, View.OnClickListener, DragSource {
    private static final String TAG = "WidgetsContainerView";
    private static final boolean LOGD = false;

    /* Global instances that are used inside this container. */
    @Thunk Launcher mLauncher;

    /* Recycler view related member variables */
    private WidgetsRecyclerView mRecyclerView;
    private WidgetsListAdapter mAdapter;

    /* Touch handling related member variables. */
    private Toast mWidgetInstructionToast;

    public WidgetsContainerView(Context context) {
        this(context, null);
    }

    public WidgetsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(context);
        LauncherAppState apps = LauncherAppState.getInstance(context);
        mAdapter = new WidgetsListAdapter(context, LayoutInflater.from(context),
                apps.getWidgetCache(), new AlphabeticIndexCompat(context), this, this,
                new WidgetsDiffReporter(apps.getIconCache()));
        mAdapter.setNotifyListener();
        if (LOGD) {
            Log.d(TAG, "WidgetsContainerView constructor");
        }
    }

    @Override
    public View getTouchDelegateTargetView() {
        return mRecyclerView;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mRecyclerView = (WidgetsRecyclerView) getContentView().findViewById(R.id.widgets_list_view);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    //
    // Returns views used for launcher transitions.
    //

    public void scrollToTop() {
        mRecyclerView.scrollToPosition(0);
    }

    //
    // Touch related handling.
    //

    @Override
    public void onClick(View v) {
        // When we have exited widget tray or are in transition, disregard clicks
        if (!mLauncher.isWidgetsViewVisible()
                || mLauncher.getWorkspace().isSwitchingState()
                || !(v instanceof WidgetCell)) return;

        handleClick();
    }

    public void handleClick() {
        // Let the user know that they have to long press to add a widget
        if (mWidgetInstructionToast != null) {
            mWidgetInstructionToast.cancel();
        }

        CharSequence msg = Utilities.wrapForTts(
                getContext().getText(R.string.long_press_widget_to_add),
                getContext().getString(R.string.long_accessible_way_to_add));
        mWidgetInstructionToast = Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT);
        mWidgetInstructionToast.show();
    }

    @Override
    public boolean onLongClick(View v) {
        // When we have exited the widget tray, disregard long clicks
        if (!mLauncher.isWidgetsViewVisible()) return false;
        return handleLongClick(v);
    }

    public boolean handleLongClick(View v) {
        if (LOGD) {
            Log.d(TAG, String.format("onLongClick [v=%s]", v));
        }
        // When we  are in transition, disregard long clicks
        if (mLauncher.getWorkspace().isSwitchingState()) return false;
        // Return if global dragging is not enabled
        if (!mLauncher.isDraggingEnabled()) return false;

        return beginDragging(v);
    }

    private boolean beginDragging(View v) {
        if (v instanceof WidgetCell) {
            if (!beginDraggingWidget((WidgetCell) v)) {
                return false;
            }
        } else {
            Log.e(TAG, "Unexpected dragging view: " + v);
        }

        // We don't enter spring-loaded mode if the drag has been cancelled
        if (mLauncher.getDragController().isDragging()) {
            // Go into spring loaded mode (must happen before we startDrag())
            mLauncher.enterSpringLoadedDragMode();
        }

        return true;
    }

    private boolean beginDraggingWidget(WidgetCell v) {
        // Get the widget preview as the drag representation
        WidgetImageView image = (WidgetImageView) v.findViewById(R.id.widget_preview);

        // If the ImageView doesn't have a drawable yet, the widget preview hasn't been loaded and
        // we abort the drag.
        if (image.getBitmap() == null) {
            return false;
        }

        int[] loc = new int[2];
        mLauncher.getDragLayer().getLocationInDragLayer(image, loc);

        new PendingItemDragHelper(v).startDrag(
                image.getBitmapBounds(), image.getBitmap().getWidth(), image.getWidth(),
                new Point(loc[0], loc[1]), this, new DragOptions());
        return true;
    }

    //
    // Drag related handling methods that implement {@link DragSource} interface.
    //

    @Override
    public boolean supportsAppInfoDropTarget() {
        return true;
    }

    /*
     * Both this method and {@link #supportsFlingToDelete} has to return {@code false} for the
     * {@link DeleteDropTarget} to be invisible.)
     */
    @Override
    public boolean supportsDeleteDropTarget() {
        return false;
    }

    @Override
    public float getIntrinsicIconScaleFactor() {
        return 0;
    }

    @Override
    public void onDropCompleted(View target, DragObject d, boolean isFlingToDelete,
            boolean success) {
        if (LOGD) {
            Log.d(TAG, "onDropCompleted");
        }
        if (isFlingToDelete || !success || (target != mLauncher.getWorkspace() &&
                !(target instanceof DeleteDropTarget) && !(target instanceof Folder))) {
            // Exit spring loaded mode if we have not successfully dropped or have not handled the
            // drop in Workspace
            mLauncher.exitSpringLoadedDragModeDelayed(true,
                    Launcher.EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT, null);
        }
        mLauncher.unlockScreenOrientation(false);

        if (!success) {
            d.deferDragViewCleanupPostAnimation = false;
        }
    }

    /**
     * Initialize the widget data model.
     */
    public void setWidgets(MultiHashMap<PackageItemInfo, WidgetItem> model) {
        mAdapter.setWidgets(model);

        View loader = getContentView().findViewById(R.id.loader);
        if (loader != null) {
            ((ViewGroup) getContentView()).removeView(loader);
        }
    }

    public boolean isEmpty() {
        return mAdapter.getItemCount() == 0;
    }

    public List<WidgetItem> getWidgetsForPackageUser(PackageUserKey packageUserKey) {
        return mAdapter.copyWidgetsForPackageUser(packageUserKey);
    }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, Target target, Target targetParent) {
        targetParent.containerType = ContainerType.WIDGETS;
    }
}