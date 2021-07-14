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
package com.android.launcher3.taskbar;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static com.android.systemui.shared.system.WindowManagerWrapper.ITYPE_BOTTOM_TAPPABLE_ELEMENT;
import static com.android.systemui.shared.system.WindowManagerWrapper.ITYPE_EXTRA_NAVIGATION_BAR;

import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.os.SystemProperties;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.R;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.taskbar.TaskbarNavButtonController.TaskbarButton;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.views.ActivityContext;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.WindowManagerWrapper;

/**
 * The {@link ActivityContext} with which we inflate Taskbar-related Views. This allows UI elements
 * that are used by both Launcher and Taskbar (such as Folder) to reference a generic
 * ActivityContext and BaseDragLayer instead of the Launcher activity and its DragLayer.
 */
public class TaskbarActivityContext extends ContextThemeWrapper implements ActivityContext {

    private static final boolean ENABLE_THREE_BUTTON_TASKBAR =
            SystemProperties.getBoolean("persist.debug.taskbar_three_button", false);
    private static final String TAG = "TaskbarActivityContext";

    private static final String WINDOW_TITLE = "Taskbar";

    private final DeviceProfile mDeviceProfile;
    private final LayoutInflater mLayoutInflater;
    private final TaskbarDragLayer mDragLayer;
    private final TaskbarIconController mIconController;
    private final MyDragController mDragController;

    private final WindowManager mWindowManager;
    private WindowManager.LayoutParams mWindowLayoutParams;

    private final SysUINavigationMode.Mode mNavMode;
    private final TaskbarNavButtonController mNavButtonController;

    private final boolean mIsSafeModeEnabled;

    @NonNull
    private TaskbarUIController mUIController = TaskbarUIController.DEFAULT;

    private final View.OnClickListener mOnTaskbarIconClickListener;
    private final View.OnLongClickListener mOnTaskbarIconLongClickListener;

    public TaskbarActivityContext(Context windowContext, DeviceProfile dp,
            TaskbarNavButtonController buttonController) {
        super(windowContext, Themes.getActivityThemeRes(windowContext));
        mDeviceProfile = dp;
        mNavButtonController = buttonController;
        mNavMode = SysUINavigationMode.getMode(windowContext);
        mIsSafeModeEnabled = TraceHelper.allowIpcs("isSafeMode",
                () -> getPackageManager().isSafeMode());

        mOnTaskbarIconLongClickListener =
                new TaskbarDragController(this)::startSystemDragOnLongClick;
        mOnTaskbarIconClickListener = this::onTaskbarIconClicked;

        float taskbarIconSize = getResources().getDimension(R.dimen.taskbar_icon_size);
        float iconScale = taskbarIconSize / mDeviceProfile.iconSizePx;
        mDeviceProfile.updateIconSize(iconScale, getResources());

        mLayoutInflater = LayoutInflater.from(this).cloneInContext(this);
        mDragLayer = (TaskbarDragLayer) mLayoutInflater
                .inflate(R.layout.taskbar, null, false);
        mIconController = new TaskbarIconController(this, mDragLayer);
        mDragController = new MyDragController(this);

        Display display = windowContext.getDisplay();
        Context c = display.getDisplayId() == Display.DEFAULT_DISPLAY
                ? windowContext.getApplicationContext()
                : windowContext.getApplicationContext().createDisplayContext(display);
        mWindowManager = c.getSystemService(WindowManager.class);
    }

