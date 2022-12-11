
package com.android.quickstep;

import android.view.RemoteAnimationTarget;
import com.android.wm.shell.splitscreen.ISplitScreen;


interface ISplitScreenExt extends ISplitScreen {

    /**
     * Blocking call that notifies and gets additional split-screen targets when entering
     * recents (for example: the dividerBar).
     *
     * @param appTargets apps that will be re-parented to display area
     */
    RemoteAnimationTarget[] onGoingToRecentsLegacy(RemoteAnimationTarget[] appTargets);
}
