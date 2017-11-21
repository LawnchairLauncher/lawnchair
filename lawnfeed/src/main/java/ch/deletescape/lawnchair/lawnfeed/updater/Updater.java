package ch.deletescape.lawnchair.lawnfeed.updater;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import ch.deletescape.lawnchair.lawnfeed.receivers.UpdateReceiver;
import ch.deletescape.lawnchair.lawnfeed.R;

public class Updater {
    public static final String VERSION_URL = "https://storage.codebucket.de/lawnchair/version.json";

    public static final String DOWNLOAD_URL = "https://storage.codebucket.de/lawnchair/%1$s/Lawnfeed-%1$s.apk";

    private static final String PREFERENCES_NAME = "updater";

    public static final String PREFERENCES_LAST_CHECKED = "last_checked";

    public static final String PREFERENCES_CACHED_UPDATE = "cached_update";

    private static final long SIX_HOURS = 21600000;

    private static final String TAG = "Updater";

    public static void checkUpdate(final Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Activity.MODE_PRIVATE);

        UpdaterTask task = new UpdaterTask(context, VERSION_URL, new UpdateListener() {
            @Override
            public void onSuccess(Update update) {
                // Don't notify the user if he is running newer version than the latest
                if (getBuildNumber(context) >= update.getBuildNumber()) {
                    Log.e(TAG, update.getBuildNumber() + " is lower than " + getBuildNumber(context) + "?");
                    return;
                }

                // We need our url as String for the Intent
                String url = update.getDownloadUrl().toString();

                // Intent for download task
                Intent intentAction = new Intent(context, UpdateReceiver.class);
                intentAction.putExtra("downloadLink", url);
                intentAction.putExtra("filename", url.substring(url.lastIndexOf('/') + 1, url.length()));

                // Build notification
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context,0, intentAction, PendingIntent.FLAG_UPDATE_CURRENT);
                Notification.Builder builder = new Notification.Builder(context)
                        .setContentTitle(context.getResources().getString(R.string.update_available_title))
                        .setContentText(context.getResources().getString(R.string.update_available))
                        .setSmallIcon(R.drawable.ic_lawnchair)
                        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                        .setVibrate(new long[]{0, 100, 100, 100})
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);

                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify(0, builder.build());

                // Cache update
                if (!update.isCached()) {
                    prefs.edit()
                            .putLong(PREFERENCES_LAST_CHECKED, System.currentTimeMillis())
                            .putString(PREFERENCES_CACHED_UPDATE, update.toString())
                            .apply();
                }
            }

            @Override
            public void onError(UpdateError error) {
                Log.e(TAG, error.toString());
            }
        });

        // Don't check for updates if last update check was not longer than 6 hours ago
        long lastChecked = prefs.getLong(PREFERENCES_LAST_CHECKED, 0);
        if (lastChecked + SIX_HOURS >= System.currentTimeMillis()) {
            Log.i(TAG, "Last update check was earlier than 6 hours ago, using cached info");

            task.onPostExecute(Update.fromString(prefs.getString(PREFERENCES_CACHED_UPDATE, "")));
            return;
        }

        Log.i(TAG, "Checking for new updates");

        // Run updater task in background
        task.execute();
    }

    // Get current app build number from versionCode
    public static int getBuildNumber(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException ex) {}

        return 0;
    }

    // Check if String is a valid URL
    public static boolean isValidUrl(String url) {
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException ignored) {}

        return false;
    }

    // Check if device have a network connection
    public static boolean isNetworkConnectivity(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            if (networkInfo != null) {
                return networkInfo.isConnected();
            }
        }

        return false;
    }

    public static class Update {
        private int buildNumber;
        private URL download;

        private boolean cached;

        public Update(int buildNumber, URL download) {
            this(buildNumber, download, false);
        }

        public Update(int buildNumber, URL download, boolean cached) {
            this.buildNumber = buildNumber;
            this.download = download;
            this.cached = cached;
        }

        public Integer getBuildNumber() {
            return buildNumber;
        }

        public URL getDownloadUrl() {
            return download;
        }

        public boolean isCached() {
            return cached;
        }

        @Override
        public String toString() {
            // Create a new json object
            JSONObject obj = new JSONObject();
            obj.put("buildNumber", buildNumber);
            obj.put("download", download.toString());

            // Return parsed json to string
            return obj.toJSONString();
        }

        public static Update fromString(String json) {
            try {
                // Read json string
                JSONObject obj = (JSONObject) new JSONParser().parse(json);
                int buildNumber = ((Long) obj.get("buildNumber")).intValue();
                URL download = new URL((String) obj.get("download"));

                // Return cached update from json
                return new Update(buildNumber, download, true);
            } catch (IOException | ParseException ex) {
                Log.e(TAG, "Invalid JSON object: " + json);
            }

            // Shouldn't be returned, but it may happen
            return new Update(0, null, true);
        }
    }

    public enum UpdateError {
        // No internet connection available
        NETWORK_NOT_AVAILABLE,

        // URL for version info is not valid
        VERSION_URL_MALFORMED,

        // Version info is invalid or unreachable
        VERSION_ERROR,

        // Download URL for update is not valid
        INVALID_DOWNLOAD_URL
    }

    public interface UpdateListener {
        void onSuccess(Update update);

        void onError(UpdateError error);
    }
}
