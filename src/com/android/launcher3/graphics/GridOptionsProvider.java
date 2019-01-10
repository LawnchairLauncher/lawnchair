package com.android.launcher3.graphics;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.InvariantDeviceProfile.GridOption;
import com.android.launcher3.R;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.util.UiThreadHelper;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Exposes various launcher grid options and allows the caller to change them.
 * APIs:
 *      /list_options: List the various available grip options, has following columns
 *          name: name of the grid
 *          rows: number of rows in the grid
 *          cols: number of columns in the grid
 *          preview_count: number of previews available for this grid option. The preview uri
 *                         looks like /preview/<grid-name>/<preview index starting with 0>
 *          is_default: true if this grid is currently active
 *
 *     /preview: Opens a file stream for the grid preview
 */
public class GridOptionsProvider extends ContentProvider {

    private static final String TAG = "GridOptionsProvider";

    private static final String KEY_NAME = "name";
    private static final String KEY_ROWS = "rows";
    private static final String KEY_COLS = "cols";
    private static final String KEY_PREVIEW_COUNT = "preview_count";
    private static final String KEY_IS_DEFAULT = "is_default";

    private static final String KEY_PREVIEW = "preview";
    private static final String MIME_TYPE_PNG = "image/png";

    public static final PipeDataWriter<Future<Bitmap>> BITMAP_WRITER =
            new PipeDataWriter<Future<Bitmap>>() {
                @Override
                public void writeDataToPipe(ParcelFileDescriptor output, Uri uri, String s,
                        Bundle bundle, Future<Bitmap> bitmap) {
                    try (AutoCloseOutputStream os = new AutoCloseOutputStream(output)) {
                        bitmap.get().compress(Bitmap.CompressFormat.PNG, 100, os);
                    } catch (Exception e) {
                        Log.w(TAG, "fail to write to pipe", e);
                    }
                }
            };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        // TODO: Validate the query uri
        MatrixCursor cursor = new MatrixCursor(new String[] {
                KEY_NAME, KEY_ROWS, KEY_COLS, KEY_PREVIEW_COUNT, KEY_IS_DEFAULT});
        InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(getContext());
        try (XmlResourceParser parser = getContext().getResources().getXml(R.xml.device_profiles)) {
            final int depth = parser.getDepth();
            int type;
            while (((type = parser.next()) != XmlPullParser.END_TAG ||
                    parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
                if ((type == XmlPullParser.START_TAG) && "grid-option".equals(parser.getName())) {
                    GridOption gridOption = new GridOption(
                            getContext(), Xml.asAttributeSet(parser));

                    cursor.newRow()
                            .add(KEY_NAME, gridOption.name)
                            .add(KEY_ROWS, gridOption.numRows)
                            .add(KEY_COLS, gridOption.numColumns)
                            .add(KEY_PREVIEW_COUNT, 1)
                            .add(KEY_IS_DEFAULT, idp.numColumns == gridOption.numColumns
                                    && idp.numRows == gridOption.numRows);
                }
            }
        } catch (IOException | XmlPullParserException e) {
            Log.e(TAG, "Error parsing device profile", e);
        }

        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        List<String> segments = uri.getPathSegments();
        if (segments.size() > 0 && KEY_PREVIEW.equals(segments.get(0))) {
            return MIME_TYPE_PNG;
        }
        return "vnd.android.cursor.dir/launcher_grid";
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        List<String> segments = uri.getPathSegments();
        if (segments.size() < 2 || !KEY_PREVIEW.equals(segments.get(0))) {
            throw new FileNotFoundException("Invalid preview url");
        }
        String profileName = segments.get(1);
        if (TextUtils.isEmpty(profileName)) {
            throw new FileNotFoundException("Invalid preview url");
        }

        InvariantDeviceProfile idp;
        try {
            idp = new InvariantDeviceProfile(getContext(), profileName);
        } catch (Exception e) {
            throw new FileNotFoundException(e.getMessage());
        }

        LooperExecutor executor = new LooperExecutor(UiThreadHelper.getBackgroundLooper());
        try {
            return openPipeHelper(uri, MIME_TYPE_PNG, null,
                    executor.submit(new LauncherPreviewRenderer(getContext(), idp)), BITMAP_WRITER);
        } catch (Exception e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }
}
