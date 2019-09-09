/**
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.launcher3.util;


import android.text.TextUtils;
import android.util.Pair;
import android.util.Xml;

import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class to build xml for Launcher Layout
 */
public class LauncherLayoutBuilder {

    // Object Tags
    private static final String TAG_WORKSPACE = "workspace";
    private static final String TAG_AUTO_INSTALL = "autoinstall";
    private static final String TAG_FOLDER = "folder";
    private static final String TAG_APPWIDGET = "appwidget";
    private static final String TAG_EXTRA = "extra";

    private static final String ATTR_CONTAINER = "container";
    private static final String ATTR_RANK = "rank";

    private static final String ATTR_PACKAGE_NAME = "packageName";
    private static final String ATTR_CLASS_NAME = "className";
    private static final String ATTR_TITLE = "title";
    private static final String ATTR_SCREEN = "screen";

    // x and y can be specified as negative integers, in which case -1 represents the
    // last row / column, -2 represents the second last, and so on.
    private static final String ATTR_X = "x";
    private static final String ATTR_Y = "y";
    private static final String ATTR_SPAN_X = "spanX";
    private static final String ATTR_SPAN_Y = "spanY";

    private static final String ATTR_CHILDREN = "children";


    // Style attrs -- "Extra"
    private static final String ATTR_KEY = "key";
    private static final String ATTR_VALUE = "value";

    private static final String CONTAINER_DESKTOP = "desktop";
    private static final String CONTAINER_HOTSEAT = "hotseat";

    private final ArrayList<Pair<String, HashMap<String, Object>>> mNodes = new ArrayList<>();

    public Location atHotseat(int rank) {
        Location l = new Location();
        l.items.put(ATTR_CONTAINER, CONTAINER_HOTSEAT);
        l.items.put(ATTR_RANK, Integer.toString(rank));
        return l;
    }

    public Location atWorkspace(int x, int y, int screen) {
        Location l = new Location();
        l.items.put(ATTR_CONTAINER, CONTAINER_DESKTOP);
        l.items.put(ATTR_X, Integer.toString(x));
        l.items.put(ATTR_Y, Integer.toString(y));
        l.items.put(ATTR_SCREEN, Integer.toString(screen));
        return l;
    }

    public String build() throws IOException {
        StringWriter writer = new StringWriter();
        build(writer);
        return writer.toString();
    }

    public void build(Writer writer) throws IOException {
        XmlSerializer serializer = Xml.newSerializer();
        serializer.setOutput(writer);

        serializer.startDocument("UTF-8", true);
        serializer.startTag(null, TAG_WORKSPACE);
        writeNodes(serializer, mNodes);
        serializer.endTag(null, TAG_WORKSPACE);
        serializer.endDocument();
        serializer.flush();
    }

    private static void writeNodes(XmlSerializer serializer,
            ArrayList<Pair<String, HashMap<String, Object>>> nodes) throws IOException {
        for (Pair<String, HashMap<String, Object>> node : nodes) {
            ArrayList<Pair<String, HashMap<String, Object>>> children = null;

            serializer.startTag(null, node.first);
            for (Map.Entry<String, Object> attr : node.second.entrySet()) {
                if (ATTR_CHILDREN.equals(attr.getKey())) {
                    children = (ArrayList<Pair<String, HashMap<String, Object>>>) attr.getValue();
                } else {
                    serializer.attribute(null, attr.getKey(), (String) attr.getValue());
                }
            }

            if (children != null) {
                writeNodes(serializer, children);
            }
            serializer.endTag(null, node.first);
        }
    }

    public class Location {

        final HashMap<String, Object> items = new HashMap<>();

        public LauncherLayoutBuilder putApp(String packageName, String className) {
            items.put(ATTR_PACKAGE_NAME, packageName);
            items.put(ATTR_CLASS_NAME, TextUtils.isEmpty(className) ? packageName : className);
            mNodes.add(Pair.create(TAG_AUTO_INSTALL, items));
            return LauncherLayoutBuilder.this;
        }

        public LauncherLayoutBuilder putWidget(String packageName, String className,
                int spanX, int spanY) {
            items.put(ATTR_PACKAGE_NAME, packageName);
            items.put(ATTR_CLASS_NAME, className);
            items.put(ATTR_SPAN_X, Integer.toString(spanX));
            items.put(ATTR_SPAN_Y, Integer.toString(spanY));
            mNodes.add(Pair.create(TAG_APPWIDGET, items));
            return LauncherLayoutBuilder.this;
        }

        public FolderBuilder putFolder(int titleResId) {
            FolderBuilder folderBuilder = new FolderBuilder();
            items.put(ATTR_TITLE, Integer.toString(titleResId));
            items.put(ATTR_CHILDREN, folderBuilder.mChildren);
            mNodes.add(Pair.create(TAG_FOLDER, items));
            return folderBuilder;
        }
    }

    public class FolderBuilder {

        final ArrayList<Pair<String, HashMap<String, Object>>> mChildren = new ArrayList<>();

        public FolderBuilder addApp(String packageName, String className) {
            HashMap<String, Object> items = new HashMap<>();
            items.put(ATTR_PACKAGE_NAME, packageName);
            items.put(ATTR_CLASS_NAME, TextUtils.isEmpty(className) ? packageName : className);
            mChildren.add(Pair.create(TAG_AUTO_INSTALL, items));
            return this;
        }

        public LauncherLayoutBuilder build() {
            return LauncherLayoutBuilder.this;
        }
    }
}
