/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.bubbles

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Process
import com.android.wm.shell.bubbles.appinfo.BubbleAppInfo
import com.android.wm.shell.bubbles.appinfo.BubbleAppInfoProvider

/** A fake implementation of [BubbleAppInfoProvider]. */
class FakeBubbleAppInfoProvider : BubbleAppInfoProvider {
    override fun resolveAppInfo(context: Context, bubble: Bubble): BubbleAppInfo {
        return BubbleAppInfo("app name", ColorDrawable(Color.RED), Process.myUserHandle())
    }
}
