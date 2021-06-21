/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.launcher3.util;

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.util.AtomicFile;
import android.util.Log;
import android.util.Xml;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.AutoInstallsLayout;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.pm.UserCache;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongFunction;

/**
 * Utility class to read/write a list of {@link com.android.launcher3.model.data.ItemInfo} on disk.
 * This class is not thread safe, the caller should ensure proper threading
 */
public class PersistedItemArray<T extends ItemInfo> {

    private static final String TAG = "PersistedItemArray";

    private static final String TAG_ROOT = "items";
    private static final String TAG_ENTRY = "entry";

    private final String mFileName;

    public PersistedItemArray(String fileName) {
        mFileName = fileName + ".xml";
    }

    /**
     * Writes the provided list of items on the disk
     */
    @WorkerThread
    public void write(Context context, List<T> items) {
        AtomicFile file = getFile(context);
        FileOutputStream fos;
        try {
            fos = file.startWrite();
        } catch (IOException e) {
            Log.e(TAG, "Unable to persist items in " + mFileName, e);
            return;
        }

        UserCache userCache = UserCache.INSTANCE.get(context);

        try {
            XmlSerializer out = Xml.newSerializer();
            out.setOutput(fos, StandardCharsets.UTF_8.name());
            out.startDocument(null, true);
            out.startTag(null, TAG_ROOT);
            for (T item : items) {
                Intent intent = item.getIntent();
                if (intent == null) {
                    continue;
                }

                out.startTag(null, TAG_ENTRY);
                out.attribute(null, Favorites.ITEM_TYPE, Integer.toString(item.itemType));
                out.attribute(null, Favorites.PROFILE_ID,
                        Long.toString(userCache.getSerialNumberForUser(item.user)));
                out.attribute(null, Favorites.INTENT, intent.toUri(0));
                out.endTag(null, TAG_ENTRY);
            }
            out.endTag(null, TAG_ROOT);
            out.endDocument();
        } catch (IOException e) {
            file.failWrite(fos);
            Log.e(TAG, "Unable to persist items in " + mFileName, e);
            return;
        }

        file.finishWrite(fos);
    }

    /**
     * Reads the items from the disk
     */
    @WorkerThread
    public List<T> read(Context context, ItemFactory<T> factory) {
        return read(context, factory, UserCache.INSTANCE.get(context)::getUserForSerialNumber);
    }

    /**
     * Reads the items from the disk
     * @param userFn method to provide user handle for a given user serial
     */
    @WorkerThread
    public List<T> read(Context context, ItemFactory<T> factory, LongFunction<UserHandle> userFn) {
        List<T> result = new ArrayList<>();
        try (FileInputStream fis = getFile(context).openRead()) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new InputStreamReader(fis, StandardCharsets.UTF_8));

            AutoInstallsLayout.beginDocument(parser, TAG_ROOT);
            final int depth = parser.getDepth();

            int type;
            while (((type = parser.next()) != XmlPullParser.END_TAG
                    || parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
                if (type != XmlPullParser.START_TAG || !TAG_ENTRY.equals(parser.getName())) {
                    continue;
                }
                try {
                    int itemType = Integer.parseInt(
                            parser.getAttributeValue(null, Favorites.ITEM_TYPE));
                    UserHandle user = userFn.apply(Long.parseLong(
                            parser.getAttributeValue(null, Favorites.PROFILE_ID)));
                    Intent intent = Intent.parseUri(
                            parser.getAttributeValue(null, Favorites.INTENT), 0);

                    if (user != null && intent != null) {
                        T item = factory.createInfo(itemType, user, intent);
                        if (item != null) {
                            result.add(item);
                        }
                    }
                } catch (Exception e) {
                    // Ignore this entry
                }
            }
        } catch (FileNotFoundException e) {
            // Ignore
        } catch (IOException | XmlPullParserException e) {
            Log.e(TAG, "Unable to read items in " + mFileName, e);
            return Collections.emptyList();
        }
        return result;
    }

    /**
     * Returns the underlying file used for persisting data
     */
    public AtomicFile getFile(Context context) {
        return new AtomicFile(context.getFileStreamPath(mFileName));
    }

    /**
     * Interface to create an ItemInfo during parsing
     */
    public interface ItemFactory<T extends ItemInfo> {

        /**
         * Returns an item info or null in which case the entry is ignored
         */
        @Nullable
        T createInfo(int itemType, UserHandle user, Intent intent);
    }
}
