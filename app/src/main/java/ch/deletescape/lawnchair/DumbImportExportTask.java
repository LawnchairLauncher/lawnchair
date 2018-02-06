package ch.deletescape.lawnchair;

import android.Manifest;
import android.app.Activity;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class DumbImportExportTask {
    public static void exportDB(Activity activity) {
        ContextWrapper contextWrapper = new ContextWrapper(activity);
        File db = contextWrapper.getDatabasePath(LauncherFiles.LAUNCHER_DB);
        exportFile(db, activity);
    }

    public static void importDB(Activity activity) {
        ContextWrapper contextWrapper = new ContextWrapper(activity);
        File db = contextWrapper.getDatabasePath(LauncherFiles.LAUNCHER_DB);
        importFile(db, activity);
    }

    public static void exportPrefs(Activity activity) {
        String dir = new ContextWrapper(activity).getCacheDir().getParent();
        File prefs = new File(dir, "shared_prefs/" + LauncherFiles.SHARED_PREFERENCES_KEY + ".xml");
        exportFile(prefs, activity);
    }

    public static void importPrefs(Activity activity) {
        String dir = new ContextWrapper(activity).getCacheDir().getParent();
        File prefs = new File(dir, "shared_prefs/" + LauncherFiles.SHARED_PREFERENCES_KEY + ".xml");
        importFile(prefs, activity);
    }

    private static void exportFile(File file, Activity activity) {
        if (!isExternalStorageWritable() || !canWriteStorage(activity)) {
            Toast.makeText(activity, activity.getString(R.string.imexport_external_storage_unwritable), Toast.LENGTH_LONG).show();
            return;
        }

        File backup = new File(getFolder(), file.getName());
        if (backup.exists()) {
            backup.delete();
        }

        if (copy(file, backup)) {
            if (file.getName().equals(LauncherFiles.SHARED_PREFERENCES_KEY + ".xml")) {
                Toast.makeText(activity, activity.getString(R.string.settings_export_success), Toast.LENGTH_LONG).show();
            } else if (file.getName().equals(LauncherFiles.LAUNCHER_DB)) {
                Toast.makeText(activity, activity.getString(R.string.db_export_success), Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(activity, activity.getString(R.string.export_error), Toast.LENGTH_LONG).show();
        }
    }

    private static void importFile(File file, Activity activity) {
        if (!isExternalStorageReadable() || !canWriteStorage(activity)) {
            Toast.makeText(activity, activity.getString(R.string.imexport_external_storage_unreadable), Toast.LENGTH_LONG).show();
            return;
        }

        File backup = new File(getFolder(), file.getName());
        if (!backup.exists()) {
            Toast.makeText(activity, activity.getString(R.string.imexport_no_backup_found), Toast.LENGTH_LONG).show();
            return;
        }

        if (file.exists()) {
            file.delete();
        }

        if (copy(backup, file)) {
            if (file.getName().equals(LauncherFiles.SHARED_PREFERENCES_KEY + ".xml")) {
                Toast.makeText(activity, activity.getString(R.string.settings_import_success), Toast.LENGTH_LONG).show();
            } else if (file.getName().equals(LauncherFiles.LAUNCHER_DB)) {
                Toast.makeText(activity, activity.getString(R.string.db_import_success), Toast.LENGTH_LONG).show();
          }
        } else {
            Toast.makeText(activity, activity.getString(R.string.import_error), Toast.LENGTH_LONG).show();
        }
    }

    @NonNull
    private static File getFolder() {
        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Lawnchair/backup");
        if (!folder.exists()) {
            folder.mkdirs();
        }

        return folder;
    }


    private static boolean canWriteStorage(Activity activity) {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean copy(File inFile, File outFile) {
        FileInputStream in;
        FileOutputStream out;

        try {
            in = new FileInputStream(inFile);
            out = new FileOutputStream(outFile);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();

            // write the output file
            out.flush();
            out.close();
            return true;
        } catch (Exception e) {
            Log.e("copy", e.getMessage(), e);
        }

        return false;
    }

    /* Checks if external storage is available for read and write */
    private static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /* Checks if external storage is available to at least read */
    private static boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }
}
