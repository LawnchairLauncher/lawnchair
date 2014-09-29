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
package com.android.launcher3;

import com.google.protobuf.nano.InvalidProtocolBufferNanoException;
import com.google.protobuf.nano.MessageNano;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.LauncherSettings.WorkspaceScreens;
import com.android.launcher3.backup.BackupProtos;
import com.android.launcher3.backup.BackupProtos.CheckedMessage;
import com.android.launcher3.backup.BackupProtos.Favorite;
import com.android.launcher3.backup.BackupProtos.Journal;
import com.android.launcher3.backup.BackupProtos.Key;
import com.android.launcher3.backup.BackupProtos.Resource;
import com.android.launcher3.backup.BackupProtos.Screen;
import com.android.launcher3.backup.BackupProtos.Widget;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.compat.UserHandleCompat;

import android.app.backup.BackupDataInputStream;
import android.app.backup.BackupDataOutput;
import android.app.backup.BackupHelper;
import android.app.backup.BackupManager;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * Persist the launcher home state across calamities.
 */
public class LauncherBackupHelper implements BackupHelper {

    private static final String TAG = "LauncherBackupHelper";
    private static final boolean VERBOSE = LauncherBackupAgentHelper.VERBOSE;
    private static final boolean DEBUG = LauncherBackupAgentHelper.DEBUG;
    private static final boolean DEBUG_PAYLOAD = false;

    private static final int MAX_JOURNAL_SIZE = 1000000;

    /** icons are large, dribble them out */
    private static final int MAX_ICONS_PER_PASS = 10;

    /** widgets contain previews, which are very large, dribble them out */
    private static final int MAX_WIDGETS_PER_PASS = 5;

    public static final int IMAGE_COMPRESSION_QUALITY = 75;

    public static final String LAUNCHER_PREFIX = "L";

    public static final String LAUNCHER_PREFS_PREFIX = "LP";

    private static final Bitmap.CompressFormat IMAGE_FORMAT =
            android.graphics.Bitmap.CompressFormat.PNG;

    private static BackupManager sBackupManager;

    private static final String[] FAVORITE_PROJECTION = {
            Favorites._ID,                     // 0
            Favorites.MODIFIED,                // 1
            Favorites.INTENT,                  // 2
            Favorites.APPWIDGET_PROVIDER,      // 3
            Favorites.APPWIDGET_ID,            // 4
            Favorites.CELLX,                   // 5
            Favorites.CELLY,                   // 6
            Favorites.CONTAINER,               // 7
            Favorites.ICON,                    // 8
            Favorites.ICON_PACKAGE,            // 9
            Favorites.ICON_RESOURCE,           // 10
            Favorites.ICON_TYPE,               // 11
            Favorites.ITEM_TYPE,               // 12
            Favorites.SCREEN,                  // 13
            Favorites.SPANX,                   // 14
            Favorites.SPANY,                   // 15
            Favorites.TITLE,                   // 16
            Favorites.PROFILE_ID,              // 17
    };

    private static final int ID_INDEX = 0;
    private static final int ID_MODIFIED = 1;
    private static final int INTENT_INDEX = 2;
    private static final int APPWIDGET_PROVIDER_INDEX = 3;
    private static final int APPWIDGET_ID_INDEX = 4;
    private static final int CELLX_INDEX = 5;
    private static final int CELLY_INDEX = 6;
    private static final int CONTAINER_INDEX = 7;
    private static final int ICON_INDEX = 8;
    private static final int ICON_PACKAGE_INDEX = 9;
    private static final int ICON_RESOURCE_INDEX = 10;
    private static final int ICON_TYPE_INDEX = 11;
    private static final int ITEM_TYPE_INDEX = 12;
    private static final int SCREEN_INDEX = 13;
    private static final int SPANX_INDEX = 14;
    private static final int SPANY_INDEX = 15;
    private static final int TITLE_INDEX = 16;
    private static final int PROFILE_ID_INDEX = 17;

    private static final String[] SCREEN_PROJECTION = {
            WorkspaceScreens._ID,              // 0
            WorkspaceScreens.MODIFIED,         // 1
            WorkspaceScreens.SCREEN_RANK       // 2
    };

    private static final int SCREEN_RANK_INDEX = 2;

    private static IconCache mIconCache;

    private final Context mContext;

    private final boolean mRestoreEnabled;

    private HashMap<ComponentName, AppWidgetProviderInfo> mWidgetMap;

    private final ArrayList<Key> mKeys;

    public LauncherBackupHelper(Context context, boolean restoreEnabled) {
        mContext = context;
        mRestoreEnabled = restoreEnabled;
        mKeys = new ArrayList<Key>();
    }

    private void dataChanged() {
        if (sBackupManager == null) {
            sBackupManager = new BackupManager(mContext);
        }
        sBackupManager.dataChanged();
    }

