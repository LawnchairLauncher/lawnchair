/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.launcher2;

import android.app.WallpaperManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.RemoteException;
import android.service.wallpaper.WallpaperService;
import android.util.Log;

import java.text.Collator;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Displays a list of live wallpapers, allowing the user to select one
 * and make it the system global wallpaper.
 */
public class LiveWallpaperPickActivity extends ActivityPicker {
    private static final String TAG = "LiveWallpaperPickActivity";

    private PackageManager mPackageManager;
    private WallpaperManager mWallpaperManager;
    
    @Override
    public void onCreate(Bundle icicle) {
        mPackageManager = getPackageManager();
        mWallpaperManager = WallpaperManager.getInstance(this);
        
        super.onCreate(icicle);
        
        // Set default return data
        setResult(RESULT_CANCELED);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void onClick(DialogInterface dialog, int which) {
        Intent intent = getIntentForPosition(which);
        try {
            mWallpaperManager.getIWallpaperManager().setWallpaperComponent(
                    intent.getComponent());
            this.setResult(RESULT_OK);
        } catch (RemoteException e) {
            // do nothing
        } catch (RuntimeException e) {
            Log.w(TAG, "Failure setting wallpaper", e);
        }
        finish();
    }

    void putLiveWallpaperItems(List<ResolveInfo> ris,
            List<PickAdapter.Item> items) {
        final int size = ris.size();
        for (int i = 0; i < size; i++) {
            ServiceInfo si = ris.get(i).serviceInfo;
            
            CharSequence label = si.loadLabel(mPackageManager);
            Drawable icon = si.loadIcon(mPackageManager);
            
            PickAdapter.Item item = new PickAdapter.Item(this, label, icon);
            
            item.packageName = si.packageName;
            item.className = si.name;
            
            items.add(item);
        }
    }
    
    @Override
    protected List<PickAdapter.Item> getItems() {
        List<PickAdapter.Item> items = new ArrayList<PickAdapter.Item>();
        
        putInstalledLiveWallpapers(items);
        
        // Sort all items together by label
        Collections.sort(items, new Comparator<PickAdapter.Item>() {
                Collator mCollator = Collator.getInstance();
                public int compare(PickAdapter.Item lhs, PickAdapter.Item rhs) {
                    return mCollator.compare(lhs.label, rhs.label);
                }
            });

        return items;
    }

    void putInstalledLiveWallpapers(List<PickAdapter.Item> items) {
        List<ResolveInfo> ris = mPackageManager.queryIntentServices(
                new Intent(WallpaperService.SERVICE_INTERFACE), 0);
        putLiveWallpaperItems(ris, items);
    }
}
