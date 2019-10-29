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

package ch.deletescape.lawnchair;

import android.app.ActivityManager;
import android.app.ActivityManager.TaskDescription;
import android.app.ActivityOptions;
import android.app.WindowConfiguration;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Region;
import android.os.Build;
import android.view.ISystemGestureExclusionListener;
import android.view.ThreadedRenderer;
import androidx.annotation.RequiresApi;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@RequiresApi(Build.VERSION_CODES.P)
public class HiddenApiCompat {

    private static Method sForName;
    private static Method sGetDeclaredMethod;

    static {
        try {
            sForName = Class.class.getDeclaredMethod("forName", String.class);
            sGetDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public static boolean checkIfAllowed() {
        if (tryAccess()) {
            return true;
        }

        tryWhitelist();

        return tryAccess();
    }

    public static boolean isNewQ() {
        if (!checkIfAllowed()) return true;
        try {
            sGetDeclaredMethod.invoke(ISystemGestureExclusionListener.class,
                    "onSystemGestureExclusionChanged", new Class[] {int.class, Region.class, Region.class});
            return true;
        } catch (IllegalAccessException | InvocationTargetException e) {
            return false;
        }
    }

    private static void tryWhitelist() {
        if (sForName == null || sGetDeclaredMethod == null) return;
        try {
            Class<?> vmRuntimeClass = (Class<?>) sForName.invoke(null, "dalvik.system.VMRuntime");
            Method getRuntime = (Method) sGetDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null);
            Method setHiddenApiExemptions = (Method) sGetDeclaredMethod.invoke(vmRuntimeClass, "setHiddenApiExemptions", new Class[]{String[].class});
            Object vmRuntime = getRuntime.invoke(null);

            setHiddenApiExemptions.invoke(vmRuntime, new Object[] { new String[] { "L" } });
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private static boolean tryAccess() {
        // TODO: is there a better way?
        try {
            ThreadedRenderer.class.getMethod("setContextPriority", int.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public static boolean isResizeableMode(int mode) {
        return ActivityInfo.isResizeableMode(mode);
    }

    public static ActivityOptions makePopupWindowOptions() {
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WindowConfiguration.WINDOWING_MODE_FREEFORM);
        return options;
    }

    public static boolean isInstantApp(ApplicationInfo applicationInfo) {
        return applicationInfo.isInstantApp();
    }

    public static int getIconResource(TaskDescription desc) {
        return desc.getIconResource();
    }

    public static Bitmap loadTaskDescriptionIcon(TaskDescription desc, int userId) {
        return ActivityManager.TaskDescription.loadTaskDescriptionIcon(desc.getIconFilename(), userId);
    }
}
