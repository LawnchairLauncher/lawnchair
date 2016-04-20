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

import android.app.backup.BackupDataInputStream;
import android.app.backup.BackupDataOutput;
import android.app.backup.BackupHelper;
import android.app.backup.BackupManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.LauncherSettings.WorkspaceScreens;
import com.android.launcher3.backup.nano.BackupProtos;
import com.android.launcher3.backup.nano.BackupProtos.CheckedMessage;
import com.android.launcher3.backup.nano.BackupProtos.DeviceProfieData;
import com.android.launcher3.backup.nano.BackupProtos.Favorite;
import com.android.launcher3.backup.nano.BackupProtos.Journal;
import com.android.launcher3.backup.nano.BackupProtos.Key;
import com.android.launcher3.backup.nano.BackupProtos.Resource;
import com.android.launcher3.backup.nano.BackupProtos.Screen;
import com.android.launcher3.backup.nano.BackupProtos.Widget;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.model.GridSizeMigrationTask;
import com.android.launcher3.util.Thunk;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;
import com.google.protobuf.nano.MessageNano;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.zip.CRC32;

/**
 * Persist the launcher home state across calamities.
 */
public class LauncherBackupHelper implements BackupHelper {
    private static final String TAG = "LauncherBackupHelper";
    private static final boolean VERBOSE = LauncherBackupAgentHelper.VERBOSE;
    private static final boolean DEBUG = LauncherBackupAgentHelper.DEBUG;

    private static final int BACKUP_VERSION = 4;
    private static final int MAX_JOURNAL_SIZE = 1000000;

    // Journal key is such that it is always smaller than any dynamically generated
    // key (any Base64 encoded string).
    private static final String JOURNAL_KEY = "#";

    /** icons are large, dribble them out */
    private static final int MAX_ICONS_PER_PASS = 10;

    /** widgets contain previews, which are very large, dribble them out */
    private static final int MAX_WIDGETS_PER_PASS = 5;

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
        Favorites.RANK,                    // 18
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
    private static final int RANK_INDEX = 18;

    private static final String[] SCREEN_PROJECTION = {
        WorkspaceScreens._ID,              // 0
        WorkspaceScreens.MODIFIED,         // 1
        WorkspaceScreens.SCREEN_RANK       // 2
    };

    private static final int SCREEN_RANK_INDEX = 2;

    @Thunk final Context mContext;
    private final HashSet<String> mExistingKeys;
    private final ArrayList<Key> mKeys;
    private final ItemTypeMatcher[] mItemTypeMatchers;
    private final long mUserSerial;

    private BackupManager mBackupManager;
    private byte[] mBuffer = new byte[512];
    private long mLastBackupRestoreTime;
    private boolean mBackupDataWasUpdated;

    private IconCache mIconCache;
    private DeviceProfieData mDeviceProfileData;
    private InvariantDeviceProfile mIdp;

    DeviceProfieData migrationCompatibleProfileData;
    HashSet<String> widgetSizes = new HashSet<>();

    boolean restoreSuccessful;
    int restoredBackupVersion = 1;

    // When migrating from a device which different hotseat configuration, the icons are shifted
    // to center along the new all-apps icon.
    private int mHotseatShift = 0;

    public LauncherBackupHelper(Context context) {
        mContext = context;
        mExistingKeys = new HashSet<String>();
        mKeys = new ArrayList<Key>();
        restoreSuccessful = true;
        mItemTypeMatchers = new ItemTypeMatcher[CommonAppTypeParser.SUPPORTED_TYPE_COUNT];

        UserManagerCompat userManager = UserManagerCompat.getInstance(mContext);
        mUserSerial = userManager.getSerialNumberForUser(UserHandleCompat.myUserHandle());
    }

    private void dataChanged() {
        if (mBackupManager == null) {
            mBackupManager = new BackupManager(mContext);
        }
        mBackupManager.dataChanged();
    }

