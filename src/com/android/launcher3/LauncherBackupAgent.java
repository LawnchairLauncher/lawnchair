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

import com.android.launcher3.LauncherSettings.ChangeLogColumns;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.LauncherSettings.WorkspaceScreens;
import com.android.launcher3.backup.BackupProtos;
import com.android.launcher3.backup.BackupProtos.CheckedMessage;
import com.android.launcher3.backup.BackupProtos.Favorite;
import com.android.launcher3.backup.BackupProtos.Journal;
import com.android.launcher3.backup.BackupProtos.Key;
import com.android.launcher3.backup.BackupProtos.Screen;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.BackupManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * Persist the launcher home state across calamities.
 */
public class LauncherBackupAgent extends BackupAgent {

    private static final String TAG = "LauncherBackupAgent";
    private static final boolean DEBUG = false;

    private static final int MAX_JOURNAL_SIZE = 1000000;

    private static BackupManager sBackupManager;

    private static final String[] FAVORITE_PROJECTION = {
            Favorites._ID,                     // 0
            Favorites.APPWIDGET_ID,            // 1
            Favorites.APPWIDGET_PROVIDER,      // 2
            Favorites.CELLX,                   // 3
            Favorites.CELLY,                   // 4
            Favorites.CONTAINER,               // 5
            Favorites.ICON,                    // 6
            Favorites.ICON_PACKAGE,            // 7
            Favorites.ICON_RESOURCE,           // 8
            Favorites.ICON_TYPE,               // 9
            Favorites.ITEM_TYPE,               // 10
            Favorites.INTENT,                  // 11
            Favorites.SCREEN,                  // 12
            Favorites.SPANX,                   // 13
            Favorites.SPANY,                   // 14
            Favorites.TITLE,                   // 15
    };

    private static final int ID_INDEX = 0;
    private static final int APPWIDGET_ID_INDEX = 1;
    private static final int APPWIDGET_PROVIDER_INDEX = 2;
    private static final int CELLX_INDEX = 3;
    private static final int CELLY_INDEX = 4;
    private static final int CONTAINER_INDEX = 5;
    private static final int ICON_INDEX = 6;
    private static final int ICON_PACKAGE_INDEX = 7;
    private static final int ICON_RESOURCE_INDEX = 8;
    private static final int ICON_TYPE_INDEX = 9;
    private static final int ITEM_TYPE_INDEX = 10;
    private static final int INTENT_INDEX = 11;
    private static final int SCREEN_INDEX = 12;
    private static final int SPANX_INDEX = 13 ;
    private static final int SPANY_INDEX = 14;
    private static final int TITLE_INDEX = 15;

    private static final String[] SCREEN_PROJECTION = {
            WorkspaceScreens._ID,              // 0
            WorkspaceScreens.SCREEN_RANK       // 1
    };

    private static final int SCREEN_RANK_INDEX = 1;

    private static final String[] ID_ONLY_PROJECTION = {
            BaseColumns._ID
    };


