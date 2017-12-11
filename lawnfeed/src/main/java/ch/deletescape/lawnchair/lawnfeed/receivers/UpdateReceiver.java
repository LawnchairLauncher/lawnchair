package ch.deletescape.lawnchair.lawnfeed.receivers;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.widget.Toast;

import java.io.File;

import ch.deletescape.lawnchair.lawnfeed.PermissionActivity;
import ch.deletescape.lawnchair.lawnfeed.PermissionActivity.*;
import ch.deletescape.lawnchair.lawnfeed.R;

public class UpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, final Intent intent) {
        // Get our download link and setup receiver to install apk after download
        final String link = intent.getStringExtra("downloadLink");
        final DownloadReceiver receiver = new DownloadReceiver() {
            @Override
            public void onDownloadDone(Uri uri) {
                // Seems like Android has changed the way to open package manager on Nougat and higher
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Open package installer and install downloaded apk file
                    Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                    install.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    // Pass downloaded file uri to our install intent
                    Uri content = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", new File(uri.getPath()));
                    install.setData(content);
                    context.startActivity(install);
                } else {
                    // The old way before Nougat
                    Intent install = new Intent(Intent.ACTION_VIEW);
                    install.setDataAndType(uri, "application/vnd.android.package-archive");
                    install.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(install);
                }
            }
        };

        // Request permissions and run asynchronously
        PermissionActivity.callAsync(context, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PermissionActivity.REQUEST_CODE,
            new PermissionResultCallback() {
                @Override
                public void onComplete(PermissionResponse response) {
                    // Don't continue if permissing aren't granted
                    if (!response.isGranted()) {
                        Log.e("Updater", "No permissions granted!");
                        return;
                    }

                    String filename = intent.getStringExtra("filename");

                    // Check if our dir exists (theoretically it should, but you never know)
                    File outputDir = new File(Environment.getExternalStorageDirectory(), "Download");
                    if (!outputDir.exists()) {
                        outputDir.mkdir();
                    }

                    File file = new File(outputDir, filename);

                    // Call onDownloadDone if file already exists (not installed due to security settings, etc.)
                    if (file.exists()) {
                        receiver.onDownloadDone(Uri.parse(file.getAbsolutePath()));
                        return;
                    }

                    // Start downloading
                    Toast.makeText(context, R.string.downloading_toast, Toast.LENGTH_LONG).show();
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
        );
    }
}
