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

package com.android.wm.shell.bubbles;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.android.wm.shell.tests.R;

/**
 * Referenced by NotificationTestHelper#makeBubbleMetadata
 */
public class BubblesTestActivity extends Activity {

    public static final String BUBBLE_ACTIVITY_OPENED = "BUBBLE_ACTIVITY_OPENED";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Intent i = new Intent(BUBBLE_ACTIVITY_OPENED);
        sendBroadcast(i);
    }
}
