/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.discovery;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;

/**
 * This class represents the model for a discovered app via app discovery.
 * It holds all information for one result retrieved from an app discovery service.
 */
public class AppDiscoveryItem {

    public final String packageName;
    public final boolean isInstantApp;
    public final boolean isRecent;
    public final float starRating;
    public final long reviewCount;
    public final Intent launchIntent;
    public final Intent installIntent;
    public final CharSequence title;
    public final String publisher;
    public final String price;
    public final Bitmap bitmap;

    public AppDiscoveryItem(String packageName,
                            boolean isInstantApp,
                            boolean isRecent,
                            float starRating,
                            long reviewCount,
                            CharSequence title,
                            String publisher,
                            Bitmap bitmap,
                            String price,
                            Intent launchIntent,
                            Intent installIntent) {
        this.packageName = packageName;
        this.isInstantApp = isInstantApp;
        this.isRecent = isRecent;
        this.starRating = starRating;
        this.reviewCount = reviewCount;
        this.launchIntent = launchIntent;
        this.installIntent = installIntent;
        this.title = title;
        this.publisher = publisher;
        this.price = price;
        this.bitmap = bitmap;
    }

}