    private void applyJournal(Journal journal) {
        mLastBackupRestoreTime = journal.t;
        mExistingKeys.clear();
        if (journal.key != null) {
            for (Key key : journal.key) {
                mExistingKeys.add(keyToBackupKey(key));
            }
        }
        restoredBackupVersion = journal.backupVersion;
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
     */
    @Override
    public void performBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
            ParcelFileDescriptor newState) {
        if (VERBOSE) Log.v(TAG, "onBackup");

        Journal in = readJournal(oldState);
        if (!launcherIsReady()) {
            dataChanged();
            // Perform backup later.
            writeJournal(newState, in);
            return;
        }

        if (mDeviceProfileData == null) {
            LauncherAppState app = LauncherAppState.getInstance();
            mIdp = app.getInvariantDeviceProfile();
            mDeviceProfileData = initDeviceProfileData(mIdp);
            mIconCache = app.getIconCache();
        }

        Log.v(TAG, "lastBackupTime = " + in.t);
        mKeys.clear();
        applyJournal(in);

        // Record the time before performing backup so that entries edited while the backup
        // was going on, do not get missed in next backup.
        long newBackupTime = System.currentTimeMillis();
        mBackupDataWasUpdated = false;
        try {
            backupFavorites(data);
            backupScreens(data);
            backupIcons(data);
            backupWidgets(data);

            // Delete any key which still exist in the old backup, but is not valid anymore.
            HashSet<String> validKeys = new HashSet<String>();
            for (Key key : mKeys) {
                validKeys.add(keyToBackupKey(key));
            }
            mExistingKeys.removeAll(validKeys);

            // Delete anything left in the existing keys.
            for (String deleted: mExistingKeys) {
                if (VERBOSE) Log.v(TAG, "dropping deleted item " + deleted);
                data.writeEntityHeader(deleted, -1);
                mBackupDataWasUpdated = true;
            }

            mExistingKeys.clear();
            if (!mBackupDataWasUpdated) {
                // Check if any metadata has changed
                mBackupDataWasUpdated = (in.profile == null)
                        || !Arrays.equals(DeviceProfieData.toByteArray(in.profile),
                            DeviceProfieData.toByteArray(mDeviceProfileData))
                        || (in.backupVersion != BACKUP_VERSION)
                        || (in.appVersion != getAppVersion());
            }

            if (mBackupDataWasUpdated) {
                mLastBackupRestoreTime = newBackupTime;

                // We store the journal at two places.
                //   1) Storing it in newState allows us to do partial backups by comparing old state
                //   2) Storing it in backup data allows us to validate keys during restore
                Journal state = getCurrentStateJournal();
                writeRowToBackup(JOURNAL_KEY, state, data);
            } else {
                if (DEBUG) Log.d(TAG, "Nothing was written during backup");
            }
        } catch (IOException e) {
            Log.e(TAG, "launcher backup has failed", e);
        }

        writeNewStateDescription(newState);
    }

    /**
     * @return true if the backup corresponding to oldstate can be successfully applied
     * to this device.
     */
    private boolean isBackupCompatible(Journal oldState) {
        DeviceProfieData currentProfile = mDeviceProfileData;
        DeviceProfieData oldProfile = oldState.profile;

        if (oldProfile == null || oldProfile.desktopCols == 0) {
            // Profile info is not valid, ignore the check.
            return true;
        }

        boolean isHotseatCompatible = false;
        if (currentProfile.allappsRank >= oldProfile.hotseatCount) {
            isHotseatCompatible = true;
            mHotseatShift = 0;
        }

        if ((currentProfile.allappsRank >= oldProfile.allappsRank)
                && ((currentProfile.hotseatCount - currentProfile.allappsRank) >=
                        (oldProfile.hotseatCount - oldProfile.allappsRank))) {
            // There is enough space on both sides of the hotseat.
            isHotseatCompatible = true;
            mHotseatShift = currentProfile.allappsRank - oldProfile.allappsRank;
        }

        if (!isHotseatCompatible) {
            return false;
        }
        if ((currentProfile.desktopCols >= oldProfile.desktopCols)
                && (currentProfile.desktopRows >= oldProfile.desktopRows)) {
            return true;
        }

        if (GridSizeMigrationTask.ENABLED) {
            // One time migrate the workspace when launcher starts.
            migrationCompatibleProfileData = initDeviceProfileData(mIdp);
            migrationCompatibleProfileData.desktopCols = oldProfile.desktopCols;
            migrationCompatibleProfileData.desktopRows = oldProfile.desktopRows;
            migrationCompatibleProfileData.hotseatCount = oldProfile.hotseatCount;
            migrationCompatibleProfileData.allappsRank = oldProfile.allappsRank;
            return true;
        }
        return false;
    }

