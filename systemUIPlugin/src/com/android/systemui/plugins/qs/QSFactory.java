/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.plugins.qs;

import com.android.systemui.plugins.Plugin;
import com.android.systemui.plugins.annotations.DependsOn;
import com.android.systemui.plugins.annotations.ProvidesInterface;

/**
 * Plugin that has the ability to create or override any part of
 * QS tiles.
 */
@ProvidesInterface(action = QSFactory.ACTION, version = QSFactory.VERSION)
@DependsOn(target = QSTile.class)
@DependsOn(target = QSTileView.class)
public interface QSFactory extends Plugin {

    String ACTION = "com.android.systemui.action.PLUGIN_QS_FACTORY";
    int VERSION = 3;

    QSTile createTile(String tileSpec);
}
