package ch.deletescape.lawnchair.lawnfeed.receivers;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.File;

import ch.deletescape.lawnchair.lawnfeed.R;

public class UpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Get our download link and setup receiver to install apk after download
        String link = intent.getStringExtra("downloadLink");
        DownloadReceiver receiver = new DownloadReceiver() {
            @Override
            public void onDownloadDone(Uri uri) {
                Log.e("UPDATER", "File DOWNLOADED");
            }
        };

        // Before doing anything, we need to check if we have write permissions!
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Find a way...
        }

        String filename = intent.getStringExtra("filename");

        // Check if our dir exists (theoretically it should, but you never know)
        File outputDir = new File(Environment.getExternalStorageDirectory(), "Download");
        if (!outputDir.exists()) {
            outputDir.mkdir();
        }

        File file = new File(outputDir, filename);

        // Start downloading
        Toast.makeText(context, context.getString(R.string.downloading_toast, filename), Toast.LENGTH_LONG).show();
        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

        if (link != null) {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(link));
            request.setDestinationUri(Uri.fromFile(file));
            receiver.setDownloadId(downloadManager.enqueue(request));
        }

        // Register our download receiver
        receiver.setFilename(filename);
        context.getApplicationContext().registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }
}
