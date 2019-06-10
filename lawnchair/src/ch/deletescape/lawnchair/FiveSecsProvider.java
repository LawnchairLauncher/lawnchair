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

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import ch.deletescape.lawnchair.BlankActivity;
import com.android.launcher3.uioverrides.WallpaperManagerCompat;

// Based on:
//      - https://gist.github.com/frmz/669eeca0b20b943b7091b9078eb3247e
//      - https://help.kustom.rocks/i194-launchers-touch-features-support-aka-remove-5-secs-delay
public class FiveSecsProvider extends ContentProvider {

    /**
     * Path used by Kustom to ask a 5 secs delay reset
     */
    private final static String PATH_RESET_5SEC_DELAY = "reset5secs";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri,
                        String[] projection,
                        String selection,
                        String[] selectionArgs,
                        String sortOrder) {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        // Not supported
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        // Not supported
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public int delete(@NonNull Uri uri,
                      String selection,
                      String[] selectionArgs) {
        checkCallingPackage();
        if (PATH_RESET_5SEC_DELAY.equals(uri.getLastPathSegment())) {
            getContext().startActivity(new Intent(getContext(), BlankActivity.class));
            return 1;
        }
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri,
                      ContentValues values,
                      String selection,
                      String[] selectionArgs) {
        // Not supported
        throw new UnsupportedOperationException("Unsupported");
    }

    /**
     * Will check weather or not calling pkg is authorized to talk with this provider
     *
     * @throws SecurityException
     */
    private void checkCallingPackage() throws SecurityException {
        String callingPkg = getCallingPackage();
        WallpaperInfo info = WallpaperManager.getInstance(getContext()).getWallpaperInfo();
        if (info != null) {
            if (info.getPackageName().equals(callingPkg)) return;
        }
        if ("org.kustom.widget".equals(callingPkg)) return;
        throw new SecurityException("Unauthorized");
    }
}
