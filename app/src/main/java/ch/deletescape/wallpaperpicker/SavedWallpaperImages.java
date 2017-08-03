/*
 * Copyright (C) 2013 The Android Open Source Project
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

package ch.deletescape.wallpaperpicker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ch.deletescape.wallpaperpicker.tileinfo.FileWallpaperInfo;

public class SavedWallpaperImages {

    private static String TAG = "SavedWallpaperImages";

    public static class SavedWallpaperInfo extends FileWallpaperInfo {

        private int mDbId;

        public SavedWallpaperInfo(int dbId, File target, Drawable thumb) {
            super(target, thumb);
            mDbId = dbId;
        }

        @Override
        public void onDelete(WallpaperPickerActivity a) {
            a.getSavedImages().deleteImage(mDbId);
        }
    }

    private final ImageDb mDb;
    private final Context mContext;

    public SavedWallpaperImages(Context context) {
        // We used to store the saved images in the cache directory, but that meant they'd get
        // deleted sometimes-- move them to the data directory
        ImageDb.moveFromCacheDirectoryIfNecessary(context);
        mDb = new ImageDb(context);
        mContext = context;
    }

    public List<SavedWallpaperInfo> loadThumbnailsAndImageIdList() {
        List<SavedWallpaperInfo> result = new ArrayList<>();

        SQLiteDatabase db = mDb.getReadableDatabase();
        Cursor c = db.query(ImageDb.TABLE_NAME,
                new String[]{ImageDb.COLUMN_ID,
                        ImageDb.COLUMN_IMAGE_THUMBNAIL_FILENAME,
                        ImageDb.COLUMN_IMAGE_FILENAME}, // cols to return
                null, // select query
                null, // args to select query
                null,
                null,
                ImageDb.COLUMN_ID + " DESC",
                null);

        while (c.moveToNext()) {
            String filename = c.getString(1);
            File file = new File(mContext.getFilesDir(), filename);

            Bitmap thumb = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (thumb != null) {
                result.add(new SavedWallpaperInfo(c.getInt(0),
                        new File(mContext.getFilesDir(), c.getString(2)),
                        new BitmapDrawable(mContext.getResources(), thumb)));
            }
        }
        c.close();
        return result;
    }

    public void deleteImage(int id) {
        SQLiteDatabase db = mDb.getWritableDatabase();

        Cursor result = db.query(ImageDb.TABLE_NAME,
                new String[]{ImageDb.COLUMN_IMAGE_THUMBNAIL_FILENAME,
                        ImageDb.COLUMN_IMAGE_FILENAME}, // cols to return
                ImageDb.COLUMN_ID + " = ?", // select query
                new String[]{Integer.toString(id)}, // args to select query
                null,
                null,
                null,
                null);
        if (result.moveToFirst()) {
            new File(mContext.getFilesDir(), result.getString(0)).delete();
            new File(mContext.getFilesDir(), result.getString(1)).delete();
        }
        result.close();

        db.delete(ImageDb.TABLE_NAME,
                ImageDb.COLUMN_ID + " = ?", // SELECT query
                new String[]{
                        Integer.toString(id) // args to SELECT query
                });
    }

    public void writeImage(Bitmap thumbnail, byte[] imageBytes) {
        try {
            File imageFile = File.createTempFile("wallpaper", "", mContext.getFilesDir());
            FileOutputStream imageFileStream =
                    mContext.openFileOutput(imageFile.getName(), Context.MODE_PRIVATE);
            imageFileStream.write(imageBytes);
            imageFileStream.close();

            File thumbFile = File.createTempFile("wallpaperthumb", "", mContext.getFilesDir());
            FileOutputStream thumbFileStream =
                    mContext.openFileOutput(thumbFile.getName(), Context.MODE_PRIVATE);
            thumbnail.compress(Bitmap.CompressFormat.JPEG, 95, thumbFileStream);
            thumbFileStream.close();

            SQLiteDatabase db = mDb.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(ImageDb.COLUMN_IMAGE_THUMBNAIL_FILENAME, thumbFile.getName());
            values.put(ImageDb.COLUMN_IMAGE_FILENAME, imageFile.getName());
            db.insert(ImageDb.TABLE_NAME, null, values);
        } catch (IOException e) {
            Log.e(TAG, "Failed writing images to storage " + e);
        }
    }

    private static class ImageDb extends SQLiteOpenHelper {
        final static int DB_VERSION = 1;
        final static String TABLE_NAME = "saved_wallpaper_images";
        final static String COLUMN_ID = "id";
        final static String COLUMN_IMAGE_THUMBNAIL_FILENAME = "image_thumbnail";
        final static String COLUMN_IMAGE_FILENAME = "image";

        public ImageDb(Context context) {
            super(context, context.getDatabasePath(WallpaperFiles.WALLPAPER_IMAGES_DB).getPath(),
                    null, DB_VERSION);
        }

        public static void moveFromCacheDirectoryIfNecessary(Context context) {
            // We used to store the saved images in the cache directory, but that meant they'd get
            // deleted sometimes-- move them to the data directory
            File oldSavedImagesFile = new File(context.getCacheDir(),
                    WallpaperFiles.WALLPAPER_IMAGES_DB);
            File savedImagesFile = context.getDatabasePath(WallpaperFiles.WALLPAPER_IMAGES_DB);
            if (oldSavedImagesFile.exists()) {
                oldSavedImagesFile.renameTo(savedImagesFile);
            }
        }

        @Override
        public void onCreate(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    COLUMN_ID + " INTEGER NOT NULL, " +
                    COLUMN_IMAGE_THUMBNAIL_FILENAME + " TEXT NOT NULL, " +
                    COLUMN_IMAGE_FILENAME + " TEXT NOT NULL, " +
                    "PRIMARY KEY (" + COLUMN_ID + " ASC) " +
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
}
