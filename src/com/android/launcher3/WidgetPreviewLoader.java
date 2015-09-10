package com.android.launcher3;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
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
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.util.LongSparseArray;

import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.widget.WidgetCell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class WidgetPreviewLoader {

    private static final String TAG = "WidgetPreviewLoader";
    private static final boolean DEBUG = false;

    private static final float WIDGET_PREVIEW_ICON_PADDING_PERCENTAGE = 0.25f;

    private final HashMap<String, long[]> mPackageVersions = new HashMap<>();

    /**
     * Weak reference objects, do not prevent their referents from being made finalizable,
     * finalized, and then reclaimed.
     * Note: synchronized block used for this variable is expensive and the block should always
     * be posted to a background thread.
     */
    @Thunk final Set<Bitmap> mUnusedBitmaps =
            Collections.newSetFromMap(new WeakHashMap<Bitmap, Boolean>());

    private final Context mContext;
    private final IconCache mIconCache;
    private final UserManagerCompat mUserManager;
    private final AppWidgetManagerCompat mManager;
    private final CacheDb mDb;
    private final int mProfileBadgeMargin;

    private final MainThreadExecutor mMainThreadExecutor = new MainThreadExecutor();
    @Thunk final Handler mWorkerHandler;

    public WidgetPreviewLoader(Context context, IconCache iconCache) {
        mContext = context;
        mIconCache = iconCache;
        mManager = AppWidgetManagerCompat.getInstance(context);
        mUserManager = UserManagerCompat.getInstance(context);
        mDb = new CacheDb(context);
        mWorkerHandler = new Handler(LauncherModel.getWorkerLooper());
        mProfileBadgeMargin = context.getResources()
                .getDimensionPixelSize(R.dimen.profile_badge_margin);
    }

    /**
     * Generates the widget preview on {@link AsyncTask#THREAD_POOL_EXECUTOR}. Must be
     * called on UI thread
     *
     * @param o either {@link LauncherAppWidgetProviderInfo} or {@link ResolveInfo}
     * @return a request id which can be used to cancel the request.
     */
    public PreviewLoadRequest getPreview(final Object o, int previewWidth,
            int previewHeight, WidgetCell caller) {
        String size = previewWidth + "x" + previewHeight;
        WidgetCacheKey key = getObjectKey(o, size);

        PreviewLoadTask task = new PreviewLoadTask(key, o, previewWidth, previewHeight, caller);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        return new PreviewLoadRequest(task);
    }

    /**
     * The DB holds the generated previews for various components. Previews can also have different
     * sizes (landscape vs portrait).
     */
    private static class CacheDb extends SQLiteOpenHelper {
        private static final int DB_VERSION = 4;

        private static final String TABLE_NAME = "shortcut_and_widget_previews";
        private static final String COLUMN_COMPONENT = "componentName";
        private static final String COLUMN_USER = "profileId";
        private static final String COLUMN_SIZE = "size";
        private static final String COLUMN_PACKAGE = "packageName";
        private static final String COLUMN_LAST_UPDATED = "lastUpdated";
        private static final String COLUMN_VERSION = "version";
        private static final String COLUMN_PREVIEW_BITMAP = "preview_bitmap";

        public CacheDb(Context context) {
            super(context, LauncherFiles.WIDGET_PREVIEWS_DB, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    COLUMN_COMPONENT + " TEXT NOT NULL, " +
                    COLUMN_USER + " INTEGER NOT NULL, " +
                    COLUMN_SIZE + " TEXT NOT NULL, " +
                    COLUMN_PACKAGE + " TEXT NOT NULL, " +
                    COLUMN_LAST_UPDATED + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_VERSION + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_PREVIEW_BITMAP + " BLOB, " +
                    "PRIMARY KEY (" + COLUMN_COMPONENT + ", " + COLUMN_USER + ", " + COLUMN_SIZE + ") " +
                    ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion != newVersion) {
                clearDB(db);
            }
        }

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion != newVersion) {
                clearDB(db);
            }
        }

        private void clearDB(SQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
            onCreate(db);
        }
    }

    private WidgetCacheKey getObjectKey(Object o, String size) {
        // should cache the string builder
        if (o instanceof LauncherAppWidgetProviderInfo) {
            LauncherAppWidgetProviderInfo info = (LauncherAppWidgetProviderInfo) o;
            return new WidgetCacheKey(info.provider, mManager.getUser(info), size);
        } else {
            ResolveInfo info = (ResolveInfo) o;
            return new WidgetCacheKey(
                    new ComponentName(info.activityInfo.packageName, info.activityInfo.name),
                    UserHandleCompat.myUserHandle(), size);
        }
    }

    @Thunk void writeToDb(WidgetCacheKey key, long[] versions, Bitmap preview) {
        ContentValues values = new ContentValues();
        values.put(CacheDb.COLUMN_COMPONENT, key.componentName.flattenToShortString());
        values.put(CacheDb.COLUMN_USER, mUserManager.getSerialNumberForUser(key.user));
        values.put(CacheDb.COLUMN_SIZE, key.size);
        values.put(CacheDb.COLUMN_PACKAGE, key.componentName.getPackageName());
        values.put(CacheDb.COLUMN_VERSION, versions[0]);
        values.put(CacheDb.COLUMN_LAST_UPDATED, versions[1]);
        values.put(CacheDb.COLUMN_PREVIEW_BITMAP, Utilities.flattenBitmap(preview));

        try {
            mDb.getWritableDatabase().insertWithOnConflict(CacheDb.TABLE_NAME, null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
        } catch (SQLException e) {
            Log.e(TAG, "Error saving image to DB", e);
        }
    }

    public void removePackage(String packageName, UserHandleCompat user) {
        removePackage(packageName, user, mUserManager.getSerialNumberForUser(user));
    }

    private void removePackage(String packageName, UserHandleCompat user, long userSerial) {
        synchronized(mPackageVersions) {
            mPackageVersions.remove(packageName);
        }

        try {
            mDb.getWritableDatabase().delete(CacheDb.TABLE_NAME,
                    CacheDb.COLUMN_PACKAGE + " = ? AND " + CacheDb.COLUMN_USER + " = ?",
                    new String[] {packageName, Long.toString(userSerial)});
        } catch (SQLException e) {
            Log.e(TAG, "Unable to delete items from DB", e);
        }
    }

    /**
     * Updates the persistent DB:
     *   1. Any preview generated for an old package version is removed
     *   2. Any preview for an absent package is removed
     * This ensures that we remove entries for packages which changed while the launcher was dead.
     */
    public void removeObsoletePreviews(ArrayList<Object> list) {
        Utilities.assertWorkerThread();

        LongSparseArray<HashSet<String>> validPackages = new LongSparseArray<>();

        for (Object obj : list) {
            final UserHandleCompat user;
            final String pkg;
            if (obj instanceof ResolveInfo) {
                user = UserHandleCompat.myUserHandle();
                pkg = ((ResolveInfo) obj).activityInfo.packageName;
            } else {
                LauncherAppWidgetProviderInfo info = (LauncherAppWidgetProviderInfo) obj;
                user = mManager.getUser(info);
                pkg = info.provider.getPackageName();
            }

            final long userId = mUserManager.getSerialNumberForUser(user);
            HashSet<String> packages = validPackages.get(userId);
            if (packages == null) {
                packages = new HashSet<>();
                validPackages.put(userId, packages);
            }
            packages.add(pkg);
        }

        LongSparseArray<HashSet<String>> packagesToDelete = new LongSparseArray<>();
        Cursor c = null;
        try {
            c = mDb.getReadableDatabase().query(CacheDb.TABLE_NAME,
                    new String[] {CacheDb.COLUMN_USER, CacheDb.COLUMN_PACKAGE,
                        CacheDb.COLUMN_LAST_UPDATED, CacheDb.COLUMN_VERSION},
                    null, null, null, null, null);
            while (c.moveToNext()) {
                long userId = c.getLong(0);
                String pkg = c.getString(1);
                long lastUpdated = c.getLong(2);
                long version = c.getLong(3);

                HashSet<String> packages = validPackages.get(userId);
                if (packages != null && packages.contains(pkg)) {
                    long[] versions = getPackageVersion(pkg);
                    if (versions[0] == version && versions[1] == lastUpdated) {
                        // Every thing checks out
                        continue;
                    }
                }

                // We need to delete this package.
                packages = packagesToDelete.get(userId);
                if (packages == null) {
                    packages = new HashSet<>();
                    packagesToDelete.put(userId, packages);
                }
                packages.add(pkg);
            }

            for (int i = 0; i < packagesToDelete.size(); i++) {
                long userId = packagesToDelete.keyAt(i);
                UserHandleCompat user = mUserManager.getUserForSerialNumber(userId);
                for (String pkg : packagesToDelete.valueAt(i)) {
                    removePackage(pkg, user, userId);
                }
            }
        } catch (SQLException e) {
            Log.e(TAG, "Error updatating widget previews", e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    /**
     * Reads the preview bitmap from the DB or null if the preview is not in the DB.
     */
    @Thunk Bitmap readFromDb(WidgetCacheKey key, Bitmap recycle, PreviewLoadTask loadTask) {
        Cursor cursor = null;
        try {
            cursor = mDb.getReadableDatabase().query(
                    CacheDb.TABLE_NAME,
                    new String[] { CacheDb.COLUMN_PREVIEW_BITMAP },
                    CacheDb.COLUMN_COMPONENT + " = ? AND " + CacheDb.COLUMN_USER + " = ? AND " + CacheDb.COLUMN_SIZE + " = ?",
                    new String[] {
                            key.componentName.flattenToString(),
                            Long.toString(mUserManager.getSerialNumberForUser(key.user)),
                            key.size
                    },
                    null, null, null);
            // If cancelled, skip getting the blob and decoding it into a bitmap
            if (loadTask.isCancelled()) {
                return null;
            }
            if (cursor.moveToNext()) {
                byte[] blob = cursor.getBlob(0);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inBitmap = recycle;
                try {
                    if (!loadTask.isCancelled()) {
                        return BitmapFactory.decodeByteArray(blob, 0, blob.length, opts);
                    }
                } catch (Exception e) {
                    return null;
                }
            }
        } catch (SQLException e) {
            Log.w(TAG, "Error loading preview from DB", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    @Thunk Bitmap generatePreview(Launcher launcher, Object info, Bitmap recycle,
            int previewWidth, int previewHeight) {
        if (info instanceof LauncherAppWidgetProviderInfo) {
            return generateWidgetPreview(launcher, (LauncherAppWidgetProviderInfo) info,
                    previewWidth, recycle, null);
        } else {
            return generateShortcutPreview(launcher,
                    (ResolveInfo) info, previewWidth, previewHeight, recycle);
        }
    }

    public Bitmap generateWidgetPreview(Launcher launcher, LauncherAppWidgetProviderInfo info,
            int maxPreviewWidth, Bitmap preview, int[] preScaledWidthOut) {
        // Load the preview image if possible
        if (maxPreviewWidth < 0) maxPreviewWidth = Integer.MAX_VALUE;

        Drawable drawable = null;
        if (info.previewImage != 0) {
            drawable = mManager.loadPreview(info);
            if (drawable != null) {
                drawable = mutateOnMainThread(drawable);
            } else {
                Log.w(TAG, "Can't load widget preview drawable 0x" +
                        Integer.toHexString(info.previewImage) + " for provider: " + info.provider);
            }
        }

        final boolean widgetPreviewExists = (drawable != null);
        final int spanX = info.spanX;
        final int spanY = info.spanY;

        int previewWidth;
        int previewHeight;
        Bitmap tileBitmap = null;

        if (widgetPreviewExists) {
            previewWidth = drawable.getIntrinsicWidth();
            previewHeight = drawable.getIntrinsicHeight();
        } else {
            // Generate a preview image if we couldn't load one
            tileBitmap = ((BitmapDrawable) mContext.getResources().getDrawable(
                    R.drawable.widget_tile)).getBitmap();
            previewWidth = tileBitmap.getWidth() * spanX;
            previewHeight = tileBitmap.getHeight() * spanY;
        }

        // Scale to fit width only - let the widget preview be clipped in the
        // vertical dimension
        float scale = 1f;
        if (preScaledWidthOut != null) {
            preScaledWidthOut[0] = previewWidth;
        }
        if (previewWidth > maxPreviewWidth) {
            scale = (maxPreviewWidth - 2 * mProfileBadgeMargin) / (float) (previewWidth);
        }
        if (scale != 1f) {
            previewWidth = (int) (scale * previewWidth);
            previewHeight = (int) (scale * previewHeight);
        }

        // If a bitmap is passed in, we use it; otherwise, we create a bitmap of the right size
        final Canvas c = new Canvas();
        if (preview == null) {
            preview = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
            c.setBitmap(preview);
        } else {
            // Reusing bitmap. Clear it.
            c.setBitmap(preview);
            c.drawColor(0, PorterDuff.Mode.CLEAR);
        }

        // Draw the scaled preview into the final bitmap
        int x = (preview.getWidth() - previewWidth) / 2;
        if (widgetPreviewExists) {
            drawable.setBounds(x, 0, x + previewWidth, previewHeight);
            drawable.draw(c);
        } else {
            final Paint p = new Paint();
            p.setFilterBitmap(true);
            int appIconSize = launcher.getDeviceProfile().iconSizePx;

            // draw the spanX x spanY tiles
            final Rect src = new Rect(0, 0, tileBitmap.getWidth(), tileBitmap.getHeight());

            float tileW = scale * tileBitmap.getWidth();
            float tileH = scale * tileBitmap.getHeight();
            final RectF dst = new RectF(0, 0, tileW, tileH);

            float tx = x;
            for (int i = 0; i < spanX; i++, tx += tileW) {
                float ty = 0;
                for (int j = 0; j < spanY; j++, ty += tileH) {
                    dst.offsetTo(tx, ty);
                    c.drawBitmap(tileBitmap, src, dst, p);
                }
            }

            // Draw the icon in the top left corner
            // TODO: use top right for RTL
            int minOffset = (int) (appIconSize * WIDGET_PREVIEW_ICON_PADDING_PERCENTAGE);
            int smallestSide = Math.min(previewWidth, previewHeight);
            float iconScale = Math.min((float) smallestSide / (appIconSize + 2 * minOffset), scale);

            try {
                Drawable icon = mutateOnMainThread(mManager.loadIcon(info, mIconCache));
                if (icon != null) {
                    int hoffset = (int) ((tileW - appIconSize * iconScale) / 2) + x;
                    int yoffset = (int) ((tileH - appIconSize * iconScale) / 2);
                    icon.setBounds(hoffset, yoffset,
                            hoffset + (int) (appIconSize * iconScale),
                            yoffset + (int) (appIconSize * iconScale));
                    icon.draw(c);
                }
            } catch (Resources.NotFoundException e) { }
            c.setBitmap(null);
        }
        int imageHeight = Math.min(preview.getHeight(), previewHeight + mProfileBadgeMargin);
        return mManager.getBadgeBitmap(info, preview, imageHeight);
    }

    private Bitmap generateShortcutPreview(
            Launcher launcher, ResolveInfo info, int maxWidth, int maxHeight, Bitmap preview) {
        final Canvas c = new Canvas();
        if (preview == null) {
            preview = Bitmap.createBitmap(maxWidth, maxHeight, Config.ARGB_8888);
            c.setBitmap(preview);
        } else if (preview.getWidth() != maxWidth || preview.getHeight() != maxHeight) {
            throw new RuntimeException("Improperly sized bitmap passed as argument");
        } else {
            // Reusing bitmap. Clear it.
            c.setBitmap(preview);
            c.drawColor(0, PorterDuff.Mode.CLEAR);
        }

        Drawable icon = mutateOnMainThread(mIconCache.getFullResIcon(info.activityInfo));
        icon.setFilterBitmap(true);

        // Draw a desaturated/scaled version of the icon in the background as a watermark
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);
        icon.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        icon.setAlpha((int) (255 * 0.06f));

        Resources res = mContext.getResources();
        int paddingTop = res.getDimensionPixelOffset(R.dimen.shortcut_preview_padding_top);
        int paddingLeft = res.getDimensionPixelOffset(R.dimen.shortcut_preview_padding_left);
        int paddingRight = res.getDimensionPixelOffset(R.dimen.shortcut_preview_padding_right);
        int scaledIconWidth = (maxWidth - paddingLeft - paddingRight);
        icon.setBounds(paddingLeft, paddingTop,
                paddingLeft + scaledIconWidth, paddingTop + scaledIconWidth);
        icon.draw(c);

        // Draw the final icon at top left corner.
        // TODO: use top right for RTL
        int appIconSize = launcher.getDeviceProfile().iconSizePx;

        icon.setAlpha(255);
        icon.setColorFilter(null);
        icon.setBounds(0, 0, appIconSize, appIconSize);
        icon.draw(c);

        c.setBitmap(null);
        return preview;
    }

    private Drawable mutateOnMainThread(final Drawable drawable) {
        try {
            return mMainThreadExecutor.submit(new Callable<Drawable>() {
                @Override
                public Drawable call() throws Exception {
                    return drawable.mutate();
                }
            }).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return an array of containing versionCode and lastUpdatedTime for the package.
     */
    @Thunk long[] getPackageVersion(String packageName) {
        synchronized (mPackageVersions) {
            long[] versions = mPackageVersions.get(packageName);
            if (versions == null) {
                versions = new long[2];
                try {
                    PackageInfo info = mContext.getPackageManager().getPackageInfo(packageName, 0);
                    versions[0] = info.versionCode;
                    versions[1] = info.lastUpdateTime;
                } catch (NameNotFoundException e) {
                    Log.e(TAG, "PackageInfo not found", e);
                }
                mPackageVersions.put(packageName, versions);
            }
            return versions;
        }
    }

    /**
     * A request Id which can be used by the client to cancel any request.
     */
    public class PreviewLoadRequest {

        @Thunk final PreviewLoadTask mTask;

        public PreviewLoadRequest(PreviewLoadTask task) {
            mTask = task;
        }

        public void cleanup() {
            if (mTask != null) {
                mTask.cancel(true);
            }

            // This only handles the case where the PreviewLoadTask is cancelled after the task has
            // successfully completed (including having written to disk when necessary).  In the
            // other cases where it is cancelled while the task is running, it will be cleaned up
            // in the tasks's onCancelled() call, and if cancelled while the task is writing to
            // disk, it will be cancelled in the task's onPostExecute() call.
            if (mTask.mBitmapToRecycle != null) {
                mWorkerHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (mUnusedBitmaps) {
                            mUnusedBitmaps.add(mTask.mBitmapToRecycle);
                        }
                        mTask.mBitmapToRecycle = null;
                    }
                });
            }
        }
    }

    public class PreviewLoadTask extends AsyncTask<Void, Void, Bitmap> {
        @Thunk final WidgetCacheKey mKey;
        private final Object mInfo;
        private final int mPreviewHeight;
        private final int mPreviewWidth;
        private final WidgetCell mCaller;
        @Thunk long[] mVersions;
        @Thunk Bitmap mBitmapToRecycle;

        PreviewLoadTask(WidgetCacheKey key, Object info, int previewWidth,
                int previewHeight, WidgetCell caller) {
            mKey = key;
            mInfo = info;
            mPreviewHeight = previewHeight;
            mPreviewWidth = previewWidth;
            mCaller = caller;
            if (DEBUG) {
                Log.d(TAG, String.format("%s, %s, %d, %d",
                        mKey, mInfo, mPreviewHeight, mPreviewWidth));
            }
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            Bitmap unusedBitmap = null;

            // If already cancelled before this gets to run in the background, then return early
            if (isCancelled()) {
                return null;
            }
            synchronized (mUnusedBitmaps) {
                // Check if we can re-use a bitmap
                for (Bitmap candidate : mUnusedBitmaps) {
                    if (candidate != null && candidate.isMutable() &&
                            candidate.getWidth() == mPreviewWidth &&
                            candidate.getHeight() == mPreviewHeight) {
                        unusedBitmap = candidate;
                        mUnusedBitmaps.remove(unusedBitmap);
                        break;
                    }
                }
            }

            // creating a bitmap is expensive. Do not do this inside synchronized block.
            if (unusedBitmap == null) {
                unusedBitmap = Bitmap.createBitmap(mPreviewWidth, mPreviewHeight, Config.ARGB_8888);
            }
            // If cancelled now, don't bother reading the preview from the DB
            if (isCancelled()) {
                return unusedBitmap;
            }
            Bitmap preview = readFromDb(mKey, unusedBitmap, this);
            // Only consider generating the preview if we have not cancelled the task already
            if (!isCancelled() && preview == null) {
                // Fetch the version info before we generate the preview, so that, in-case the
                // app was updated while we are generating the preview, we use the old version info,
                // which would gets re-written next time.
                mVersions = getPackageVersion(mKey.componentName.getPackageName());

                Launcher launcher = (Launcher) mCaller.getContext();

                // it's not in the db... we need to generate it
                preview = generatePreview(launcher, mInfo, unusedBitmap, mPreviewWidth, mPreviewHeight);
            }
            return preview;
        }

        @Override
        protected void onPostExecute(final Bitmap preview) {
            mCaller.applyPreview(preview);

            // Write the generated preview to the DB in the worker thread
            if (mVersions != null) {
                mWorkerHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isCancelled()) {
                            // If we are still using this preview, then write it to the DB and then
                            // let the normal clear mechanism recycle the bitmap
                            writeToDb(mKey, mVersions, preview);
                            mBitmapToRecycle = preview;
                        } else {
                            // If we've already cancelled, then skip writing the bitmap to the DB
                            // and manually add the bitmap back to the recycled set
                            synchronized (mUnusedBitmaps) {
                                mUnusedBitmaps.add(preview);
                            }
                        }
                    }
                });
            } else {
                // If we don't need to write to disk, then ensure the preview gets recycled by
                // the normal clear mechanism
                mBitmapToRecycle = preview;
            }
        }

        @Override
        protected void onCancelled(final Bitmap preview) {
            // If we've cancelled while the task is running, then can return the bitmap to the
            // recycled set immediately. Otherwise, it will be recycled after the preview is written
            // to disk.
            if (preview != null) {
                mWorkerHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (mUnusedBitmaps) {
                            mUnusedBitmaps.add(preview);
                        }
                    }
                });
            }
        }
    }

    private static final class WidgetCacheKey extends ComponentKey {

        // TODO: remove dependency on size
        @Thunk final String size;

        public WidgetCacheKey(ComponentName componentName, UserHandleCompat user, String size) {
            super(componentName, user);
            this.size = size;
        }

        @Override
        public int hashCode() {
            return super.hashCode() ^ size.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return super.equals(o) && ((WidgetCacheKey) o).size.equals(size);
        }
    }
}
