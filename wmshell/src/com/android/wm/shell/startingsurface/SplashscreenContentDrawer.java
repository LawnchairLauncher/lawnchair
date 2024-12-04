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

package com.android.wm.shell.startingsurface;

import static android.content.Context.CONTEXT_RESTRICTED;
import static android.os.Process.THREAD_PRIORITY_TOP_APP_BOOST;
import static android.os.Trace.TRACE_TAG_WINDOW_MANAGER;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.window.StartingWindowInfo.STARTING_WINDOW_TYPE_LEGACY_SPLASH_SCREEN;
import static android.window.StartingWindowInfo.STARTING_WINDOW_TYPE_SOLID_COLOR_SPLASH_SCREEN;
import static android.window.StartingWindowInfo.STARTING_WINDOW_TYPE_SPLASH_SCREEN;
import static android.window.StartingWindowInfo.TYPE_PARAMETER_APP_PREFERS_ICON;

import android.annotation.ColorInt;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityThread;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.Trace;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.DisplayMetrics;
import android.util.Slog;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.SplashScreenView;
import android.window.StartingWindowInfo;
import android.window.StartingWindowInfo.StartingWindowType;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.graphics.palette.Palette;
import com.android.internal.graphics.palette.Quantizer;
import com.android.internal.graphics.palette.VariationalKMeansQuantizer;
import com.android.internal.policy.PhoneWindow;
import com.android.internal.protolog.common.ProtoLog;
import com.android.launcher3.icons.BaseIconFactory;
import com.android.launcher3.icons.IconProvider;
import com.android.wm.shell.common.TransactionPool;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Util class to create the view for a splash screen content.
 * Everything execute in this class should be post to mSplashscreenWorkerHandler.
 * @hide
 */
public class SplashscreenContentDrawer {
    private static final String TAG = StartingWindowController.TAG;

    /**
     * The minimum duration during which the splash screen is shown when the splash screen icon is
     * animated.
     */
    static final long MINIMAL_ANIMATION_DURATION = 400L;

    /**
     * Allow the icon style splash screen to be displayed for longer to give time for the animation
     * to finish, i.e. the extra buffer time to keep the splash screen if the animation is slightly
     * longer than the {@link #MINIMAL_ANIMATION_DURATION} duration.
     */
    static final long TIME_WINDOW_DURATION = 100L;

    /**
     * The maximum duration during which the splash screen will be shown if the application is ready
     * to show before the icon animation finishes.
     */
    static final long MAX_ANIMATION_DURATION = MINIMAL_ANIMATION_DURATION + TIME_WINDOW_DURATION;

    private final Context mContext;
    private final HighResIconProvider mHighResIconProvider;
    private int mIconSize;
    private int mDefaultIconSize;
    private int mBrandingImageWidth;
    private int mBrandingImageHeight;
    private int mMainWindowShiftLength;
    private float mEnlargeForegroundIconThreshold;
    private float mNoBackgroundScale;
    private int mLastPackageContextConfigHash;
    private final TransactionPool mTransactionPool;
    private final SplashScreenWindowAttrs mTmpAttrs = new SplashScreenWindowAttrs();
    private final Handler mSplashscreenWorkerHandler;
    private final boolean mCanUseAppIconForSplashScreen;
    @VisibleForTesting
    final ColorCache mColorCache;

    SplashscreenContentDrawer(Context context, IconProvider iconProvider, TransactionPool pool) {
        mContext = context;
        mHighResIconProvider = new HighResIconProvider(mContext, iconProvider);
        mTransactionPool = pool;

        // Initialize Splashscreen worker thread
        // TODO(b/185288910) move it into WMShellConcurrencyModule and provide an executor to make
        //  it easier to test stuff that happens on that thread later.
        final HandlerThread shellSplashscreenWorkerThread =
                new HandlerThread("wmshell.splashworker", THREAD_PRIORITY_TOP_APP_BOOST);
        shellSplashscreenWorkerThread.start();
        mSplashscreenWorkerHandler = shellSplashscreenWorkerThread.getThreadHandler();
        mColorCache = new ColorCache(mContext, mSplashscreenWorkerHandler);
        mCanUseAppIconForSplashScreen = context.getResources().getBoolean(
                com.android.wm.shell.R.bool.config_canUseAppIconForSplashScreen);
    }

    /**
     * Help method to create a layout parameters for a window.
     */
    static Context createContext(Context initContext, StartingWindowInfo windowInfo,
            int theme, @StartingWindowInfo.StartingWindowType int suggestType,
            DisplayManager displayManager) {
        final ActivityManager.RunningTaskInfo taskInfo = windowInfo.taskInfo;
        final ActivityInfo activityInfo = windowInfo.targetActivityInfo != null
                ? windowInfo.targetActivityInfo
                : taskInfo.topActivityInfo;
        if (activityInfo == null || activityInfo.packageName == null) {
            return null;
        }

        final int displayId = taskInfo.displayId;
        final int taskId = taskInfo.taskId;

        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                "addSplashScreen for package: %s with theme: %s for task: %d, suggestType: %d",
                activityInfo.packageName, Integer.toHexString(theme), taskId, suggestType);
        final Display display = displayManager.getDisplay(displayId);
        if (display == null) {
            // Can't show splash screen on requested display, so skip showing at all.
            return null;
        }
        Context context = displayId == DEFAULT_DISPLAY
                ? initContext : initContext.createDisplayContext(display);
        if (context == null) {
            return null;
        }
        if (theme != context.getThemeResId()) {
            try {
                context = context.createPackageContextAsUser(activityInfo.packageName,
                        CONTEXT_RESTRICTED, UserHandle.of(taskInfo.userId));
                context.setTheme(theme);
            } catch (PackageManager.NameNotFoundException e) {
                Slog.w(TAG, "Failed creating package context with package name "
                        + activityInfo.packageName + " for user " + taskInfo.userId, e);
                return null;
            }
        }

