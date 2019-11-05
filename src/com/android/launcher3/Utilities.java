/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import static com.android.launcher3.ItemInfoWithIcon.FLAG_ICON_BADGED;

import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.Person;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.TransactionTooLargeException;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TtsSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.Interpolator;

import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.ShortcutConfigActivityInfo;
import com.android.launcher3.dragndrop.FolderAdaptiveIcon;
import com.android.launcher3.graphics.RotationMode;
import com.android.launcher3.graphics.TintedDrawableSpan;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.views.Transposable;
import com.android.launcher3.widget.PendingAddShortcutInfo;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Various utilities shared amongst the Launcher's classes.
 */
public final class Utilities {

    private static final String TAG = "Launcher.Utilities";

    private static final Pattern sTrimPattern =
            Pattern.compile("^[\\s|\\p{javaSpaceChar}]*(.*)[\\s|\\p{javaSpaceChar}]*$");

    private static final int[] sLoc0 = new int[2];
    private static final int[] sLoc1 = new int[2];
    private static final Matrix sMatrix = new Matrix();
    private static final Matrix sInverseMatrix = new Matrix();

    public static final String[] EMPTY_STRING_ARRAY = new String[0];
    public static final Person[] EMPTY_PERSON_ARRAY = new Person[0];

    public static final boolean ATLEAST_Q = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;

    public static final boolean ATLEAST_P =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;

    public static final boolean ATLEAST_OREO_MR1 =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1;

    public static final boolean ATLEAST_OREO =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;

    /**
     * Set on a motion event dispatched from the nav bar. See {@link MotionEvent#setEdgeFlags(int)}.
     */
    public static final int EDGE_NAV_BAR = 1 << 8;

    /**
     * Indicates if the device has a debug build. Should only be used to store additional info or
     * add extra logging and not for changing the app behavior.
     */
    public static final boolean IS_DEBUG_DEVICE =
            Build.TYPE.toLowerCase(Locale.ROOT).contains("debug") ||
            Build.TYPE.toLowerCase(Locale.ROOT).equals("eng");

