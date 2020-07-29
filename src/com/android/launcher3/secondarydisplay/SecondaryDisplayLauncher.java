/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.secondarydisplay;

import static com.android.launcher3.model.AppLaunchTracker.CONTAINER_ALL_APPS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewAnimationUtils;
import android.view.inputmethod.InputMethodManager;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.PromiseAppInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.ViewOnDrawExecutor;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.widget.WidgetListRowEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Launcher activity for secondary displays
 */
public class SecondaryDisplayLauncher extends BaseDraggingActivity
        implements BgDataModel.Callbacks {

    private LauncherModel mModel;

    private BaseDragLayer mDragLayer;
    private AllAppsContainerView mAppsView;
    private View mAppsButton;

    private PopupDataProvider mPopupDataProvider;

    private boolean mAppDrawerShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mModel = LauncherAppState.getInstance(this).getModel();
        if (getWindow().getDecorView().isAttachedToWindow()) {
            initUi();
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        initUi();
    }

    private void initUi() {
        if (mDragLayer != null) {
            return;
        }
        InvariantDeviceProfile currentDisplayIdp =
                new InvariantDeviceProfile(this, getWindow().getDecorView().getDisplay());

        // Disable transpose layout and use multi-window mode so that the icons are scaled properly
        mDeviceProfile = currentDisplayIdp.getDeviceProfile(this)
                .toBuilder(this)
                .setMultiWindowMode(true)
                .setTransposeLayoutWithOrientation(false)
                .build();
        mDeviceProfile.autoResizeAllAppsCells();

        setContentView(R.layout.secondary_launcher);
        mDragLayer = findViewById(R.id.drag_layer);
        mAppsView = findViewById(R.id.apps_view);
        mAppsButton = findViewById(R.id.all_apps_button);

        mPopupDataProvider = new PopupDataProvider(
                mAppsView.getAppsStore()::updateNotificationDots);

        mModel.addCallbacksAndLoad(this);
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            // Hide keyboard.
            final View v = getWindow().peekDecorView();
            if (v != null && v.getWindowToken() != null) {
                getSystemService(InputMethodManager.class).hideSoftInputFromWindow(
                        v.getWindowToken(), 0);
            }
        }

        // A new intent will bring the launcher to top. Hide the app drawer to reset the state.
        showAppDrawer(false);
    }

    @Override
    public void onBackPressed() {
        if (finishAutoCancelActionMode()) {
            return;
        }

        // Note: There should be at most one log per method call. This is enforced implicitly
        // by using if-else statements.
        AbstractFloatingView topView = AbstractFloatingView.getTopOpenView(this);
        if (topView != null && topView.onBackPressed()) {
            // Handled by the floating view.
        } else {
            showAppDrawer(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mModel.removeCallbacks(this);
    }

    public boolean isAppDrawerShown() {
        return mAppDrawerShown;
    }

    public AllAppsContainerView getAppsView() {
        return mAppsView;
    }

    @Override
    public <T extends View> T getOverviewPanel() {
        return null;
    }

    @Override
    public View getRootView() {
        return mDragLayer;
    }

    @Override
    public ActivityOptions getActivityLaunchOptions(View v) {
        return null;
    }

    @Override
    protected void reapplyUi() { }

    @Override
    public BaseDragLayer getDragLayer() {
        return mDragLayer;
    }

    @Override
    public int getPageToBindSynchronously() {
        return 0;
    }

    @Override
    public void clearPendingBinds() { }

    @Override
    public void startBinding() { }

    @Override
    public void bindItems(List<ItemInfo> shortcuts, boolean forceAnimateIcons) { }

    @Override
    public void bindPredictedItems(List<AppInfo> appInfos, IntArray ranks) { }

    @Override
    public void bindScreens(IntArray orderedScreenIds) { }

    @Override
    public void finishFirstPageBind(ViewOnDrawExecutor executor) {
        if (executor != null) {
            executor.onLoadAnimationCompleted();
        }
    }

    @Override
    public void finishBindingItems(int pageBoundFirst) { }

    @Override
    public void preAddApps() { }

    @Override
    public void bindAppsAdded(IntArray newScreens, ArrayList<ItemInfo> addNotAnimated,
            ArrayList<ItemInfo> addAnimated) { }

    @Override
    public void bindPromiseAppProgressUpdated(PromiseAppInfo app) {
        mAppsView.getAppsStore().updatePromiseAppProgress(app);
    }

    @Override
    public void bindWorkspaceItemsChanged(ArrayList<WorkspaceItemInfo> updated) { }

    @Override
    public void bindWidgetsRestored(ArrayList<LauncherAppWidgetInfo> widgets) { }

    @Override
    public void bindRestoreItemsChange(HashSet<ItemInfo> updates) { }

    @Override
    public void bindWorkspaceComponentsRemoved(ItemInfoMatcher matcher) { }

    @Override
    public void bindAllWidgets(ArrayList<WidgetListRowEntry> widgets) { }

    @Override
    public void onPageBoundSynchronously(int page) { }

    @Override
    public void executeOnNextDraw(ViewOnDrawExecutor executor) {
        executor.attachTo(getDragLayer(), false, null);
    }

    /**
     * Called when apps-button is clicked
     */
    public void onAppsButtonClicked(View v) {
        showAppDrawer(true);
    }

    /**
     * Show/hide app drawer card with animation.
     */
    public void showAppDrawer(boolean show) {
        if (show == mAppDrawerShown) {
            return;
        }

        float openR = (float) Math.hypot(mAppsView.getWidth(), mAppsView.getHeight());
        float closeR = Themes.getDialogCornerRadius(this);
        float startR = mAppsButton.getWidth() / 2f;

        float[] buttonPos = new float[] { startR, startR};
        mDragLayer.getDescendantCoordRelativeToSelf(mAppsButton, buttonPos);
        mDragLayer.mapCoordInSelfToDescendant(mAppsView, buttonPos);
        final Animator animator = ViewAnimationUtils.createCircularReveal(mAppsView,
                (int) buttonPos[0], (int) buttonPos[1],
                show ? closeR : openR, show ? openR : closeR);

        if (show) {
            mAppDrawerShown = true;
            mAppsView.setVisibility(View.VISIBLE);
            mAppsButton.setVisibility(View.INVISIBLE);
        } else {
            mAppDrawerShown = false;
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mAppsView.setVisibility(View.INVISIBLE);
                    mAppsButton.setVisibility(View.VISIBLE);
                    mAppsView.getSearchUiManager().resetSearch();
                }
            });
        }
        animator.start();
    }

    @Override
    public void bindDeepShortcutMap(HashMap<ComponentKey, Integer> deepShortcutMap) {
        mPopupDataProvider.setDeepShortcutMap(deepShortcutMap);
    }

    @Override
    public void bindAllApplications(AppInfo[] apps, int flags) {
        mAppsView.getAppsStore().setApps(apps, flags);
    }

    public PopupDataProvider getPopupDataProvider() {
        return mPopupDataProvider;
    }

    @Override
    public OnClickListener getItemOnClickListener() {
        return this::onIconClicked;
    }

    private void onIconClicked(View v) {
        // Make sure that rogue clicks don't get through while allapps is launching, or after the
        // view has detached (it's possible for this to happen if the view is removed mid touch).
        if (v.getWindowToken() == null) return;

        Object tag = v.getTag();
        if (tag instanceof ItemInfo) {
            ItemInfo item = (ItemInfo) tag;
            Intent intent;
            if (item instanceof PromiseAppInfo) {
                PromiseAppInfo promiseAppInfo = (PromiseAppInfo) item;
                intent = promiseAppInfo.getMarketIntent(this);
            } else {
                intent = item.getIntent();
            }
            if (intent == null) {
                throw new IllegalArgumentException("Input must have a valid intent");
            }
            startActivitySafely(v, intent, item, CONTAINER_ALL_APPS);
        }
    }
}
