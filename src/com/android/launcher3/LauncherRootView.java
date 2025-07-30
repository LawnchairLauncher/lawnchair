package com.android.launcher3;

import static com.android.launcher3.config.FeatureFlags.SEPARATE_RECENTS_ACTIVITY;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ViewDebug;
import android.view.WindowInsets;

import com.android.launcher3.graphics.SysUiScrim;
import com.android.launcher3.statemanager.StatefulActivity;
import com.hoko.blur.HokoBlur;
import com.patrykmichalik.opto.core.PreferenceExtensionsKt;
import com.android.launcher3.util.window.WindowManagerProxy;

import java.util.Collections;
import java.util.List;

import app.lawnchair.preferences.PreferenceManager;
import app.lawnchair.preferences2.PreferenceManager2;
import app.lawnchair.util.FileAccessManager;
import app.lawnchair.util.FileAccessState;

public class LauncherRootView extends InsettableFrameLayout {

    private final Rect mTempRect = new Rect();

    private final StatefulActivity mActivity;

    @ViewDebug.ExportedProperty(category = "launcher")
    private static final List<Rect> SYSTEM_GESTURE_EXCLUSION_RECT = Collections.singletonList(new Rect());

    private WindowStateListener mWindowStateListener;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mDisallowBackGesture;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mForceHideBackArrow;

    private final SysUiScrim mSysUiScrim;
    private final boolean mEnableTaskbarOnPhone;

    private final PreferenceManager pref;

    public LauncherRootView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mActivity = StatefulActivity.fromContext(context);
        mSysUiScrim = new SysUiScrim(this);

        pref = PreferenceManager.getInstance(context);
        PreferenceManager2 prefs2 = PreferenceManager2.getInstance(context);
        
        mEnableTaskbarOnPhone = PreferenceExtensionsKt.firstBlocking(prefs2.getEnableTaskbarOnPhone());

        FileAccessManager fileAccessManager = FileAccessManager.getInstance(context);
        FileAccessState wallpaperAccessState = fileAccessManager.getWallpaperAccessState().getValue();
        if (pref.getEnableWallpaperBlur().get() && wallpaperAccessState != FileAccessState.Denied.INSTANCE) {
            setUpBlur(context);
        }
    }

    private void setUpBlur(Context context) {
        var display = mActivity.getDeviceProfile();
        int width = display.widthPx;
        int height = display.heightPx;

        var wallpaper = getScaledWallpaperDrawable(width, height);
        if (wallpaper == null) {
            return;
        }

        Bitmap originalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(originalBitmap);

        wallpaper.setBounds(0, 0, width, height);
        wallpaper.draw(canvas);

        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setAlpha((int) (0.2 * 255));
        canvas.drawRect(0, 0, width, height, paint);

        Bitmap blurredBitmap = HokoBlur.with(context)
                .forceCopy(true)
                .scheme(HokoBlur.SCHEME_OPENGL)
                .sampleFactor(pref.getWallpaperBlurFactorThreshold().get())
                .radius(pref.getWallpaperBlur().get())
                .blur(originalBitmap);

        setBackground(new BitmapDrawable(getContext().getResources(), blurredBitmap));
    }

    private Drawable getScaledWallpaperDrawable(int width, int height) {
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(getContext());
        Drawable wallpaperDrawable = wallpaperManager.getDrawable();

        if (wallpaperDrawable != null) {
            Bitmap originalBitmap = Bitmap.createBitmap(
                    width, height, Bitmap.Config.ARGB_8888
            );
            Canvas canvas = new Canvas(originalBitmap);

            wallpaperDrawable.setBounds(0, 0, width, height);
            wallpaperDrawable.draw(canvas);

            return new BitmapDrawable(getContext().getResources(), originalBitmap);
        }
        return null;
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
        mActivity.handleConfigurationChanged(mActivity.getResources().getConfiguration());

        insets = WindowManagerProxy.INSTANCE.get(getContext())
                .normalizeWindowInsets(getContext(), insets, mTempRect);
        handleSystemWindowInsets(mTempRect);
        return insets;
    }

    @Override
    public void setInsets(Rect insets) {
        // If the insets haven't changed, this is a no-op. Avoid unnecessary layout
        // caused by
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
        mSysUiScrim.setSize(r - l, b - t);
    }

    public void setForceHideBackArrow(boolean forceHideBackArrow) {
        this.mForceHideBackArrow = forceHideBackArrow;
        setDisallowBackGesture(mDisallowBackGesture);
    }

    public void setDisallowBackGesture(boolean disallowBackGesture) {
        if (SEPARATE_RECENTS_ACTIVITY.get()) {
            return;
        }
        mDisallowBackGesture = disallowBackGesture;
        if (Utilities.ATLEAST_Q) {
            setSystemGestureExclusionRects((mForceHideBackArrow || mDisallowBackGesture)
                    ? SYSTEM_GESTURE_EXCLUSION_RECT
                    : Collections.emptyList());
        }
    }

    public SysUiScrim getSysUiScrim() {
        return mSysUiScrim;
    }

    public interface WindowStateListener {

        void onWindowFocusChanged(boolean hasFocus);

        void onWindowVisibilityChanged(int visibility);
    }
}
