package com.android.launcher3;

import static com.android.launcher3.ResourceUtils.INVALID_RESOURCE_HANDLE;
import static com.android.launcher3.config.FeatureFlags.SEPARATE_RECENTS_ACTIVITY;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.ViewDebug;
import android.view.WindowInsets;

import androidx.annotation.RequiresApi;

import com.android.launcher3.graphics.SysUiScrim;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.uioverrides.ApiWrapper;

import java.util.Collections;
import java.util.List;

public class LauncherRootView extends InsettableFrameLayout {

    private final Rect mTempRect = new Rect();

    private final StatefulActivity mActivity;

    @ViewDebug.ExportedProperty(category = "launcher")
    private static final List<Rect> SYSTEM_GESTURE_EXCLUSION_RECT =
            Collections.singletonList(new Rect());

    private WindowStateListener mWindowStateListener;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mDisallowBackGesture;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mForceHideBackArrow;

    private final SysUiScrim mSysUiScrim;

    public LauncherRootView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mActivity = StatefulActivity.fromContext(context);
        mSysUiScrim = new SysUiScrim(this);
    }

    private void handleSystemWindowInsets(Rect insets) {
        // Update device profile before notifying the children.
        mActivity.getDeviceProfile().updateInsets(insets);
        boolean resetState = !insets.equals(mInsets);
        setInsets(insets);

        if (resetState) {
            mActivity.getStateManager().reapplyState(true /* cancelCurrentAnimation */);
        }
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        if (Utilities.ATLEAST_R) {
            insets = updateInsetsDueToTaskbar(insets);
            Insets systemWindowInsets = insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            mTempRect.set(systemWindowInsets.left, systemWindowInsets.top, systemWindowInsets.right,
                    systemWindowInsets.bottom);
        } else {
            mTempRect.set(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
        }
        handleSystemWindowInsets(mTempRect);
        return insets;
    }

    /**
     * Taskbar provides nav bar and tappable insets. However, taskbar is not attached immediately,
     * and can be destroyed and recreated. Thus, instead of relying on taskbar being present to
     * get its insets, we calculate them ourselves so they are stable regardless of whether taskbar
     * is currently attached.
     *
     * @param oldInsets The system-provided insets, which we are modifying.
     * @return The updated insets.
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    private WindowInsets updateInsetsDueToTaskbar(WindowInsets oldInsets) {
        if (!ApiWrapper.TASKBAR_DRAWN_IN_PROCESS) {
            // 3P launchers based on Launcher3 should still be inset like normal.
            return oldInsets;
        }

        WindowInsets.Builder updatedInsetsBuilder = new WindowInsets.Builder(oldInsets);

        DeviceProfile dp = mActivity.getDeviceProfile();
        Resources resources = getResources();

        Insets oldNavInsets = oldInsets.getInsets(WindowInsets.Type.navigationBars());
        Rect newNavInsets = new Rect(oldNavInsets.left, oldNavInsets.top, oldNavInsets.right,
                oldNavInsets.bottom);

        if (dp.isLandscape) {
            boolean isGesturalMode = ResourceUtils.getIntegerByName(
                    "config_navBarInteractionMode",
                    resources,
                    INVALID_RESOURCE_HANDLE) == 2;
            if (dp.isTablet || isGesturalMode) {
                newNavInsets.bottom = dp.isTaskbarPresent
                        ? 0
                        : ResourceUtils.getNavbarSize("navigation_bar_height_landscape", resources);
            } else {
                int navWidth = ResourceUtils.getNavbarSize("navigation_bar_width", resources);
                if (dp.isSeascape()) {
                    newNavInsets.left = navWidth;
                } else {
                    newNavInsets.right = navWidth;
                }
            }
        } else {
            newNavInsets.bottom = dp.isTaskbarPresent
                    ? 0
                    : ResourceUtils.getNavbarSize("navigation_bar_height", resources);
        }
        updatedInsetsBuilder.setInsets(WindowInsets.Type.navigationBars(), Insets.of(newNavInsets));
        updatedInsetsBuilder.setInsetsIgnoringVisibility(WindowInsets.Type.navigationBars(),
                Insets.of(newNavInsets));

        mActivity.updateWindowInsets(updatedInsetsBuilder, oldInsets);

        return updatedInsetsBuilder.build();
    }

    @Override
    public void setInsets(Rect insets) {
        // If the insets haven't changed, this is a no-op. Avoid unnecessary layout caused by
        // modifying child layout params.
        if (!insets.equals(mInsets)) {
            super.setInsets(insets);
            mSysUiScrim.onInsetsChanged(insets);
        }
    }

    public void dispatchInsets() {
        mActivity.getDeviceProfile().updateInsets(mInsets);
        super.setInsets(mInsets);
    }

    public void setWindowStateListener(WindowStateListener listener) {
        mWindowStateListener = listener;
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (mWindowStateListener != null) {
            mWindowStateListener.onWindowFocusChanged(hasWindowFocus);
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (mWindowStateListener != null) {
            mWindowStateListener.onWindowVisibilityChanged(visibility);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        mSysUiScrim.draw(canvas);
        super.dispatchDraw(canvas);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        SYSTEM_GESTURE_EXCLUSION_RECT.get(0).set(l, t, r, b);
        setDisallowBackGesture(mDisallowBackGesture);
        mSysUiScrim.setSize(r - l, b - t);
    }

    @TargetApi(Build.VERSION_CODES.Q)
    public void setForceHideBackArrow(boolean forceHideBackArrow) {
        this.mForceHideBackArrow = forceHideBackArrow;
        setDisallowBackGesture(mDisallowBackGesture);
    }

    @TargetApi(Build.VERSION_CODES.Q)
    public void setDisallowBackGesture(boolean disallowBackGesture) {
        if (!Utilities.ATLEAST_Q || SEPARATE_RECENTS_ACTIVITY.get()) {
            return;
        }
        mDisallowBackGesture = disallowBackGesture;
        setSystemGestureExclusionRects((mForceHideBackArrow || mDisallowBackGesture)
                ? SYSTEM_GESTURE_EXCLUSION_RECT
                : Collections.emptyList());
    }

    public SysUiScrim getSysUiScrim() {
        return mSysUiScrim;
    }

    public interface WindowStateListener {

        void onWindowFocusChanged(boolean hasFocus);

        void onWindowVisibilityChanged(int visibility);
    }
}
