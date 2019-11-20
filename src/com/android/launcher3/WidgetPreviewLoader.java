package com.android.launcher3;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.CancellationSignal;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;
import android.util.LongSparseArray;

import androidx.annotation.Nullable;

import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.ShortcutConfigActivityInfo;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.icons.GraphicsUtils;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.icons.ShadowGenerator;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.SQLiteCacheHelper;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.widget.WidgetCell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;

public class WidgetPreviewLoader {

    private static final String TAG = "WidgetPreviewLoader";
    private static final boolean DEBUG = false;

    private final HashMap<String, long[]> mPackageVersions = new HashMap<>();

    /**
     * Weak reference objects, do not prevent their referents from being made finalizable,
     * finalized, and then reclaimed.
     * Note: synchronized block used for this variable is expensive and the block should always
     * be posted to a background thread.
     */
    @Thunk final Set<Bitmap> mUnusedBitmaps = Collections.newSetFromMap(new WeakHashMap<>());

    private final Context mContext;
    private final IconCache mIconCache;
    private final UserManagerCompat mUserManager;
    private final CacheDb mDb;

    public WidgetPreviewLoader(Context context, IconCache iconCache) {
        mContext = context;
        mIconCache = iconCache;
        mUserManager = UserManagerCompat.getInstance(context);
        mDb = new CacheDb(context);
    }

    /**
     * Generates the widget preview on {@link AsyncTask#THREAD_POOL_EXECUTOR}. Must be
     * called on UI thread
     *
     * @return a request id which can be used to cancel the request.
     */
    public CancellationSignal getPreview(WidgetItem item, int previewWidth,
            int previewHeight, WidgetCell caller) {
        String size = previewWidth + "x" + previewHeight;
        WidgetCacheKey key = new WidgetCacheKey(item.componentName, item.user, size);

        PreviewLoadTask task = new PreviewLoadTask(key, item, previewWidth, previewHeight, caller);
        task.executeOnExecutor(Executors.THREAD_POOL_EXECUTOR);

        CancellationSignal signal = new CancellationSignal();
        signal.setOnCancelListener(task);
        return signal;
    }

    public void refresh() {
        mDb.clear();

    }
    /**
     * The DB holds the generated previews for various components. Previews can also have different
     * sizes (landscape vs portrait).
     */
    private static class CacheDb extends SQLiteCacheHelper {
        private static final int DB_VERSION = 9;

        private static final String TABLE_NAME = "shortcut_and_widget_previews";
        private static final String COLUMN_COMPONENT = "componentName";
        private static final String COLUMN_USER = "profileId";
        private static final String COLUMN_SIZE = "size";
        private static final String COLUMN_PACKAGE = "packageName";
        private static final String COLUMN_LAST_UPDATED = "lastUpdated";
        private static final String COLUMN_VERSION = "version";
        private static final String COLUMN_PREVIEW_BITMAP = "preview_bitmap";

        public CacheDb(Context context) {
            super(context, LauncherFiles.WIDGET_PREVIEWS_DB, DB_VERSION, TABLE_NAME);
        }

        @Override
        public void onCreateTable(SQLiteDatabase database) {
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
    }

    @Thunk void writeToDb(WidgetCacheKey key, long[] versions, Bitmap preview) {
        ContentValues values = new ContentValues();
        values.put(CacheDb.COLUMN_COMPONENT, key.componentName.flattenToShortString());
        values.put(CacheDb.COLUMN_USER, mUserManager.getSerialNumberForUser(key.user));
        values.put(CacheDb.COLUMN_SIZE, key.size);
        values.put(CacheDb.COLUMN_PACKAGE, key.componentName.getPackageName());
        values.put(CacheDb.COLUMN_VERSION, versions[0]);
        values.put(CacheDb.COLUMN_LAST_UPDATED, versions[1]);
        values.put(CacheDb.COLUMN_PREVIEW_BITMAP, GraphicsUtils.flattenBitmap(preview));
        mDb.insertOrReplace(values);
    }

    public void removePackage(String packageName, UserHandle user) {
        removePackage(packageName, user, mUserManager.getSerialNumberForUser(user));
    }

