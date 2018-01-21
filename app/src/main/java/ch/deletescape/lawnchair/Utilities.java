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

package ch.deletescape.lawnchair;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TtsSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.deletescape.lawnchair.config.IThemer;
import ch.deletescape.lawnchair.config.ThemeProvider;
import ch.deletescape.lawnchair.dynamicui.ExtractedColors;
import ch.deletescape.lawnchair.graphics.ShadowGenerator;
import ch.deletescape.lawnchair.overlay.ILauncherClient;
import ch.deletescape.lawnchair.overlay.LawnfeedClient;
import ch.deletescape.lawnchair.pixelify.AdaptiveIconDrawableCompat;
import ch.deletescape.lawnchair.preferences.IPreferenceProvider;
import ch.deletescape.lawnchair.preferences.PreferenceFlags;
import ch.deletescape.lawnchair.preferences.PreferenceProvider;
import ch.deletescape.lawnchair.shortcuts.DeepShortcutManager;
import ch.deletescape.lawnchair.shortcuts.ShortcutInfoCompat;
import ch.deletescape.lawnchair.util.IconNormalizer;
import ch.deletescape.lawnchair.util.PackageManagerHelper;

import static ch.deletescape.lawnchair.util.PackageManagerHelper.isAppEnabled;

/**
 * Various utilities shared amongst the Launcher's classes.
 */
public final class Utilities {

    private static final String TAG = "Launcher.Utilities";

    private static final Rect sOldBounds = new Rect();
    private static final Canvas sCanvas = new Canvas();

    private static final Pattern sTrimPattern =
            Pattern.compile("^[\\s|\\p{javaSpaceChar}]*(.*)[\\s|\\p{javaSpaceChar}]*$");