    public static boolean isDevelopersOptionsEnabled(Context context) {
        return Settings.Global.getInt(context.getApplicationContext().getContentResolver(),
                        Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
    }

    // An intent extra to indicate the horizontal scroll of the wallpaper.
    public static final String EXTRA_WALLPAPER_OFFSET = "com.android.launcher3.WALLPAPER_OFFSET";
    public static final String EXTRA_WALLPAPER_FLAVOR = "com.android.launcher3.WALLPAPER_FLAVOR";

    public static boolean IS_RUNNING_IN_TEST_HARNESS =
                    ActivityManager.isRunningInTestHarness();

    public static void enableRunningInTestHarnessForTests() {
        IS_RUNNING_IN_TEST_HARNESS = true;
    }

    public static boolean isPropertyEnabled(String propertyName) {
        return Log.isLoggable(propertyName, Log.VERBOSE);
    }

    public static boolean existsStyleWallpapers(Context context) {
        ResolveInfo ri = context.getPackageManager().resolveActivity(
                PackageManagerHelper.getStyleWallpapersIntent(context), 0);
        return ri != null;
    }

    /**
     * Given a coordinate relative to the descendant, find the coordinate in a parent view's
     * coordinates.
     *
     * @param descendant The descendant to which the passed coordinate is relative.
     * @param ancestor The root view to make the coordinates relative to.
     * @param coord The coordinate that we want mapped.
     * @param includeRootScroll Whether or not to account for the scroll of the descendant:
     *          sometimes this is relevant as in a child's coordinates within the descendant.
     * @return The factor by which this descendant is scaled relative to this DragLayer. Caution
     *         this scale factor is assumed to be equal in X and Y, and so if at any point this
     *         assumption fails, we will need to return a pair of scale factors.
     */
    public static float getDescendantCoordRelativeToAncestor(
            View descendant, View ancestor, float[] coord, boolean includeRootScroll) {
        return getDescendantCoordRelativeToAncestor(descendant, ancestor, coord, includeRootScroll,
                false, null);
    }

    /**
     * Given a coordinate relative to the descendant, find the coordinate in a parent view's
     * coordinates.
     *
     * @param descendant The descendant to which the passed coordinate is relative.
     * @param ancestor The root view to make the coordinates relative to.
     * @param coord The coordinate that we want mapped.
     * @param includeRootScroll Whether or not to account for the scroll of the descendant:
     *          sometimes this is relevant as in a child's coordinates within the descendant.
     * @param ignoreTransform If true, view transform is ignored
     * @param outRotation If not null, and {@param ignoreTransform} is true, this is set to the
     *                   overall rotation of the view in degrees.
     * @return The factor by which this descendant is scaled relative to this DragLayer. Caution
     *         this scale factor is assumed to be equal in X and Y, and so if at any point this
     *         assumption fails, we will need to return a pair of scale factors.
     */
    public static float getDescendantCoordRelativeToAncestor(View descendant, View ancestor,
            float[] coord, boolean includeRootScroll, boolean ignoreTransform,
            float[] outRotation) {
        float scale = 1.0f;
        View v = descendant;
        while(v != ancestor && v != null) {
            // For TextViews, scroll has a meaning which relates to the text position
            // which is very strange... ignore the scroll.
            if (v != descendant || includeRootScroll) {
                offsetPoints(coord, -v.getScrollX(), -v.getScrollY());
            }

            if (ignoreTransform) {
                if (v instanceof Transposable) {
                    RotationMode m = ((Transposable) v).getRotationMode();
                    if (m.isTransposed) {
                        sMatrix.setRotate(m.surfaceRotation, v.getPivotX(), v.getPivotY());
                        sMatrix.mapPoints(coord);

                        if (outRotation != null) {
                            outRotation[0] += m.surfaceRotation;
                        }
                    }
                }
            } else {
                v.getMatrix().mapPoints(coord);
            }
            offsetPoints(coord, v.getLeft(), v.getTop());
            scale *= v.getScaleX();

            v = (View) v.getParent();
        }
        return scale;
    }

    /**
     * Inverse of {@link #getDescendantCoordRelativeToAncestor(View, View, float[], boolean)}.
     */
    public static void mapCoordInSelfToDescendant(View descendant, View root, float[] coord) {
        sMatrix.reset();
        View v = descendant;
        while(v != root) {
            sMatrix.postTranslate(-v.getScrollX(), -v.getScrollY());
            sMatrix.postConcat(v.getMatrix());
            sMatrix.postTranslate(v.getLeft(), v.getTop());
            v = (View) v.getParent();
        }
        sMatrix.postTranslate(-v.getScrollX(), -v.getScrollY());
        sMatrix.invert(sInverseMatrix);
        sInverseMatrix.mapPoints(coord);
    }

    /**
     * Sets {@param out} to be same as {@param in} by rounding individual values
     */
    public static void roundArray(float[] in, int[] out) {
       for (int i = 0; i < in.length; i++) {
           out[i] = Math.round(in[i]);
       }
    }

    public static void offsetPoints(float[] points, float offsetX, float offsetY) {
        for (int i = 0; i < points.length; i += 2) {
            points[i] += offsetX;
            points[i + 1] += offsetY;
        }
    }

    /**
     * Utility method to determine whether the given point, in local coordinates,
     * is inside the view, where the area of the view is expanded by the slop factor.
     * This method is called while processing touch-move events to determine if the event
     * is still within the view.
     */
    public static boolean pointInView(View v, float localX, float localY, float slop) {
        return localX >= -slop && localY >= -slop && localX < (v.getWidth() + slop) &&
                localY < (v.getHeight() + slop);
    }

    public static int[] getCenterDeltaInScreenSpace(View v0, View v1) {
        v0.getLocationInWindow(sLoc0);
        v1.getLocationInWindow(sLoc1);

        sLoc0[0] += (v0.getMeasuredWidth() * v0.getScaleX()) / 2;
        sLoc0[1] += (v0.getMeasuredHeight() * v0.getScaleY()) / 2;
        sLoc1[0] += (v1.getMeasuredWidth() * v1.getScaleX()) / 2;
        sLoc1[1] += (v1.getMeasuredHeight() * v1.getScaleY()) / 2;
        return new int[] {sLoc1[0] - sLoc0[0], sLoc1[1] - sLoc0[1]};
    }

    public static void scaleRectFAboutCenter(RectF r, float scale) {
        if (scale != 1.0f) {
            float cx = r.centerX();
            float cy = r.centerY();
            r.offset(-cx, -cy);
            r.left = r.left * scale;
            r.top = r.top * scale ;
            r.right = r.right * scale;
            r.bottom = r.bottom * scale;
            r.offset(cx, cy);
        }
    }

    public static void scaleRectAboutCenter(Rect r, float scale) {
        if (scale != 1.0f) {
            int cx = r.centerX();
            int cy = r.centerY();
            r.offset(-cx, -cy);
            scaleRect(r, scale);
            r.offset(cx, cy);
        }
    }

    public static void scaleRect(Rect r, float scale) {
        if (scale != 1.0f) {
            r.left = (int) (r.left * scale + 0.5f);
            r.top = (int) (r.top * scale + 0.5f);
            r.right = (int) (r.right * scale + 0.5f);
            r.bottom = (int) (r.bottom * scale + 0.5f);
        }
    }

    public static void insetRect(Rect r, Rect insets) {
        r.left = Math.min(r.right, r.left + insets.left);
        r.top = Math.min(r.bottom, r.top + insets.top);
        r.right = Math.max(r.left, r.right - insets.right);
        r.bottom = Math.max(r.top, r.bottom - insets.bottom);
    }

    public static float shrinkRect(Rect r, float scaleX, float scaleY) {
        float scale = Math.min(Math.min(scaleX, scaleY), 1.0f);
        if (scale < 1.0f) {
            int deltaX = (int) (r.width() * (scaleX - scale) * 0.5f);
            r.left += deltaX;
            r.right -= deltaX;

            int deltaY = (int) (r.height() * (scaleY - scale) * 0.5f);
            r.top += deltaY;
            r.bottom -= deltaY;
        }
        return scale;
    }

    /**
     * Maps t from one range to another range.
     * @param t The value to map.
     * @param fromMin The lower bound of the range that t is being mapped from.
     * @param fromMax The upper bound of the range that t is being mapped from.
     * @param toMin The lower bound of the range that t is being mapped to.
     * @param toMax The upper bound of the range that t is being mapped to.
     * @return The mapped value of t.
     */
    public static float mapToRange(float t, float fromMin, float fromMax, float toMin, float toMax,
            Interpolator interpolator) {
        if (fromMin == fromMax || toMin == toMax) {
            Log.e(TAG, "mapToRange: range has 0 length");
            return toMin;
        }
        float progress = getProgress(t, fromMin, fromMax);
        return mapRange(interpolator.getInterpolation(progress), toMin, toMax);
    }

    public static float getProgress(float current, float min, float max) {
        return Math.abs(current - min) / Math.abs(max - min);
    }

    public static float mapRange(float value, float min, float max) {
        return min + (value * (max - min));
    }

    /**
     * Trims the string, removing all whitespace at the beginning and end of the string.
     * Non-breaking whitespaces are also removed.
     */
    public static String trim(CharSequence s) {
        if (s == null) {
            return null;
        }

        // Just strip any sequence of whitespace or java space characters from the beginning and end
        Matcher m = sTrimPattern.matcher(s);
        return m.replaceAll("$1");
    }

    /**
     * Calculates the height of a given string at a specific text size.
     */
    public static int calculateTextHeight(float textSizePx) {
        Paint p = new Paint();
        p.setTextSize(textSizePx);
        Paint.FontMetrics fm = p.getFontMetrics();
        return (int) Math.ceil(fm.bottom - fm.top);
    }

    public static boolean isRtl(Resources res) {
        return res.getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    public static float dpiFromPx(int size, DisplayMetrics metrics){
        float densityRatio = (float) metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT;
        return (size / densityRatio);
    }

    public static int pxFromSp(float size, DisplayMetrics metrics) {
        return (int) Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                size, metrics));
    }

