package com.android.launcher3;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

abstract class SoftReferenceThreadLocal<T> {
    private ThreadLocal<SoftReference<T>> mThreadLocal;
    public SoftReferenceThreadLocal() {
        mThreadLocal = new ThreadLocal<SoftReference<T>>();
    }

    abstract T initialValue();

    public void set(T t) {
        mThreadLocal.set(new SoftReference<T>(t));
    }

    public T get() {
        SoftReference<T> reference = mThreadLocal.get();
        T obj;
        if (reference == null) {
            obj = initialValue();
            mThreadLocal.set(new SoftReference<T>(obj));
            return obj;
        } else {
            obj = reference.get();
            if (obj == null) {
                obj = initialValue();
                mThreadLocal.set(new SoftReference<T>(obj));
            }
            return obj;
        }
    }
}

class CanvasCache extends SoftReferenceThreadLocal<Canvas> {
    @Override
    protected Canvas initialValue() {
        return new Canvas();
    }
}

class PaintCache extends SoftReferenceThreadLocal<Paint> {
    @Override
    protected Paint initialValue() {
        return null;
    }
}

class BitmapCache extends SoftReferenceThreadLocal<Bitmap> {
    @Override
    protected Bitmap initialValue() {
        return null;
    }
}

class RectCache extends SoftReferenceThreadLocal<Rect> {
    @Override
    protected Rect initialValue() {
        return new Rect();
    }
}

class BitmapFactoryOptionsCache extends SoftReferenceThreadLocal<BitmapFactory.Options> {
    @Override
    protected BitmapFactory.Options initialValue() {
        return new BitmapFactory.Options();
    }
}

public class WidgetPreviewLoader {
    static final String TAG = "WidgetPreviewLoader";

    private int mPreviewBitmapWidth;
    private int mPreviewBitmapHeight;
    private String mSize;
    private Context mContext;
    private PackageManager mPackageManager;
    private PagedViewCellLayout mWidgetSpacingLayout;

    // Used for drawing shortcut previews
    private BitmapCache mCachedShortcutPreviewBitmap = new BitmapCache();
    private PaintCache mCachedShortcutPreviewPaint = new PaintCache();
    private CanvasCache mCachedShortcutPreviewCanvas = new CanvasCache();

    // Used for drawing widget previews
    private CanvasCache mCachedAppWidgetPreviewCanvas = new CanvasCache();
    private RectCache mCachedAppWidgetPreviewSrcRect = new RectCache();
    private RectCache mCachedAppWidgetPreviewDestRect = new RectCache();
    private PaintCache mCachedAppWidgetPreviewPaint = new PaintCache();
    private String mCachedSelectQuery;
    private BitmapFactoryOptionsCache mCachedBitmapFactoryOptions = new BitmapFactoryOptionsCache();

    private int mAppIconSize;
    private IconCache mIconCache;

    private final float sWidgetPreviewIconPaddingPercentage = 0.25f;

    private CacheDb mDb;

    private HashMap<String, WeakReference<Bitmap>> mLoadedPreviews;
    private ArrayList<SoftReference<Bitmap>> mUnusedBitmaps;
    private static HashSet<String> sInvalidPackages;

    static {
        sInvalidPackages = new HashSet<String>();
    }

    public WidgetPreviewLoader(Context context) {
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();

        mContext = context;
        mPackageManager = mContext.getPackageManager();
        mAppIconSize = grid.iconSizePx;
        mIconCache = app.getIconCache();
        mDb = app.getWidgetPreviewCacheDb();
        mLoadedPreviews = new HashMap<String, WeakReference<Bitmap>>();
        mUnusedBitmaps = new ArrayList<SoftReference<Bitmap>>();
    }

    public void setPreviewSize(int previewWidth, int previewHeight,
            PagedViewCellLayout widgetSpacingLayout) {
        mPreviewBitmapWidth = previewWidth;
        mPreviewBitmapHeight = previewHeight;
        mSize = previewWidth + "x" + previewHeight;
        mWidgetSpacingLayout = widgetSpacingLayout;
    }