    public void init() {
        mWindowLayoutParams = new WindowManager.LayoutParams(
                MATCH_PARENT,
                mDeviceProfile.taskbarSize,
                TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        mWindowLayoutParams.setTitle(WINDOW_TITLE);
        mWindowLayoutParams.packageName = getPackageName();
        mWindowLayoutParams.gravity = Gravity.BOTTOM;
        mWindowLayoutParams.setFitInsetsTypes(0);
        mWindowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        mWindowLayoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        mWindowLayoutParams.setSystemApplicationOverlay(true);

        WindowManagerWrapper wmWrapper = WindowManagerWrapper.getInstance();
        wmWrapper.setProvidesInsetsTypes(
                mWindowLayoutParams,
                new int[] { ITYPE_EXTRA_NAVIGATION_BAR, ITYPE_BOTTOM_TAPPABLE_ELEMENT }
        );

        mIconController.init(mOnTaskbarIconClickListener, mOnTaskbarIconLongClickListener);
        mWindowManager.addView(mDragLayer, mWindowLayoutParams);
    }

    /**
     * Updates the TaskbarContainer height (pass deviceProfile.taskbarSize to reset).
     */
    public void setTaskbarWindowHeight(int height) {
        if (mWindowLayoutParams.height == height) {
            return;
        }
        mWindowLayoutParams.height = height;
        mWindowManager.updateViewLayout(mDragLayer, mWindowLayoutParams);
    }

    public boolean canShowNavButtons() {
        return ENABLE_THREE_BUTTON_TASKBAR && mNavMode == Mode.THREE_BUTTONS;
    }

    @Override
    public LayoutInflater getLayoutInflater() {
        return mLayoutInflater;
    }

    @Override
    public TaskbarDragLayer getDragLayer() {
        return mDragLayer;
    }

    @Override
    public DeviceProfile getDeviceProfile() {
        return mDeviceProfile;
    }

    @Override
    public Rect getFolderBoundingBox() {
        return mDragLayer.getFolderBoundingBox();
    }

    @Override
    public DragController getDragController() {
        return mDragController;
    }

    /**
     * Sets a new data-source for this taskbar instance
     */
    public void setUIController(@NonNull TaskbarUIController uiController) {
        mUIController.onDestroy();
        mUIController = uiController;
        mIconController.setUIController(mUIController);
        mUIController.onCreate();
    }

    /**
     * Called when this instance of taskbar is no longer needed
     */
    public void onDestroy() {
        setUIController(TaskbarUIController.DEFAULT);
        mIconController.onDestroy();
        mWindowManager.removeViewImmediate(mDragLayer);
    }

    void onNavigationButtonClick(@TaskbarButton int buttonType) {
        mNavButtonController.onButtonClick(buttonType);
    }

    /**
     * Should be called when the IME visibility changes, so we can hide/show Taskbar accordingly.
     */
    public void setImeIsVisible(boolean isImeVisible) {
        mIconController.setImeIsVisible(isImeVisible);
    }

    /**
     * When in 3 button nav, the above doesn't get called since we prevent sysui nav bar from
     * instantiating at all, which is what's responsible for sending sysui state flags over.
     *
     * @param vis IME visibility flag
     */
    public void updateImeStatus(int displayId, int vis, boolean showImeSwitcher) {
        mIconController.updateImeStatus(displayId, vis, showImeSwitcher);
    }

    /**
     * Updates the TaskbarContainer to MATCH_PARENT vs original Taskbar size.
     */
    protected void setTaskbarWindowFullscreen(boolean fullscreen) {
        setTaskbarWindowHeight(fullscreen ? MATCH_PARENT : getDeviceProfile().taskbarSize);
    }

    protected void onTaskbarIconClicked(View view) {
        Object tag = view.getTag();
        if (tag instanceof Task) {
            Task task = (Task) tag;
            ActivityManagerWrapper.getInstance().startActivityFromRecents(task.key,
                    ActivityOptions.makeBasic());
        } else if (tag instanceof FolderInfo) {
            FolderIcon folderIcon = (FolderIcon) view;
            Folder folder = folderIcon.getFolder();
            setTaskbarWindowFullscreen(true);

            getDragLayer().post(() -> {
                folder.animateOpen();

                folder.iterateOverItems((itemInfo, itemView) -> {
                    itemView.setOnClickListener(mOnTaskbarIconClickListener);
                    itemView.setOnLongClickListener(mOnTaskbarIconLongClickListener);
                    // To play haptic when dragging, like other Taskbar items do.
                    itemView.setHapticFeedbackEnabled(true);
                    return false;
                });
            });
        } else if (tag instanceof WorkspaceItemInfo) {
            WorkspaceItemInfo info = (WorkspaceItemInfo) tag;
            if (!(info.isDisabled() && ItemClickHandler.handleDisabledItemClicked(info, this))) {
                Intent intent = new Intent(info.getIntent())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    if (mIsSafeModeEnabled && !PackageManagerHelper.isSystemApp(this, intent)) {
                        Toast.makeText(this, R.string.safemode_shortcut_error,
                                Toast.LENGTH_SHORT).show();
                    } else  if (info.isPromise()) {
                        intent = new PackageManagerHelper(this)
                                .getMarketIntent(info.getTargetPackage())
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);

                    } else if (info.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                        String id = info.getDeepShortcutId();
                        String packageName = intent.getPackage();
                        getSystemService(LauncherApps.class)
                                .startShortcut(packageName, id, null, null, info.user);
                    } else if (info.user.equals(Process.myUserHandle())) {
                        startActivity(intent);
                    } else {
                        getSystemService(LauncherApps.class).startMainActivity(
                                intent.getComponent(), info.user, intent.getSourceBounds(), null);
                    }
                } catch (NullPointerException | ActivityNotFoundException | SecurityException e) {
                    Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT)
                            .show();
                    Log.e(TAG, "Unable to launch. tag=" + info + " intent=" + intent, e);
                }
            }
        } else {
            Log.e(TAG, "Unknown type clicked: " + tag);
        }

        AbstractFloatingView.closeAllOpenViews(this);
    }

    private static class MyDragController extends DragController<TaskbarActivityContext> {
        MyDragController(TaskbarActivityContext activity) {
            super(activity);
        }

        @Override
        protected DragView startDrag(@Nullable Drawable drawable, @Nullable View view,
                DraggableView originalView, int dragLayerX, int dragLayerY, DragSource source,
                ItemInfo dragInfo, Point dragOffset, Rect dragRegion, float initialDragViewScale,
                float dragViewScaleOnDrop, DragOptions options) {
            return null;
        }

        @Override
        protected void exitDrag() {
        }

        @Override
        protected DropTarget getDefaultDropTarget(int[] dropCoordinates) {
            return null;
        }
    }
}