    public static String createDbSelectionQuery(String columnName, IntArray values) {
        return String.format(Locale.ENGLISH, "%s IN (%s)", columnName, values.toConcatString());
    }

    public static boolean isBootCompleted() {
        return "1".equals(getSystemProperty("sys.boot_completed", "1"));
    }

    public static String getSystemProperty(String property, String defaultValue) {
        try {
            Class clazz = Class.forName("android.os.SystemProperties");
            Method getter = clazz.getDeclaredMethod("get", String.class);
            String value = (String) getter.invoke(null, property);
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        } catch (Exception e) {
            Log.d(TAG, "Unable to read system properties");
        }
        return defaultValue;
    }

    /**
     * Ensures that a value is within given bounds. Specifically:
     * If value is less than lowerBound, return lowerBound; else if value is greater than upperBound,
     * return upperBound; else return value unchanged.
     */
    public static int boundToRange(int value, int lowerBound, int upperBound) {
        return Math.max(lowerBound, Math.min(value, upperBound));
    }

    /**
     * @see #boundToRange(int, int, int).
     */
    public static float boundToRange(float value, float lowerBound, float upperBound) {
        return Math.max(lowerBound, Math.min(value, upperBound));
    }

    /**
     * @see #boundToRange(int, int, int).
     */
    public static long boundToRange(long value, long lowerBound, long upperBound) {
        return Math.max(lowerBound, Math.min(value, upperBound));
    }

