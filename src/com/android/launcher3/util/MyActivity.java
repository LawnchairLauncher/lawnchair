/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import com.android.launcher3.LauncherAppState;

import com.android.launcher3.graphics.LauncherPreviewRenderer;

/**
 * TODO: Remove this class
 */
@TargetApi(Build.VERSION_CODES.O)
public class MyActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new AsyncTask<Void, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Void... voids) {
                LauncherPreviewRenderer renderer = new LauncherPreviewRenderer(MyActivity.this,
                        LauncherAppState.getIDP(MyActivity.this));

                Bitmap b = renderer.createScreenShot();
                return b;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                ImageView view = new ImageView(MyActivity.this);
                view.setImageBitmap(bitmap);
                setContentView(view);

                view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

            }
        }.execute();
    }
}
