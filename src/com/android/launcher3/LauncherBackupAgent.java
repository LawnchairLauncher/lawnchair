package com.android.launcher3;

import static com.android.launcher3.LauncherPrefs.NO_DB_FILES_RESTORED;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.os.ParcelFileDescriptor;

import com.android.launcher3.logging.FileLog;
import com.android.launcher3.provider.RestoreDbTask;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

public class LauncherBackupAgent extends BackupAgent {
    private static final String TAG = "LauncherBackupAgent";
    private static final String DB_FILE_PREFIX = "launcher";
    private static final String DB_FILE_SUFFIX = ".db";

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
        RestoreDbTask.setPending(this);
        FileLog.d(TAG, "onRestoreFinished: set pending for RestoreDbTask");
        markIfFilesWereNotActuallyRestored();
    }

    /**
     * When restore is finished, we check to see if any db files were successfully restored. If not,
     * our restore will fail later, but will report a different cause. This is important to split
     * out the metric failures that are launcher's fault, and those that are due to bugs in the
     * backup/restore code itself.
     */
    private void markIfFilesWereNotActuallyRestored() {
        File directory = new File(getDatabasePath(InvariantDeviceProfile.INSTANCE.get(this).dbFile)
                .getParent());
        if (!directory.exists()) {
            FileLog.e(TAG, "restore failed as target database directory doesn't exist");
        } else {
            // Check for any db file that was restored, and collect as list
            String fileNames = Arrays.stream(directory.listFiles())
                    .map(File::getName)
                    .filter(n -> n.startsWith(DB_FILE_PREFIX) && n.endsWith(DB_FILE_SUFFIX))
                    .collect(Collectors.joining(", "));
            if (fileNames.isBlank()) {
                FileLog.e(TAG, "no database files were successfully restored");
                LauncherPrefs.get(this).putSync(NO_DB_FILES_RESTORED.to(true));
            } else {
                FileLog.d(TAG, "database files successfully restored: " + fileNames);
            }
        }
    }
}
