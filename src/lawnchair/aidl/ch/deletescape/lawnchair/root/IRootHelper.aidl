package ch.deletescape.lawnchair.root;

import ch.deletescape.lawnchair.clockhide.IconBlacklistPreference;

interface IRootHelper {

    void goToSleep();

    void sendKeyEvent(int code, int action, int flags, long downTime, long eventTime);

    IconBlacklistPreference getIconBlacklistPreference();
}
