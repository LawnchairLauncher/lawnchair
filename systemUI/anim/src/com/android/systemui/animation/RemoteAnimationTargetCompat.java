/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.animation;

import android.util.ArrayMap;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionInfo.Change;

import com.android.wm.shell.shared.TransitionUtil;

import java.util.ArrayList;
import java.util.function.Predicate;

/**
 * Some utility methods for creating {@link RemoteAnimationTarget} instances.
 */
public class RemoteAnimationTargetCompat {

    /**
     * Represents a TransitionInfo object as an array of old-style app targets
     *
     * @param leashMap Temporary map of change leash -> launcher leash. Is an output, so should be
     *                 populated by this function. If null, it is ignored.
     */
    public static RemoteAnimationTarget[] wrapApps(TransitionInfo info,
            SurfaceControl.Transaction t, ArrayMap<SurfaceControl, SurfaceControl> leashMap) {
        // LeafTaskFilter is order-dependent, so the same object needs to be used for all Change
        // objects. That's why it's constructed here and captured by the lambda instead of building
        // a new one ad hoc every time.
        TransitionUtil.LeafTaskFilter taskFilter = new TransitionUtil.LeafTaskFilter();
        return wrap(info, t, leashMap, (change) -> {
            // Intra-task activity -> activity transitions should be categorized as apps.
            if (change.getActivityComponent() != null) return true;
            return taskFilter.test(change);
        });
    }

    /**
     * Represents a TransitionInfo object as an array of old-style non-app targets
     *
     * @param wallpapers If true, this will return wallpaper targets; otherwise it returns
     *                   non-wallpaper targets.
     * @param leashMap Temporary map of change leash -> launcher leash. Is an output, so should be
     *                 populated by this function. If null, it is ignored.
     */
    public static RemoteAnimationTarget[] wrapNonApps(TransitionInfo info, boolean wallpapers,
            SurfaceControl.Transaction t, ArrayMap<SurfaceControl, SurfaceControl> leashMap) {
        return wrap(info, t, leashMap, (change) -> {
            // Intra-task activity -> activity transitions should be categorized as apps.
            if (change.getActivityComponent() != null) return false;
            return wallpapers
                    ? TransitionUtil.isWallpaper(change) : TransitionUtil.isNonApp(change);
        });
    }

    private static RemoteAnimationTarget[] wrap(TransitionInfo info,
            SurfaceControl.Transaction t, ArrayMap<SurfaceControl, SurfaceControl> leashMap,
            Predicate<Change> filter) {
        final ArrayList<RemoteAnimationTarget> out = new ArrayList<>();
        for (int i = 0; i < info.getChanges().size(); i++) {
            TransitionInfo.Change change = info.getChanges().get(i);
            if (TransitionUtil.isOrderOnly(change)) continue;
            if (filter.test(change)) {
                out.add(TransitionUtil.newTarget(
                        change, info.getChanges().size() - i, info, t, leashMap));
            }
        }
        return out.toArray(new RemoteAnimationTarget[out.size()]);
    }
}
