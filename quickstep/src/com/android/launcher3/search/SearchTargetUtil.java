/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.launcher3.search;

import static com.android.app.search.LayoutType.DIVIDER;
import static com.android.app.search.LayoutType.ICON_HORIZONTAL_TEXT;
import static com.android.app.search.LayoutType.SMALL_ICON_HORIZONTAL_TEXT;
import static com.android.app.search.LayoutType.THUMBNAIL;
import static com.android.app.search.ResultType.ACTION;
import static com.android.app.search.ResultType.SCREENSHOT;
import static com.android.app.search.ResultType.SUGGEST;

import android.app.PendingIntent;
import android.app.search.SearchAction;
import android.app.search.SearchTarget;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;

import com.android.app.search.ResultType;

public class SearchTargetUtil {

    public static final String BUNDLE_EXTRA_SHOULD_START = "should_start";
    public static final String BUNDLE_EXTRA_SHOULD_START_FOR_RESULT = "should_start_for_result";
    public static final String BUNDLE_EXTRA_BADGE_WITH_PACKAGE = "badge_with_package";
    public static final String BUNDLE_EXTRA_PRIMARY_ICON_FROM_TITLE = "primary_icon_from_title";

    public static final String EXTRA_CLASS = "class";

    private static final String TITLE = " title: weather ";
    private static final String SUBTITLE = " subtitle: 68 degrees ";
    private static final String PACKAGE2 = "com.google.android.gm";
    private static final UserHandle USERHANDLE = Process.myUserHandle();


    /**
     * Generate SearchTargetUtil for ICON_HORIZONTAL_TEXT layout type.
     *
     * targets.add(SearchTargetUtil.generateIconDoubleHorizontalText_SearchAction(
     * mContext, "red", Color.RED));
     * targets.add(SearchTargetUtil.generateIconDoubleHorizontalText_SearchAction(
     * mContext, "yellow", Color.YELLOW));
     */
    public static SearchTarget generateIcoHorizontalText_usingSearchAction(
            Context context, String id, int color) {
        SearchTarget.Builder builder =
                new SearchTarget.Builder(ACTION, ICON_HORIZONTAL_TEXT, id)
                        .setPackageName(PACKAGE2) /* required */
                        .setUserHandle(USERHANDLE); /* required */

        Intent intent = new Intent("com.google.android.googlequicksearchbox.GENERIC_QUERY");
        intent.putExtra("query", "weather");
        intent.putExtra("full_screen", false);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context, 1, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(color);
        Icon icon = Icon.createWithAdaptiveBitmap(bitmap);

        Bundle b = new Bundle();
        b.putBoolean(BUNDLE_EXTRA_SHOULD_START_FOR_RESULT, true);
        b.putBoolean(BUNDLE_EXTRA_BADGE_WITH_PACKAGE, true);
        b.putBoolean(BUNDLE_EXTRA_PRIMARY_ICON_FROM_TITLE, true);

        builder.setSearchAction(new SearchAction.Builder(id, id + TITLE)
                .setSubtitle(id + SUBTITLE)
                .setPendingIntent(pendingIntent)
                .setIcon(icon)
                .setExtras(b)
                .build());
        return builder.build();
    }

    /**
     * Inside SearchServicePipeline, add following samples to test the search target.
     *
     * targets.add(SearchTargetUtil.generateThumbnail_SearchAction("blue", Color.BLUE));
     * targets.add(SearchTargetUtil.generateThumbnail_SearchAction("red", Color.RED));
     * targets.add(SearchTargetUtil.generateThumbnail_SearchAction("green", Color.GREEN));
     */
    public static SearchTarget generateThumbnail_usingSearchAction(String id, int color) {
        SearchTarget.Builder builder =
                new SearchTarget.Builder(SCREENSHOT, THUMBNAIL, id)
                        .setPackageName(PACKAGE2) /* required */
                        .setUserHandle(USERHANDLE); /* required */


        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setData(Uri.parse("uri blah blah"))
                .setType("image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Bitmap bitmap = Bitmap.createBitmap(1000, 500, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(color);
        Icon icon = Icon.createWithBitmap(bitmap);

        builder.setSearchAction(new SearchAction.Builder(id, TITLE)
                .setSubtitle(SUBTITLE)
                .setIcon(icon)
                .setIntent(intent)
                .build());
        return builder.build();

    }

    /**
     * Generate SearchTargetUtil for SMALL_ICON_HORIZONTAL_TEXT layout type.
     *
     * targets.add(SearchTargetUtil.generateIconHorizontalText_SearchAction(
     * mContext, "red", Color.RED));
     * targets.add(SearchTargetUtil.generateIconHorizontalText_SearchAction(
     * mContext, "yellow", Color.YELLOW));
     */
    public static SearchTarget generateSmallIconHorizontalText_usingSearchAction(
            Context context, String id, int color) {
        String title = "Ask the assistant";
        String fallbackQuery = "sourdough bread";
        SearchTarget.Builder builder =
                new SearchTarget.Builder(SUGGEST, SMALL_ICON_HORIZONTAL_TEXT, id)
                        .setPackageName(PACKAGE2) /* required */
                        .setUserHandle(USERHANDLE); /* required */

        Intent intent3 = new Intent("com.google.android.googlequicksearchbox.GENERIC_QUERY");
        intent3.putExtra("query", fallbackQuery);
        intent3.putExtra("full_screen", false);
        PendingIntent pendingIntent3 =
                PendingIntent.getActivity(
                        context, 1, intent3,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(color);
        Icon icon = Icon.createWithAdaptiveBitmap(bitmap);

        Bundle extra = new Bundle();
        extra.putBoolean(BUNDLE_EXTRA_SHOULD_START_FOR_RESULT, true);

        SearchAction searchAction = new SearchAction.Builder(id, title)
                .setSubtitle(fallbackQuery)
                .setPendingIntent(pendingIntent3)
                .setIcon(icon)
                .setExtras(extra)
                .build();
        return builder.setSearchAction(searchAction).build();
    }

    public static SearchTarget generateDivider() {
        SearchTarget.Builder builder =
                new SearchTarget.Builder(SUGGEST, DIVIDER, "divider")
                        .setPackageName("") /* required but not used*/
                        .setUserHandle(USERHANDLE); /* required */
        return builder.build();
    }


    /**
     * Generate SearchTargetUtil for ICON_DOUBLE_HORIZONTAL_TEXT layout type.
     */
    public static SearchTarget generateIconDoubleHorizontalText_ShortcutInfo(Context context) {
        String id = "23456";
        SearchTarget.Builder builder =
                new SearchTarget.Builder(ResultType.SHORTCUT, SMALL_ICON_HORIZONTAL_TEXT, id)
                        .setPackageName("com.google.android.gm") /* required */
                        .setUserHandle(UserHandle.CURRENT); /* required */

        builder.setShortcutInfo(new ShortcutInfo.Builder(context, id).build());
        return builder.build();
    }
}
