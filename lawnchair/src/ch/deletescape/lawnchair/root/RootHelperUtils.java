/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.root;

import android.hardware.input.InputManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.content.ContextCompat;
import android.view.KeyEvent;
import eu.chainfire.librootjava.RootJava;

public class RootHelperUtils {

    private static PowerManager getPowerManager() {
        return ContextCompat.getSystemService(RootJava.getSystemContext(), PowerManager.class);
    }

    public static void goToSleep() {
        getPowerManager().goToSleep(SystemClock.uptimeMillis());
    }

    public static void injectInputEvent(KeyEvent ev) {
        InputManager.getInstance().injectInputEvent(ev, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }
}
