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

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Bundle;

import com.android.launcher3.R;

import java.util.UUID;

/**
 * A custom shortcut is a 1x1 widget that launches a specific intent when user tap on it.
 * Custom shortcuts are replaced by deep shortcuts after api 25.
 */
public class CustomShortcutConfigActivity extends BaseTestingActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent launchIntent = new Intent(this, BaseTestingActivity.class)
                .setAction("com.android.launcher3.intent.action.test_shortcut");
        Intent shortcutIntent = createShortcutResultIntent(
                this, UUID.randomUUID().toString(), "Shortcut",
                R.drawable.ic_widget, launchIntent);
        setResult(RESULT_OK, shortcutIntent);
        finish();
    }

    private static Intent createShortcutResultIntent(
            Context context, String uniqueId, String name, int iconId, Intent launchIntent) {
        ShortcutInfo shortcutInfo =
                createShortcutInfo(context, uniqueId, name, iconId, launchIntent);
        ShortcutManager sm = context.getSystemService(ShortcutManager.class);
        return sm.createShortcutResultIntent(shortcutInfo);
    }

    private static ShortcutInfo createShortcutInfo(
            Context context, String uniqueId, String name, int iconId, Intent launchIntent) {
        return new ShortcutInfo.Builder(context, uniqueId)
                .setShortLabel(name)
                .setLongLabel(name)
                .setIcon(Icon.createWithResource(context, iconId))
                .setIntent(launchIntent)
                .build();
    }
}