    private void removePackage(String packageName, UserHandle user, long userSerial) {
        synchronized(mPackageVersions) {
            mPackageVersions.remove(packageName);
        }

        mDb.delete(
                CacheDb.COLUMN_PACKAGE + " = ? AND " + CacheDb.COLUMN_USER + " = ?",
                new String[]{packageName, Long.toString(userSerial)});
    }

    /**
     * Updates the persistent DB:
     *   1. Any preview generated for an old package version is removed
     *   2. Any preview for an absent package is removed
     * This ensures that we remove entries for packages which changed while the launcher was dead.
     *
     * @param packageUser if provided, specifies that list only contains previews for the
     *                    given package/user, otherwise the list contains all previews
     */
    public void removeObsoletePreviews(ArrayList<? extends ComponentKey> list,
            @Nullable PackageUserKey packageUser) {
        Preconditions.assertWorkerThread();

        LongSparseArray<HashSet<String>> validPackages = new LongSparseArray<>();

        for (ComponentKey key : list) {
            final long userId = mUserManager.getSerialNumberForUser(key.user);
            HashSet<String> packages = validPackages.get(userId);
            if (packages == null) {
                packages = new HashSet<>();
                validPackages.put(userId, packages);
            }
            packages.add(key.componentName.getPackageName());
        }

        LongSparseArray<HashSet<String>> packagesToDelete = new LongSparseArray<>();
        long passedUserId = packageUser == null ? 0
                : mUserManager.getSerialNumberForUser(packageUser.mUser);
        Cursor c = null;
        try {
            c = mDb.query(
                    new String[]{CacheDb.COLUMN_USER, CacheDb.COLUMN_PACKAGE,
                            CacheDb.COLUMN_LAST_UPDATED, CacheDb.COLUMN_VERSION},
                    null, null);
            while (c.moveToNext()) {
                long userId = c.getLong(0);
                String pkg = c.getString(1);
                long lastUpdated = c.getLong(2);
                long version = c.getLong(3);

                if (packageUser != null && (!pkg.equals(packageUser.mPackageName)
                        || userId != passedUserId)) {
                    // This preview is associated with a different package/user, no need to remove.
                    continue;
                }

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
                UserHandle user = mUserManager.getUserForSerialNumber(userId);
                for (String pkg : packagesToDelete.valueAt(i)) {
                    removePackage(pkg, user, userId);
                }
            }
        } catch (SQLException e) {
            Log.e(TAG, "Error updating widget previews", e);
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
            cursor = mDb.query(
                    new String[]{CacheDb.COLUMN_PREVIEW_BITMAP},
                    CacheDb.COLUMN_COMPONENT + " = ? AND " + CacheDb.COLUMN_USER + " = ? AND "
                            + CacheDb.COLUMN_SIZE + " = ?",
                    new String[]{
                            key.componentName.flattenToShortString(),
                            Long.toString(mUserManager.getSerialNumberForUser(key.user)),
                            key.size
                    });
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

    private Bitmap generatePreview(BaseActivity launcher, WidgetItem item, Bitmap recycle,
            int previewWidth, int previewHeight) {
        if (item.widgetInfo != null) {
            return generateWidgetPreview(launcher, item.widgetInfo,
                    previewWidth, recycle, null);
        } else {
            return generateShortcutPreview(launcher, item.activityInfo,
                    previewWidth, previewHeight, recycle);
        }
    }

    /**
     * Generates the widget preview from either the {@link AppWidgetManagerCompat} or cache
     * and add badge at the bottom right corner.
     *
     * @param launcher
     * @param info                        information about the widget
     * @param maxPreviewWidth             width of the preview on either workspace or tray
     * @param preview                     bitmap that can be recycled
     * @param preScaledWidthOut           return the width of the returned bitmap
     * @return
     */
    public Bitmap generateWidgetPreview(BaseActivity launcher, LauncherAppWidgetProviderInfo info,
            int maxPreviewWidth, Bitmap preview, int[] preScaledWidthOut) {
        // Load the preview image if possible
        if (maxPreviewWidth < 0) maxPreviewWidth = Integer.MAX_VALUE;

        Drawable drawable = null;
        if (info.previewImage != 0) {
            try {
                drawable = info.loadPreviewImage(mContext, 0);
            } catch (OutOfMemoryError e) {
                Log.w(TAG, "Error loading widget preview for: " + info.provider, e);
                // During OutOfMemoryError, the previous heap stack is not affected. Catching
                // an OOM error here should be safe & not affect other parts of launcher.
                drawable = null;
            }
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

        if (widgetPreviewExists && drawable.getIntrinsicWidth() > 0
                && drawable.getIntrinsicHeight() > 0) {
            previewWidth = drawable.getIntrinsicWidth();
            previewHeight = drawable.getIntrinsicHeight();
        } else {
            DeviceProfile dp = launcher.getDeviceProfile();
            int tileSize = Math.min(dp.cellWidthPx, dp.cellHeightPx);
            previewWidth = tileSize * spanX;
            previewHeight = tileSize * spanY;
        }

        // Scale to fit width only - let the widget preview be clipped in the
        // vertical dimension
        float scale = 1f;
        if (preScaledWidthOut != null) {
            preScaledWidthOut[0] = previewWidth;
        }
        if (previewWidth > maxPreviewWidth) {
            scale = maxPreviewWidth / (float) (previewWidth);
        }
        if (scale != 1f) {
            previewWidth = Math.max((int)(scale * previewWidth), 1);
            previewHeight = Math.max((int)(scale * previewHeight), 1);
        }

        // If a bitmap is passed in, we use it; otherwise, we create a bitmap of the right size
        final Canvas c = new Canvas();
        if (preview == null) {
            preview = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
            c.setBitmap(preview);
        } else {
            // We use the preview bitmap height to determine where the badge will be drawn in the
            // UI. If its larger than what we need, resize the preview bitmap so that there are
            // no transparent pixels between the preview and the badge.
            if (preview.getHeight() > previewHeight) {
                preview.reconfigure(preview.getWidth(), previewHeight, preview.getConfig());
            }
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
            RectF boxRect = drawBoxWithShadow(c, previewWidth, previewHeight);

            // Draw horizontal and vertical lines to represent individual columns.
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(mContext.getResources()
                    .getDimension(R.dimen.widget_preview_cell_divider_width));
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

            float t = boxRect.left;
            float tileSize = boxRect.width() / spanX;
            for (int i = 1; i < spanX; i++) {
                t += tileSize;
                c.drawLine(t, 0, t, previewHeight, p);
            }

            t = boxRect.top;
            tileSize = boxRect.height() / spanY;
            for (int i = 1; i < spanY; i++) {
                t += tileSize;
                c.drawLine(0, t, previewWidth, t, p);
            }

            // Draw icon in the center.
            try {
                Drawable icon =
                        mIconCache.getFullResIcon(info.provider.getPackageName(), info.icon);
                if (icon != null) {
                    int appIconSize = launcher.getDeviceProfile().iconSizePx;
                    int iconSize = (int) Math.min(appIconSize * scale,
                            Math.min(boxRect.width(), boxRect.height()));

                    icon = mutateOnMainThread(icon);
                    int hoffset = (previewWidth - iconSize) / 2;
                    int yoffset = (previewHeight - iconSize) / 2;
                    icon.setBounds(hoffset, yoffset, hoffset + iconSize, yoffset + iconSize);
                    icon.draw(c);
                }
            } catch (Resources.NotFoundException e) { }
            c.setBitmap(null);
        }
        return preview;
    }

    private RectF drawBoxWithShadow(Canvas c, int width, int height) {
        Resources res = mContext.getResources();

        ShadowGenerator.Builder builder = new ShadowGenerator.Builder(Color.WHITE);
        builder.shadowBlur = res.getDimension(R.dimen.widget_preview_shadow_blur);
        builder.radius = res.getDimension(R.dimen.widget_preview_corner_radius);
        builder.keyShadowDistance = res.getDimension(R.dimen.widget_preview_key_shadow_distance);

        builder.bounds.set(builder.shadowBlur, builder.shadowBlur,
                width - builder.shadowBlur,
                height - builder.shadowBlur - builder.keyShadowDistance);
        builder.drawShadow(c);
        return builder.bounds;
    }

    private Bitmap generateShortcutPreview(BaseActivity launcher, ShortcutConfigActivityInfo info,
            int maxWidth, int maxHeight, Bitmap preview) {
        int iconSize = launcher.getDeviceProfile().allAppsIconSizePx;
        int padding = launcher.getResources()
                .getDimensionPixelSize(R.dimen.widget_preview_shortcut_padding);

        int size = iconSize + 2 * padding;
        if (maxHeight < size || maxWidth < size) {
            throw new RuntimeException("Max size is too small for preview");
        }
        final Canvas c = new Canvas();
        if (preview == null || preview.getWidth() < size || preview.getHeight() < size) {
            preview = Bitmap.createBitmap(size, size, Config.ARGB_8888);
            c.setBitmap(preview);
        } else {
            if (preview.getWidth() > size || preview.getHeight() > size) {
                preview.reconfigure(size, size, preview.getConfig());
            }

            // Reusing bitmap. Clear it.
            c.setBitmap(preview);
            c.drawColor(0, PorterDuff.Mode.CLEAR);
        }
        RectF boxRect = drawBoxWithShadow(c, size, size);

        LauncherIcons li = LauncherIcons.obtain(mContext);
        Bitmap icon = li.createBadgedIconBitmap(
                mutateOnMainThread(info.getFullResIcon(mIconCache)),
                Process.myUserHandle(), 0).icon;
        li.recycle();

        Rect src = new Rect(0, 0, icon.getWidth(), icon.getHeight());

        boxRect.set(0, 0, iconSize, iconSize);
        boxRect.offset(padding, padding);
        c.drawBitmap(icon, src, boxRect,
                new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        c.setBitmap(null);
        return preview;
    }

    private Drawable mutateOnMainThread(final Drawable drawable) {
        try {
            return MAIN_EXECUTOR.submit(drawable::mutate).get();
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
                    PackageInfo info = mContext.getPackageManager().getPackageInfo(packageName,
                            PackageManager.GET_UNINSTALLED_PACKAGES);
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

    public class PreviewLoadTask extends AsyncTask<Void, Void, Bitmap>
            implements CancellationSignal.OnCancelListener {
        @Thunk final WidgetCacheKey mKey;
        private final WidgetItem mInfo;
        private final int mPreviewHeight;
        private final int mPreviewWidth;
        private final WidgetCell mCaller;
        private final BaseActivity mActivity;
        @Thunk long[] mVersions;
        @Thunk Bitmap mBitmapToRecycle;

        PreviewLoadTask(WidgetCacheKey key, WidgetItem info, int previewWidth,
                int previewHeight, WidgetCell caller) {
            mKey = key;
            mInfo = info;
            mPreviewHeight = previewHeight;
            mPreviewWidth = previewWidth;
            mCaller = caller;
            mActivity = BaseActivity.fromContext(mCaller.getContext());
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
                boolean persistable = mInfo.activityInfo == null
                        || mInfo.activityInfo.isPersistable();
                mVersions = persistable ? getPackageVersion(mKey.componentName.getPackageName())
                        : null;

                // it's not in the db... we need to generate it
                preview = generatePreview(mActivity, mInfo, unusedBitmap, mPreviewWidth, mPreviewHeight);
            }
            return preview;
        }

        @Override
        protected void onPostExecute(final Bitmap preview) {
            mCaller.applyPreview(preview);

            // Write the generated preview to the DB in the worker thread
            if (mVersions != null) {
                MODEL_EXECUTOR.post(new Runnable() {
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
                MODEL_EXECUTOR.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (mUnusedBitmaps) {
                            mUnusedBitmaps.add(preview);
                        }
                    }
                });
            }
        }

        @Override
        public void onCancel() {
            cancel(true);

            // This only handles the case where the PreviewLoadTask is cancelled after the task has
            // successfully completed (including having written to disk when necessary).  In the
            // other cases where it is cancelled while the task is running, it will be cleaned up
            // in the tasks's onCancelled() call, and if cancelled while the task is writing to
            // disk, it will be cancelled in the task's onPostExecute() call.
            if (mBitmapToRecycle != null) {
                MODEL_EXECUTOR.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (mUnusedBitmaps) {
                            mUnusedBitmaps.add(mBitmapToRecycle);
                        }
                        mBitmapToRecycle = null;
                    }
                });
            }
        }
    }

    private static final class WidgetCacheKey extends ComponentKey {

        @Thunk final String size;

        public WidgetCacheKey(ComponentName componentName, UserHandle user, String size) {
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
