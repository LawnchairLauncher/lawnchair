package ch.deletescape.lawnchair.lawnfeed.updater;

import android.content.Context;
import android.os.AsyncTask;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class UpdaterTask extends AsyncTask<Void, Void, Updater.Update> {
    private Context context;
    private String update;
    private Updater.UpdateListener listener;

    public UpdaterTask(Context context, String update, Updater.UpdateListener listener) {
        this.context = context;
        this.update = update;
        this.listener = listener;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();

        // No listener = no actions
        if (listener == null) {
            cancel(true);
            return;
        }

        // Check if device is connected to the Internet
        if (!Updater.isNetworkConnectivity(context)) {
            listener.onError(Updater.UpdateError.NETWORK_NOT_AVAILABLE);
            cancel(true);
            return;
        }

        // Check if URL is valid
        if (!Updater.isValidUrl(update)) {
            listener.onError(Updater.UpdateError.VERSION_URL_MALFORMED);
            cancel(true);
            return;
        }
    }

    @Override
    protected Updater.Update doInBackground(Void... voids) {
        // Our version.json from the storage server
        JSONObject json = null;

        try {
            // Retrieve json object from URL
            URL url = new URL(update);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            json = (JSONObject) new JSONParser().parse(new InputStreamReader(connection.getInputStream()));
        } catch (IOException | ParseException ex) {
            // Throw error if listener is not null
            if (listener != null) {
                listener.onError(Updater.UpdateError.VERSION_ERROR);
            }

            // Of course cancel the task
            cancel(true);
            return null;
        }

        // Don't continue if the json doesn't contain the last build number
        if (!json.containsKey("travis_build_number")) {
            return null;
        }

        int buildNumber = Integer.valueOf((String) json.get("travis_build_number"));

        String version = (String) json.get("app_version");
        String downloadUrl = String.format(Updater.DOWNLOAD_URL, version);

        URL download = null;

        try {
            download = new URL(downloadUrl);
        } catch (MalformedURLException ex) {
            // Throw error if listener is not null
            if (listener != null) {
                listener.onError(Updater.UpdateError.INVALID_DOWNLOAD_URL);
            }

            // Of course cancel the task
            cancel(true);
            return null;
        }

        return new Updater.Update(buildNumber, download);
    }

    @Override
    protected void onPostExecute(Updater.Update update) {
        super.onPostExecute(update);

        // Return fetched update to listener
        if (listener != null) {
            listener.onSuccess(update);
        }
    }
}