    /**
     * Restore launcher configuration from the restored data stream.
     * It assumes that the keys will arrive in lexical order. So if the journal was present in the
     * backup, it should arrive first.
     *
     * @param data the key/value pair from the server
     */
    @Override
    public void restoreEntity(BackupDataInputStream data) {
        if (!restoreSuccessful) {
            return;
        }

        if (mDeviceProfileData == null) {
            // This call does not happen on a looper thread. So LauncherAppState
            // can't be created . Instead initialize required dependencies directly.
            mIdp = new InvariantDeviceProfile(mContext);
            mDeviceProfileData = initDeviceProfileData(mIdp);
            mIconCache = new IconCache(mContext, mIdp);
        }

        int dataSize = data.size();
        if (mBuffer.length < dataSize) {
            mBuffer = new byte[dataSize];
        }
        try {
            int bytesRead = data.read(mBuffer, 0, dataSize);
            if (DEBUG) Log.d(TAG, "read " + bytesRead + " of " + dataSize + " available");
            String backupKey = data.getKey();

            if (JOURNAL_KEY.equals(backupKey)) {
                if (VERBOSE) Log.v(TAG, "Journal entry restored");
                if (!mKeys.isEmpty()) {
                    // We received the journal key after a restore key.
                    Log.wtf(TAG, keyToBackupKey(mKeys.get(0)) + " received after " + JOURNAL_KEY);
                    restoreSuccessful = false;
                    return;
                }

                Journal journal = new Journal();
                MessageNano.mergeFrom(journal, readCheckedBytes(mBuffer, dataSize));
                applyJournal(journal);
                restoreSuccessful = isBackupCompatible(journal);
                return;
            }

            if (!mExistingKeys.isEmpty() && !mExistingKeys.contains(backupKey)) {
                if (DEBUG) Log.e(TAG, "Ignoring key not present in the backup state " + backupKey);
                return;
            }
            Key key = backupKeyToKey(backupKey);
            mKeys.add(key);
            switch (key.type) {
                case Key.FAVORITE:
                    restoreFavorite(key, mBuffer, dataSize);
                    break;

                case Key.SCREEN:
                    restoreScreen(key, mBuffer, dataSize);
                    break;

                case Key.ICON:
                    restoreIcon(key, mBuffer, dataSize);
                    break;

                case Key.WIDGET:
                    restoreWidget(key, mBuffer, dataSize);
                    break;

                default:
                    Log.w(TAG, "unknown restore entity type: " + key.type);
                    mKeys.remove(key);
                    break;
            }
        } catch (IOException e) {
            Log.w(TAG, "ignoring unparsable backup entry", e);
        }
    }

    /**
     * Record the restore state for the next backup.
     *
     * @param newState notes about the backup state after restore.
     */
    @Override
    public void writeNewStateDescription(ParcelFileDescriptor newState) {
        writeJournal(newState, getCurrentStateJournal());
    }

    private Journal getCurrentStateJournal() {
        Journal journal = new Journal();
        journal.t = mLastBackupRestoreTime;
        journal.key = mKeys.toArray(new BackupProtos.Key[mKeys.size()]);
        journal.appVersion = getAppVersion();
        journal.backupVersion = BACKUP_VERSION;
        journal.profile = mDeviceProfileData;
        return journal;
    }

    private int getAppVersion() {
        try {
            return mContext.getPackageManager()
                    .getPackageInfo(mContext.getPackageName(), 0).versionCode;
        } catch (NameNotFoundException e) {
            return 0;
        }
    }

    private DeviceProfieData initDeviceProfileData(InvariantDeviceProfile profile) {
        DeviceProfieData data = new DeviceProfieData();
        data.desktopRows = profile.numRows;
        data.desktopCols = profile.numColumns;
        data.hotseatCount = profile.numHotseatIcons;
        data.allappsRank = profile.hotseatAllAppsRank;
        return data;
    }

    /**
     * Write all modified favorites to the data stream.
     *
     * @param data output stream for key/value pairs
     * @throws IOException
     */
    private void backupFavorites(BackupDataOutput data) throws IOException {
        // persist things that have changed since the last backup
        ContentResolver cr = mContext.getContentResolver();
        // Don't backup apps in other profiles for now.
        Cursor cursor = cr.query(Favorites.CONTENT_URI, FAVORITE_PROJECTION,
                getUserSelectionArg(), null, null);
        try {
            cursor.moveToPosition(-1);
            while(cursor.moveToNext()) {
                final long id = cursor.getLong(ID_INDEX);
                final long updateTime = cursor.getLong(ID_MODIFIED);
                Key key = getKey(Key.FAVORITE, id);
                mKeys.add(key);
                final String backupKey = keyToBackupKey(key);

                // Favorite proto changed in v4. Backup again if the version is old.
                if (!mExistingKeys.contains(backupKey) || updateTime >= mLastBackupRestoreTime
                        || restoredBackupVersion < 4) {
                    writeRowToBackup(key, packFavorite(cursor), data);
                } else {
                    if (DEBUG) Log.d(TAG, "favorite already backup up: " + id);
                }
            }
        } finally {
            cursor.close();
        }
    }

