package ch.deletescape.lawnchair.blur;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;

import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.LauncherAppState;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.config.FeatureFlags;

public class BlurWallpaperProvider {
    private final Context mContext;
    private final WallpaperManager mWallpaperManager;
    private final List<Listener> mListeners = new ArrayList<>();
    private DisplayMetrics mDisplayMetrics = new DisplayMetrics();
    private Bitmap mWallpaper;
    private Bitmap mPlaceholder;
    private float mOffset;
    private int mBlurRadius = 75;
    private Runnable mNotifyRunnable = new Runnable() {
        @Override
        public void run() {
            for (Listener listener : mListeners) {
                listener.onWallpaperChanged(mWallpaper, mPlaceholder);
            }
        }
    };

    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint mColorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path mPath = new Path();

    private final Runnable mUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updateWallpaper();
        }
    };

    public BlurWallpaperProvider(Context context) {
        mContext = context;

        mWallpaperManager = WallpaperManager.getInstance(context);
        sEnabled = mWallpaperManager.getWallpaperInfo() == null && FeatureFlags.isBlurEnabled(mContext);
    }

    private void updateWallpaper() {
        Launcher launcher = LauncherAppState.getInstance().getLauncher();
        boolean enabled = mWallpaperManager.getWallpaperInfo() == null && FeatureFlags.isBlurEnabled(mContext);
        if (enabled != sEnabled) {
            launcher.scheduleKill();
        }

        if (!sEnabled) return;

        mBlurRadius = (int) Utilities.getPrefs(mContext).getFloat("pref_blurRadius", 75f);

        Bitmap wallpaper = upscaleToScreenSize(((BitmapDrawable) mWallpaperManager.getDrawable()).getBitmap());
        mWallpaper = null;
        mPlaceholder = createPlaceholder(wallpaper.getWidth(), wallpaper.getHeight());
        launcher.runOnUiThread(mNotifyRunnable);
        if (FeatureFlags.isVibrancyEnabled(mContext)) {
            wallpaper = applyVibrancy(wallpaper, getTintColor());
        }
        mWallpaper = blur(wallpaper);
        launcher.runOnUiThread(mNotifyRunnable);
    }

    private Bitmap upscaleToScreenSize(Bitmap bitmap) {
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        display.getRealMetrics(mDisplayMetrics);

        int width = mDisplayMetrics.widthPixels, height = mDisplayMetrics.heightPixels;

        float widthFactor = 0f, heightFactor = 0f;
        if (width > bitmap.getWidth()) {
            widthFactor = ((float) width) / bitmap.getWidth();
        }
        if (height > bitmap.getHeight()) {
            heightFactor = ((float) height) / bitmap.getHeight();
        }

        float upscaleFactor = Math.max(widthFactor, heightFactor);
        if (upscaleFactor <= 0) {
            return bitmap;
        }

        int scaledWidth = (int) (bitmap.getWidth() * upscaleFactor);
        int scaledHeight = (int) (bitmap.getHeight() * upscaleFactor);
        Bitmap scaled = Bitmap.createScaledBitmap(
                bitmap,
                scaledWidth,
                scaledHeight, false);

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas();
        canvas.setBitmap(result);

        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        if (widthFactor > heightFactor) {
            canvas.drawBitmap(scaled, 0, (height - scaledHeight) / 2, paint);
        } else {
            canvas.drawBitmap(scaled, (width - scaledWidth) / 2, 0, paint);
        }

        return result;
    }

    private Bitmap createPlaceholder(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas();
        canvas.setBitmap(bitmap);

        mPath.moveTo(0, 0);
        mPath.lineTo(0, height);
        mPath.lineTo(width, height);
        mPath.lineTo(width, 0);
        mColorPaint.setXfermode(null);
        mColorPaint.setColor(getTintColor());
        canvas.drawPath(mPath, mColorPaint);

        return bitmap;
    }

    private int getTintColor() {
        return 0x45FFFFFF;
    }

    public void updateAsync() {
        Utilities.THREAD_POOL_EXECUTOR.execute(mUpdateRunnable);
    }

    private Bitmap applyVibrancy(Bitmap wallpaper, int color) {
        int width = wallpaper.getWidth(), height = wallpaper.getHeight();

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas();
        canvas.setBitmap(bitmap);
        canvas.drawBitmap(wallpaper, 0, 0, mPaint);

        mPath.moveTo(0, 0);
        mPath.lineTo(0, height);
        mPath.lineTo(width, height);
        mPath.lineTo(width, 0);
        mColorPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.LIGHTEN));
        mColorPaint.setColor(color);
        canvas.drawPath(mPath, mColorPaint);

        return bitmap;
    }

    public void addListener(Listener listener) {
        mListeners.add(listener);
        listener.onOffsetChanged(mOffset);
    }

    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    public BlurDrawable createDrawable() {
        return new BlurDrawable(this, 0, false);
    }

    public BlurDrawable createDrawable(float radius, boolean allowTransparencyMode) {
        return new BlurDrawable(this, radius, allowTransparencyMode);
    }

    public void setWallpaperOffset(float offset) {
        if (!isEnabled()) return;
        if (mWallpaper == null) return;

        final int availw = mDisplayMetrics.widthPixels - mWallpaper.getWidth();
        int xPixels = availw / 2;

        if (availw < 0)
            xPixels += (int) (availw * (offset - .5f) + .5f);

        mOffset = -xPixels;

        for (Listener listener : mListeners) {
            listener.onOffsetChanged(mOffset);
        }
    }

    public void setUseTransparency(boolean useTransparency) {
        for (Listener listener : mListeners) {
            listener.setUseTransparency(useTransparency);
        }
    }

    interface Listener {

        void onWallpaperChanged(Bitmap wallpaper, Bitmap placeholder);
        void onOffsetChanged(float offset);
        void setUseTransparency(boolean useTransparency);
    }

    private static boolean sEnabled;

    public static boolean isEnabled() {
        return sEnabled;
    }

    public static BlurWallpaperProvider getInstance() {
        return LauncherAppState.getInstance().getLauncher().getBlurWallpaperProvider();
    }

    public Bitmap blur(Bitmap image) {
        int width = image.getWidth();
        int height = image.getHeight();
        image = Bitmap.createScaledBitmap(image, width, height, false);

        Bitmap bitmap = image.copy(image.getConfig(), true);

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        int[] pix = new int[w * h];
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);

        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = mBlurRadius + mBlurRadius + 1;

        int r[] = new int[wh];
        int g[] = new int[wh];
        int b[] = new int[wh];
        int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
        int vmin[] = new int[Math.max(w, h)];

        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int dv[] = new int[256 * divsum];
        for (i = 0; i < 256 * divsum; i++) {
            dv[i] = (i / divsum);
        }

        yw = yi = 0;

        int[][] stack = new int[div][3];
        int stackpointer;
        int stackstart;
        int[] sir;
        int rbs;
        int r1 = mBlurRadius + 1;
        int routsum, goutsum, boutsum;
        int rinsum, ginsum, binsum;

        for (y = 0; y < h; y++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            for (i = -mBlurRadius; i <= mBlurRadius; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                sir = stack[i + mBlurRadius];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
            }
            stackpointer = mBlurRadius;

            for (x = 0; x < w; x++) {

                r[yi] = dv[rsum];
                g[yi] = dv[gsum];
                b[yi] = dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - mBlurRadius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (y == 0) {
                    vmin[x] = Math.min(x + mBlurRadius + 1, wm);
                }
                p = pix[yw + vmin[x]];

                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[(stackpointer) % div];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi++;
            }
            yw += w;
        }
        for (x = 0; x < w; x++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            yp = -mBlurRadius * w;
            for (i = -mBlurRadius; i <= mBlurRadius; i++) {
                yi = Math.max(0, yp) + x;

                sir = stack[i + mBlurRadius];

                sir[0] = r[yi];
                sir[1] = g[yi];
                sir[2] = b[yi];

                rbs = r1 - Math.abs(i);

                rsum += r[yi] * rbs;
                gsum += g[yi] * rbs;
                bsum += b[yi] * rbs;

                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }

                if (i < hm) {
                    yp += w;
                }
            }
            yi = x;
            stackpointer = mBlurRadius;
            for (y = 0; y < h; y++) {
                // Preserve alpha channel: ( 0xff000000 & pix[yi] )
                pix[yi] = ( 0xff000000 & pix[yi] ) | ( dv[rsum] << 16 ) | ( dv[gsum] << 8 ) | dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - mBlurRadius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w;
                }
                p = x + vmin[y];

                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi += w;
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h);

        return (bitmap);
    }
}
