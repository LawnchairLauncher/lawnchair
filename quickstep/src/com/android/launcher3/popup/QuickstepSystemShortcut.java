/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.popup;

import android.view.View;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;

public interface QuickstepSystemShortcut {

    static SystemShortcut.Factory<BaseQuickstepLauncher> getSplitSelectShortcutByPosition(
            SplitPositionOption position) {
        return (activity, itemInfo) -> new QuickstepSystemShortcut.SplitSelectSystemShortcut(
                activity, itemInfo, position);
    }

    class SplitSelectSystemShortcut extends SystemShortcut<BaseQuickstepLauncher> {

        private final BaseQuickstepLauncher mLauncher;
        private final ItemInfo mItemInfo;
        private final SplitPositionOption mPosition;

        public SplitSelectSystemShortcut(BaseQuickstepLauncher launcher, ItemInfo itemInfo,
                SplitPositionOption position) {
            super(position.iconResId, position.textResId, launcher, itemInfo);

            mLauncher = launcher;
            mItemInfo = itemInfo;
            mPosition = position;
        }

        @Override
        public void onClick(View view) {
        }
    }
}
