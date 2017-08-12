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

import ch.deletescape.lawnchair.Launcher.LauncherOverlay;
import ch.deletescape.lawnchair.overlay.ILauncherClient;

public class LauncherTab {

    private ILauncherClient mLauncherClient;

    public LauncherTab(Launcher launcher) {
        mLauncherClient = ILauncherClient.Companion.create(launcher);

        launcher.setLauncherOverlay(new LauncherOverlays());
    }

    protected ILauncherClient getClient() {
        return mLauncherClient;
    }

    private class LauncherOverlays implements LauncherOverlay {
        @Override
        public void onScrollInteractionBegin() {
            mLauncherClient.startMove();
        }

        @Override
        public void onScrollInteractionEnd() {
            mLauncherClient.endMove();
        }

        @Override
        public void onScrollChange(float progress, boolean rtl) {
            mLauncherClient.updateMove(progress);
        }
    }
}
