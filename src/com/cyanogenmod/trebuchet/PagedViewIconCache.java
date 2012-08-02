/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.cyanogenmod.trebuchet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.pm.ComponentInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;

/**
 * Simple cache mechanism for PagedView outlines.
 */
public class PagedViewIconCache {
    public static class Key {
        public enum Type {
            ApplicationInfoKey,
            AppWidgetProviderInfoKey,
            ResolveInfoKey
        }
        private final ComponentName mComponentName;
        private final Type mType;

        public Key(ApplicationInfo info) {
            mComponentName = info.componentName;
            mType = Type.ApplicationInfoKey;
        }
        public Key(ResolveInfo info) {
            final ComponentInfo ci = info.activityInfo != null ? info.activityInfo :
                info.serviceInfo;
            mComponentName = new ComponentName(ci.packageName, ci.name);
            mType = Type.ResolveInfoKey;
        }
        public Key(AppWidgetProviderInfo info) {
            mComponentName = info.provider;
            mType = Type.AppWidgetProviderInfoKey;
        }

        private ComponentName getComponentName() {
            return mComponentName;
        }
        public boolean isKeyType(Type t) {
            return (mType == t);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Key) {
                Key k = (Key) o;
                return mComponentName.equals(k.mComponentName);
            }
            return super.equals(o);
        }
        @Override
        public int hashCode() {
            return getComponentName().hashCode();
        }
    }

    private final HashMap<Key, Bitmap> mIconOutlineCache = new HashMap<Key, Bitmap>();

    public void clear() {
        for (Key key : mIconOutlineCache.keySet()) {
            mIconOutlineCache.get(key).recycle();
        }
        mIconOutlineCache.clear();
    }
    private void retainAll(HashSet<Key> keysToKeep, Key.Type t) {
        HashSet<Key> keysToRemove = new HashSet<Key>(mIconOutlineCache.keySet());
        keysToRemove.removeAll(keysToKeep);
        for (Key key : keysToRemove) {
            if (key.isKeyType(t)) {
                mIconOutlineCache.get(key).recycle();
                mIconOutlineCache.remove(key);
            }
        }
    }
    /** Removes all the keys to applications that aren't in the passed in collection */
    public void retainAllApps(ArrayList<ApplicationInfo> keys) {
        HashSet<Key> keysSet = new HashSet<Key>();
        for (ApplicationInfo info : keys) {
            keysSet.add(new Key(info));
        }
        retainAll(keysSet, Key.Type.ApplicationInfoKey);
    }
    /** Removes all the keys to shortcuts that aren't in the passed in collection */
    public void retainAllShortcuts(List<ResolveInfo> keys) {
        HashSet<Key> keysSet = new HashSet<Key>();
        for (ResolveInfo info : keys) {
            keysSet.add(new Key(info));
        }
        retainAll(keysSet, Key.Type.ResolveInfoKey);
    }
    /** Removes all the keys to widgets that aren't in the passed in collection */
    public void retainAllAppWidgets(List<AppWidgetProviderInfo> keys) {
        HashSet<Key> keysSet = new HashSet<Key>();
        for (AppWidgetProviderInfo info : keys) {
            keysSet.add(new Key(info));
        }
        retainAll(keysSet, Key.Type.AppWidgetProviderInfoKey);
    }
    public void addOutline(Key key, Bitmap b) {
        mIconOutlineCache.put(key, b);
    }
    public void removeOutline(Key key) {
        if (mIconOutlineCache.containsKey(key)) {
            mIconOutlineCache.get(key).recycle();
            mIconOutlineCache.remove(key);
        }
    }
    public Bitmap getOutline(Key key) {
        return mIconOutlineCache.get(key);
    }
}
