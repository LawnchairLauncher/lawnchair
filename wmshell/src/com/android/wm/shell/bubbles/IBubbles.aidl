/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.content.Intent;
import android.graphics.Rect;
import com.android.wm.shell.bubbles.IBubblesListener;
import com.android.wm.shell.common.bubbles.BubbleBarLocation;

/**
 * Interface that is exposed to remote callers (launcher) to manipulate the bubbles feature when
 * showing in the bubble bar.
 */
interface IBubbles {

    oneway void registerBubbleListener(in IBubblesListener listener) = 1;

    oneway void unregisterBubbleListener(in IBubblesListener listener) = 2;

    oneway void showBubble(in String key, in int topOnScreen) = 3;

    oneway void dragBubbleToDismiss(in String key) = 4;

    oneway void removeAllBubbles() = 5;

    oneway void collapseBubbles() = 6;

    oneway void startBubbleDrag(in String key) = 7;

    oneway void showUserEducation(in int positionX, in int positionY) = 8;

    oneway void setBubbleBarLocation(in BubbleBarLocation location) = 9;

    oneway void updateBubbleBarTopOnScreen(in int topOnScreen) = 10;

    oneway void stopBubbleDrag(in BubbleBarLocation location, in int topOnScreen) = 11;
}