    public Bitmap getPreview(final Object o) {
        final String name = getObjectName(o);
        final String packageName = getObjectPackage(o);
        // check if the package is valid
        boolean packageValid = true;
        synchronized(sInvalidPackages) {
            packageValid = !sInvalidPackages.contains(packageName);
        }
        if (!packageValid) {
            return null;
        }
        if (packageValid) {
            synchronized(mLoadedPreviews) {
                // check if it exists in our existing cache
                if (mLoadedPreviews.containsKey(name) && mLoadedPreviews.get(name).get() != null) {
                    return mLoadedPreviews.get(name).get();
                }
            }
        }

        Bitmap unusedBitmap = null;
        synchronized(mUnusedBitmaps) {
            // not in cache; we need to load it from the db
            while ((unusedBitmap == null || !unusedBitmap.isMutable() ||
                    unusedBitmap.getWidth() != mPreviewBitmapWidth ||
                    unusedBitmap.getHeight() != mPreviewBitmapHeight)
                    && mUnusedBitmaps.size() > 0) {
                unusedBitmap = mUnusedBitmaps.remove(0).get();
            }
            if (unusedBitmap != null) {
                final Canvas c = mCachedAppWidgetPreviewCanvas.get();
                c.setBitmap(unusedBitmap);
                c.drawColor(0, PorterDuff.Mode.CLEAR);
                c.setBitmap(null);
            }
        }

        if (unusedBitmap == null) {
            unusedBitmap = Bitmap.createBitmap(mPreviewBitmapWidth, mPreviewBitmapHeight,
                    Bitmap.Config.ARGB_8888);
        }

        Bitmap preview = null;

        if (packageValid) {
            preview = readFromDb(name, unusedBitmap);
        }

        if (preview != null) {
            synchronized(mLoadedPreviews) {
                mLoadedPreviews.put(name, new WeakReference<Bitmap>(preview));
            }
            return preview;
        } else {
            // it's not in the db... we need to generate it
            final Bitmap generatedPreview = generatePreview(o, unusedBitmap);
            preview = generatedPreview;
            if (preview != unusedBitmap) {
                throw new RuntimeException("generatePreview is not recycling the bitmap " + o);
            }

            synchronized(mLoadedPreviews) {
                mLoadedPreviews.put(name, new WeakReference<Bitmap>(preview));
            }

            // write to db on a thread pool... this can be done lazily and improves the performance
            // of the first time widget previews are loaded
            new AsyncTask<Void, Void, Void>() {
                public Void doInBackground(Void ... args) {
                    writeToDb(o, generatedPreview);
                    return null;
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void) null);

            return preview;
        }
    }

    public void recycleBitmap(Object o, Bitmap bitmapToRecycle) {
        String name = getObjectName(o);
        synchronized (mLoadedPreviews) {
            if (mLoadedPreviews.containsKey(name)) {
                Bitmap b = mLoadedPreviews.get(name).get();
                if (b == bitmapToRecycle) {
                    mLoadedPreviews.remove(name);
                    if (bitmapToRecycle.isMutable()) {
                        synchronized (mUnusedBitmaps) {
                            mUnusedBitmaps.add(new SoftReference<Bitmap>(b));
                        }
                    }
                } else {
                    throw new RuntimeException("Bitmap passed in doesn't match up");
                }
            }
        }
    }

    static class CacheDb extends SQLiteOpenHelper {
        final static int DB_VERSION = 2;
        final static String DB_NAME = "widgetpreviews.db";
        final static String TABLE_NAME = "shortcut_and_widget_previews";
        final static String COLUMN_NAME = "name";
        final static String COLUMN_SIZE = "size";
        final static String COLUMN_PREVIEW_BITMAP = "preview_bitmap";
        Context mContext;

        public CacheDb(Context context) {
            super(context, new File(context.getCacheDir(), DB_NAME).getPath(), null, DB_VERSION);
            // Store the context for later use
            mContext = context;
        }

