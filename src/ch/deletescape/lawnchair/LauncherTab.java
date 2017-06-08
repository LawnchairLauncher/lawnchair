/*
 * Copyright (C) 2017 Paranoid Android
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

package ch.deletescape.lawnchair;

import com.google.android.libraries.launcherclient.LauncherClient;
import com.google.firebase.analytics.FirebaseAnalytics;

import ch.deletescape.lawnchair.Launcher.LauncherOverlay;

public class LauncherTab {

    private LauncherClient mLauncherClient;
    private FirebaseAnalytics fa;

    public LauncherTab(Launcher launcher) {
        mLauncherClient = new LauncherClient(launcher, true);

        launcher.setLauncherOverlay(new LauncherOverlays());
        fa = FirebaseAnalytics.getInstance(launcher);
    }

    protected LauncherClient getClient() {
        return mLauncherClient;
    }

    private class LauncherOverlays implements LauncherOverlay {
        @Override
        public void onScrollInteractionBegin() {
            mLauncherClient.startMove();
            fa.logEvent("overlay_start_move", null);
        }

        @Override
        public void onScrollInteractionEnd() {
            mLauncherClient.endMove();
            fa.logEvent("overlay_end_move", null);
        }

        @Override
        public void onScrollChange(float progress, boolean rtl) {
            mLauncherClient.updateMove(progress);
            fa.logEvent("overlay_update_move", null);
        }
    }
}
