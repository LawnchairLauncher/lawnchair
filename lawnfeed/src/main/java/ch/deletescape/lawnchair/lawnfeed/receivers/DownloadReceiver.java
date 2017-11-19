package ch.deletescape.lawnchair.lawnfeed.receivers;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.widget.Toast;

import ch.deletescape.lawnchair.lawnfeed.R;

public abstract class DownloadReceiver extends BroadcastReceiver {
    public String mFilename;
    private long downloadId;

    @Override
    public void onReceive(Context context, Intent intent) {
        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        String action = intent.getAction();

        // We only want to check if the download has completed
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);

            // Check if file has been successfully downloaded
            Cursor c = downloadManager.query(query);
            if (c.moveToFirst()) {
                int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int status = c.getInt(columnIndex);
                switch (status) {
                    // If everything is fine, call the abstract method and proceed
                    case DownloadManager.STATUS_SUCCESSFUL:
                        Uri uri = Uri.parse(c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)));
                        onDownloadDone(uri);
                        break;

                    // Otherwise tell the user that the download failed
                    default:
                        Toast.makeText(context, R.string.download_file_error, Toast.LENGTH_LONG);
                        break;
                }

                context.unregisterReceiver(this);
            }

            // Close any open resources
            c.close();
        }
    }

    public void setDownloadId(long id) {
        this.downloadId = id;
    }

    public void setFilename(String filename) {
        this.mFilename = filename;
    }

    public abstract void onDownloadDone(Uri uri);
}