        @Override
        public void onCreate(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    COLUMN_NAME + " TEXT NOT NULL, " +
                    COLUMN_SIZE + " TEXT NOT NULL, " +
                    COLUMN_PREVIEW_BITMAP + " BLOB NOT NULL, " +
                    "PRIMARY KEY (" + COLUMN_NAME + ", " + COLUMN_SIZE + ") " +
                    ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion != newVersion) {
                // Delete all the records; they'll be repopulated as this is a cache
                db.execSQL("DELETE FROM " + TABLE_NAME);
            }
        }
    }

    private static final String WIDGET_PREFIX = "Widget:";
    private static final String SHORTCUT_PREFIX = "Shortcut:";

    private static String getObjectName(Object o) {
        // should cache the string builder
        StringBuilder sb = new StringBuilder();
        String output;
        if (o instanceof AppWidgetProviderInfo) {
            sb.append(WIDGET_PREFIX);
            sb.append(((AppWidgetProviderInfo) o).provider.flattenToString());
            output = sb.toString();
            sb.setLength(0);
        } else {
            sb.append(SHORTCUT_PREFIX);

            ResolveInfo info = (ResolveInfo) o;
            sb.append(new ComponentName(info.activityInfo.packageName,
                    info.activityInfo.name).flattenToString());
            output = sb.toString();
            sb.setLength(0);
        }
        return output;
    }

    private String getObjectPackage(Object o) {
        if (o instanceof AppWidgetProviderInfo) {
            return ((AppWidgetProviderInfo) o).provider.getPackageName();
        } else {
            ResolveInfo info = (ResolveInfo) o;
            return info.activityInfo.packageName;
        }
    }

    private void writeToDb(Object o, Bitmap preview) {
        String name = getObjectName(o);
        SQLiteDatabase db = mDb.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(CacheDb.COLUMN_NAME, name);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        preview.compress(Bitmap.CompressFormat.PNG, 100, stream);
        values.put(CacheDb.COLUMN_PREVIEW_BITMAP, stream.toByteArray());
        values.put(CacheDb.COLUMN_SIZE, mSize);
        db.insert(CacheDb.TABLE_NAME, null, values);
    }

    public static void removePackageFromDb(final CacheDb cacheDb, final String packageName) {
        synchronized(sInvalidPackages) {
            sInvalidPackages.add(packageName);
        }
        new AsyncTask<Void, Void, Void>() {
            public Void doInBackground(Void ... args) {
                SQLiteDatabase db = cacheDb.getWritableDatabase();
                db.delete(CacheDb.TABLE_NAME,
                        CacheDb.COLUMN_NAME + " LIKE ? OR " +
                        CacheDb.COLUMN_NAME + " LIKE ?", // SELECT query
                        new String[] {
                            WIDGET_PREFIX + packageName + "/%",
                            SHORTCUT_PREFIX + packageName + "/%"} // args to SELECT query
                            );
                synchronized(sInvalidPackages) {
                    sInvalidPackages.remove(packageName);
                }
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void) null);
    }

    public static void removeItemFromDb(final CacheDb cacheDb, final String objectName) {
        new AsyncTask<Void, Void, Void>() {
            public Void doInBackground(Void ... args) {
                SQLiteDatabase db = cacheDb.getWritableDatabase();
                db.delete(CacheDb.TABLE_NAME,
                        CacheDb.COLUMN_NAME + " = ? ", // SELECT query
                        new String[] { objectName }); // args to SELECT query
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void) null);
    }

    private Bitmap readFromDb(String name, Bitmap b) {
        if (mCachedSelectQuery == null) {
            mCachedSelectQuery = CacheDb.COLUMN_NAME + " = ? AND " +
                    CacheDb.COLUMN_SIZE + " = ?";
        }
        SQLiteDatabase db = mDb.getReadableDatabase();
        Cursor result = db.query(CacheDb.TABLE_NAME,
                new String[] { CacheDb.COLUMN_PREVIEW_BITMAP }, // cols to return
                mCachedSelectQuery, // select query
                new String[] { name, mSize }, // args to select query
                null,
                null,
                null,
                null);
        if (result.getCount() > 0) {
            result.moveToFirst();
            byte[] blob = result.getBlob(0);
            result.close();
            final BitmapFactory.Options opts = mCachedBitmapFactoryOptions.get();
            opts.inBitmap = b;
            opts.inSampleSize = 1;
            try {
                return BitmapFactory.decodeByteArray(blob, 0, blob.length, opts);
            } catch (IllegalArgumentException e) {
                removeItemFromDb(mDb, name);
                return null;
            }
        } else {
            result.close();
            return null;
        }
    }

    public Bitmap generatePreview(Object info, Bitmap preview) {
        if (preview != null &&
                (preview.getWidth() != mPreviewBitmapWidth ||
                preview.getHeight() != mPreviewBitmapHeight)) {
            throw new RuntimeException("Improperly sized bitmap passed as argument");
        }
        if (info instanceof AppWidgetProviderInfo) {
            return generateWidgetPreview((AppWidgetProviderInfo) info, preview);
        } else {
            return generateShortcutPreview(
                    (ResolveInfo) info, mPreviewBitmapWidth, mPreviewBitmapHeight, preview);
        }
    }

    public Bitmap generateWidgetPreview(AppWidgetProviderInfo info, Bitmap preview) {
        int[] cellSpans = Launcher.getSpanForWidget(mContext, info);
        int maxWidth = maxWidthForWidgetPreview(cellSpans[0]);
        int maxHeight = maxHeightForWidgetPreview(cellSpans[1]);
        return generateWidgetPreview(info.provider, info.previewImage, info.icon,
                cellSpans[0], cellSpans[1], maxWidth, maxHeight, preview, null);
    }

    public int maxWidthForWidgetPreview(int spanX) {
        return Math.min(mPreviewBitmapWidth,
                mWidgetSpacingLayout.estimateCellWidth(spanX));
    }

    public int maxHeightForWidgetPreview(int spanY) {
        return Math.min(mPreviewBitmapHeight,
                mWidgetSpacingLayout.estimateCellHeight(spanY));
    }

    public Bitmap generateWidgetPreview(ComponentName provider, int previewImage,
            int iconId, int cellHSpan, int cellVSpan, int maxPreviewWidth, int maxPreviewHeight,
            Bitmap preview, int[] preScaledWidthOut) {
        // Load the preview image if possible
        String packageName = provider.getPackageName();
        if (maxPreviewWidth < 0) maxPreviewWidth = Integer.MAX_VALUE;
        if (maxPreviewHeight < 0) maxPreviewHeight = Integer.MAX_VALUE;

        Drawable drawable = null;
        if (previewImage != 0) {
            drawable = mPackageManager.getDrawable(packageName, previewImage, null);
            if (drawable == null) {
                Log.w(TAG, "Can't load widget preview drawable 0x" +
                        Integer.toHexString(previewImage) + " for provider: " + provider);
            }
        }

        int previewWidth;
        int previewHeight;
        Bitmap defaultPreview = null;
        boolean widgetPreviewExists = (drawable != null);
        if (widgetPreviewExists) {
            previewWidth = drawable.getIntrinsicWidth();
            previewHeight = drawable.getIntrinsicHeight();
        } else {
            // Generate a preview image if we couldn't load one
            if (cellHSpan < 1) cellHSpan = 1;
            if (cellVSpan < 1) cellVSpan = 1;

            BitmapDrawable previewDrawable = (BitmapDrawable) mContext.getResources()
                    .getDrawable(R.drawable.widget_tile);
            final int previewDrawableWidth = previewDrawable
                    .getIntrinsicWidth();
            final int previewDrawableHeight = previewDrawable
                    .getIntrinsicHeight();
            previewWidth = previewDrawableWidth * cellHSpan;
            previewHeight = previewDrawableHeight * cellVSpan;

            defaultPreview = Bitmap.createBitmap(previewWidth, previewHeight,
                    Config.ARGB_8888);
            final Canvas c = mCachedAppWidgetPreviewCanvas.get();
            c.setBitmap(defaultPreview);
            previewDrawable.setBounds(0, 0, previewWidth, previewHeight);
            previewDrawable.setTileModeXY(Shader.TileMode.REPEAT,
                    Shader.TileMode.REPEAT);
            previewDrawable.draw(c);
            c.setBitmap(null);

            // Draw the icon in the top left corner
            int minOffset = (int) (mAppIconSize * sWidgetPreviewIconPaddingPercentage);
            int smallestSide = Math.min(previewWidth, previewHeight);
            float iconScale = Math.min((float) smallestSide
                    / (mAppIconSize + 2 * minOffset), 1f);

            try {
                Drawable icon = null;
                int hoffset =
                        (int) ((previewDrawableWidth - mAppIconSize * iconScale) / 2);
                int yoffset =
                        (int) ((previewDrawableHeight - mAppIconSize * iconScale) / 2);
                if (iconId > 0)
                    icon = mIconCache.getFullResIcon(packageName, iconId);
                if (icon != null) {
                    renderDrawableToBitmap(icon, defaultPreview, hoffset,
                            yoffset, (int) (mAppIconSize * iconScale),
                            (int) (mAppIconSize * iconScale));
                }
            } catch (Resources.NotFoundException e) {
            }
        }

        // Scale to fit width only - let the widget preview be clipped in the
        // vertical dimension
        float scale = 1f;
        if (preScaledWidthOut != null) {
            preScaledWidthOut[0] = previewWidth;
        }
        if (previewWidth > maxPreviewWidth) {
            scale = maxPreviewWidth / (float) previewWidth;
        }
        if (scale != 1f) {
            previewWidth = (int) (scale * previewWidth);
            previewHeight = (int) (scale * previewHeight);
        }

        // If a bitmap is passed in, we use it; otherwise, we create a bitmap of the right size
        if (preview == null) {
            preview = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        }

        // Draw the scaled preview into the final bitmap
        int x = (preview.getWidth() - previewWidth) / 2;
        if (widgetPreviewExists) {
            renderDrawableToBitmap(drawable, preview, x, 0, previewWidth,
                    previewHeight);
        } else {
            final Canvas c = mCachedAppWidgetPreviewCanvas.get();
            final Rect src = mCachedAppWidgetPreviewSrcRect.get();
            final Rect dest = mCachedAppWidgetPreviewDestRect.get();
            c.setBitmap(preview);
            src.set(0, 0, defaultPreview.getWidth(), defaultPreview.getHeight());
            dest.set(x, 0, x + previewWidth, previewHeight);

            Paint p = mCachedAppWidgetPreviewPaint.get();
            if (p == null) {
                p = new Paint();
                p.setFilterBitmap(true);
                mCachedAppWidgetPreviewPaint.set(p);
            }
            c.drawBitmap(defaultPreview, src, dest, p);
            c.setBitmap(null);
        }
        return preview;
    }

    private Bitmap generateShortcutPreview(
            ResolveInfo info, int maxWidth, int maxHeight, Bitmap preview) {
        Bitmap tempBitmap = mCachedShortcutPreviewBitmap.get();
        final Canvas c = mCachedShortcutPreviewCanvas.get();
        if (tempBitmap == null ||
                tempBitmap.getWidth() != maxWidth ||
                tempBitmap.getHeight() != maxHeight) {
            tempBitmap = Bitmap.createBitmap(maxWidth, maxHeight, Config.ARGB_8888);
            mCachedShortcutPreviewBitmap.set(tempBitmap);
        } else {
            c.setBitmap(tempBitmap);
            c.drawColor(0, PorterDuff.Mode.CLEAR);
            c.setBitmap(null);
        }
        // Render the icon
        Drawable icon = mIconCache.getFullResIcon(info);

        int paddingTop = mContext.
                getResources().getDimensionPixelOffset(R.dimen.shortcut_preview_padding_top);
        int paddingLeft = mContext.
                getResources().getDimensionPixelOffset(R.dimen.shortcut_preview_padding_left);
        int paddingRight = mContext.
                getResources().getDimensionPixelOffset(R.dimen.shortcut_preview_padding_right);

        int scaledIconWidth = (maxWidth - paddingLeft - paddingRight);

        renderDrawableToBitmap(
                icon, tempBitmap, paddingLeft, paddingTop, scaledIconWidth, scaledIconWidth);

        if (preview != null &&
                (preview.getWidth() != maxWidth || preview.getHeight() != maxHeight)) {
            throw new RuntimeException("Improperly sized bitmap passed as argument");
        } else if (preview == null) {
            preview = Bitmap.createBitmap(maxWidth, maxHeight, Config.ARGB_8888);
        }

        c.setBitmap(preview);
        // Draw a desaturated/scaled version of the icon in the background as a watermark
        Paint p = mCachedShortcutPreviewPaint.get();
        if (p == null) {
            p = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            p.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
            p.setAlpha((int) (255 * 0.06f));
            mCachedShortcutPreviewPaint.set(p);
        }
        c.drawBitmap(tempBitmap, 0, 0, p);
        c.setBitmap(null);

        renderDrawableToBitmap(icon, preview, 0, 0, mAppIconSize, mAppIconSize);

        return preview;
    }


    public static void renderDrawableToBitmap(
            Drawable d, Bitmap bitmap, int x, int y, int w, int h) {
        renderDrawableToBitmap(d, bitmap, x, y, w, h, 1f);
    }

    private static void renderDrawableToBitmap(
            Drawable d, Bitmap bitmap, int x, int y, int w, int h,
            float scale) {
        if (bitmap != null) {
            Canvas c = new Canvas(bitmap);
            c.scale(scale, scale);
            Rect oldBounds = d.copyBounds();
            d.setBounds(x, y, x + w, y + h);
            d.draw(c);
            d.setBounds(oldBounds); // Restore the bounds
            c.setBitmap(null);
        }
    }

}