    /**
     * Wraps a message with a TTS span, so that a different message is spoken than
     * what is getting displayed.
     * @param msg original message
     * @param ttsMsg message to be spoken
     */
    public static CharSequence wrapForTts(CharSequence msg, String ttsMsg) {
        SpannableString spanned = new SpannableString(msg);
        spanned.setSpan(new TtsSpan.TextBuilder(ttsMsg).build(),
                0, spanned.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        return spanned;
    }

    /**
     * Prefixes a text with the provided icon
     */
    public static CharSequence prefixTextWithIcon(Context context, int iconRes, CharSequence msg) {
        // Update the hint to contain the icon.
        // Prefix the original hint with two spaces. The first space gets replaced by the icon
        // using span. The second space is used for a singe space character between the hint
        // and the icon.
        SpannableString spanned = new SpannableString("  " + msg);
        spanned.setSpan(new TintedDrawableSpan(context, iconRes),
                0, 1, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
        return spanned;
    }

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(
                LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
    }

    public static SharedPreferences getDevicePrefs(Context context) {
        return context.getSharedPreferences(
                LauncherFiles.DEVICE_PREFERENCES_KEY, Context.MODE_PRIVATE);
    }

    public static boolean areAnimationsEnabled(Context context) {
        return ATLEAST_OREO
                ? ValueAnimator.areAnimatorsEnabled()
                : !context.getSystemService(PowerManager.class).isPowerSaveMode();
    }

    public static boolean isWallpaperAllowed(Context context) {
        return context.getSystemService(WallpaperManager.class).isSetWallpaperAllowed();
    }

    public static boolean isBinderSizeError(Exception e) {
        return e.getCause() instanceof TransactionTooLargeException
                || e.getCause() instanceof DeadObjectException;
    }

    /**
     * Utility method to post a runnable on the handler, skipping the synchronization barriers.
     */
    public static void postAsyncCallback(Handler handler, Runnable callback) {
        Message msg = Message.obtain(handler, callback);
        msg.setAsynchronous(true);
        handler.sendMessage(msg);
    }

    /**
     * Parses a string encoded using {@link #getPointString(int, int)}
     */
    public static Point parsePoint(String point) {
        String[] split = point.split(",");
        return new Point(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
    }

    /**
     * Encodes a point to string to that it can be persisted atomically.
     */
    public static String getPointString(int x, int y) {
        return String.format(Locale.ENGLISH, "%d,%d", x, y);
    }

    public static void unregisterReceiverSafely(Context context, BroadcastReceiver receiver) {
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {}
    }

    /**
     * Returns the full drawable for {@param info}.
     * @param outObj this is set to the internal data associated with {@param info},
     *               eg {@link LauncherActivityInfo} or {@link ShortcutInfo}.
     */
    public static Drawable getFullDrawable(Launcher launcher, ItemInfo info, int width, int height,
            boolean flattenDrawable, Object[] outObj) {
        LauncherAppState appState = LauncherAppState.getInstance(launcher);
        if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
            LauncherActivityInfo activityInfo = LauncherAppsCompat.getInstance(launcher)
                    .resolveActivity(info.getIntent(), info.user);
            outObj[0] = activityInfo;
            return (activityInfo != null) ? appState.getIconCache()
                    .getFullResIcon(activityInfo, flattenDrawable) : null;
        } else if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
            if (info instanceof PendingAddShortcutInfo) {
                ShortcutConfigActivityInfo activityInfo =
                        ((PendingAddShortcutInfo) info).activityInfo;
                outObj[0] = activityInfo;
                return activityInfo.getFullResIcon(appState.getIconCache());
            }
            ShortcutKey key = ShortcutKey.fromItemInfo(info);
            DeepShortcutManager sm = DeepShortcutManager.getInstance(launcher);
            List<ShortcutInfo> si = sm.queryForFullDetails(
                    key.componentName.getPackageName(), Arrays.asList(key.getId()), key.user);
            if (si.isEmpty()) {
                return null;
            } else {
                outObj[0] = si.get(0);
                return sm.getShortcutIconDrawable(si.get(0),
                        appState.getInvariantDeviceProfile().fillResIconDpi);
            }
        } else if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER) {
            FolderAdaptiveIcon icon = FolderAdaptiveIcon.createFolderAdaptiveIcon(
                    launcher, info.id, new Point(width, height));
            if (icon == null) {
                return null;
            }
            outObj[0] = icon;
            return icon;
        } else {
            return null;
        }
    }