    /**
     * Read a favorite from the stream.
     *
     * <P>Keys arrive in any order, so screens and containers may not exist yet.
     *
     * @param key identifier for the row
     * @param buffer the serialized proto from the stream, may be larger than dataSize
     * @param dataSize the size of the proto from the stream
     */
    private void restoreFavorite(Key key, byte[] buffer, int dataSize) throws IOException {
        if (VERBOSE) Log.v(TAG, "unpacking favorite " + key.id);
        if (DEBUG) Log.d(TAG, "read (" + buffer.length + "): " +
                Base64.encodeToString(buffer, 0, dataSize, Base64.NO_WRAP));

        ContentResolver cr = mContext.getContentResolver();
        ContentValues values = unpackFavorite(buffer, dataSize);
        cr.insert(Favorites.CONTENT_URI, values);
    }

    /**
     * Write all modified screens to the data stream.
     *
     * @param data output stream for key/value pairs
     * @throws IOException
     */
    private void backupScreens(BackupDataOutput data) throws IOException {
        // persist things that have changed since the last backup
        ContentResolver cr = mContext.getContentResolver();
        Cursor cursor = cr.query(WorkspaceScreens.CONTENT_URI, SCREEN_PROJECTION,
                null, null, null);
        try {
            cursor.moveToPosition(-1);
            if (DEBUG) Log.d(TAG, "dumping screens after: " + mLastBackupRestoreTime);
            while(cursor.moveToNext()) {
                final long id = cursor.getLong(ID_INDEX);
                final long updateTime = cursor.getLong(ID_MODIFIED);
                Key key = getKey(Key.SCREEN, id);
                mKeys.add(key);
                final String backupKey = keyToBackupKey(key);
                if (!mExistingKeys.contains(backupKey) || updateTime >= mLastBackupRestoreTime) {
                    writeRowToBackup(key, packScreen(cursor), data);
                } else {
                    if (VERBOSE) Log.v(TAG, "screen already backup up " + id);
                }
            }
        } finally {
            cursor.close();
        }
    }

    /**
     * Read a screen from the stream.
     *
     * <P>Keys arrive in any order, so children of this screen may already exist.
     *
     * @param key identifier for the row
     * @param buffer the serialized proto from the stream, may be larger than dataSize
     * @param dataSize the size of the proto from the stream
     */
    private void restoreScreen(Key key, byte[] buffer, int dataSize) throws IOException {
        if (VERBOSE) Log.v(TAG, "unpacking screen " + key.id);
        if (DEBUG) Log.d(TAG, "read (" + buffer.length + "): " +
                Base64.encodeToString(buffer, 0, dataSize, Base64.NO_WRAP));

        ContentResolver cr = mContext.getContentResolver();
        ContentValues values = unpackScreen(buffer, dataSize);
        cr.insert(WorkspaceScreens.CONTENT_URI, values);
    }

