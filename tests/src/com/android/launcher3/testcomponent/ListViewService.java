/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.launcher3.testcomponent;

import android.content.Intent;
import android.os.IBinder;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

public class ListViewService extends RemoteViewsService {

    public static IBinder sBinderForTest;

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new SimpleViewsFactory();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return sBinderForTest != null ? sBinderForTest : super.onBind(intent);
    }

    public static class SimpleViewsFactory implements RemoteViewsFactory {

        public int viewCount = 0;

        @Override
        public void onCreate() { }

        @Override
        public void onDataSetChanged() { }

        @Override
        public void onDestroy() { }

        @Override
        public int getCount() {
            return viewCount;
        }

        @Override
        public RemoteViews getViewAt(int i) {
            RemoteViews views = new RemoteViews("android", android.R.layout.simple_list_item_1);
            views.setTextViewText(android.R.id.text1, getLabel(i));
            return views;
        }

        public String getLabel(int i) {
            return "Item " + i;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        public IBinder toBinder() {
            return new RemoteViewsService() {
                @Override
                public RemoteViewsFactory onGetViewFactory(Intent intent) {
                    return SimpleViewsFactory.this;
                }
            }.onBind(new Intent("dummy_intent"));
        }
    }
}