    /**
     * For apps icons and shortcut icons that have badges, this method creates a drawable that can
     * later on be rendered on top of the layers for the badges. For app icons, work profile badges
     * can only be applied. For deep shortcuts, when dragged from the pop up container, there's no
     * badge. When dragged from workspace or folder, it may contain app AND/OR work profile badge
     **/
    @TargetApi(Build.VERSION_CODES.O)
    public static Drawable getBadge(Launcher launcher, ItemInfo info, Object obj) {
        LauncherAppState appState = LauncherAppState.getInstance(launcher);
        int iconSize = appState.getInvariantDeviceProfile().iconBitmapSize;
        if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
            boolean iconBadged = (info instanceof ItemInfoWithIcon)
                    && (((ItemInfoWithIcon) info).runtimeStatusFlags & FLAG_ICON_BADGED) > 0;
            if ((info.id == ItemInfo.NO_ID && !iconBadged)
                    || !(obj instanceof ShortcutInfo)) {
                // The item is not yet added on home screen.
                return new FixedSizeEmptyDrawable(iconSize);
            }
            ShortcutInfo si = (ShortcutInfo) obj;
            LauncherIcons li = LauncherIcons.obtain(appState.getContext());
            Bitmap badge = li.getShortcutInfoBadge(si, appState.getIconCache()).iconBitmap;
            li.recycle();
            float badgeSize = LauncherIcons.getBadgeSizeForIconSize(iconSize);
            float insetFraction = (iconSize - badgeSize) / iconSize;
            return new InsetDrawable(new FastBitmapDrawable(badge),
                    insetFraction, insetFraction, 0, 0);
        } else if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER) {
            return ((FolderAdaptiveIcon) obj).getBadge();
        } else {
            return launcher.getPackageManager()
                    .getUserBadgedIcon(new FixedSizeEmptyDrawable(iconSize), info.user);
        }
    }

    public static float squaredHypot(float x, float y) {
        return x * x + y * y;
    }

    public static float squaredTouchSlop(Context context) {
        float slop = ViewConfiguration.get(context).getScaledTouchSlop();
        return slop * slop;
    }

    private static class FixedSizeEmptyDrawable extends ColorDrawable {

        private final int mSize;

        public FixedSizeEmptyDrawable(int size) {
            super(Color.TRANSPARENT);
            mSize = size;
        }

        @Override
        public int getIntrinsicHeight() {
            return mSize;
        }

        @Override
        public int getIntrinsicWidth() {
            return mSize;
        }
    }
}
