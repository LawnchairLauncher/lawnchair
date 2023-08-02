package com.android.launcher3;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.os.ParcelFileDescriptor;

import com.android.launcher3.logging.FileLog;
import com.android.launcher3.provider.RestoreDbTask;

import java.io.File;
import java.io.IOException;

public class LauncherBackupAgent extends BackupAgent {

    private static final String TAG = "LauncherBackupAgent";

    @Override
    public void onCreate() {
        super.onCreate();
        // Set the log dir as LauncherAppState is not initialized during restore.
        FileLog.setDir(getFilesDir());
    }

    @Override
    public void onRestore(
            BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState) {
        // Doesn't do incremental backup/restore
    }

    @Override
    public void onRestoreFile(ParcelFileDescriptor data, long size, File destination, int type,
            long mode, long mtime) throws IOException {
        // Remove old files which might contain obsolete attributes like idp_grid_name in shared
        // preference that will obstruct backup's attribute from writing to shared preferences.
        if (destination.delete()) {
            FileLog.d(TAG, "onRestoreFile: Removed obsolete file " + destination);
        }
        super.onRestoreFile(data, size, destination, type, mode, mtime);
    }

    @Override
    public void onBackup(
            ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) {
        // Doesn't do incremental backup/restore
    }

    @Override
    public void onRestoreFinished() {
        FileLog.d(TAG, "onRestoreFinished: set pending for RestoreDbTask");
        RestoreDbTask.setPending(this);
    }
}
