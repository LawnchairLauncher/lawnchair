/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

/*
 * Copyright (C) 2018 Potato Open Sauce Project
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

package ch.deletescape.lawnchair.bugreport;

import android.util.JsonReader;
import android.os.Handler;
import android.os.HandlerThread;


import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/**
 * Helper functions for uploading to del.dog
 */
public final class DogbinUtils {
    private static final String TAG = "DogbinUtils";
    private static final String BASE_URL = "https://del.dog";
    private static final String API_URL = String.format("%s/documents", BASE_URL);
    private static Handler handler;

    private DogbinUtils() {
    }

    /**
     * Uploads {@code content} to dogbin
     *
     * @param content the content to upload to dogbin
     * @param callback the callback to call on success / failure
     */
    public static void upload(final String content, final UploadResultCallback callback) {
        getHandler().post(() -> {
            try {
                HttpsURLConnection urlConnection = (HttpsURLConnection) new URL(API_URL).openConnection();
                try {
                    urlConnection.setRequestProperty("Accept-Charset", "UTF-8");
                    urlConnection.setDoOutput(true);

                    try (OutputStream output = urlConnection.getOutputStream()) {
                        output.write(content.getBytes("UTF-8"));
                    }
                    String key = "";
                    try (JsonReader reader = new JsonReader(
                            new InputStreamReader(urlConnection.getInputStream(), "UTF-8"))) {
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String name = reader.nextName();
                            if (name.equals("key")) {
                                key = reader.nextString();
                                break;
                            } else {
                                reader.skipValue();
                            }
                        }
                        reader.endObject();
                    }
                    if (!key.isEmpty()) {
                        callback.onSuccess(getUrl(key));
                    } else {
                        String msg = "Failed to upload to dogbin: No key retrieved";
                        callback.onFail(msg, new DogbinException(msg));
                    }
                } finally {
                    urlConnection.disconnect();
                }
            } catch (Exception e) {
                callback.onFail("Failed to upload to dogbin", e);
            }
        });
    }

    /**
     * Get the view URL from a key
     */
    private static String getUrl(String key) {
        return String.format("%s/%s", BASE_URL, key);
    }

    private static Handler getHandler() {
        if (handler == null) {
            HandlerThread handlerThread = new HandlerThread("dogbinThread");
            if (!handlerThread.isAlive())
                handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        }
        return handler;
    }

    public interface UploadResultCallback {
        void onSuccess(String url);

        void onFail(String message, Exception e);
    }
}
