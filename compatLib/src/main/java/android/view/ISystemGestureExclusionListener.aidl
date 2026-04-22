/**
 * Copyright (c) 2019, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.view;

import android.graphics.Region;

/**
 * Listener for changes to the system gesture exclusion region
 *
 * {@hide}
 */
oneway interface ISystemGestureExclusionListener {
    /**
     * Called when the system gesture exclusion for the given display changed.
     * @param displayId the display whose system gesture exclusion changed
     * @param systemGestureExclusion a {@code Region} where the app would like priority over the
     *                               system gestures, in display coordinates. Certain restrictions
     *                               might be applied such that apps don't get all the exclusions
     *                               they request.
     * @param systemGestureExclusionUnrestricted a {@code Region} where the app would like priority
     *                               over the system gestures, in display coordinates, without
     *                               any restrictions applied. Null if no restrictions have been
     *                               applied.
     */
    void onSystemGestureExclusionChanged(int displayId, in Region systemGestureExclusion,
            in Region systemGestureExclusionUnrestricted);
}