    static {
        sCanvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.DITHER_FLAG,
                Paint.FILTER_BITMAP_FLAG));
    }

    private static final int[] sLoc0 = new int[2];
    private static final int[] sLoc1 = new int[2];

    public static final boolean ATLEAST_MARSHMALLOW =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;

    public static final boolean ATLEAST_LOLLIPOP_MR1 =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1;

    public static final boolean ATLEAST_NOUGAT =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;

    public static final boolean ATLEAST_NOUGAT_MR1 =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1;

    public static final boolean ATLEAST_OREO =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;

    // An intent extra to indicate the horizontal scroll of the wallpaper.
    public static final String EXTRA_WALLPAPER_OFFSET = "ch.deletescape.lawnchair.WALLPAPER_OFFSET";

    // These values are same as that in {@link AsyncTask}.
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final int KEEP_ALIVE = 1;

    /**
     * An {@link Executor} to be used with async task with no limit on the queue size.
     */
    public static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
            TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    // Blacklisted APKs which will be hidden, these include simple regex formatting, without
    // full regex formatting (e.g. com.android. will block everything that starts with com.android.)
    // Taken from: https://github.com/substratum/substratum/blob/dev/app/src/main/java/projekt/substratum/common/Systems.java
    private static final String[] BLACKLISTED_APPLICATIONS = {
        "com.android.vending.billing.InAppBillingService.",
        "uret.jasi2169.",
        "com.dimonvideo.luckypatcher",
        "com.chelpus.",
        "com.forpda.lp",
        "zone.jasi2169."
    };

    public static boolean isPropertyEnabled(String propertyName) {
        return Log.isLoggable(propertyName, Log.VERBOSE);
    }

    public static Bitmap createIconBitmap(Cursor c, int iconIndex, Context context) {
        byte[] data = c.getBlob(iconIndex);
        try {
            return createIconBitmap(BitmapFactory.decodeByteArray(data, 0, data.length), context);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns a bitmap suitable for the all apps view. If the package or the resource do not
     * exist, it returns null.
     */
    public static Bitmap createIconBitmap(String packageName, String resourceName,
                                          Context context) {
        PackageManager packageManager = context.getPackageManager();
        // the resource
        try {
            Resources resources = packageManager.getResourcesForApplication(packageName);
            if (resources != null) {
                final int id = resources.getIdentifier(resourceName, null, null);
                return createIconBitmap(
                        resources.getDrawableForDensity(id, LauncherAppState.getInstance()
                                .getInvariantDeviceProfile().fillResIconDpi, null), context);
            }
        } catch (Exception e) {
            // Icon not found.
        }
        return null;
    }

    private static int getIconBitmapSize() {
        return LauncherAppState.getInstance().getInvariantDeviceProfile().iconBitmapSize;
    }

    /**
     * Returns a bitmap which is of the appropriate size to be displayed as an icon
     */
    public static Bitmap createIconBitmap(Bitmap icon, Context context) {
        final int iconBitmapSize = getIconBitmapSize();
        if (iconBitmapSize == icon.getWidth() && iconBitmapSize == icon.getHeight()) {
            return icon;
        }
        return createIconBitmap(new BitmapDrawable(context.getResources(), icon), context);
    }

    /**
     * Returns a bitmap suitable for the all apps view. The icon is badged for {@param user}.
     * The bitmap is also visually normalized with other icons.
     */
    public static Bitmap createBadgedIconBitmap(
            Drawable icon, UserHandle user, Context context) {
        float scale = IconNormalizer.getInstance().getScale(icon, null);
        Bitmap bitmap = createIconBitmap(icon, context, scale);
        if (isAdaptive(icon))
            bitmap = addShadowToIcon(bitmap, bitmap.getWidth());
        return badgeIconForUser(bitmap, user, context);
    }

    /**
     * Badges the provided icon with the user badge if required.
     */
    public static Bitmap badgeIconForUser(Bitmap icon, UserHandle user, Context context) {
        if (user != null && !Utilities.myUserHandle().equals(user)) {
            BitmapDrawable drawable = new FixedSizeBitmapDrawable(icon);
            Drawable badged = context.getPackageManager().getUserBadgedIcon(
                    drawable, user);
            if (badged instanceof BitmapDrawable) {
                return ((BitmapDrawable) badged).getBitmap();
            } else {
                return createIconBitmap(badged, context);
            }
        } else {
            return icon;
        }
    }

    /**
     * Creates a normalized bitmap suitable for the all apps view. The bitmap is also visually
     * normalized with other icons and has enough spacing to add shadow.
     */
    public static Bitmap createScaledBitmapWithoutShadow(Drawable icon, Context context) {
        RectF iconBounds = new RectF();
        float scale = IconNormalizer.getInstance().getScale(icon, iconBounds);
        scale = Math.min(scale, ShadowGenerator.getScaleForBounds(iconBounds));
        return createIconBitmap(icon, context, scale);
    }

    /**
     * Adds a shadow to the provided icon. It assumes that the icon has already been scaled using
     * {@link #createScaledBitmapWithoutShadow(Drawable, Context)}
     */
    public static Bitmap addShadowToIcon(Bitmap icon) {
        return ShadowGenerator.getInstance().recreateIcon(icon);
    }

    public static Bitmap addShadowToIcon(Bitmap icon, int size) {
        return ShadowGenerator.getInstance().recreateIcon(icon, size);
    }

    public static Bitmap getShadowForIcon(Bitmap icon, int size) {
        return ShadowGenerator.getInstance().createShadow(icon, size);
    }

    /**
     * Adds the {@param badge} on top of {@param srcTgt} using the badge dimensions.
     */
    public static Bitmap badgeWithBitmap(Bitmap srcTgt, Bitmap badge, Context context) {
        int badgeSize = context.getResources().getDimensionPixelSize(R.dimen.profile_badge_size);
        synchronized (sCanvas) {
            sCanvas.setBitmap(srcTgt);
            sCanvas.drawBitmap(badge, new Rect(0, 0, badge.getWidth(), badge.getHeight()),
                    new Rect(srcTgt.getWidth() - badgeSize,
                            srcTgt.getHeight() - badgeSize, srcTgt.getWidth(), srcTgt.getHeight()),
                    new Paint(Paint.FILTER_BITMAP_FLAG));
            sCanvas.setBitmap(null);
        }
        return srcTgt;
    }

    /**
     * Returns a bitmap suitable for the all apps view.
     */
    public static Bitmap createIconBitmap(Drawable icon, Context context) {
        return createIconBitmap(icon, context, 1.0f /* scale */);
    }

    private static Bitmap createIconBitmap(Drawable icon, Context context, float scale) {
        synchronized (sCanvas) {
            final int iconBitmapSize = getIconBitmapSize();
            int width = iconBitmapSize;
            int height = iconBitmapSize;

            if (icon instanceof PaintDrawable) {
                PaintDrawable painter = (PaintDrawable) icon;
                painter.setIntrinsicWidth(width);
                painter.setIntrinsicHeight(height);
            } else if (icon instanceof BitmapDrawable) {
                // Ensure the bitmap has a density.
                BitmapDrawable bitmapDrawable = (BitmapDrawable) icon;
                Bitmap bitmap = bitmapDrawable.getBitmap();
                if (bitmap != null && bitmap.getDensity() == Bitmap.DENSITY_NONE) {
                    bitmapDrawable.setTargetDensity(context.getResources().getDisplayMetrics());
                }
            }

            int sourceWidth = icon.getIntrinsicWidth();
            int sourceHeight = icon.getIntrinsicHeight();
            if (sourceWidth > 0 && sourceHeight > 0) {
                // Scale the icon proportionally to the icon dimensions
                final float ratio = (float) sourceWidth / sourceHeight;
                if (sourceWidth > sourceHeight) {
                    height = (int) (width / ratio);
                } else if (sourceHeight > sourceWidth) {
                    width = (int) (height * ratio);
                }
            }
            // no intrinsic size --> use default size

            Bitmap bitmap = Bitmap.createBitmap(iconBitmapSize, iconBitmapSize,
                    Bitmap.Config.ARGB_8888);
            final Canvas canvas = sCanvas;
            canvas.setBitmap(bitmap);

            final int left = (iconBitmapSize -width) / 2;
            final int top = (iconBitmapSize -height) / 2;

            sOldBounds.set(icon.getBounds());
            if (Utilities.isAdaptive(icon)) {
                int offset = Math.max((int)(ShadowGenerator.BLUR_FACTOR * iconBitmapSize),
                        Math.min(left, top));
                int size = Math.max(width, height);
                icon.setBounds(offset, offset, size, size);
            } else {
                icon.setBounds(left, top, left+width, top+height);
            }
            canvas.save(Canvas.MATRIX_SAVE_FLAG);
            canvas.scale(scale, scale, iconBitmapSize / 2, iconBitmapSize / 2);
            icon.draw(canvas);
            canvas.restore();
            icon.setBounds(sOldBounds);
            canvas.setBitmap(null);

            return bitmap;
        }
    }

    /**
     * Given a coordinate relative to the descendant, find the coordinate in a parent view's
     * coordinates.
     *
     * @param descendant        The descendant to which the passed coordinate is relative.
     * @param ancestor          The root view to make the coordinates relative to.
     * @param coord             The coordinate that we want mapped.
     * @param includeRootScroll Whether or not to account for the scroll of the descendant:
     *                          sometimes this is relevant as in a child's coordinates within the descendant.
     * @return The factor by which this descendant is scaled relative to this DragLayer. Caution
     * this scale factor is assumed to be equal in X and Y, and so if at any point this
     * assumption fails, we will need to return a pair of scale factors.
     */
    public static float getDescendantCoordRelativeToAncestor(
            View descendant, View ancestor, int[] coord, boolean includeRootScroll) {
        float[] pt = {coord[0], coord[1]};
        float scale = 1.0f;
        View v = descendant;
        while (v != ancestor && v != null) {
            // For TextViews, scroll has a meaning which relates to the text position
            // which is very strange... ignore the scroll.
            if (v != descendant || includeRootScroll) {
                pt[0] -= v.getScrollX();
                pt[1] -= v.getScrollY();
            }

            v.getMatrix().mapPoints(pt);
            pt[0] += v.getLeft();
            pt[1] += v.getTop();
            scale *= v.getScaleX();

            v = (View) v.getParent();
        }

        coord[0] = Math.round(pt[0]);
        coord[1] = Math.round(pt[1]);
        return scale;
    }

    /**
     * Inverse of {@link #getDescendantCoordRelativeToAncestor(View, View, int[], boolean)}.
     */
    public static float mapCoordInSelfToDescendent(View descendant, View root,
                                                   int[] coord) {
        ArrayList<View> ancestorChain = new ArrayList<>();

        float[] pt = {coord[0], coord[1]};

        View v = descendant;
        while (v != root) {
            ancestorChain.add(v);
            v = (View) v.getParent();
        }
        ancestorChain.add(root);

        float scale = 1.0f;
        Matrix inverse = new Matrix();
        int count = ancestorChain.size();
        for (int i = count - 1; i >= 0; i--) {
            View ancestor = ancestorChain.get(i);
            View next = i > 0 ? ancestorChain.get(i - 1) : null;

            pt[0] += ancestor.getScrollX();
            pt[1] += ancestor.getScrollY();

            if (next != null) {
                pt[0] -= next.getLeft();
                pt[1] -= next.getTop();
                next.getMatrix().invert(inverse);
                inverse.mapPoints(pt);
                scale *= next.getScaleX();
            }
        }

        coord[0] = Math.round(pt[0]);
        coord[1] = Math.round(pt[1]);
        return scale;
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

    public static int[] getCenterDeltaInScreenSpace(View v0, View v1, int[] delta) {
        v0.getLocationInWindow(sLoc0);
        v1.getLocationInWindow(sLoc1);

        sLoc0[0] += (v0.getMeasuredWidth() * v0.getScaleX()) / 2;
        sLoc0[1] += (v0.getMeasuredHeight() * v0.getScaleY()) / 2;
        sLoc1[0] += (v1.getMeasuredWidth() * v1.getScaleX()) / 2;
        sLoc1[1] += (v1.getMeasuredHeight() * v1.getScaleY()) / 2;

        if (delta == null) {
            delta = new int[2];
        }

        delta[0] = sLoc1[0] - sLoc0[0];
        delta[1] = sLoc1[1] - sLoc0[1];

        return delta;
    }

    public static void scaleRectAboutCenter(Rect r, float scale) {
        if (scale != 1.0f) {
            int cx = r.centerX();
            int cy = r.centerY();
            r.offset(-cx, -cy);

            r.left = (int) (r.left * scale + 0.5f);
            r.top = (int) (r.top * scale + 0.5f);
            r.right = (int) (r.right * scale + 0.5f);
            r.bottom = (int) (r.bottom * scale + 0.5f);

            r.offset(cx, cy);
        }
    }

    public static void startActivityForResultSafely(
            Activity activity, Intent intent, int requestCode) {
        try {
            activity.startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(activity, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Launcher does not have the permission to launch " + intent +
                    ". Make sure to create a MAIN intent-filter for the corresponding activity " +
                    "or use the exported attribute for this activity.", e);
        }
    }

    public static boolean isSystemApp(Context context, Intent intent) {
        PackageManager pm = context.getPackageManager();
        ComponentName cn = intent.getComponent();
        String packageName = null;
        if (cn == null) {
            ResolveInfo info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if ((info != null) && (info.activityInfo != null)) {
                packageName = info.activityInfo.packageName;
            }
        } else {
            packageName = cn.getPackageName();
        }
        if (packageName != null) {
            try {
                PackageInfo info = pm.getPackageInfo(packageName, 0);
                return (info != null) && (info.applicationInfo != null) &&
                        ((info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
            } catch (NameNotFoundException e) {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * This picks a dominant color, looking for high-saturation, high-value, repeated hues.
     *
     * @param bitmap  The bitmap to scan
     * @param samples The approximate max number of samples to use.
     */
    public static int findDominantColorByHue(Bitmap bitmap, int samples) {
        final int height = bitmap.getHeight();
        final int width = bitmap.getWidth();
        int sampleStride = (int) Math.sqrt((height * width) / samples);
        if (sampleStride < 1) {
            sampleStride = 1;
        }

        // This is an out-param, for getting the hsv values for an rgb
        float[] hsv = new float[3];

        // First get the best hue, by creating a histogram over 360 hue buckets,
        // where each pixel contributes a score weighted by saturation, value, and alpha.
        float[] hueScoreHistogram = new float[360];
        float highScore = -1;
        int bestHue = -1;

        for (int y = 0; y < height; y += sampleStride) {
            for (int x = 0; x < width; x += sampleStride) {
                int argb = bitmap.getPixel(x, y);
                int alpha = 0xFF & (argb >> 24);
                if (alpha < 0x80) {
                    // Drop mostly-transparent pixels.
                    continue;
                }
                // Remove the alpha channel.
                int rgb = argb | 0xFF000000;
                Color.colorToHSV(rgb, hsv);
                // Bucket colors by the 360 integer hues.
                int hue = (int) hsv[0];
                if (hue < 0 || hue >= hueScoreHistogram.length) {
                    // Defensively avoid array bounds violations.
                    continue;
                }
                float score = hsv[1] * hsv[2];
                hueScoreHistogram[hue] += score;
                if (hueScoreHistogram[hue] > highScore) {
                    highScore = hueScoreHistogram[hue];
                    bestHue = hue;
                }
            }
        }

        SparseArray<Float> rgbScores = new SparseArray<>();
        int bestColor = 0xff000000;
        highScore = -1;
        // Go back over the RGB colors that match the winning hue,
        // creating a histogram of weighted s*v scores, for up to 100*100 [s,v] buckets.
        // The highest-scoring RGB color wins.
        for (int y = 0; y < height; y += sampleStride) {
            for (int x = 0; x < width; x += sampleStride) {
                int rgb = bitmap.getPixel(x, y) | 0xff000000;
                Color.colorToHSV(rgb, hsv);
                int hue = (int) hsv[0];
                if (hue == bestHue) {
                    float s = hsv[1];
                    float v = hsv[2];
                    int bucket = (int) (s * 100) + (int) (v * 10000);
                    // Score by cumulative saturation * value.
                    float score = s * v;
                    Float oldTotal = rgbScores.get(bucket);
                    float newTotal = oldTotal == null ? score : oldTotal + score;
                    rgbScores.put(bucket, newTotal);
                    if (newTotal > highScore) {
                        highScore = newTotal;
                        // All the colors in the winning bucket are very similar. Last in wins.
                        bestColor = rgb;
                    }
                }
            }
        }
        return bestColor;
    }

    /*
     * Finds a system apk which had a broadcast receiver listening to a particular action.
     * @param action intent action used to find the apk
     * @return a pair of apk package name and the resources.
     */
    public static Pair<String, Resources> findSystemApk(String action, PackageManager pm) {
        final Intent intent = new Intent(action);
        for (ResolveInfo info : pm.queryBroadcastReceivers(intent, 0)) {
            if (info.activityInfo != null &&
                    (info.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                final String packageName = info.activityInfo.packageName;
                try {
                    final Resources res = pm.getResourcesForApplication(packageName);
                    return Pair.create(packageName, res);
                } catch (NameNotFoundException e) {
                    Log.w(TAG, "Failed to find resources for " + packageName);
                }
            }
        }
        return null;
    }

    /**
     * Compresses the bitmap to a byte array for serialization.
     */
    public static byte[] flattenBitmap(Bitmap bitmap) {
        // Try go guesstimate how much space the icon will take when serialized
        // to avoid unnecessary allocations/copies during the write.
        int size = bitmap.getWidth() * bitmap.getHeight() * 4;
        ByteArrayOutputStream out = new ByteArrayOutputStream(size);
        try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            return out.toByteArray();
        } catch (IOException e) {
            Log.w(TAG, "Could not write bitmap");
            return null;
        }
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
    public static int calculateTextHeight(float textSizePx, boolean twoLines) {
        Paint p = new Paint();
        p.setTextSize(textSizePx);
        Paint.FontMetrics fm = p.getFontMetrics();
        int result = (int) Math.ceil(fm.bottom - fm.top);
        return twoLines ? result * 2 : result;
    }

    public static boolean isRtl(Resources res) {
        return res.getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    /**
     * Returns true if the intent is a valid launch intent for a launcher activity of an app.
     * This is used to identify shortcuts which are different from the ones exposed by the
     * applications' manifest file.
     *
     * @param launchIntent The intent that will be launched when the shortcut is clicked.
     */
    public static boolean isLauncherAppTarget(Intent launchIntent) {
        if (launchIntent != null
                && Intent.ACTION_MAIN.equals(launchIntent.getAction())
                && launchIntent.getComponent() != null
                && launchIntent.getCategories() != null
                && launchIntent.getCategories().size() == 1
                && launchIntent.hasCategory(Intent.CATEGORY_LAUNCHER)
                && TextUtils.isEmpty(launchIntent.getDataString())) {
            // An app target can either have no extra or have ItemInfo.EXTRA_PROFILE.
            Bundle extras = launchIntent.getExtras();
            if (extras == null) {
                return true;
            } else {
                Set<String> keys = extras.keySet();
                return keys.size() == 1 && keys.contains(ItemInfo.EXTRA_PROFILE);
            }
        }
        return false;
    }

    public static float dpiFromPx(int size, DisplayMetrics metrics) {
        float densityRatio = (float) metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT;
        return (size / densityRatio);
    }

    public static int pxFromDp(float size, DisplayMetrics metrics) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                size, metrics));
    }

    public static int pxFromSp(float size, DisplayMetrics metrics) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                size, metrics));
    }

    public static Paint.FontMetricsInt fontMetricsIntFromFontMetrics(Paint.FontMetrics fontMetrics) {
        Paint.FontMetricsInt fontMetricsInt = new Paint.FontMetricsInt();
        fontMetricsInt.ascent = Math.round(fontMetrics.ascent);
        fontMetricsInt.bottom = Math.round(fontMetrics.bottom);
        fontMetricsInt.descent = Math.round(fontMetrics.descent);
        fontMetricsInt.leading = Math.round(fontMetrics.leading);
        fontMetricsInt.top = Math.round(fontMetrics.top);
        return fontMetricsInt;
    }

    public static String createDbSelectionQuery(String columnName, Iterable<?> values) {
        return String.format(Locale.ENGLISH, "%s IN (%s)", columnName, TextUtils.join(", ", values));
    }

    public static boolean isBootCompleted() {
        return "1".equals(getProp("sys.boot_completed", "1"));
    }

    private static String getProp(String propName, String defaultValue) {
        Process p = null;
        String result = defaultValue;
        try {
            p = new ProcessBuilder("/system/bin/getprop", propName)
                    .redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream())
            )) {
                String line;
                while ((line = br.readLine()) != null) {
                    result = line;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
        return result;
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
     * Wraps a message with a TTS span, so that a different message is spoken than
     * what is getting displayed.
     *
     * @param msg    original message
     * @param ttsMsg message to be spoken
     */
    public static CharSequence wrapForTts(CharSequence msg, String ttsMsg) {
        SpannableString spanned = new SpannableString(msg);
        spanned.setSpan(new TtsSpan.TextBuilder(ttsMsg).build(),
                0, spanned.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        return spanned;
    }

    @NonNull
    public static IPreferenceProvider getPrefs(Context context) {
        return PreferenceProvider.INSTANCE.getPreferences(context);
    }

    @NonNull
    public static IThemer getThemer() {
        return ThemeProvider.INSTANCE.getThemer();
    }

    public static boolean isPowerSaverOn(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return powerManager != null && powerManager.isPowerSaveMode();
    }

    public static boolean isWallapaperAllowed(Context context) {
        if (ATLEAST_NOUGAT) {
            try {
                WallpaperManager wm = context.getSystemService(WallpaperManager.class);
                return (Boolean) (wm != null ? wm.getClass().getDeclaredMethod("isSetWallpaperAllowed")
                        .invoke(wm) : false);
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    public static void closeSilently(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Returns whether the collection is null or empty.
     */
    public static boolean isEmpty(Collection c) {
        return c == null || c.isEmpty();
    }

    public static void setDefaultLauncher(@NotNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        ComponentName fakeLauncher =
                new ComponentName(context.getPackageName(), context.getPackageName() + ".FakeLauncher");
        ComponentName launcher = new ComponentName(context, Launcher.class);

        packageManager.setComponentEnabledSetting(fakeLauncher, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        packageManager.setComponentEnabledSetting(launcher, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

        Intent picker = new Intent(Intent.ACTION_MAIN);
        picker.addCategory(Intent.CATEGORY_HOME);
        picker.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(picker);

        packageManager.setComponentEnabledSetting(fakeLauncher, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
        packageManager.setComponentEnabledSetting(launcher, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
    }

    /**
     * An extension of {@link BitmapDrawable} which returns the bitmap pixel size as intrinsic size.
     * This allows the badging to be done based on the action bitmap size rather than
     * the scaled bitmap size.
     */
    private static class FixedSizeBitmapDrawable extends BitmapDrawable {

        FixedSizeBitmapDrawable(Bitmap bitmap) {
            super(null, bitmap);
        }

        @Override
        public int getIntrinsicHeight() {
            return getBitmap().getWidth();
        }

        @Override
        public int getIntrinsicWidth() {
            return getBitmap().getWidth();
        }
    }

    public static int getColorAccent(Context context) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{android.R.attr.colorAccent});
        int colorAccent = ta.getColor(0, 0);
        ta.recycle();
        return colorAccent;
    }

    public static int getColor(Context context, int index, int defaultColor) {
        ExtractedColors ec = new ExtractedColors();
        ec.load(context);
        return ec.getColor(index, defaultColor);
    }

    public static void sendCustomAccessibilityEvent(View target, int type, String text) {
        AccessibilityManager accessibilityManager = (AccessibilityManager)
                target.getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (accessibilityManager != null && accessibilityManager.isEnabled()) {
            AccessibilityEvent event = AccessibilityEvent.obtain(type);
            target.onInitializeAccessibilityEvent(event);
            event.getText().add(text);
            accessibilityManager.sendAccessibilityEvent(event);
        }
    }

    public static UserHandle myUserHandle() {
        return android.os.Process.myUserHandle();
    }

    public static <T> HashSet<T> singletonHashSet(T obj) {
        HashSet<T> hashSet = new HashSet<>(1);
        hashSet.add(obj);
        return hashSet;
    }

    public static void setAppVisibility(Context context, String key, boolean visible) {
        Set<String> hiddenApps = getPrefs(context).getHiddenAppsSet();
        if (visible)
            hiddenApps.remove(key);
        else
            hiddenApps.add(key);
        getPrefs(context).setHiddenAppsSet(hiddenApps);
    }

    public static boolean isAppHidden(Context context, String key) {
        return getPrefs(context).getHiddenAppsSet().contains(key);
    }

    public static int getDynamicAccent(Context context) {
        if (!Utilities.getPrefs(context).getEnableDynamicUi()) return getColorAccent(context);
        return getColor(context, ExtractedColors.VIBRANT_INDEX, getColorAccent(context));
    }

    public static int getDynamicBadgeColor(Context context) {
        int defaultColor = context.getResources().getColor(R.color.badge_color);
        if (!Utilities.getPrefs(context).getEnableDynamicUi()) return defaultColor;
        return getColor(context, ExtractedColors.VIBRANT_INDEX, defaultColor);
    }

    public static int resolveAttributeData(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    private static int getPreviousBuildNumber(IPreferenceProvider prefs) {
        return prefs.getPreviousBuildNumber();
    }

    private static void setBuildNumber(IPreferenceProvider prefs, int buildNumber) {
        prefs.setPreviousBuildNumber(buildNumber);
    }

    public static void showChangelog(Context context) {
        if (!BuildConfig.TRAVIS || BuildConfig.TAGGED_BUILD || !BuildConfig.DEBUG) return;
        final IPreferenceProvider prefs = getPrefs(context);
        if (BuildConfig.TRAVIS_BUILD_NUMBER != getPreviousBuildNumber(prefs)) {
            new AlertDialog.Builder(context)
                    .setTitle(String.format(context.getString(R.string.changelog_title), BuildConfig.TRAVIS_BUILD_NUMBER))
                    .setMessage(getChangelog().trim())
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            setBuildNumber(prefs, BuildConfig.TRAVIS_BUILD_NUMBER);
                        }
                    })
                    .show();
        }
    }

    @NonNull
    public static String getChangelog() {
        StringBuilder builder = new StringBuilder();
        String[] lines = BuildConfig.CHANGELOG.split("\n");
        for (String line : lines) {
            if (line.startsWith("Merge pull request")) continue;
            if (line.contains("[no ci]")) {
                line = line.replace("[no ci]", "");
            }
            builder
                    .append("- ")
                    .append(line.trim())
                    .append('\n');
        }
        builder.deleteCharAt(builder.lastIndexOf("\n"));
        return builder.toString();
    }

    public static void updatePackage(Context context, UserHandle userHandle, String packageName) {
        if (!PackageManagerHelper.isAppEnabled(context.getPackageManager(), packageName, 0)) return;
        LauncherAppState instance = LauncherAppState.getInstance();
        instance.getModel().onPackageChanged(packageName, userHandle);
        List<ShortcutInfoCompat> queryForPinnedShortcuts = DeepShortcutManager.getInstance(context).queryForPinnedShortcuts(packageName, userHandle);
        if (!queryForPinnedShortcuts.isEmpty()) {
            instance.getModel().updatePinnedShortcuts(packageName, queryForPinnedShortcuts, userHandle);
        }
    }

    public static Drawable getMyIcon(Context context) {
        return context.getPackageManager().getApplicationIcon(context.getApplicationInfo());
    }

    public static boolean isAwarenessApiEnabled(Context context) {
        IPreferenceProvider prefs = getPrefs(context);
        return PreferenceFlags.PREF_WEATHER_PROVIDER_AWARENESS.equals(prefs.getWeatherProvider());
    }

    private static boolean isComponentClock(ComponentName componentName, boolean stockAppOnly) {
        if (componentName == null) {
            return false;
        }

        if (stockAppOnly) {
            return "com.google.android.deskclock/com.android.deskclock.DeskClock".equals(componentName.flattenToString());
        }

        // TODO: Maybe we can add all apps that end with .clockpackage/.DeskClock/.clock/???
        // Or that contain .clock./.deskclock or end with those?
        ArrayList<String> clockApps = new ArrayList<>();
        clockApps.add("com.android.deskclock/com.android.deskclock.DeskClock"); // Stock
        clockApps.add("com.sec.android.app.clockpackage/com.sec.android.app.clockpackage.ClockPackage"); // Samsung
        clockApps.add("com.android.deskclock/com.android.deskclock.DeskClockTabActivity"); // MIUI

        return clockApps.contains(componentName.flattenToString());
    }

    public static boolean isAdaptive(Drawable drawable) {
        return drawable != null && (ATLEAST_OREO && drawable instanceof AdaptiveIconDrawable || drawable instanceof AdaptiveIconDrawableCompat);
    }

    private static void ensureAdaptiveIcon(Drawable drawable) {
        if (!isAdaptive(drawable))
            throw new IllegalStateException("Not an adaptive icon");
    }

    public static Drawable getBackground(Drawable drawable) {
        ensureAdaptiveIcon(drawable);
        if (ATLEAST_OREO && drawable instanceof AdaptiveIconDrawable)
            return ((AdaptiveIconDrawable) drawable).getBackground();
        else if (drawable instanceof AdaptiveIconDrawableCompat)
            return ((AdaptiveIconDrawableCompat) drawable).getBackground();
        return null;
    }

    public static Drawable getForeground(Drawable drawable) {
        ensureAdaptiveIcon(drawable);
        if (ATLEAST_OREO && drawable instanceof AdaptiveIconDrawable)
            return ((AdaptiveIconDrawable) drawable).getForeground();
        else if (drawable instanceof AdaptiveIconDrawableCompat)
            return ((AdaptiveIconDrawableCompat) drawable).getForeground();
        return null;
    }

    public static Path getIconMask(Drawable drawable) {
        ensureAdaptiveIcon(drawable);
        if (ATLEAST_OREO && drawable instanceof AdaptiveIconDrawable)
            return ((AdaptiveIconDrawable) drawable).getIconMask();
        else if (drawable instanceof AdaptiveIconDrawableCompat)
            return ((AdaptiveIconDrawableCompat) drawable).getIconMask();
        return null;
    }

    public static boolean isAnimatedClock(Context context, ComponentName componentName) {
        return Utilities.getPrefs(context).getAnimatedClockIcon() &&
                Utilities.isComponentClock(componentName, !Utilities.getPrefs(context).getAnimatedClockIconAlternativeClockApps());
    }

    public static boolean isBlacklistedAppInstalled(Context context) {
        final PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo packageInfo : packages) {
            for (String packageName : BLACKLISTED_APPLICATIONS) {
                if (packageInfo.packageName.startsWith(packageName)) {
                    return true;
                }
            }
        }

        return BLACKLISTED_APPLICATIONS.length == 0 || false;
    }

    public static void showOutdatedLawnfeedPopup(final Context context) {
        if (!BuildConfig.ENABLE_LAWNFEED || ILauncherClient.Companion.getEnabledState(context) != ILauncherClient.DISABLED_CLIENT_OUTDATED) return;
        new AlertDialog.Builder(context)
            .setTitle(R.string.lawnfeed_outdated_title)
            .setMessage(R.string.lawnfeed_outdated)
            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    try {
                        // Open website with download link for Lawnfeed
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://lawnchair.info/getlawnfeed.html"));
                        context.startActivity(intent);
                    } catch (ActivityNotFoundException exc) {
                        // Believe me, this actually happens.
                        Toast.makeText(context, R.string.error_no_browser, Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton(android.R.string.no, null)
            .show();
    }

    public static boolean checkOutdatedLawnfeed(Context context) {
        // Don't check Lawnfeed version on CI builds, should fix disabled setting option
        if (!BuildConfig.ENABLE_LAWNFEED) return false;

        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(LawnfeedClient.PROXY_PACKAGE, 0);
            // All Lawnfeed builds below version code 1655 aren't signed properly!
            if (info != null && info.versionCode <= 1655 && !info.versionName.equals("dev")) {
                return true;
            }
        } catch (PackageManager.NameNotFoundException ignored) {}

        return false;
    }

    public static void restartLauncher(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent startActivity = pm.getLaunchIntentForPackage(context.getPackageName());

        startActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Create a pending intent so the application is restarted after System.exit(0) was called.
        // We use an AlarmManager to call this intent in 100ms
        PendingIntent mPendingIntent = PendingIntent.getActivity(context, 0, startActivity, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);

        // Kill the application
        System.exit(0);
    }

    public static int getNumberOfHotseatRows(Context context){
        boolean twoLines = PreferenceProvider.INSTANCE.getPreferences(context).getTwoRowDock();
        return twoLines ? 2 : 1;
    }

    public static void showResetAlternativeIcons(final Context context, final List<String> appsList) {
        if (appsList.size() <= 0) {
            return;
        }

        new AlertDialog.Builder(context)
            .setTitle(R.string.reset_alternative_icons_title)
            .setMessage(String.format(context.getString(R.string.reset_alternative_icons), appsList.size()))
            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    IPreferenceProvider prefs = Utilities.getPrefs(context);
                    Launcher launcher = LauncherAppState.getInstanceNoCreate().getLauncher();

                    for (String app : appsList) {
                        prefs.removeAlternateIcon(app);
                    }

                    // Ensure those icons get updated
                    launcher.scheduleReloadIcons();
                }
            })
            .setNegativeButton(android.R.string.no, null)
            .show();
    }

    public static List<String> getAlternativeIconList(Context context) {
        List<String> apps = new ArrayList<>();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(PreferenceFlags.KEY_ALTERNATE_ICON_PREFIX)) {
                String regex = "^" + PreferenceFlags.KEY_ALTERNATE_ICON_PREFIX;
                apps.add(key.replaceFirst(regex, ""));
            }
        }

        return apps;
    }

    public static void setupPirateLocale(Activity activity){
        if (!PreferenceProvider.INSTANCE.getPreferences(activity).getAyyMatey()) {
            return;
        }
        // Based on: https://stackoverflow.com/a/9173571
        Locale locale = new Locale("pir");
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.locale = locale;
        Resources baseResources = activity.getBaseContext().getResources();
        baseResources.updateConfiguration(config, baseResources.getDisplayMetrics());
    }
}