        final Configuration taskConfig = taskInfo.getConfiguration();
        if (taskConfig.diffPublicOnly(context.getResources().getConfiguration()) != 0) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                    "addSplashScreen: creating context based on task Configuration %s",
                    taskConfig);
            final Context overrideContext = context.createConfigurationContext(taskConfig);
            overrideContext.setTheme(theme);
            final TypedArray typedArray = overrideContext.obtainStyledAttributes(
                    com.android.internal.R.styleable.Window);
            final int resId = typedArray.getResourceId(R.styleable.Window_windowBackground, 0);
            try {
                if (resId != 0 && overrideContext.getDrawable(resId) != null) {
                    // We want to use the windowBackground for the override context if it is
                    // available, otherwise we use the default one to make sure a themed starting
                    // window is displayed for the app.
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                            "addSplashScreen: apply overrideConfig %s",
                            taskConfig);
                    context = overrideContext;
                }
            } catch (Resources.NotFoundException e) {
                Slog.w(TAG, "failed creating starting window for overrideConfig at taskId: "
                        + taskId, e);
                return null;
            }
            typedArray.recycle();
        }
        return context;
    }

    /**
     * Creates the window layout parameters for splashscreen window.
     */
    static WindowManager.LayoutParams createLayoutParameters(Context context,
            StartingWindowInfo windowInfo,
            @StartingWindowInfo.StartingWindowType int suggestType,
            CharSequence title, int pixelFormat, IBinder appToken) {
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_APPLICATION_STARTING);
        params.setFitInsetsSides(0);
        params.setFitInsetsTypes(0);
        params.format = pixelFormat;
        int windowFlags = WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
        final TypedArray a = context.obtainStyledAttributes(R.styleable.Window);
        if (a.getBoolean(R.styleable.Window_windowShowWallpaper, false)) {
            windowFlags |= WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
        }
        if (suggestType == STARTING_WINDOW_TYPE_LEGACY_SPLASH_SCREEN) {
            if (a.getBoolean(R.styleable.Window_windowDrawsSystemBarBackgrounds, false)) {
                windowFlags |= WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
            }
        } else {
            windowFlags |= WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        }

        final ActivityManager.RunningTaskInfo taskInfo = windowInfo.taskInfo;
        final ActivityInfo activityInfo = windowInfo.targetActivityInfo != null
                ? windowInfo.targetActivityInfo
                : taskInfo.topActivityInfo;
        final boolean isEdgeToEdgeEnforced = PhoneWindow.isEdgeToEdgeEnforced(
                activityInfo.applicationInfo, false /* local */, a);
        if (isEdgeToEdgeEnforced) {
            params.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_EDGE_TO_EDGE_ENFORCED;
        }
        params.layoutInDisplayCutoutMode = a.getInt(
                R.styleable.Window_windowLayoutInDisplayCutoutMode,
                isEdgeToEdgeEnforced
                        ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                        : params.layoutInDisplayCutoutMode);
        params.windowAnimations = a.getResourceId(R.styleable.Window_windowAnimationStyle, 0);
        a.recycle();

        final int displayId = taskInfo.displayId;
        // Assumes it's safe to show starting windows of launched apps while
        // the keyguard is being hidden. This is okay because starting windows never show
        // secret information.
        // TODO(b/113840485): Occluded may not only happen on default display
        if (displayId == DEFAULT_DISPLAY && windowInfo.isKeyguardOccluded) {
            windowFlags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        }

        // Force the window flags: this is a fake window, so it is not really
        // touchable or focusable by the user.  We also add in the ALT_FOCUSABLE_IM
        // flag because we do know that the next window will take input
        // focus, so we want to get the IME window up on top of us right away.
        // Touches will only pass through to the host activity window and will be blocked from
        // passing to any other windows.
        windowFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        params.flags = windowFlags;
        params.token = appToken;
        params.packageName = activityInfo.packageName;
        params.privateFlags |= WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
        params.setTitle("Splash Screen " + title);
        return params;
    }
    /**
     * Create a SplashScreenView object.
     *
     * In order to speed up the splash screen view to show on first frame, preparing the
     * view on background thread so the view and the drawable can be create and pre-draw in
     * parallel.
     *
     * @param suggestType Suggest type to create the splash screen view.
     * @param splashScreenViewConsumer Receiving the SplashScreenView object, which will also be
     *                                 executed on splash screen thread. Note that the view can be
     *                                 null if failed.
     */
    void createContentView(Context context, @StartingWindowType int suggestType,
            StartingWindowInfo info, Consumer<SplashScreenView> splashScreenViewConsumer,
            Consumer<Runnable> uiThreadInitConsumer) {
        mSplashscreenWorkerHandler.post(() -> {
            SplashScreenView contentView;
            try {
                Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "makeSplashScreenContentView");
                contentView = makeSplashScreenContentView(context, info, suggestType,
                        uiThreadInitConsumer);
                Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
            } catch (RuntimeException e) {
                Slog.w(TAG, "failed creating starting window content at taskId: "
                        + info.taskInfo.taskId, e);
                contentView = null;
            }
            splashScreenViewConsumer.accept(contentView);
        });
    }

    private void updateDensity() {
        mIconSize = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.starting_surface_icon_size);
        mDefaultIconSize = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.starting_surface_default_icon_size);
        mBrandingImageWidth = mContext.getResources().getDimensionPixelSize(
                com.android.wm.shell.R.dimen.starting_surface_brand_image_width);
        mBrandingImageHeight = mContext.getResources().getDimensionPixelSize(
                com.android.wm.shell.R.dimen.starting_surface_brand_image_height);
        mMainWindowShiftLength = mContext.getResources().getDimensionPixelSize(
                com.android.wm.shell.R.dimen.starting_surface_exit_animation_window_shift_length);
        mEnlargeForegroundIconThreshold = mContext.getResources().getFloat(
                com.android.wm.shell.R.dimen.splash_icon_enlarge_foreground_threshold);
        mNoBackgroundScale = mContext.getResources().getFloat(
                com.android.wm.shell.R.dimen.splash_icon_no_background_scale_factor);
    }

    /**
     * @return Current system background color.
     */
    public static int getSystemBGColor() {
        final Context systemContext = ActivityThread.currentApplication();
        if (systemContext == null) {
            Slog.e(TAG, "System context does not exist!");
            return Color.BLACK;
        }
        final Resources res = systemContext.getResources();
        return res.getColor(com.android.wm.shell.R.color.splash_window_background_default);
    }

    /**
     * Estimate the background color of the app splash screen, this may take a while so use it only
     * if there is no starting window exists for that context.
     **/
    int estimateTaskBackgroundColor(Context context) {
        final SplashScreenWindowAttrs windowAttrs = new SplashScreenWindowAttrs();
        getWindowAttrs(context, windowAttrs);
        return peekWindowBGColor(context, windowAttrs);
    }

    private static Drawable createDefaultBackgroundDrawable() {
        return new ColorDrawable(getSystemBGColor());
    }

    /** Extract the window background color from {@code attrs}. */
    private static int peekWindowBGColor(Context context, SplashScreenWindowAttrs attrs) {
        Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "peekWindowBGColor");
        final Drawable themeBGDrawable;
        if (attrs.mWindowBgColor != 0) {
            themeBGDrawable = new ColorDrawable(attrs.mWindowBgColor);
        } else if (attrs.mWindowBgResId != 0) {
            themeBGDrawable = context.getDrawable(attrs.mWindowBgResId);
        } else {
            themeBGDrawable = createDefaultBackgroundDrawable();
            Slog.w(TAG, "Window background does not exist, using " + themeBGDrawable);
        }
        final int estimatedWindowBGColor = estimateWindowBGColor(themeBGDrawable);
        Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
        return estimatedWindowBGColor;
    }

    private static int estimateWindowBGColor(Drawable themeBGDrawable) {
        final DrawableColorTester themeBGTester = new DrawableColorTester(
                themeBGDrawable, DrawableColorTester.TRANSLUCENT_FILTER /* filterType */);
        if (themeBGTester.passFilterRatio() < 0.5f) {
            // more than half pixels of the window background is translucent, unable to draw
            Slog.w(TAG, "Window background is translucent, fill background with black color");
            return getSystemBGColor();
        } else {
            return themeBGTester.getDominateColor();
        }
    }

    private static Drawable peekLegacySplashscreenContent(Context context,
            SplashScreenWindowAttrs attrs) {
        final TypedArray a = context.obtainStyledAttributes(R.styleable.Window);
        final int resId = safeReturnAttrDefault((def) ->
                a.getResourceId(R.styleable.Window_windowSplashscreenContent, def), 0);
        a.recycle();
        if (resId != 0) {
            return context.getDrawable(resId);
        }
        if (attrs.mWindowBgResId != 0) {
            return context.getDrawable(attrs.mWindowBgResId);
        }
        return null;
    }

    /**
     * Creates a SplashScreenView without read animatable icon and branding image.
     */
    SplashScreenView makeSimpleSplashScreenContentView(Context context,
            StartingWindowInfo info, int themeBGColor) {
        updateDensity();
        mTmpAttrs.reset();
        final ActivityInfo ai = info.targetActivityInfo != null
                ? info.targetActivityInfo
                : info.taskInfo.topActivityInfo;

        final SplashViewBuilder builder = new SplashViewBuilder(context, ai);
        final SplashScreenView view = builder
                .setWindowBGColor(themeBGColor)
                .chooseStyle(STARTING_WINDOW_TYPE_SOLID_COLOR_SPLASH_SCREEN)
                .build();
        view.setNotCopyable();
        return view;
    }

    private SplashScreenView makeSplashScreenContentView(Context context, StartingWindowInfo info,
            @StartingWindowType int suggestType, Consumer<Runnable> uiThreadInitConsumer) {
        updateDensity();

        getWindowAttrs(context, mTmpAttrs);
        mLastPackageContextConfigHash = context.getResources().getConfiguration().hashCode();

        final @StartingWindowType int splashType =
                suggestType == STARTING_WINDOW_TYPE_SPLASH_SCREEN && !canUseIcon(info)
                ? STARTING_WINDOW_TYPE_SOLID_COLOR_SPLASH_SCREEN : suggestType;
        final Drawable legacyDrawable = splashType == STARTING_WINDOW_TYPE_LEGACY_SPLASH_SCREEN
                ? peekLegacySplashscreenContent(context, mTmpAttrs) : null;
        final ActivityInfo ai = info.targetActivityInfo != null
                ? info.targetActivityInfo
                : info.taskInfo.topActivityInfo;
        final int themeBGColor = legacyDrawable != null
                ? getBGColorFromCache(ai, () -> estimateWindowBGColor(legacyDrawable))
                : getBGColorFromCache(ai, () -> peekWindowBGColor(context, mTmpAttrs));

        return new SplashViewBuilder(context, ai)
                .setWindowBGColor(themeBGColor)
                .overlayDrawable(legacyDrawable)
                .chooseStyle(splashType)
                .setUiThreadInitConsumer(uiThreadInitConsumer)
                .setAllowHandleSolidColor(info.allowHandleSolidColorSplashScreen())
                .build();
    }

    private boolean canUseIcon(StartingWindowInfo info) {
        return mCanUseAppIconForSplashScreen || mTmpAttrs.mSplashScreenIcon != null
             || (info.startingWindowTypeParameter & TYPE_PARAMETER_APP_PREFERS_ICON) != 0;
    }

    private int getBGColorFromCache(ActivityInfo ai, IntSupplier windowBgColorSupplier) {
        return mColorCache.getWindowColor(ai.packageName, mLastPackageContextConfigHash,
                mTmpAttrs.mWindowBgColor, mTmpAttrs.mWindowBgResId, windowBgColorSupplier).mBgColor;
    }

    private static <T> T safeReturnAttrDefault(UnaryOperator<T> getMethod, T def) {
        try {
            return getMethod.apply(def);
        } catch (RuntimeException e) {
            Slog.w(TAG, "Get attribute fail, return default: " + e.getMessage());
            return def;
        }
    }

    /**
     * Get the {@link SplashScreenWindowAttrs} from {@code context} and fill them into
     * {@code attrs}.
     */
    private static void getWindowAttrs(Context context, SplashScreenWindowAttrs attrs) {
        final TypedArray typedArray = context.obtainStyledAttributes(
                com.android.internal.R.styleable.Window);
        attrs.mWindowBgResId = typedArray.getResourceId(R.styleable.Window_windowBackground, 0);
        attrs.mWindowBgColor = safeReturnAttrDefault((def) -> typedArray.getColor(
                R.styleable.Window_windowSplashScreenBackground, def),
                Color.TRANSPARENT);
        attrs.mSplashScreenIcon = safeReturnAttrDefault((def) -> typedArray.getDrawable(
                R.styleable.Window_windowSplashScreenAnimatedIcon), null);
        attrs.mBrandingImage = safeReturnAttrDefault((def) -> typedArray.getDrawable(
                R.styleable.Window_windowSplashScreenBrandingImage), null);
        attrs.mIconBgColor = safeReturnAttrDefault((def) -> typedArray.getColor(
                R.styleable.Window_windowSplashScreenIconBackgroundColor, def),
                Color.TRANSPARENT);
        typedArray.recycle();
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                "getWindowAttrs: window attributes color: %s, replace icon: %b",
                Integer.toHexString(attrs.mWindowBgColor), attrs.mSplashScreenIcon != null);
    }

    /** Creates the wrapper with system theme to avoid unexpected styles from app. */
    ContextThemeWrapper createViewContextWrapper(Context appContext) {
        return new ContextThemeWrapper(appContext, mContext.getTheme());
    }

    /** The configuration of the splash screen window. */
    public static class SplashScreenWindowAttrs {
        private int mWindowBgResId = 0;
        private int mWindowBgColor = Color.TRANSPARENT;
        private Drawable mSplashScreenIcon = null;
        private Drawable mBrandingImage = null;
        private int mIconBgColor = Color.TRANSPARENT;

        void reset() {
            mWindowBgResId = 0;
            mWindowBgColor = Color.TRANSPARENT;
            mSplashScreenIcon = null;
            mBrandingImage = null;
            mIconBgColor = Color.TRANSPARENT;
        }
    }

    /**
     * Get an optimal animation duration to keep the splash screen from showing.
     *
     * @param animationDuration The animation duration defined from app.
     * @param appReadyDuration The real duration from the starting the app to the first app window
     *                         drawn.
     */
    @VisibleForTesting
    static long getShowingDuration(long animationDuration, long appReadyDuration) {
        if (animationDuration <= appReadyDuration) {
            // app window ready took longer time than animation, it can be removed ASAP.
            return appReadyDuration;
        }
        if (appReadyDuration < MAX_ANIMATION_DURATION) {
            if (animationDuration > MAX_ANIMATION_DURATION
                    || appReadyDuration < MINIMAL_ANIMATION_DURATION) {
                // animation is too long or too short, cut off with minimal duration
                return MINIMAL_ANIMATION_DURATION;
            }
            // animation is longer than dOpt but shorter than max, allow it to play till finish
            return MAX_ANIMATION_DURATION;
        }
        // the shortest duration is longer than dMax, cut off no matter how long the animation
        // will be.
        return appReadyDuration;
    }

    private class SplashViewBuilder {
        private final Context mContext;
        private final ActivityInfo mActivityInfo;

        private Drawable mOverlayDrawable;
        private int mSuggestType;
        private int mThemeColor;
        private Drawable[] mFinalIconDrawables;
        private int mFinalIconSize = mIconSize;
        private Consumer<Runnable> mUiThreadInitTask;
        /** @see #setAllowHandleSolidColor(boolean) **/
        private boolean mAllowHandleSolidColor;

        SplashViewBuilder(@NonNull Context context, @NonNull ActivityInfo aInfo) {
            mContext = context;
            mActivityInfo = aInfo;
        }

        SplashViewBuilder setWindowBGColor(@ColorInt int background) {
            mThemeColor = background;
            return this;
        }

        SplashViewBuilder overlayDrawable(Drawable overlay) {
            mOverlayDrawable = overlay;
            return this;
        }

        SplashViewBuilder chooseStyle(int suggestType) {
            mSuggestType = suggestType;
            return this;
        }

        // Set up the UI thread for the View.
        SplashViewBuilder setUiThreadInitConsumer(Consumer<Runnable> uiThreadInitTask) {
            mUiThreadInitTask = uiThreadInitTask;
            return this;
        }

        /**
         * If true, the application will receive a the
         * {@link
         * android.window.SplashScreen.OnExitAnimationListener#onSplashScreenExit(SplashScreenView)}
         * callback, effectively copying the {@link SplashScreenView} into the client process.
         */
        SplashViewBuilder setAllowHandleSolidColor(boolean allowHandleSolidColor) {
            mAllowHandleSolidColor = allowHandleSolidColor;
            return this;
        }

        SplashScreenView build() {
            Drawable iconDrawable;
            if (mSuggestType == STARTING_WINDOW_TYPE_SOLID_COLOR_SPLASH_SCREEN
                    || mSuggestType == STARTING_WINDOW_TYPE_LEGACY_SPLASH_SCREEN) {
                // empty or legacy splash screen case
                mFinalIconSize = 0;
            } else if (mTmpAttrs.mSplashScreenIcon != null) {
                // Using the windowSplashScreenAnimatedIcon attribute
                iconDrawable = mTmpAttrs.mSplashScreenIcon;

                // There is no background below the icon, so scale the icon up
                if (mTmpAttrs.mIconBgColor == Color.TRANSPARENT
                        || mTmpAttrs.mIconBgColor == mThemeColor) {
                    mFinalIconSize *= mNoBackgroundScale;
                }
                createIconDrawable(iconDrawable, false /* legacy */, false /* loadInDetail */);
            } else {
                final float iconScale = (float) mIconSize / (float) mDefaultIconSize;
                final int densityDpi = mContext.getResources().getConfiguration().densityDpi;
                final int scaledIconDpi =
                        (int) (0.5f + iconScale * densityDpi * mNoBackgroundScale);
                Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "getIcon");
                iconDrawable = mHighResIconProvider.getIcon(
                        mActivityInfo, densityDpi, scaledIconDpi);
                Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
                if (!processAdaptiveIcon(iconDrawable)) {
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                            "The icon is not an AdaptiveIconDrawable");
                    Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "legacy_icon_factory");
                    final ShapeIconFactory factory = new ShapeIconFactory(
                            SplashscreenContentDrawer.this.mContext,
                            scaledIconDpi, mFinalIconSize);
                    final Bitmap bitmap = factory.createScaledBitmap(iconDrawable,
                            BaseIconFactory.MODE_DEFAULT);
                    Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
                    createIconDrawable(new BitmapDrawable(bitmap), true,
                            mHighResIconProvider.mLoadInDetail);
                }
            }

            return fillViewWithIcon(mFinalIconSize, mFinalIconDrawables, mUiThreadInitTask);
        }

        private class ShapeIconFactory extends BaseIconFactory {
            protected ShapeIconFactory(Context context, int fillResIconDpi, int iconBitmapSize) {
                super(context, fillResIconDpi, iconBitmapSize, true /* shapeDetection */);
            }
        }

        private void createIconDrawable(Drawable iconDrawable, boolean legacy,
                boolean loadInDetail) {
            if (legacy) {
                mFinalIconDrawables = SplashscreenIconDrawableFactory.makeLegacyIconDrawable(
                        iconDrawable, mDefaultIconSize, mFinalIconSize, loadInDetail,
                        mSplashscreenWorkerHandler);
            } else {
                mFinalIconDrawables = SplashscreenIconDrawableFactory.makeIconDrawable(
                        mTmpAttrs.mIconBgColor, mThemeColor, iconDrawable, mDefaultIconSize,
                        mFinalIconSize, loadInDetail, mSplashscreenWorkerHandler);
            }
        }

        private boolean processAdaptiveIcon(Drawable iconDrawable) {
            if (!(iconDrawable instanceof AdaptiveIconDrawable)) {
                return false;
            }

            Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "processAdaptiveIcon");
            final AdaptiveIconDrawable adaptiveIconDrawable = (AdaptiveIconDrawable) iconDrawable;
            final Drawable iconForeground = adaptiveIconDrawable.getForeground();
            final ColorCache.IconColor iconColor = mColorCache.getIconColor(
                    mActivityInfo.packageName, mActivityInfo.getIconResource(),
                    mLastPackageContextConfigHash,
                    () -> new DrawableColorTester(iconForeground,
                            DrawableColorTester.TRANSLUCENT_FILTER /* filterType */),
                    () -> new DrawableColorTester(adaptiveIconDrawable.getBackground()));
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                    "processAdaptiveIcon: FgMainColor=%s, BgMainColor=%s, "
                            + "IsBgComplex=%b, FromCache=%b, ThemeColor=%s",
                    Integer.toHexString(iconColor.mFgColor),
                    Integer.toHexString(iconColor.mBgColor),
                    iconColor.mIsBgComplex,
                    iconColor.mReuseCount > 0,
                    Integer.toHexString(mThemeColor));

            // Only draw the foreground of AdaptiveIcon to the splash screen if below condition
            // meet:
            // A. The background of the adaptive icon is not complicated. If it is complicated,
            // it may contain some information, and
            // B. The background of the adaptive icon is similar to the theme color, or
            // C. The background of the adaptive icon is grayscale, and the foreground of the
            // adaptive icon forms a certain contrast with the theme color.
            // D. Didn't specify icon background color.
            if (!iconColor.mIsBgComplex && mTmpAttrs.mIconBgColor == Color.TRANSPARENT
                    && (isRgbSimilarInHsv(mThemeColor, iconColor.mBgColor)
                            || (iconColor.mIsBgGrayscale
                                    && !isRgbSimilarInHsv(mThemeColor, iconColor.mFgColor)))) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                        "processAdaptiveIcon: choose fg icon");
                // Reference AdaptiveIcon description, outer is 108 and inner is 72, so we
                // scale by 192/160 if we only draw adaptiveIcon's foreground.
                final float noBgScale =
                        iconColor.mFgNonTranslucentRatio < mEnlargeForegroundIconThreshold
                                ? mNoBackgroundScale : 1f;
                // Using AdaptiveIconDrawable here can help keep the shape consistent with the
                // current settings.
                mFinalIconSize = (int) (0.5f + mIconSize * noBgScale);
                createIconDrawable(iconForeground, false, mHighResIconProvider.mLoadInDetail);
            } else {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                        "processAdaptiveIcon: draw whole icon");
                createIconDrawable(iconDrawable, false, mHighResIconProvider.mLoadInDetail);
            }
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
            return true;
        }

        private SplashScreenView fillViewWithIcon(int iconSize, @Nullable Drawable[] iconDrawable,
                Consumer<Runnable> uiThreadInitTask) {
            Drawable foreground = null;
            Drawable background = null;
            if (iconDrawable != null) {
                foreground = iconDrawable.length > 0 ? iconDrawable[0] : null;
                background = iconDrawable.length > 1 ? iconDrawable[1] : null;
            }

            Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "fillViewWithIcon");
            final ContextThemeWrapper wrapper = createViewContextWrapper(mContext);
            final SplashScreenView.Builder builder = new SplashScreenView.Builder(wrapper)
                    .setBackgroundColor(mThemeColor)
                    .setOverlayDrawable(mOverlayDrawable)
                    .setIconSize(iconSize)
                    .setIconBackground(background)
                    .setCenterViewDrawable(foreground)
                    .setUiThreadInitConsumer(uiThreadInitTask)
                    .setAllowHandleSolidColor(mAllowHandleSolidColor);

            if (mSuggestType == STARTING_WINDOW_TYPE_SPLASH_SCREEN
                    && mTmpAttrs.mBrandingImage != null) {
                builder.setBrandingDrawable(mTmpAttrs.mBrandingImage, mBrandingImageWidth,
                        mBrandingImageHeight);
            }
            final SplashScreenView splashScreenView = builder.build();
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
            return splashScreenView;
        }
    }

    private static boolean isRgbSimilarInHsv(int a, int b) {
        if (a == b) {
            return true;
        }
        final float lumA = Color.luminance(a);
        final float lumB = Color.luminance(b);
        final float contrastRatio = lumA > lumB
                ? (lumA + 0.05f) / (lumB + 0.05f) : (lumB + 0.05f) / (lumA + 0.05f);
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                "isRgbSimilarInHsv a:%s, b:%s, contrast ratio:%f",
                Integer.toHexString(a), Integer.toHexString(b), contrastRatio);
        if (contrastRatio < 2) {
            return true;
        }

        final float[] aHsv = new float[3];
        final float[] bHsv = new float[3];
        Color.colorToHSV(a, aHsv);
        Color.colorToHSV(b, bHsv);
        // Minimum degree of the hue between two colors, the result range is 0-180.
        int minAngle = (int) Math.abs(aHsv[0] - bHsv[0]);
        minAngle = (minAngle + 180) % 360 - 180;

        // Calculate the difference between two colors based on the HSV dimensions.
        final float normalizeH = minAngle / 180f;
        final double squareH = Math.pow(normalizeH, 2);
        final double squareS = Math.pow(aHsv[1] - bHsv[1], 2);
        final double squareV = Math.pow(aHsv[2] - bHsv[2], 2);
        final double square = squareH + squareS + squareV;
        final double mean = square / 3;
        final double root = Math.sqrt(mean);
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                "isRgbSimilarInHsv hsvDiff: %d, ah: %f, bh: %f, as: %f, bs: %f, av: %f, bv: %f, "
                        + "sqH: %f, sqS: %f, sqV: %f, rsm: %f",
                minAngle, aHsv[0], bHsv[0], aHsv[1], bHsv[1], aHsv[2], bHsv[2],
                squareH, squareS, squareV, root);
        return root < 0.1;
    }

    private static class DrawableColorTester {
        private static final int NO_ALPHA_FILTER = 0;
        // filter out completely invisible pixels
        private static final int TRANSPARENT_FILTER = 1;
        // filter out translucent and invisible pixels
        private static final int TRANSLUCENT_FILTER = 2;

        @IntDef(flag = true, value = {
                NO_ALPHA_FILTER,
                TRANSPARENT_FILTER,
                TRANSLUCENT_FILTER
        })
        private @interface QuantizerFilterType {}

        private final ColorTester mColorChecker;

        DrawableColorTester(Drawable drawable) {
            this(drawable, NO_ALPHA_FILTER /* filterType */);
        }

        DrawableColorTester(Drawable drawable, @QuantizerFilterType int filterType) {
            // Some applications use LayerDrawable for their windowBackground. To ensure that we
            // only get the real background, so that the color is not affected by the alpha of the
            // upper layer, try to get the lower layer here. This can also speed up the calculation.
            if (drawable instanceof LayerDrawable) {
                LayerDrawable layerDrawable = (LayerDrawable) drawable;
                if (layerDrawable.getNumberOfLayers() > 0) {
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                            "DrawableColorTester: replace drawable with bottom layer drawable");
                    drawable = layerDrawable.getDrawable(0);
                }
            }
            if (drawable == null) {
                mColorChecker = new SingleColorTester(
                        (ColorDrawable) createDefaultBackgroundDrawable());
            } else {
                mColorChecker = drawable instanceof ColorDrawable
                        ? new SingleColorTester((ColorDrawable) drawable)
                        : new ComplexDrawableTester(drawable, filterType);
            }
        }

        public float passFilterRatio() {
            return mColorChecker.passFilterRatio();
        }

        public boolean isComplexColor() {
            return mColorChecker.isComplexColor();
        }

        public int getDominateColor() {
            return mColorChecker.getDominantColor();
        }

        public boolean isGrayscale() {
            return mColorChecker.isGrayscale();
        }

        /**
         * A help class to check the color information from a Drawable.
         */
        private interface ColorTester {
            float passFilterRatio();

            boolean isComplexColor();

            int getDominantColor();

            boolean isGrayscale();
        }

        private static boolean isGrayscaleColor(int color) {
            final int red = Color.red(color);
            final int green = Color.green(color);
            final int blue = Color.blue(color);
            return red == green && green == blue;
        }

        /**
         * For ColorDrawable only. There will be only one color so don't spend too much resource for
         * it.
         */
        private static class SingleColorTester implements ColorTester {
            private final ColorDrawable mColorDrawable;

            SingleColorTester(@NonNull ColorDrawable drawable) {
                mColorDrawable = drawable;
            }

            @Override
            public float passFilterRatio() {
                final int alpha = mColorDrawable.getAlpha();
                return alpha / 255.0f;
            }

            @Override
            public boolean isComplexColor() {
                return false;
            }

            @Override
            public int getDominantColor() {
                return mColorDrawable.getColor();
            }

            @Override
            public boolean isGrayscale() {
                return isGrayscaleColor(mColorDrawable.getColor());
            }
        }

        /**
         * For any other Drawable except ColorDrawable. This will use the Palette API to check the
         * color information and use a quantizer to filter out transparent colors when needed.
         */
        private static class ComplexDrawableTester implements ColorTester {
            private static final int MAX_BITMAP_SIZE = 40;
            private final Palette mPalette;
            private final boolean mFilterTransparent;
            private static final AlphaFilterQuantizer ALPHA_FILTER_QUANTIZER =
                    new AlphaFilterQuantizer();

            /**
             * @param drawable The test target.
             * @param filterType Targeting to filter out transparent or translucent pixels,
             *                   this would be needed if want to check
             *                   {@link #passFilterRatio()}, also affecting the estimated result
             *                   of the dominant color.
             */
            ComplexDrawableTester(Drawable drawable, @QuantizerFilterType int filterType) {
                Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "ComplexDrawableTester");
                final Rect initialBounds = drawable.copyBounds();
                int width = drawable.getIntrinsicWidth();
                int height = drawable.getIntrinsicHeight();
                // Some drawables do not have intrinsic dimensions
                if (width <= 0 || height <= 0) {
                    width = MAX_BITMAP_SIZE;
                    height = MAX_BITMAP_SIZE;
                } else {
                    width = Math.min(width, MAX_BITMAP_SIZE);
                    height = Math.min(height, MAX_BITMAP_SIZE);
                }

                final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                final Canvas bmpCanvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                drawable.draw(bmpCanvas);
                // restore to original bounds
                drawable.setBounds(initialBounds);

                final Palette.Builder builder;
                // The Palette API will ignore Alpha, so it cannot handle transparent pixels, but
                // sometimes we will need this information to know if this Drawable object is
                // transparent.
                mFilterTransparent = filterType != NO_ALPHA_FILTER;
                if (mFilterTransparent) {
                    ALPHA_FILTER_QUANTIZER.setFilter(filterType);
                    builder = new Palette.Builder(bitmap, ALPHA_FILTER_QUANTIZER)
                            .maximumColorCount(5);
                } else {
                    builder = new Palette.Builder(bitmap, null)
                            .maximumColorCount(5);
                }
                mPalette = builder.generate();
                bitmap.recycle();
                Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
            }

            @Override
            public float passFilterRatio() {
                return mFilterTransparent ? ALPHA_FILTER_QUANTIZER.mPassFilterRatio : 1;
            }

            @Override
            public boolean isComplexColor() {
                return mPalette.getSwatches().size() > 1;
            }

            @Override
            public int getDominantColor() {
                final Palette.Swatch mainSwatch = mPalette.getDominantSwatch();
                if (mainSwatch != null) {
                    return mainSwatch.getInt();
                }
                return Color.BLACK;
            }

            @Override
            public boolean isGrayscale() {
                final List<Palette.Swatch> swatches = mPalette.getSwatches();
                if (swatches != null) {
                    for (int i = swatches.size() - 1; i >= 0; i--) {
                        Palette.Swatch swatch = swatches.get(i);
                        if (!isGrayscaleColor(swatch.getInt())) {
                            return false;
                        }
                    }
                }
                return true;
            }

            private static class AlphaFilterQuantizer implements Quantizer {
                private static final int NON_TRANSPARENT = 0xFF000000;
                private final Quantizer mInnerQuantizer = new VariationalKMeansQuantizer();
                private final IntPredicate mTransparentFilter = i -> (i & NON_TRANSPARENT) != 0;
                private final IntPredicate mTranslucentFilter = i ->
                        (i & NON_TRANSPARENT) == NON_TRANSPARENT;

                private IntPredicate mFilter = mTransparentFilter;
                private float mPassFilterRatio;

                void setFilter(@QuantizerFilterType int filterType) {
                    switch (filterType) {
                        case TRANSLUCENT_FILTER:
                            mFilter = mTranslucentFilter;
                            break;
                        case TRANSPARENT_FILTER:
                        default:
                            mFilter = mTransparentFilter;
                            break;
                    }
                }

                @Override
                public void quantize(final int[] pixels, final int maxColors) {
                    mPassFilterRatio = 0;
                    int realSize = 0;
                    for (int i = pixels.length - 1; i > 0; i--) {
                        if (mFilter.test(pixels[i])) {
                            realSize++;
                        }
                    }
                    if (realSize == 0) {
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                                "DrawableTester quantize: pure transparent image");
                        mInnerQuantizer.quantize(pixels, maxColors);
                        return;
                    }
                    mPassFilterRatio = (float) realSize / pixels.length;
                    final int[] samplePixels = new int[realSize];
                    int rowIndex = 0;
                    for (int i = pixels.length - 1; i > 0; i--) {
                        if (mFilter.test(pixels[i])) {
                            samplePixels[rowIndex] = pixels[i];
                            rowIndex++;
                        }
                    }
                    mInnerQuantizer.quantize(samplePixels, maxColors);
                }

                @Override
                public List<Palette.Swatch> getQuantizedColors() {
                    return mInnerQuantizer.getQuantizedColors();
                }
            }
        }
    }

    /** Cache the result of {@link DrawableColorTester} to reduce expensive calculation. */
    @VisibleForTesting
    static class ColorCache extends BroadcastReceiver {
        /**
         * The color may be different according to resource id and configuration (e.g. night mode),
         * so this allows to cache more than one color per package.
         */
        private static final int CACHE_SIZE = 2;

        /** The computed colors of packages. */
        private final ArrayMap<String, Colors> mColorMap = new ArrayMap<>();

        private static class Colors {
            final WindowColor[] mWindowColors = new WindowColor[CACHE_SIZE];
            final IconColor[] mIconColors = new IconColor[CACHE_SIZE];
        }

        private static class Cache {
            /** The hash used to check whether this cache is hit. */
            final int mHash;

            /** The number of times this cache has been reused. */
            int mReuseCount;

            Cache(int hash) {
                mHash = hash;
            }
        }

        static class WindowColor extends Cache {
            final int mBgColor;

            WindowColor(int hash, int bgColor) {
                super(hash);
                mBgColor = bgColor;
            }
        }

        static class IconColor extends Cache {
            final int mFgColor;
            final int mBgColor;
            final boolean mIsBgComplex;
            final boolean mIsBgGrayscale;
            final float mFgNonTranslucentRatio;

            IconColor(int hash, int fgColor, int bgColor, boolean isBgComplex,
                    boolean isBgGrayscale, float fgNonTranslucnetRatio) {
                super(hash);
                mFgColor = fgColor;
                mBgColor = bgColor;
                mIsBgComplex = isBgComplex;
                mIsBgGrayscale = isBgGrayscale;
                mFgNonTranslucentRatio = fgNonTranslucnetRatio;
            }
        }

        ColorCache(Context context, Handler handler) {
            // This includes reinstall and uninstall.
            final IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);
            filter.addDataScheme(IntentFilter.SCHEME_PACKAGE);
            context.registerReceiverAsUser(this, UserHandle.ALL, filter,
                    null /* broadcastPermission */, handler);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final Uri packageUri = intent.getData();
            if (packageUri != null) {
                mColorMap.remove(packageUri.getEncodedSchemeSpecificPart());
            }
        }

        /**
         * Gets the existing cache if the hash matches. If null is returned, the caller can use
         * outLeastUsedIndex to put the new cache.
         */
        private static <T extends Cache> T getCache(T[] caches, int hash, int[] outLeastUsedIndex) {
            int minReuseCount = Integer.MAX_VALUE;
            for (int i = 0; i < CACHE_SIZE; i++) {
                final T cache = caches[i];
                if (cache == null) {
                    // Empty slot has the highest priority to put new cache.
                    minReuseCount = -1;
                    outLeastUsedIndex[0] = i;
                    continue;
                }
                if (cache.mHash == hash) {
                    cache.mReuseCount++;
                    return cache;
                }
                if (cache.mReuseCount < minReuseCount) {
                    minReuseCount = cache.mReuseCount;
                    outLeastUsedIndex[0] = i;
                }
            }
            return null;
        }

        @NonNull WindowColor getWindowColor(String packageName, int configHash, int windowBgColor,
                int windowBgResId, IntSupplier windowBgColorSupplier) {
            Colors colors = mColorMap.get(packageName);
            int hash = 31 * configHash + windowBgColor;
            hash = 31 * hash + windowBgResId;
            final int[] leastUsedIndex = { 0 };
            if (colors != null) {
                final WindowColor windowColor = getCache(colors.mWindowColors, hash,
                        leastUsedIndex);
                if (windowColor != null) {
                    return windowColor;
                }
            } else {
                colors = new Colors();
                mColorMap.put(packageName, colors);
            }
            final WindowColor windowColor = new WindowColor(hash, windowBgColorSupplier.getAsInt());
            colors.mWindowColors[leastUsedIndex[0]] = windowColor;
            return windowColor;
        }

        @NonNull IconColor getIconColor(String packageName, int configHash, int iconResId,
                Supplier<DrawableColorTester> fgColorTesterSupplier,
                Supplier<DrawableColorTester> bgColorTesterSupplier) {
            Colors colors = mColorMap.get(packageName);
            final int hash = configHash * 31 + iconResId;
            final int[] leastUsedIndex = { 0 };
            if (colors != null) {
                final IconColor iconColor = getCache(colors.mIconColors, hash, leastUsedIndex);
                if (iconColor != null) {
                    return iconColor;
                }
            } else {
                colors = new Colors();
                mColorMap.put(packageName, colors);
            }
            final DrawableColorTester fgTester = fgColorTesterSupplier.get();
            final DrawableColorTester bgTester = bgColorTesterSupplier.get();
            final IconColor iconColor = new IconColor(hash, fgTester.getDominateColor(),
                    bgTester.getDominateColor(), bgTester.isComplexColor(), bgTester.isGrayscale(),
                    fgTester.passFilterRatio());
            colors.mIconColors[leastUsedIndex[0]] = iconColor;
            return iconColor;
        }
    }

    /**
     * Create and play the default exit animation for splash screen view.
     */
    void applyExitAnimation(SplashScreenView view, SurfaceControl leash,
            Rect frame, Runnable finishCallback, long createTime, float roundedCornerRadius) {
        final Runnable playAnimation = () -> {
            final SplashScreenExitAnimation animation = new SplashScreenExitAnimation(mContext,
                    view, leash, frame, mMainWindowShiftLength, mTransactionPool, finishCallback,
                    roundedCornerRadius);
            animation.startAnimations();
        };
        if (view.getIconView() == null) {
            playAnimation.run();
            return;
        }
        final long appReadyDuration = SystemClock.uptimeMillis() - createTime;
        final long animDuration = view.getIconAnimationDuration() != null
                ? view.getIconAnimationDuration().toMillis() : 0;
        final long minimumShowingDuration = getShowingDuration(animDuration, appReadyDuration);
        final long delayed = minimumShowingDuration - appReadyDuration;
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                "applyExitAnimation delayed: %s", delayed);
        if (delayed > 0) {
            view.postDelayed(playAnimation, delayed);
        } else {
            playAnimation.run();
        }
    }

    /**
     * When loading a BitmapDrawable object with specific density, there will decode the image based
     * on the density from display metrics, so even when load with higher override density, the
     * final intrinsic size of a BitmapDrawable can still not big enough to draw on expect size.
     *
     * So here we use a standalone IconProvider object to load the Drawable object for higher
     * density, and the resources object won't affect the entire system.
     *
     */
    private static class HighResIconProvider {
        private final Context mSharedContext;
        private final IconProvider mSharedIconProvider;
        private boolean mLoadInDetail;

        // only create standalone icon provider when the density dpi is low.
        private Context mStandaloneContext;
        private IconProvider mStandaloneIconProvider;

        HighResIconProvider(Context context, IconProvider sharedIconProvider) {
            mSharedContext = context;
            mSharedIconProvider = sharedIconProvider;
        }

        Drawable getIcon(ActivityInfo activityInfo, int currentDpi, int iconDpi) {
            mLoadInDetail = false;
            Drawable drawable;
            if (currentDpi < iconDpi && currentDpi < DisplayMetrics.DENSITY_XHIGH) {
                drawable = loadFromStandalone(activityInfo, currentDpi, iconDpi);
            } else {
                drawable = mSharedIconProvider.getIcon(activityInfo, iconDpi);
            }

            if (drawable == null) {
                drawable = mSharedContext.getPackageManager().getDefaultActivityIcon();
            }
            return drawable;
        }

        private Drawable loadFromStandalone(ActivityInfo activityInfo, int currentDpi,
                int iconDpi) {
            if (mStandaloneContext == null) {
                final Configuration defConfig = mSharedContext.getResources().getConfiguration();
                mStandaloneContext = mSharedContext.createConfigurationContext(defConfig);
                mStandaloneIconProvider = new IconProvider(mStandaloneContext);
            }
            Resources resources;
            try {
                resources = mStandaloneContext.getPackageManager()
                        .getResourcesForApplication(activityInfo.applicationInfo);
            } catch (PackageManager.NameNotFoundException | Resources.NotFoundException exc) {
                resources = null;
            }
            if (resources != null) {
                updateResourcesDpi(resources, iconDpi);
            }
            final Drawable drawable = mStandaloneIconProvider.getIcon(activityInfo, iconDpi);
            mLoadInDetail = true;
            // reset density dpi
            if (resources != null) {
                updateResourcesDpi(resources, currentDpi);
            }
            return drawable;
        }

        private void updateResourcesDpi(Resources resources, int densityDpi) {
            final Configuration config = resources.getConfiguration();
            final DisplayMetrics displayMetrics = resources.getDisplayMetrics();
            config.densityDpi = densityDpi;
            displayMetrics.densityDpi = densityDpi;
            resources.updateConfiguration(config, displayMetrics);
        }
    }
}