    /**
     * Back up launcher data so we can restore the user's state on a new device.
     *
     * <P>The journal is a timestamp and a list of keys that were saved as of that time.
     *
     * <P>Keys may come back in any order, so each key/value is one complete row of the database.
     *
     * @param oldState notes from the last backup
     * @param data incremental key/value pairs to persist off-device
     * @param newState notes for the next backup
     * @throws IOException
     */
    @Override
    public void performBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
            ParcelFileDescriptor newState) {
        if (VERBOSE) Log.v(TAG, "onBackup");

        Journal in = readJournal(oldState);
        Journal out = new Journal();

        long lastBackupTime = in.t;
        out.t = System.currentTimeMillis();
        out.rows = 0;
        out.bytes = 0;

        Log.v(TAG, "lastBackupTime = " + lastBackupTime);

        ArrayList<Key> keys = new ArrayList<Key>();
        if (launcherIsReady()) {
            try {
                backupFavorites(in, data, out, keys);
                backupScreens(in, data, out, keys);
                backupIcons(in, data, out, keys);
                backupWidgets(in, data, out, keys);
            } catch (IOException e) {
                Log.e(TAG, "launcher backup has failed", e);
            }
            out.key = keys.toArray(new BackupProtos.Key[keys.size()]);
        } else {
            out = in;
        }

        writeJournal(newState, out);
        Log.v(TAG, "onBackup: wrote " + out.bytes + "b in " + out.rows + " rows.");
    }

    /**
     * Restore launcher configuration from the restored data stream.
     *
     * <P>Keys may arrive in any order.
     *
     * @param data the key/value pair from the server
     */
    @Override
    public void restoreEntity(BackupDataInputStream data) {
        if (VERBOSE) Log.v(TAG, "restoreEntity");
        byte[] buffer = new byte[512];
            String backupKey = data.getKey();
            int dataSize = data.size();
            if (buffer.length < dataSize) {
                buffer = new byte[dataSize];
            }
            Key key = null;
        int bytesRead = 0;
        try {
            bytesRead = data.read(buffer, 0, dataSize);
            if (DEBUG) Log.d(TAG, "read " + bytesRead + " of " + dataSize + " available");
        } catch (IOException e) {
            Log.e(TAG, "failed to read entity from restore data", e);
        }
        try {
            key = backupKeyToKey(backupKey);
            mKeys.add(key);
            switch (key.type) {
                case Key.FAVORITE:
                    restoreFavorite(key, buffer, dataSize, mKeys);
                    break;

                case Key.SCREEN:
                    restoreScreen(key, buffer, dataSize, mKeys);
                    break;

                case Key.ICON:
                    restoreIcon(key, buffer, dataSize, mKeys);
                    break;

                case Key.WIDGET:
                    restoreWidget(key, buffer, dataSize, mKeys);
                    break;

                default:
                    Log.w(TAG, "unknown restore entity type: " + key.type);
                    break;
            }
        } catch (KeyParsingException e) {
            Log.w(TAG, "ignoring unparsable backup key: " + backupKey);
        }

    }

    /**
     * Record the restore state for the next backup.
     *
     * @param newState notes about the backup state after restore.
     */
    @Override
    public void writeNewStateDescription(ParcelFileDescriptor newState) {
        // clear the output journal time, to force a full backup to
        // will catch any changes the restore process might have made
        Journal out = new Journal();
        out.t = 0;
        out.key = mKeys.toArray(new BackupProtos.Key[mKeys.size()]);
        writeJournal(newState, out);
        Log.v(TAG, "onRestore: read " + mKeys.size() + " rows");
        mKeys.clear();
    }

    /**
     * Write all modified favorites to the data stream.
     *
     *
     * @param in notes from last backup
     * @param data output stream for key/value pairs
     * @param out notes about this backup
     * @param keys keys to mark as clean in the notes for next backup
     * @throws IOException
     */
    private void backupFavorites(Journal in, BackupDataOutput data, Journal out,
            ArrayList<Key> keys)
            throws IOException {
        // read the old ID set
        Set<String> savedIds = getSavedIdsByType(Key.FAVORITE, in);
        if (DEBUG) Log.d(TAG, "favorite savedIds.size()=" + savedIds.size());

        // persist things that have changed since the last backup
        ContentResolver cr = mContext.getContentResolver();
        // Don't backup apps in other profiles for now.
        Cursor cursor = cr.query(Favorites.CONTENT_URI, FAVORITE_PROJECTION,
                getUserSelectionArg(), null, null);
        Set<String> currentIds = new HashSet<String>(cursor.getCount());
        try {
            cursor.moveToPosition(-1);
            while(cursor.moveToNext()) {
                final long id = cursor.getLong(ID_INDEX);
                final long updateTime = cursor.getLong(ID_MODIFIED);
                Key key = getKey(Key.FAVORITE, id);
                keys.add(key);
                final String backupKey = keyToBackupKey(key);
                currentIds.add(backupKey);
                if (!savedIds.contains(backupKey) || updateTime >= in.t) {
                    byte[] blob = packFavorite(cursor);
                    writeRowToBackup(key, blob, out, data);
                } else {
                    if (VERBOSE) Log.v(TAG, "favorite " + id + " was too old: " + updateTime);
                }
            }
        } finally {
            cursor.close();
        }
        if (DEBUG) Log.d(TAG, "favorite currentIds.size()=" + currentIds.size());

        // these IDs must have been deleted
        savedIds.removeAll(currentIds);
        out.rows += removeDeletedKeysFromBackup(savedIds, data);
    }

    /**
     * Read a favorite from the stream.
     *
     * <P>Keys arrive in any order, so screens and containers may not exist yet.
     *
     * @param key identifier for the row
     * @param buffer the serialized proto from the stream, may be larger than dataSize
     * @param dataSize the size of the proto from the stream
     * @param keys keys to mark as clean in the notes for next backup
     */
    private void restoreFavorite(Key key, byte[] buffer, int dataSize, ArrayList<Key> keys) {
        if (VERBOSE) Log.v(TAG, "unpacking favorite " + key.id);
        if (DEBUG) Log.d(TAG, "read (" + buffer.length + "): " +
                Base64.encodeToString(buffer, 0, dataSize, Base64.NO_WRAP));

        if (!mRestoreEnabled) {
            if (VERBOSE) Log.v(TAG, "restore not enabled: skipping database mutation");
            return;
        }

        try {
            ContentResolver cr = mContext.getContentResolver();
            ContentValues values = unpackFavorite(buffer, 0, dataSize);
            cr.insert(Favorites.CONTENT_URI_NO_NOTIFICATION, values);
        } catch (InvalidProtocolBufferNanoException e) {
            Log.e(TAG, "failed to decode favorite", e);
        }
    }

    /**
     * Write all modified screens to the data stream.
     *
     *
     * @param in notes from last backup
     * @param data output stream for key/value pairs
     * @param out notes about this backup
     * @param keys keys to mark as clean in the notes for next backup
     * @throws IOException
     */
    private void backupScreens(Journal in, BackupDataOutput data, Journal out,
            ArrayList<Key> keys)
            throws IOException {
        // read the old ID set
        Set<String> savedIds = getSavedIdsByType(Key.SCREEN, in);
        if (DEBUG) Log.d(TAG, "screen savedIds.size()=" + savedIds.size());

        // persist things that have changed since the last backup
        ContentResolver cr = mContext.getContentResolver();
        Cursor cursor = cr.query(WorkspaceScreens.CONTENT_URI, SCREEN_PROJECTION,
                null, null, null);
        Set<String> currentIds = new HashSet<String>(cursor.getCount());
        try {
            cursor.moveToPosition(-1);
            if (DEBUG) Log.d(TAG, "dumping screens after: " + in.t);
            while(cursor.moveToNext()) {
                final long id = cursor.getLong(ID_INDEX);
                final long updateTime = cursor.getLong(ID_MODIFIED);
                Key key = getKey(Key.SCREEN, id);
                keys.add(key);
                final String backupKey = keyToBackupKey(key);
                currentIds.add(backupKey);
                if (!savedIds.contains(backupKey) || updateTime >= in.t) {
                    byte[] blob = packScreen(cursor);
                    writeRowToBackup(key, blob, out, data);
                } else {
                    if (VERBOSE) Log.v(TAG, "screen " + id + " was too old: " + updateTime);
                }
            }
        } finally {
            cursor.close();
        }
        if (DEBUG) Log.d(TAG, "screen currentIds.size()=" + currentIds.size());

        // these IDs must have been deleted
        savedIds.removeAll(currentIds);
        out.rows += removeDeletedKeysFromBackup(savedIds, data);
    }

    /**
     * Read a screen from the stream.
     *
     * <P>Keys arrive in any order, so children of this screen may already exist.
     *
     * @param key identifier for the row
     * @param buffer the serialized proto from the stream, may be larger than dataSize
     * @param dataSize the size of the proto from the stream
     * @param keys keys to mark as clean in the notes for next backup
     */
    private void restoreScreen(Key key, byte[] buffer, int dataSize, ArrayList<Key> keys) {
        if (VERBOSE) Log.v(TAG, "unpacking screen " + key.id);
        if (DEBUG) Log.d(TAG, "read (" + buffer.length + "): " +
                Base64.encodeToString(buffer, 0, dataSize, Base64.NO_WRAP));

        if (!mRestoreEnabled) {
            if (VERBOSE) Log.v(TAG, "restore not enabled: skipping database mutation");
            return;
        }

        try {
            ContentResolver cr = mContext.getContentResolver();
            ContentValues values = unpackScreen(buffer, 0, dataSize);
            cr.insert(WorkspaceScreens.CONTENT_URI, values);

        } catch (InvalidProtocolBufferNanoException e) {
            Log.e(TAG, "failed to decode screen", e);
        }
    }

    /**
     * Write all the static icon resources we need to render placeholders
     * for a package that is not installed.
     *
     * @param in notes from last backup
     * @param data output stream for key/value pairs
     * @param out notes about this backup
     * @param keys keys to mark as clean in the notes for next backup
     * @throws IOException
     */
    private void backupIcons(Journal in, BackupDataOutput data, Journal out,
            ArrayList<Key> keys) throws IOException {
        // persist icons that haven't been persisted yet
        if (!initializeIconCache()) {
            dataChanged(); // try again later
            if (DEBUG) Log.d(TAG, "Launcher is not initialized, delaying icon backup");
            return;
        }
        final ContentResolver cr = mContext.getContentResolver();
        final int dpi = mContext.getResources().getDisplayMetrics().densityDpi;
        final UserHandleCompat myUserHandle = UserHandleCompat.myUserHandle();

        // read the old ID set
        Set<String> savedIds = getSavedIdsByType(Key.ICON, in);
        if (DEBUG) Log.d(TAG, "icon savedIds.size()=" + savedIds.size());

        // Don't backup apps in other profiles for now.
        int startRows = out.rows;
        if (DEBUG) Log.d(TAG, "starting here: " + startRows);

        String where = "(" + Favorites.ITEM_TYPE + "=" + Favorites.ITEM_TYPE_APPLICATION + " OR " +
                Favorites.ITEM_TYPE + "=" + Favorites.ITEM_TYPE_SHORTCUT + ") AND " +
                getUserSelectionArg();
        Cursor cursor = cr.query(Favorites.CONTENT_URI, FAVORITE_PROJECTION,
                where, null, null);
        Set<String> currentIds = new HashSet<String>(cursor.getCount());
        try {
            cursor.moveToPosition(-1);
            while(cursor.moveToNext()) {
                final long id = cursor.getLong(ID_INDEX);
                final String intentDescription = cursor.getString(INTENT_INDEX);
                try {
                    Intent intent = Intent.parseUri(intentDescription, 0);
                    ComponentName cn = intent.getComponent();
                    Key key = null;
                    String backupKey = null;
                    if (cn != null) {
                        key = getKey(Key.ICON, cn.flattenToShortString());
                        backupKey = keyToBackupKey(key);
                        currentIds.add(backupKey);
                    } else {
                        Log.w(TAG, "empty intent on application favorite: " + id);
                    }
                    if (savedIds.contains(backupKey)) {
                        if (VERBOSE) Log.v(TAG, "already saved icon " + backupKey);

                        // remember that we already backed this up previously
                        keys.add(key);
                    } else if (backupKey != null) {
                        if (DEBUG) Log.d(TAG, "I can count this high: " + out.rows);
                        if ((out.rows - startRows) < MAX_ICONS_PER_PASS) {
                            if (VERBOSE) Log.v(TAG, "saving icon " + backupKey);
                            Bitmap icon = mIconCache.getIcon(intent, myUserHandle);
                            keys.add(key);
                            if (icon != null && !mIconCache.isDefaultIcon(icon, myUserHandle)) {
                                byte[] blob = packIcon(dpi, icon);
                                writeRowToBackup(key, blob, out, data);
                            }
                        } else {
                            if (VERBOSE) Log.d(TAG, "deferring icon backup " + backupKey);
                            // too many icons for this pass, request another.
                            dataChanged();
                        }
                    }
                } catch (URISyntaxException e) {
                    Log.e(TAG, "invalid URI on application favorite: " + id);
                } catch (IOException e) {
                    Log.e(TAG, "unable to save application icon for favorite: " + id);
                }

            }
        } finally {
            cursor.close();
        }
        if (DEBUG) Log.d(TAG, "icon currentIds.size()=" + currentIds.size());

        // these IDs must have been deleted
        savedIds.removeAll(currentIds);
        out.rows += removeDeletedKeysFromBackup(savedIds, data);
    }

    /**
     * Read an icon from the stream.
     *
     * <P>Keys arrive in any order, so shortcuts that use this icon may already exist.
     *
     * @param key identifier for the row
     * @param buffer the serialized proto from the stream, may be larger than dataSize
     * @param dataSize the size of the proto from the stream
     * @param keys keys to mark as clean in the notes for next backup
     */
    private void restoreIcon(Key key, byte[] buffer, int dataSize, ArrayList<Key> keys) {
        if (VERBOSE) Log.v(TAG, "unpacking icon " + key.id);
        if (DEBUG) Log.d(TAG, "read (" + buffer.length + "): " +
                Base64.encodeToString(buffer, 0, dataSize, Base64.NO_WRAP));

        try {
            Resource res = unpackIcon(buffer, 0, dataSize);
            if (DEBUG) {
                Log.d(TAG, "unpacked " + res.dpi + " dpi icon");
            }
            if (DEBUG_PAYLOAD) {
                Log.d(TAG, "read " +
                        Base64.encodeToString(res.data, 0, res.data.length,
                                Base64.NO_WRAP));
            }
            Bitmap icon = BitmapFactory.decodeByteArray(res.data, 0, res.data.length);
            if (icon == null) {
                Log.w(TAG, "failed to unpack icon for " + key.name);
            }

            if (!mRestoreEnabled) {
                if (VERBOSE) {
                    Log.v(TAG, "restore not enabled: skipping database mutation");
                }
                return;
            } else {
                if (VERBOSE) Log.v(TAG, "saving restored icon as: " + key.name);
                IconCache.preloadIcon(mContext, ComponentName.unflattenFromString(key.name),
                        icon, res.dpi);
            }
        } catch (IOException e) {
            Log.d(TAG, "failed to save restored icon for: " + key.name, e);
        }
    }

    /**
     * Write all the static widget resources we need to render placeholders
     * for a package that is not installed.
     *
     * @param in notes from last backup
     * @param data output stream for key/value pairs
     * @param out notes about this backup
     * @param keys keys to mark as clean in the notes for next backup
     * @throws IOException
     */
    private void backupWidgets(Journal in, BackupDataOutput data, Journal out,
            ArrayList<Key> keys) throws IOException {
        // persist static widget info that hasn't been persisted yet
        final LauncherAppState appState = LauncherAppState.getInstanceNoCreate();
        if (appState == null || !initializeIconCache()) {
            Log.w(TAG, "Failed to get icon cache during restore");
            return;
        }
        final ContentResolver cr = mContext.getContentResolver();
        final WidgetPreviewLoader previewLoader = new WidgetPreviewLoader(mContext);
        final PagedViewCellLayout widgetSpacingLayout = new PagedViewCellLayout(mContext);
        final int dpi = mContext.getResources().getDisplayMetrics().densityDpi;
        final DeviceProfile profile = appState.getDynamicGrid().getDeviceProfile();
        if (DEBUG) Log.d(TAG, "cellWidthPx: " + profile.cellWidthPx);

        // read the old ID set
        Set<String> savedIds = getSavedIdsByType(Key.WIDGET, in);
        if (DEBUG) Log.d(TAG, "widgets savedIds.size()=" + savedIds.size());

        int startRows = out.rows;
        if (DEBUG) Log.d(TAG, "starting here: " + startRows);
        String where = Favorites.ITEM_TYPE + "=" + Favorites.ITEM_TYPE_APPWIDGET + " AND "
                + getUserSelectionArg();
        Cursor cursor = cr.query(Favorites.CONTENT_URI, FAVORITE_PROJECTION,
                where, null, null);
        Set<String> currentIds = new HashSet<String>(cursor.getCount());
        try {
            cursor.moveToPosition(-1);
            while(cursor.moveToNext()) {
                final long id = cursor.getLong(ID_INDEX);
                final String providerName = cursor.getString(APPWIDGET_PROVIDER_INDEX);
                final int spanX = cursor.getInt(SPANX_INDEX);
                final int spanY = cursor.getInt(SPANY_INDEX);
                final ComponentName provider = ComponentName.unflattenFromString(providerName);
                Key key = null;
                String backupKey = null;
                if (provider != null) {
                    key = getKey(Key.WIDGET, providerName);
                    backupKey = keyToBackupKey(key);
                    currentIds.add(backupKey);
                } else {
                    Log.w(TAG, "empty intent on appwidget: " + id);
                }
                if (savedIds.contains(backupKey)) {
                    if (VERBOSE) Log.v(TAG, "already saved widget " + backupKey);

                    // remember that we already backed this up previously
                    keys.add(key);
                } else if (backupKey != null) {
                    if (DEBUG) Log.d(TAG, "I can count this high: " + out.rows);
                    if ((out.rows - startRows) < MAX_WIDGETS_PER_PASS) {
                        if (VERBOSE) Log.v(TAG, "saving widget " + backupKey);
                        previewLoader.setPreviewSize(spanX * profile.cellWidthPx,
                                spanY * profile.cellHeightPx, widgetSpacingLayout);
                        byte[] blob = packWidget(dpi, previewLoader, mIconCache, provider);
                        keys.add(key);
                        writeRowToBackup(key, blob, out, data);

                    } else {
                        if (VERBOSE) Log.d(TAG, "deferring widget backup " + backupKey);
                        // too many widgets for this pass, request another.
                        dataChanged();
                    }
                }
            }
        } finally {
            cursor.close();
        }
        if (DEBUG) Log.d(TAG, "widget currentIds.size()=" + currentIds.size());

        // these IDs must have been deleted
        savedIds.removeAll(currentIds);
        out.rows += removeDeletedKeysFromBackup(savedIds, data);
    }

    /**
     * Read a widget from the stream.
     *
     * <P>Keys arrive in any order, so widgets that use this data may already exist.
     *
     * @param key identifier for the row
     * @param buffer the serialized proto from the stream, may be larger than dataSize
     * @param dataSize the size of the proto from the stream
     * @param keys keys to mark as clean in the notes for next backup
     */
    private void restoreWidget(Key key, byte[] buffer, int dataSize, ArrayList<Key> keys) {
        if (VERBOSE) Log.v(TAG, "unpacking widget " + key.id);
        if (DEBUG) Log.d(TAG, "read (" + buffer.length + "): " +
                Base64.encodeToString(buffer, 0, dataSize, Base64.NO_WRAP));
        try {
            Widget widget = unpackWidget(buffer, 0, dataSize);
            if (DEBUG) Log.d(TAG, "unpacked " + widget.provider);
            if (widget.icon.data != null)  {
                Bitmap icon = BitmapFactory
                        .decodeByteArray(widget.icon.data, 0, widget.icon.data.length);
                if (icon == null) {
                    Log.w(TAG, "failed to unpack widget icon for " + key.name);
                } else {
                    IconCache.preloadIcon(mContext, ComponentName.unflattenFromString(widget.provider),
                            icon, widget.icon.dpi);
                }
            }

            if (!mRestoreEnabled) {
                if (VERBOSE) Log.v(TAG, "restore not enabled: skipping database mutation");
                return;
            } else {
                // future site of widget table mutation
            }
        } catch (InvalidProtocolBufferNanoException e) {
            Log.e(TAG, "failed to decode widget", e);
        }
    }

    /** create a new key, with an integer ID.
     *
     * <P> Keys contain their own checksum instead of using
     * the heavy-weight CheckedMessage wrapper.
     */
    private Key getKey(int type, long id) {
        Key key = new Key();
        key.type = type;
        key.id = id;
        key.checksum = checkKey(key);
        return key;
    }

    /** create a new key for a named object.
     *
     * <P> Keys contain their own checksum instead of using
     * the heavy-weight CheckedMessage wrapper.
     */
    private Key getKey(int type, String name) {
        Key key = new Key();
        key.type = type;
        key.name = name;
        key.checksum = checkKey(key);
        return key;
    }

    /** keys need to be strings, serialize and encode. */
    private String keyToBackupKey(Key key) {
        return Base64.encodeToString(Key.toByteArray(key), Base64.NO_WRAP);
    }

    /** keys need to be strings, decode and parse. */
    private Key backupKeyToKey(String backupKey) throws KeyParsingException {
        try {
            Key key = Key.parseFrom(Base64.decode(backupKey, Base64.DEFAULT));
            if (key.checksum != checkKey(key)) {
                key = null;
                throw new KeyParsingException("invalid key read from stream" + backupKey);
            }
            return key;
        } catch (InvalidProtocolBufferNanoException e) {
            throw new KeyParsingException(e);
        } catch (IllegalArgumentException e) {
            throw new KeyParsingException(e);
        }
    }

    private String getKeyName(Key key) {
        if (TextUtils.isEmpty(key.name)) {
            return Long.toString(key.id);
        } else {
            return key.name;
        }

    }

    private String geKeyType(Key key) {
        switch (key.type) {
            case Key.FAVORITE:
                return "favorite";
            case Key.SCREEN:
                return "screen";
            case Key.ICON:
                return "icon";
            case Key.WIDGET:
                return "widget";
            default:
                return "anonymous";
        }
    }

    /** Compute the checksum over the important bits of a key. */
    private long checkKey(Key key) {
        CRC32 checksum = new CRC32();
        checksum.update(key.type);
        checksum.update((int) (key.id & 0xffff));
        checksum.update((int) ((key.id >> 32) & 0xffff));
        if (!TextUtils.isEmpty(key.name)) {
            checksum.update(key.name.getBytes());
        }
        return checksum.getValue();
    }

    /** Serialize a Favorite for persistence, including a checksum wrapper. */
    private byte[] packFavorite(Cursor c) {
        Favorite favorite = new Favorite();
        favorite.id = c.getLong(ID_INDEX);
        favorite.screen = c.getInt(SCREEN_INDEX);
        favorite.container = c.getInt(CONTAINER_INDEX);
        favorite.cellX = c.getInt(CELLX_INDEX);
        favorite.cellY = c.getInt(CELLY_INDEX);
        favorite.spanX = c.getInt(SPANX_INDEX);
        favorite.spanY = c.getInt(SPANY_INDEX);
        favorite.iconType = c.getInt(ICON_TYPE_INDEX);
        if (favorite.iconType == Favorites.ICON_TYPE_RESOURCE) {
            String iconPackage = c.getString(ICON_PACKAGE_INDEX);
            if (!TextUtils.isEmpty(iconPackage)) {
                favorite.iconPackage = iconPackage;
            }
            String iconResource = c.getString(ICON_RESOURCE_INDEX);
            if (!TextUtils.isEmpty(iconResource)) {
                favorite.iconResource = iconResource;
            }
        }
        if (favorite.iconType == Favorites.ICON_TYPE_BITMAP) {
            byte[] blob = c.getBlob(ICON_INDEX);
            if (blob != null && blob.length > 0) {
                favorite.icon = blob;
            }
        }
        String title = c.getString(TITLE_INDEX);
        if (!TextUtils.isEmpty(title)) {
            favorite.title = title;
        }
        String intentDescription = c.getString(INTENT_INDEX);
        if (!TextUtils.isEmpty(intentDescription)) {
            try {
                Intent intent = Intent.parseUri(intentDescription, 0);
                intent.removeExtra(ItemInfo.EXTRA_PROFILE);
                favorite.intent = intent.toUri(0);
            } catch (URISyntaxException e) {
                Log.e(TAG, "Invalid intent", e);
           }
        }
        favorite.itemType = c.getInt(ITEM_TYPE_INDEX);
        if (favorite.itemType == Favorites.ITEM_TYPE_APPWIDGET) {
            favorite.appWidgetId = c.getInt(APPWIDGET_ID_INDEX);
            String appWidgetProvider = c.getString(APPWIDGET_PROVIDER_INDEX);
            if (!TextUtils.isEmpty(appWidgetProvider)) {
                favorite.appWidgetProvider = appWidgetProvider;
            }
        }

        return writeCheckedBytes(favorite);
    }

    /** Deserialize a Favorite from persistence, after verifying checksum wrapper. */
    private ContentValues unpackFavorite(byte[] buffer, int offset, int dataSize)
            throws InvalidProtocolBufferNanoException {
        Favorite favorite = new Favorite();
        MessageNano.mergeFrom(favorite, readCheckedBytes(buffer, offset, dataSize));
        if (VERBOSE) Log.v(TAG, "unpacked favorite " + favorite.itemType + ", " +
                (TextUtils.isEmpty(favorite.title) ? favorite.id : favorite.title));
        ContentValues values = new ContentValues();
        values.put(Favorites._ID, favorite.id);
        values.put(Favorites.SCREEN, favorite.screen);
        values.put(Favorites.CONTAINER, favorite.container);
        values.put(Favorites.CELLX, favorite.cellX);
        values.put(Favorites.CELLY, favorite.cellY);
        values.put(Favorites.SPANX, favorite.spanX);
        values.put(Favorites.SPANY, favorite.spanY);
        values.put(Favorites.ICON_TYPE, favorite.iconType);
        if (favorite.iconType == Favorites.ICON_TYPE_RESOURCE) {
            values.put(Favorites.ICON_PACKAGE, favorite.iconPackage);
            values.put(Favorites.ICON_RESOURCE, favorite.iconResource);
        }
        if (favorite.iconType == Favorites.ICON_TYPE_BITMAP) {
            values.put(Favorites.ICON, favorite.icon);
        }
        if (!TextUtils.isEmpty(favorite.title)) {
            values.put(Favorites.TITLE, favorite.title);
        } else {
            values.put(Favorites.TITLE, "");
        }
        if (!TextUtils.isEmpty(favorite.intent)) {
            values.put(Favorites.INTENT, favorite.intent);
        }
        values.put(Favorites.ITEM_TYPE, favorite.itemType);

        UserHandleCompat myUserHandle = UserHandleCompat.myUserHandle();
        long userSerialNumber =
                UserManagerCompat.getInstance(mContext).getSerialNumberForUser(myUserHandle);
        values.put(LauncherSettings.Favorites.PROFILE_ID, userSerialNumber);

        if (favorite.itemType == Favorites.ITEM_TYPE_APPWIDGET) {
            if (!TextUtils.isEmpty(favorite.appWidgetProvider)) {
                values.put(Favorites.APPWIDGET_PROVIDER, favorite.appWidgetProvider);
            }
            values.put(Favorites.APPWIDGET_ID, favorite.appWidgetId);
            values.put(LauncherSettings.Favorites.RESTORED,
                    LauncherAppWidgetInfo.FLAG_ID_NOT_VALID |
                    LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY |
                    LauncherAppWidgetInfo.FLAG_UI_NOT_READY);
        } else {
            // Let LauncherModel know we've been here.
            values.put(LauncherSettings.Favorites.RESTORED, 1);
        }

        return values;
    }

    /** Serialize a Screen for persistence, including a checksum wrapper. */
    private byte[] packScreen(Cursor c) {
        Screen screen = new Screen();
        screen.id = c.getLong(ID_INDEX);
        screen.rank = c.getInt(SCREEN_RANK_INDEX);

        return writeCheckedBytes(screen);
    }

    /** Deserialize a Screen from persistence, after verifying checksum wrapper. */
    private ContentValues unpackScreen(byte[] buffer, int offset, int dataSize)
            throws InvalidProtocolBufferNanoException {
        Screen screen = new Screen();
        MessageNano.mergeFrom(screen, readCheckedBytes(buffer, offset, dataSize));
        if (VERBOSE) Log.v(TAG, "unpacked screen " + screen.id + "/" + screen.rank);
        ContentValues values = new ContentValues();
        values.put(WorkspaceScreens._ID, screen.id);
        values.put(WorkspaceScreens.SCREEN_RANK, screen.rank);
        return values;
    }

    /** Serialize an icon Resource for persistence, including a checksum wrapper. */
    private byte[] packIcon(int dpi, Bitmap icon) {
        Resource res = new Resource();
        res.dpi = dpi;
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        if (icon.compress(IMAGE_FORMAT, IMAGE_COMPRESSION_QUALITY, os)) {
            res.data = os.toByteArray();
        }
        return writeCheckedBytes(res);
    }

    /** Deserialize an icon resource from persistence, after verifying checksum wrapper. */
    private static Resource unpackIcon(byte[] buffer, int offset, int dataSize)
            throws InvalidProtocolBufferNanoException {
        Resource res = new Resource();
        MessageNano.mergeFrom(res, readCheckedBytes(buffer, offset, dataSize));
        if (VERBOSE) Log.v(TAG, "unpacked icon " + res.dpi + "/" + res.data.length);
        return res;
    }

    /** Serialize a widget for persistence, including a checksum wrapper. */
    private byte[] packWidget(int dpi, WidgetPreviewLoader previewLoader, IconCache iconCache,
            ComponentName provider) {
        final AppWidgetProviderInfo info = findAppWidgetProviderInfo(provider);
        Widget widget = new Widget();
        widget.provider = provider.flattenToShortString();
        widget.label = info.label;
        widget.configure = info.configure != null;
        if (info.icon != 0) {
            widget.icon = new Resource();
            Drawable fullResIcon = iconCache.getFullResIcon(provider.getPackageName(), info.icon);
            Bitmap icon = Utilities.createIconBitmap(fullResIcon, mContext);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            if (icon.compress(IMAGE_FORMAT, IMAGE_COMPRESSION_QUALITY, os)) {
                widget.icon.data = os.toByteArray();
                widget.icon.dpi = dpi;
            }
        }
        if (info.previewImage != 0) {
            widget.preview = new Resource();
            Bitmap preview = previewLoader.generateWidgetPreview(info, null);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            if (preview.compress(IMAGE_FORMAT, IMAGE_COMPRESSION_QUALITY, os)) {
                widget.preview.data = os.toByteArray();
                widget.preview.dpi = dpi;
            }
        }
        return writeCheckedBytes(widget);
    }

    /** Deserialize a widget from persistence, after verifying checksum wrapper. */
    private Widget unpackWidget(byte[] buffer, int offset, int dataSize)
            throws InvalidProtocolBufferNanoException {
        Widget widget = new Widget();
        MessageNano.mergeFrom(widget, readCheckedBytes(buffer, offset, dataSize));
        if (VERBOSE) Log.v(TAG, "unpacked widget " + widget.provider);
        return widget;
    }

    /**
     * Read the old journal from the input file.
     *
     * In the event of any error, just pretend we didn't have a journal,
     * in that case, do a full backup.
     *
     * @param oldState the read-0only file descriptor pointing to the old journal
     * @return a Journal protocol buffer
     */
    private Journal readJournal(ParcelFileDescriptor oldState) {
        Journal journal = new Journal();
        if (oldState == null) {
            return journal;
        }
        FileInputStream inStream = new FileInputStream(oldState.getFileDescriptor());
        try {
            int availableBytes = inStream.available();
            if (DEBUG) Log.d(TAG, "available " + availableBytes);
            if (availableBytes < MAX_JOURNAL_SIZE) {
                byte[] buffer = new byte[availableBytes];
                int bytesRead = 0;
                boolean valid = false;
                InvalidProtocolBufferNanoException lastProtoException = null;
                while (availableBytes > 0) {
                    try {
                        // OMG what are you doing? This is crazy inefficient!
                        // If we read a byte that is not ours, we will cause trouble: b/12491813
                        // However, we don't know how many bytes to expect (oops).
                        // So we have to step through *slowly*, watching for the end.
                        int result = inStream.read(buffer, bytesRead, 1);
                        if (result > 0) {
                            availableBytes -= result;
                            bytesRead += result;
                            if (DEBUG && (bytesRead % 100 == 0)) {
                                Log.d(TAG, "read some bytes: " + bytesRead);
                            }
                        } else {
                            Log.w(TAG, "unexpected end of file while reading journal.");
                            // stop reading and see what there is to parse
                            availableBytes = 0;
                        }
                    } catch (IOException e) {
                        buffer = null;
                        availableBytes = 0;
                    }

                    // check the buffer to see if we have a valid journal
                    try {
                        MessageNano.mergeFrom(journal, readCheckedBytes(buffer, 0, bytesRead));
                        // if we are here, then we have read a valid, checksum-verified journal
                        valid = true;
                        availableBytes = 0;
                        if (VERBOSE) Log.v(TAG, "read " + bytesRead + " bytes of journal");
                    } catch (InvalidProtocolBufferNanoException e) {
                        // if we don't have the whole journal yet, mergeFrom will throw. keep going.
                        lastProtoException = e;
                        journal.clear();
                    }
                }
                if (DEBUG) Log.d(TAG, "journal bytes read: " + bytesRead);
                if (!valid) {
                    Log.w(TAG, "could not find a valid journal", lastProtoException);
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "failed to close the journal", e);
        } finally {
            try {
                inStream.close();
            } catch (IOException e) {
                Log.w(TAG, "failed to close the journal", e);
            }
        }
        return journal;
    }

    private void writeRowToBackup(Key key, byte[] blob, Journal out,
            BackupDataOutput data) throws IOException {
        String backupKey = keyToBackupKey(key);
        data.writeEntityHeader(backupKey, blob.length);
        data.writeEntityData(blob, blob.length);
        out.rows++;
        out.bytes += blob.length;
        if (VERBOSE) Log.v(TAG, "saving " + geKeyType(key) + " " + backupKey + ": " +
                getKeyName(key) + "/" + blob.length);
        if(DEBUG_PAYLOAD) {
            String encoded = Base64.encodeToString(blob, 0, blob.length, Base64.NO_WRAP);
            final int chunkSize = 1024;
            for (int offset = 0; offset < encoded.length(); offset += chunkSize) {
                int end = offset + chunkSize;
                end = Math.min(end, encoded.length());
                Log.w(TAG, "wrote " + encoded.substring(offset, end));
            }
        }
    }

    private Set<String> getSavedIdsByType(int type, Journal in) {
        Set<String> savedIds = new HashSet<String>();
        for(int i = 0; i < in.key.length; i++) {
            Key key = in.key[i];
            if (key.type == type) {
                savedIds.add(keyToBackupKey(key));
            }
        }
        return savedIds;
    }

    private int removeDeletedKeysFromBackup(Set<String> deletedIds, BackupDataOutput data)
            throws IOException {
        int rows = 0;
        for(String deleted: deletedIds) {
            if (VERBOSE) Log.v(TAG, "dropping deleted item " + deleted);
            data.writeEntityHeader(deleted, -1);
            rows++;
        }
        return rows;
    }

    /**
     * Write the new journal to the output file.
     *
     * In the event of any error, just pretend we didn't have a journal,
     * in that case, do a full backup.

     * @param newState the write-only file descriptor pointing to the new journal
     * @param journal a Journal protocol buffer
     */
    private void writeJournal(ParcelFileDescriptor newState, Journal journal) {
        FileOutputStream outStream = null;
        try {
            outStream = new FileOutputStream(newState.getFileDescriptor());
            final byte[] journalBytes = writeCheckedBytes(journal);
            outStream.write(journalBytes);
            outStream.close();
            if (VERBOSE) Log.v(TAG, "wrote " + journalBytes.length + " bytes of journal");
        } catch (IOException e) {
            Log.w(TAG, "failed to write backup journal", e);
        }
    }

    /** Wrap a proto in a CheckedMessage and compute the checksum. */
    private byte[] writeCheckedBytes(MessageNano proto) {
        CheckedMessage wrapper = new CheckedMessage();
        wrapper.payload = MessageNano.toByteArray(proto);
        CRC32 checksum = new CRC32();
        checksum.update(wrapper.payload);
        wrapper.checksum = checksum.getValue();
        return MessageNano.toByteArray(wrapper);
    }

    /** Unwrap a proto message from a CheckedMessage, verifying the checksum. */
    private static byte[] readCheckedBytes(byte[] buffer, int offset, int dataSize)
            throws InvalidProtocolBufferNanoException {
        CheckedMessage wrapper = new CheckedMessage();
        MessageNano.mergeFrom(wrapper, buffer, offset, dataSize);
        CRC32 checksum = new CRC32();
        checksum.update(wrapper.payload);
        if (wrapper.checksum != checksum.getValue()) {
            throw new InvalidProtocolBufferNanoException("checksum does not match");
        }
        return wrapper.payload;
    }

    private AppWidgetProviderInfo findAppWidgetProviderInfo(ComponentName component) {
        if (mWidgetMap == null) {
            List<AppWidgetProviderInfo> widgets =
                    AppWidgetManager.getInstance(mContext).getInstalledProviders();
            mWidgetMap = new HashMap<ComponentName, AppWidgetProviderInfo>(widgets.size());
            for (AppWidgetProviderInfo info : widgets) {
                mWidgetMap.put(info.provider, info);
            }
        }
        return mWidgetMap.get(component);
    }


    private boolean initializeIconCache() {
        if (mIconCache != null) {
            return true;
        }

        final LauncherAppState appState = LauncherAppState.getInstanceNoCreate();
        if (appState == null) {
            Throwable stackTrace = new Throwable();
            stackTrace.fillInStackTrace();
            Log.w(TAG, "Failed to get app state during backup/restore", stackTrace);
            return false;
        }
        mIconCache = appState.getIconCache();
        return mIconCache != null;
    }


   // check if the launcher is in a state to support backup
    private boolean launcherIsReady() {
        ContentResolver cr = mContext.getContentResolver();
        Cursor cursor = cr.query(Favorites.CONTENT_URI, FAVORITE_PROJECTION, null, null, null);
        if (cursor == null) {
            // launcher data has been wiped, do nothing
            return false;
        }
        cursor.close();

        if (!initializeIconCache()) {
            // launcher services are unavailable, try again later
            dataChanged();
            return false;
        }

        return true;
    }

    private String getUserSelectionArg() {
        return Favorites.PROFILE_ID + '=' + UserManagerCompat.getInstance(mContext)
                .getSerialNumberForUser(UserHandleCompat.myUserHandle());
    }

    private class KeyParsingException extends Throwable {
        private KeyParsingException(Throwable cause) {
            super(cause);
        }

        public KeyParsingException(String reason) {
            super(reason);
        }
    }
}
