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
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.view.ThreadedRenderer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@RequiresApi(Build.VERSION_CODES.P)
public class HiddenApiCompat {

    public static boolean checkIfAllowed() {
        if (tryAccess()) {
            return true;
        }

        tryWhitelist();

        return tryAccess();
    }

    private static void tryWhitelist() {
        try {
            Method forName = Class.class.getDeclaredMethod("forName", String.class);
            Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);

            Class<?> vmRuntimeClass = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
            Method getRuntime = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null);
            Method setHiddenApiExemptions = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "setHiddenApiExemptions", new Class[]{String[].class});
            Object vmRuntime = getRuntime.invoke(null);

            setHiddenApiExemptions.invoke(vmRuntime, new Object[] { new String[] { "L" } });
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
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

    public static boolean supportsMultiWindow(Context context) {
        return ActivityManager.supportsMultiWindow(context);
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