    /**
     * Notify the backup manager that out database is dirty.
     *
     * <P>This does not force an immediate backup.
     *
     * @param context application context
     */
    public static void dataChanged(Context context) {
        if (sBackupManager == null) {
            sBackupManager = new BackupManager(context);
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
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
            ParcelFileDescriptor newState)
            throws IOException {
        Log.v(TAG, "onBackup");

        Journal in = readJournal(oldState);
        Journal out = new Journal();

        long lastBackupTime = in.t;
        out.t = System.currentTimeMillis();
        out.rows = 0;
        out.bytes = 0;

        Log.v(TAG, "lastBackupTime=" + lastBackupTime);

        ArrayList<Key> keys = new ArrayList<Key>();
        backupFavorites(in, data, out, keys);
        backupScreens(in, data, out, keys);

        out.key = keys.toArray(BackupProtos.Key.EMPTY_ARRAY);
        writeJournal(newState, out);
        Log.v(TAG, "onBackup: wrote " + out.bytes + "b in " + out.rows + " rows.");

        Log.v(TAG, "onBackup: finished");
    }

    /**
     * Restore home screen from the restored data stream.
     *
     * <P>Keys may arrive in any order.
     *
     * @param data the key/value pairs from the server
     * @param versionCode the version of the app that generated the data
     * @param newState notes for the next backup
     * @throws IOException
     */
    @Override
    public void onRestore(BackupDataInput data, int versionCode, ParcelFileDescriptor newState)
            throws IOException {
        Log.v(TAG, "onRestore");
        int numRows = 0;
        Journal out = new Journal();

        ArrayList<Key> keys = new ArrayList<Key>();
        byte[] buffer = new byte[512];
        while (data.readNextHeader()) {
            numRows++;
            String backupKey = data.getKey();
            int dataSize = data.getDataSize();
            if (buffer.length < dataSize) {
                buffer = new byte[dataSize];
            }
            Key key = null;
            int bytesRead = data.readEntityData(buffer, 0, dataSize);
            if (DEBUG) {
                Log.d(TAG, "read " + bytesRead + " of " + dataSize + " available");
            }
            try {
                key = backupKeyToKey(backupKey);
                switch (key.type) {
                    case Key.FAVORITE:
                        restoreFavorite(key, buffer, dataSize, keys);
                        break;

                    case Key.SCREEN:
                        restoreScreen(key, buffer, dataSize, keys);
                        break;

                    default:
                        Log.w(TAG, "unknown restore entity type: " + key.type);
                        break;
                }
            } catch (KeyParsingException e) {
                Log.w(TAG, "ignoring unparsable backup key: " + backupKey);
            }
        }

        // clear the output journal time, to force a full backup to
        // will catch any changes the restore process might have made
        out.t = 0;
        out.key = keys.toArray(BackupProtos.Key.EMPTY_ARRAY);
        writeJournal(newState, out);
        Log.v(TAG, "onRestore: read " + numRows + " rows");
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
        Set<String> savedIds = new HashSet<String>();
        for(int i = 0; i < in.key.length; i++) {
            Key key = in.key[i];
            if (key.type == Key.FAVORITE) {
                savedIds.add(keyToBackupKey(key));
            }
        }
        if (DEBUG) Log.d(TAG, "favorite savedIds.size()=" + savedIds.size());

        // persist things that have changed since the last backup
        ContentResolver cr = getContentResolver();
        String where = ChangeLogColumns.MODIFIED + " > ?";
        String[] args = {Long.toString(in.t)};
        String updateOrder = ChangeLogColumns.MODIFIED;
        Cursor updated = cr.query(Favorites.CONTENT_URI, FAVORITE_PROJECTION,
                where, args, updateOrder);
        if (DEBUG) Log.d(TAG, "favorite updated.getCount()=" + updated.getCount());
        try {
            updated.moveToPosition(-1);
            while(updated.moveToNext()) {
                final long id = updated.getLong(ID_INDEX);
                Key key = getKey(Key.FAVORITE, id);
                byte[] blob = packFavorite(updated);
                String backupKey = keyToBackupKey(key);
                data.writeEntityHeader(backupKey, blob.length);
                data.writeEntityData(blob, blob.length);
                out.rows++;
                out.bytes += blob.length;
                Log.v(TAG, "saving favorite " + backupKey + ": " + id + "/" + blob.length);
                if(DEBUG) Log.d(TAG, "wrote " +
                        Base64.encodeToString(blob, 0, blob.length, Base64.NO_WRAP));
                // remember that is was a new column, so we don't delete it.
                savedIds.add(backupKey);
            }
        } finally {
            updated.close();
        }
        if (DEBUG) Log.d(TAG, "favorite savedIds.size()=" + savedIds.size());

        // build the current ID set
        String idOrder = BaseColumns._ID;
        Cursor idCursor = cr.query(Favorites.CONTENT_URI, ID_ONLY_PROJECTION,
                null, null, idOrder);
        Set<String> currentIds = new HashSet<String>(idCursor.getCount());
        try {
            idCursor.moveToPosition(-1);
            while(idCursor.moveToNext()) {
                Key key = getKey(Key.FAVORITE, idCursor.getLong(ID_INDEX));
                currentIds.add(keyToBackupKey(key));
                // save the IDs for next time
                keys.add(key);
            }
        } finally {
            idCursor.close();
        }
        if (DEBUG) Log.d(TAG, "favorite currentIds.size()=" + currentIds.size());

        // these IDs must have been deleted
        savedIds.removeAll(currentIds);
        for (String deleted : savedIds) {
            Log.v(TAG, "dropping favorite " + deleted);
            data.writeEntityHeader(deleted, -1);
            out.rows++;
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
     * @param keys keys to mark as clean in the notes for next backup
     */
    private void restoreFavorite(Key key, byte[] buffer, int dataSize, ArrayList<Key> keys) {
        Log.v(TAG, "unpacking favorite " + key.id + " (" + dataSize + " bytes)");
        if (DEBUG) Log.d(TAG, "read (" + buffer.length + "): " +
                Base64.encodeToString(buffer, 0, dataSize, Base64.NO_WRAP));

        try {
            Favorite favorite =  unpackFavorite(buffer, 0, dataSize);
            if (DEBUG) Log.d(TAG, "unpacked " + favorite.itemType);
        } catch (InvalidProtocolBufferNanoException e) {
            Log.w(TAG, "failed to decode proto", e);
        }
    }

    /**
     * Write all modified screens to the data stream.
     *
     *
     * @param in notes from last backup
     * @param data output stream for key/value pairs
     * @param out notes about this backup
     * @param keys keys to mark as clean in the notes for next backup  @throws IOException
     */
    private void backupScreens(Journal in, BackupDataOutput data, Journal out,
            ArrayList<Key> keys)
            throws IOException {
        // read the old ID set
        Set<String> savedIds = new HashSet<String>();
        for(int i = 0; i < in.key.length; i++) {
            Key key = in.key[i];
            if (key.type == Key.SCREEN) {
                savedIds.add(keyToBackupKey(key));
            }
        }
        if (DEBUG) Log.d(TAG, "screens savedIds.size()=" + savedIds.size());

        // persist things that have changed since the last backup
        ContentResolver cr = getContentResolver();
        String where = ChangeLogColumns.MODIFIED + " > ?";
        String[] args = {Long.toString(in.t)};
        String updateOrder = ChangeLogColumns.MODIFIED;
        Cursor updated = cr.query(WorkspaceScreens.CONTENT_URI, SCREEN_PROJECTION,
                where, args, updateOrder);
        updated.moveToPosition(-1);
        if (DEBUG) Log.d(TAG, "screens updated.getCount()=" + updated.getCount());
        try {
            while(updated.moveToNext()) {
                final long id = updated.getLong(ID_INDEX);
                Key key = getKey(Key.SCREEN, id);
                byte[] blob = packScreen(updated);
                String backupKey = keyToBackupKey(key);
                data.writeEntityHeader(backupKey, blob.length);
                data.writeEntityData(blob, blob.length);
                out.rows++;
                out.bytes += blob.length;
                Log.v(TAG, "saving screen " + backupKey + ": " + id + "/" + blob.length);
                if(DEBUG) Log.d(TAG, "wrote " +
                        Base64.encodeToString(blob, 0, blob.length, Base64.NO_WRAP));
                // remember that is was a new column, so we don't delete it.
                savedIds.add(backupKey);
            }
        } finally {
            updated.close();
        }
        if (DEBUG) Log.d(TAG, "screen savedIds.size()=" + savedIds.size());

        // build the current ID set
        String idOrder = BaseColumns._ID;
        Cursor idCursor = cr.query(WorkspaceScreens.CONTENT_URI, ID_ONLY_PROJECTION,
                null, null, idOrder);
        idCursor.moveToPosition(-1);
        Set<String> currentIds = new HashSet<String>(idCursor.getCount());
        try {
            while(idCursor.moveToNext()) {
                Key key = getKey(Key.SCREEN, idCursor.getLong(ID_INDEX));
                currentIds.add(keyToBackupKey(key));
                // save the IDs for next time
                keys.add(key);
            }
        } finally {
            idCursor.close();
        }
        if (DEBUG) Log.d(TAG, "screen currentIds.size()=" + currentIds.size());

        // these IDs must have been deleted
        savedIds.removeAll(currentIds);
        for(String deleted: savedIds) {
            Log.v(TAG, "dropping screen " + deleted);
            data.writeEntityHeader(deleted, -1);
            out.rows++;
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
     * @param keys keys to mark as clean in the notes for next backup
     */
    private void restoreScreen(Key key, byte[] buffer, int dataSize, ArrayList<Key> keys) {
        Log.v(TAG, "unpacking screen " + key.id);
        if (DEBUG) Log.d(TAG, "read (" + buffer.length + "): " +
                Base64.encodeToString(buffer, 0, dataSize, Base64.NO_WRAP));
        try {
            Screen screen = unpackScreen(buffer, 0, dataSize);
            if (DEBUG) Log.d(TAG, "unpacked " + screen.rank);
        } catch (InvalidProtocolBufferNanoException e) {
            Log.w(TAG, "failed to decode proto", e);
        }
    }

    /** create a new key object.
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

    /** keys need to be strings, serialize and encode. */
    private String keyToBackupKey(Key key) {
        return Base64.encodeToString(Key.toByteArray(key), Base64.NO_WRAP | Base64.NO_PADDING);
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
        String intent = c.getString(INTENT_INDEX);
        if (!TextUtils.isEmpty(intent)) {
            favorite.intent = intent;
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
    private Favorite unpackFavorite(byte[] buffer, int offset, int dataSize)
            throws InvalidProtocolBufferNanoException {
        Favorite favorite = new Favorite();
        MessageNano.mergeFrom(favorite, readCheckedBytes(buffer, offset, dataSize));
        return favorite;
    }

    /** Serialize a Screen for persistence, including a checksum wrapper. */
    private byte[] packScreen(Cursor c) {
        Screen screen = new Screen();
        screen.id = c.getLong(ID_INDEX);
        screen.rank = c.getInt(SCREEN_RANK_INDEX);

        return writeCheckedBytes(screen);
    }

    /** Deserialize a Screen from persistence, after verifying checksum wrapper. */
    private Screen unpackScreen(byte[] buffer, int offset, int dataSize)
            throws InvalidProtocolBufferNanoException {
        Screen screen = new Screen();
        MessageNano.mergeFrom(screen, readCheckedBytes(buffer, offset, dataSize));
        return screen;
    }

    /**
     * Read the old journal from the input file.
     *
     * In the event of any error, just pretend we didn't have a journal,
     * in that case, do a full backup.
     *
     * @param oldState the read-0only file descriptor pointing to the old journal
     * @return a Journal protocol bugffer
     */
    private Journal readJournal(ParcelFileDescriptor oldState) {
        int fileSize = (int) oldState.getStatSize();
        int remaining = fileSize;
        byte[] buffer = null;
        Journal journal = new Journal();
        if (remaining < MAX_JOURNAL_SIZE) {
            FileInputStream inStream = new FileInputStream(oldState.getFileDescriptor());
            int offset = 0;

            buffer = new byte[remaining];
            while (remaining > 0) {
                int bytesRead = 0;
                try {
                    bytesRead = inStream.read(buffer, offset, remaining);
                } catch (IOException e) {
                    Log.w(TAG, "failed to read the journal", e);
                    buffer = null;
                    remaining = 0;
                }
                if (bytesRead > 0) {
                    remaining -= bytesRead;
                } else {
                    // act like there is not journal
                    Log.w(TAG, "failed to read the journal");
                    buffer = null;
                    remaining = 0;
                }
            }

            if (buffer != null) {
                try {
                    MessageNano.mergeFrom(journal, readCheckedBytes(buffer, 0, fileSize));
                } catch (InvalidProtocolBufferNanoException e) {
                    Log.d(TAG, "failed to read the journal", e);
                    journal.clear();
                }
            }

            try {
                inStream.close();
            } catch (IOException e) {
                Log.d(TAG, "failed to close the journal", e);
            }
        }
        return journal;
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
            outStream.write(writeCheckedBytes(journal));
            outStream.close();
        } catch (IOException e) {
            Log.d(TAG, "failed to write backup journal", e);
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
    private byte[] readCheckedBytes(byte[] buffer, int offset, int dataSize)
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

    private class KeyParsingException extends Throwable {
        private KeyParsingException(Throwable cause) {
            super(cause);
        }

        public KeyParsingException(String reason) {
            super(reason);
        }
    }
}
