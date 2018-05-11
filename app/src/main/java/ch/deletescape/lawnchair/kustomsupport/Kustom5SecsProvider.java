package ch.deletescape.lawnchair.kustomsupport;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class Kustom5SecsProvider extends ContentProvider {

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
            /*
             * This assumes you have a transparent activity that will just call finish() during its onCreate method
             * Activity in this case also provides a static method for starting itself
             */
            AutoFinishTransparentActivity.start(getContext());
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
        if ("org.kustom.wallpaper".equals(callingPkg)) return;
        if ("org.kustom.widget".equals(callingPkg)) return;
        throw new SecurityException("Unauthorized");
    }
}