    /**
     * Write all the static icon resources we need to render placeholders
     * for a package that is not installed.
     *
     * @param data output stream for key/value pairs
     */
    private void backupIcons(BackupDataOutput data) throws IOException {
        // persist icons that haven't been persisted yet
        final ContentResolver cr = mContext.getContentResolver();
        final int dpi = mContext.getResources().getDisplayMetrics().densityDpi;
        final UserHandleCompat myUserHandle = UserHandleCompat.myUserHandle();
        int backupUpIconCount = 0;

        // Don't backup apps in other profiles for now.
        String where = "(" + Favorites.ITEM_TYPE + "=" + Favorites.ITEM_TYPE_APPLICATION + " OR " +
                Favorites.ITEM_TYPE + "=" + Favorites.ITEM_TYPE_SHORTCUT + ") AND " +
                getUserSelectionArg();
        Cursor cursor = cr.query(Favorites.CONTENT_URI, FAVORITE_PROJECTION,
                where, null, null);
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
                    } else {
                        Log.w(TAG, "empty intent on application favorite: " + id);
                    }
                    if (mExistingKeys.contains(backupKey)) {
                        if (DEBUG) Log.d(TAG, "already saved icon " + backupKey);

                        // remember that we already backed this up previously
                        mKeys.add(key);
                    } else if (backupKey != null) {
                        if (DEBUG) Log.d(TAG, "I can count this high: " + backupUpIconCount);
                        if (backupUpIconCount < MAX_ICONS_PER_PASS) {
                            if (DEBUG) Log.d(TAG, "saving icon " + backupKey);
                            Bitmap icon = mIconCache.getIcon(intent, myUserHandle);
                            if (icon != null && !mIconCache.isDefaultIcon(icon, myUserHandle)) {
                                writeRowToBackup(key, packIcon(dpi, icon), data);
                                mKeys.add(key);
                                backupUpIconCount ++;
                            }
                        } else {
                            if (VERBOSE) Log.v(TAG, "deferring icon backup " + backupKey);
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
    }

    /**
     * Read an icon from the stream.
     *
     * <P>Keys arrive in any order, so shortcuts that use this icon may already exist.
     *
     * @param key identifier for the row
     * @param buffer the serialized proto from the stream, may be larger than dataSize
     * @param dataSize the size of the proto from the stream
     */
    private void restoreIcon(Key key, byte[] buffer, int dataSize) throws IOException {
        if (VERBOSE) Log.v(TAG, "unpacking icon " + key.id);
        if (DEBUG) Log.d(TAG, "read (" + buffer.length + "): " +
                Base64.encodeToString(buffer, 0, dataSize, Base64.NO_WRAP));

        Resource res = unpackProto(new Resource(), buffer, dataSize);
        if (DEBUG) {
            Log.d(TAG, "unpacked " + res.dpi + " dpi icon");
        }
        Bitmap icon = BitmapFactory.decodeByteArray(res.data, 0, res.data.length);
        if (icon == null) {
            Log.w(TAG, "failed to unpack icon for " + key.name);
        } else {
            if (VERBOSE) Log.v(TAG, "saving restored icon as: " + key.name);
            mIconCache.preloadIcon(ComponentName.unflattenFromString(key.name), icon, res.dpi,
                    "" /* label */, mUserSerial, mIdp);
        }
    }

    /**
     * Write all the static widget resources we need to render placeholders
     * for a package that is not installed.
     *
     * @param data output stream for key/value pairs
     * @throws IOException
     */
    private void backupWidgets(BackupDataOutput data) throws IOException {
        // persist static widget info that hasn't been persisted yet
        final ContentResolver cr = mContext.getContentResolver();
        final int dpi = mContext.getResources().getDisplayMetrics().densityDpi;
        int backupWidgetCount = 0;

        String where = Favorites.ITEM_TYPE + "=" + Favorites.ITEM_TYPE_APPWIDGET + " AND "
                + getUserSelectionArg();
        Cursor cursor = cr.query(Favorites.CONTENT_URI, FAVORITE_PROJECTION,
                where, null, null);
        AppWidgetManagerCompat widgetManager = AppWidgetManagerCompat.getInstance(mContext);
        try {
            cursor.moveToPosition(-1);
            while(cursor.moveToNext()) {
                final long id = cursor.getLong(ID_INDEX);
                final String providerName = cursor.getString(APPWIDGET_PROVIDER_INDEX);
                final ComponentName provider = ComponentName.unflattenFromString(providerName);

                Key key = null;
                String backupKey = null;
                if (provider != null) {
                    key = getKey(Key.WIDGET, providerName);
                    backupKey = keyToBackupKey(key);
                } else {
                    Log.w(TAG, "empty intent on appwidget: " + id);
                }

                // Widget backup proto changed in v3. So add it again if the original backup is old.
                if (mExistingKeys.contains(backupKey) && restoredBackupVersion >= 3) {
                    if (DEBUG) Log.d(TAG, "already saved widget " + backupKey);

                    // remember that we already backed this up previously
                    mKeys.add(key);
                } else if (backupKey != null) {
                    if (DEBUG) Log.d(TAG, "I can count this high: " + backupWidgetCount);
                    if (backupWidgetCount < MAX_WIDGETS_PER_PASS) {
                        LauncherAppWidgetProviderInfo widgetInfo = widgetManager
                                .getLauncherAppWidgetInfo(cursor.getInt(APPWIDGET_ID_INDEX));
                        if (widgetInfo != null) {
                            if (DEBUG) Log.d(TAG, "saving widget " + backupKey);
                            writeRowToBackup(key, packWidget(dpi, widgetInfo), data);
                            mKeys.add(key);
                            backupWidgetCount ++;
                        }
                    } else {
                        if (VERBOSE) Log.v(TAG, "deferring widget backup " + backupKey);
                        // too many widgets for this pass, request another.
                        dataChanged();
                    }
                }
            }
        } finally {
            cursor.close();
        }
    }

    /**
     * Read a widget from the stream.
     *
     * <P>Keys arrive in any order, so widgets that use this data may already exist.
     *
     * @param key identifier for the row
     * @param buffer the serialized proto from the stream, may be larger than dataSize
     * @param dataSize the size of the proto from the stream
     */
    private void restoreWidget(Key key, byte[] buffer, int dataSize) throws IOException {
        if (VERBOSE) Log.v(TAG, "unpacking widget " + key.id);
        if (DEBUG) Log.d(TAG, "read (" + buffer.length + "): " +
                Base64.encodeToString(buffer, 0, dataSize, Base64.NO_WRAP));
        Widget widget = unpackProto(new Widget(), buffer, dataSize);
        if (DEBUG) Log.d(TAG, "unpacked " + widget.provider);
        if (widget.icon.data != null)  {
            Bitmap icon = BitmapFactory
                    .decodeByteArray(widget.icon.data, 0, widget.icon.data.length);
            if (icon == null) {
                Log.w(TAG, "failed to unpack widget icon for " + key.name);
            } else {
                mIconCache.preloadIcon(ComponentName.unflattenFromString(widget.provider),
                        icon, widget.icon.dpi, widget.label, mUserSerial, mIdp);
            }
        }

        // Cache widget min sizes incase migration is required.
        widgetSizes.add(widget.provider + "#" + widget.minSpanX + "," + widget.minSpanY);
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
    private Key backupKeyToKey(String backupKey) throws InvalidBackupException {
        try {
            Key key = Key.parseFrom(Base64.decode(backupKey, Base64.DEFAULT));
            if (key.checksum != checkKey(key)) {
                throw new InvalidBackupException("invalid key read from stream" + backupKey);
            }
            return key;
        } catch (InvalidProtocolBufferNanoException | IllegalArgumentException e) {
            throw new InvalidBackupException(e);
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

    /**
     * @return true if its an hotseat item, that can be replaced during restore.
     * TODO: Extend check for folders in hotseat.
     */
    private boolean isReplaceableHotseatItem(Favorite favorite) {
        return favorite.container == Favorites.CONTAINER_HOTSEAT
                && favorite.intent != null
                && (favorite.itemType == Favorites.ITEM_TYPE_APPLICATION
                || favorite.itemType == Favorites.ITEM_TYPE_SHORTCUT);
    }

    /** Serialize a Favorite for persistence, including a checksum wrapper. */
    private Favorite packFavorite(Cursor c) {
        Favorite favorite = new Favorite();
        favorite.id = c.getLong(ID_INDEX);
        favorite.screen = c.getInt(SCREEN_INDEX);
        favorite.container = c.getInt(CONTAINER_INDEX);
        favorite.cellX = c.getInt(CELLX_INDEX);
        favorite.cellY = c.getInt(CELLY_INDEX);
        favorite.spanX = c.getInt(SPANX_INDEX);
        favorite.spanY = c.getInt(SPANY_INDEX);
        favorite.iconType = c.getInt(ICON_TYPE_INDEX);
        favorite.rank = c.getInt(RANK_INDEX);

        String title = c.getString(TITLE_INDEX);
        if (!TextUtils.isEmpty(title)) {
            favorite.title = title;
        }
        String intentDescription = c.getString(INTENT_INDEX);
        Intent intent = null;
        if (!TextUtils.isEmpty(intentDescription)) {
            try {
                intent = Intent.parseUri(intentDescription, 0);
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
        } else if (favorite.itemType == Favorites.ITEM_TYPE_SHORTCUT) {
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

            byte[] blob = c.getBlob(ICON_INDEX);
            if (blob != null && blob.length > 0) {
                favorite.icon = blob;
            }
        }

        if (isReplaceableHotseatItem(favorite)) {
            if (intent != null && intent.getComponent() != null) {
                PackageManager pm = mContext.getPackageManager();
                ActivityInfo activity = null;;
                try {
                    activity = pm.getActivityInfo(intent.getComponent(), 0);
                } catch (NameNotFoundException e) {
                    Log.e(TAG, "Target not found", e);
                }
                if (activity == null) {
                    return favorite;
                }
                for (int i = 0; i < mItemTypeMatchers.length; i++) {
                    if (mItemTypeMatchers[i] == null) {
                        mItemTypeMatchers[i] = new ItemTypeMatcher(
                                CommonAppTypeParser.getResourceForItemType(i));
                    }
                    if (mItemTypeMatchers[i].matches(activity, pm)) {
                        favorite.targetType = i;
                        break;
                    }
                }
            }
        }

        return favorite;
    }

    /** Deserialize a Favorite from persistence, after verifying checksum wrapper. */
    private ContentValues unpackFavorite(byte[] buffer, int dataSize)
            throws IOException {
        Favorite favorite = unpackProto(new Favorite(), buffer, dataSize);

        // If it is a hotseat item, move it accordingly.
        if (favorite.container == Favorites.CONTAINER_HOTSEAT) {
            favorite.screen += mHotseatShift;
        }

        ContentValues values = new ContentValues();
        values.put(Favorites._ID, favorite.id);
        values.put(Favorites.SCREEN, favorite.screen);
        values.put(Favorites.CONTAINER, favorite.container);
        values.put(Favorites.CELLX, favorite.cellX);
        values.put(Favorites.CELLY, favorite.cellY);
        values.put(Favorites.SPANX, favorite.spanX);
        values.put(Favorites.SPANY, favorite.spanY);
        values.put(Favorites.RANK, favorite.rank);

        if (favorite.itemType == Favorites.ITEM_TYPE_SHORTCUT) {
            values.put(Favorites.ICON_TYPE, favorite.iconType);
            if (favorite.iconType == Favorites.ICON_TYPE_RESOURCE) {
                values.put(Favorites.ICON_PACKAGE, favorite.iconPackage);
                values.put(Favorites.ICON_RESOURCE, favorite.iconResource);
            }
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

        // If we will attempt grid resize, use the original profile to validate grid size, as
        // anything which fits in the original grid should fit in the current grid after
        // grid migration.
        DeviceProfieData currentProfile = migrationCompatibleProfileData == null
                ? mDeviceProfileData : migrationCompatibleProfileData;

        if (favorite.itemType == Favorites.ITEM_TYPE_APPWIDGET) {
            if (!TextUtils.isEmpty(favorite.appWidgetProvider)) {
                values.put(Favorites.APPWIDGET_PROVIDER, favorite.appWidgetProvider);
            }
            values.put(Favorites.APPWIDGET_ID, favorite.appWidgetId);
            values.put(LauncherSettings.Favorites.RESTORED,
                    LauncherAppWidgetInfo.FLAG_ID_NOT_VALID |
                    LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY |
                    LauncherAppWidgetInfo.FLAG_UI_NOT_READY);

            // Verify placement
            if (((favorite.cellX + favorite.spanX) > currentProfile.desktopCols)
                    || ((favorite.cellY + favorite.spanY) > currentProfile.desktopRows)) {
                restoreSuccessful = false;
                throw new InvalidBackupException("Widget not in screen bounds, aborting restore");
            }
        } else {
            // Check if it is an hotseat item, that can be replaced.
            if (isReplaceableHotseatItem(favorite)
                    && favorite.targetType != Favorite.TARGET_NONE
                    && favorite.targetType < CommonAppTypeParser.SUPPORTED_TYPE_COUNT) {
                Log.e(TAG, "Added item type flag");
                values.put(LauncherSettings.Favorites.RESTORED,
                        1 | CommonAppTypeParser.encodeItemTypeToFlag(favorite.targetType));
            } else {
                // Let LauncherModel know we've been here.
                values.put(LauncherSettings.Favorites.RESTORED, 1);
            }

            // Verify placement
            if (favorite.container == Favorites.CONTAINER_HOTSEAT) {
                if ((favorite.screen >= currentProfile.hotseatCount)
                        || (favorite.screen == currentProfile.allappsRank)) {
                    restoreSuccessful = false;
                    throw new InvalidBackupException("Item not in hotseat bounds, aborting restore");
                }
            } else {
                if ((favorite.cellX >= currentProfile.desktopCols)
                        || (favorite.cellY >= currentProfile.desktopRows)) {
                    restoreSuccessful = false;
                    throw new InvalidBackupException("Item not in desktop bounds, aborting restore");
                }
            }
        }

        return values;
    }

    /** Serialize a Screen for persistence, including a checksum wrapper. */
    private Screen packScreen(Cursor c) {
        Screen screen = new Screen();
        screen.id = c.getLong(ID_INDEX);
        screen.rank = c.getInt(SCREEN_RANK_INDEX);
        return screen;
    }

    /** Deserialize a Screen from persistence, after verifying checksum wrapper. */
    private ContentValues unpackScreen(byte[] buffer, int dataSize)
            throws InvalidProtocolBufferNanoException {
        Screen screen = unpackProto(new Screen(), buffer, dataSize);
        ContentValues values = new ContentValues();
        values.put(WorkspaceScreens._ID, screen.id);
        values.put(WorkspaceScreens.SCREEN_RANK, screen.rank);
        return values;
    }

    /** Serialize an icon Resource for persistence, including a checksum wrapper. */
    private Resource packIcon(int dpi, Bitmap icon) {
        Resource res = new Resource();
        res.dpi = dpi;
        res.data = Utilities.flattenBitmap(icon);
        return res;
    }

    /** Serialize a widget for persistence, including a checksum wrapper. */
    private Widget packWidget(int dpi, LauncherAppWidgetProviderInfo info) {
        Widget widget = new Widget();
        widget.provider = info.provider.flattenToShortString();
        widget.label = info.label;
        widget.configure = info.configure != null;
        if (info.icon != 0) {
            widget.icon = new Resource();
            Drawable fullResIcon = mIconCache.getFullResIcon(info.provider.getPackageName(), info.icon);
            Bitmap icon = Utilities.createIconBitmap(fullResIcon, mContext);
            widget.icon.data = Utilities.flattenBitmap(icon);
            widget.icon.dpi = dpi;
        }

        Point spans = info.getMinSpans(mIdp, mContext);
        widget.minSpanX = spans.x;
        widget.minSpanY = spans.y;
        return widget;
    }

    /**
     * Deserialize a proto after verifying checksum wrapper.
     */
    private <T extends MessageNano> T unpackProto(T proto, byte[] buffer, int dataSize)
            throws InvalidProtocolBufferNanoException {
        MessageNano.mergeFrom(proto, readCheckedBytes(buffer, dataSize));
        if (DEBUG) Log.d(TAG, "unpacked proto " + proto);
        return proto;
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
                        MessageNano.mergeFrom(journal, readCheckedBytes(buffer, bytesRead));
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
        }
        return journal;
    }

    private void writeRowToBackup(Key key, MessageNano proto, BackupDataOutput data)
            throws IOException {
        writeRowToBackup(keyToBackupKey(key), proto, data);
    }

    private void writeRowToBackup(String backupKey, MessageNano proto,
            BackupDataOutput data) throws IOException {
        byte[] blob = writeCheckedBytes(proto);
        data.writeEntityHeader(backupKey, blob.length);
        data.writeEntityData(blob, blob.length);
        mBackupDataWasUpdated = true;
        if (VERBOSE) Log.v(TAG, "Writing New entry " + backupKey);
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
        try {
            FileOutputStream outStream = new FileOutputStream(newState.getFileDescriptor());
            final byte[] journalBytes = writeCheckedBytes(journal);
            outStream.write(journalBytes);
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
    private static byte[] readCheckedBytes(byte[] buffer, int dataSize)
            throws InvalidProtocolBufferNanoException {
        CheckedMessage wrapper = new CheckedMessage();
        MessageNano.mergeFrom(wrapper, buffer, 0, dataSize);
        CRC32 checksum = new CRC32();
        checksum.update(wrapper.payload);
        if (wrapper.checksum != checksum.getValue()) {
            throw new InvalidProtocolBufferNanoException("checksum does not match");
        }
        return wrapper.payload;
    }

    /**
     * @return true if the launcher is in a state to support backup
     */
    private boolean launcherIsReady() {
        ContentResolver cr = mContext.getContentResolver();
        Cursor cursor = cr.query(Favorites.CONTENT_URI, FAVORITE_PROJECTION, null, null, null);
        if (cursor == null) {
            // launcher data has been wiped, do nothing
            return false;
        }
        cursor.close();

        if (LauncherAppState.getInstanceNoCreate() == null) {
            // launcher services are unavailable, try again later
            return false;
        }

        return true;
    }

    private String getUserSelectionArg() {
        return Favorites.PROFILE_ID + '=' + UserManagerCompat.getInstance(mContext)
                .getSerialNumberForUser(UserHandleCompat.myUserHandle());
    }

    @Thunk class InvalidBackupException extends IOException {

        private static final long serialVersionUID = 8931456637211665082L;

        @Thunk InvalidBackupException(Throwable cause) {
            super(cause);
        }

        @Thunk InvalidBackupException(String reason) {
            super(reason);
        }
    }

    public boolean shouldAttemptWorkspaceMigration() {
        return migrationCompatibleProfileData != null;
    }

    /**
     * A class to check if an activity can handle one of the intents from a list of
     * predefined intents.
     */
    private class ItemTypeMatcher {

        private final ArrayList<Intent> mIntents;

        ItemTypeMatcher(int xml_res) {
            mIntents = xml_res == 0 ? new ArrayList<Intent>() : parseIntents(xml_res);
        }

        private ArrayList<Intent> parseIntents(int xml_res) {
            ArrayList<Intent> intents = new ArrayList<Intent>();
            XmlResourceParser parser = mContext.getResources().getXml(xml_res);
            try {
                DefaultLayoutParser.beginDocument(parser, DefaultLayoutParser.TAG_RESOLVE);
                final int depth = parser.getDepth();
                int type;
                while (((type = parser.next()) != XmlPullParser.END_TAG ||
                        parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
                    if (type != XmlPullParser.START_TAG) {
                        continue;
                    } else if (DefaultLayoutParser.TAG_FAVORITE.equals(parser.getName())) {
                        final String uri = DefaultLayoutParser.getAttributeValue(
                                parser, DefaultLayoutParser.ATTR_URI);
                        intents.add(Intent.parseUri(uri, 0));
                    }
                }
            } catch (URISyntaxException | XmlPullParserException | IOException e) {
                Log.e(TAG, "Unable to parse " + xml_res, e);
            } finally {
                parser.close();
            }
            return intents;
        }

        public boolean matches(ActivityInfo activity, PackageManager pm) {
            for (Intent intent : mIntents) {
                intent.setPackage(activity.packageName);
                ResolveInfo info = pm.resolveActivity(intent, 0);
                if (info != null && (info.activityInfo.name.equals(activity.name)
                        || info.activityInfo.name.equals(activity.targetActivity))) {
                    return true;
                }
            }
            return false;
        }
    }
}
