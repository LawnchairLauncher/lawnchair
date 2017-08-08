package ch.deletescape.lawnchair.shortcuts.backport;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class ShortcutPackageParser {

    private static final String TAG = "ShortcutPackageParser";

    private static final String ANDROID_MANIFEST_FILENAME = "AndroidManifest.xml";
    private static final String NAMESPACE_ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String TAG_MANIFEST = "manifest";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_ACTIVITY_ALIAS = "activity-alias";
    private static final String TAG_META_DATA = "meta-data";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_RESOURCE = "resource";
    private static final String META_APP_SHORTCUTS = "android.app.shortcuts";

    private final Context mContext;
    private final String mPackageName;
    private Resources mResources;
    private AssetManager mAssets;

    private Map<ComponentName, Integer> mShortcutsResMap = new HashMap<>();

    public ShortcutPackageParser(Context context, String packageName) throws Exception {
        mContext = context;
        mPackageName = packageName;

        //long startTime = System.currentTimeMillis();
        loadPackageResources();
        int cookie = loadApkIntoAssetManager();
        parseManifest(cookie);
        //Log.i(TAG, "Took " + (System.currentTimeMillis() - startTime) + "ms to parse manifest");
    }

    private void loadPackageResources() throws PackageManager.NameNotFoundException {
        mResources = mContext.createPackageContext(mPackageName, Context.CONTEXT_IGNORE_SECURITY)
                .getResources();
        mAssets = mResources.getAssets();
    }

    @SuppressLint("PrivateApi")
    private int loadApkIntoAssetManager() throws PackageManager.NameNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        ApplicationInfo info = mContext.getPackageManager().getApplicationInfo(mPackageName,
                PackageManager.GET_META_DATA | PackageManager.GET_SHARED_LIBRARY_FILES);

        Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
        int cookie = (int) addAssetPath.invoke(mAssets, info.publicSourceDir);
        if (cookie == 0) {
            throw new RuntimeException("Failed adding asset path: " + info.publicSourceDir);
        }
        return cookie;
    }

    private void parseManifest(int cookie) throws IOException, XmlPullParserException {
        XmlPullParser parser = mAssets.openXmlResourceParser(cookie, ANDROID_MANIFEST_FILENAME);

        parser.next();
        parser.next();
        parser.require(XmlPullParser.START_TAG, null, TAG_MANIFEST);
        while (parser.next() == XmlPullParser.START_TAG) {
            if (TAG_APPLICATION.equals(parser.getName())) {
                //Log.d(TAG, "<application>");
                break;
            } else {
                // ignore any tag before <application />
                skip(parser);
            }
        }
        if (TAG_APPLICATION.equals(parser.getName())) {
            while (parser.next() == XmlPullParser.START_TAG) {
                if (TAG_ACTIVITY.equals(parser.getName())
                        || TAG_ACTIVITY_ALIAS.equals(parser.getName())) {
                    parseActivity(parser);
                } else {
                    skip(parser);
                }
            }
            //Log.d(TAG, "</application>");
        } else {
            throw new IllegalStateException();
        }
    }

    private void parseActivity(XmlPullParser parser) throws IOException, XmlPullParserException {
        String activityName = getAttribute(parser, ATTR_NAME);
        if (activityName == null) {
            skip(parser);
            return;
        }
        //Log.d(TAG, "activityName: " + activityName);
        ComponentName componentName = new ComponentName(mPackageName, activityName);
        //Log.d(TAG, "    <activity name=\"" + activityName + "\">");
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    if (TAG_META_DATA.equals(parser.getName())) {
                        parseMeta(parser, componentName);
                        depth--;
                    }
                    break;
            }
        }
        //Log.d(TAG, "    </activity>");
    }

    private void parseMeta(XmlPullParser parser, ComponentName componentName) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, null, TAG_META_DATA);
        String metaName = getAttribute(parser, ATTR_NAME);
        //Log.d(TAG, "        <meta name=\"" + metaName + "\">");
        if (META_APP_SHORTCUTS.equals(metaName)) {
            try {
                int resId = Integer.parseInt(getAttribute(parser, ATTR_RESOURCE).substring(1));
                mShortcutsResMap.put(componentName, resId);
            } catch (NumberFormatException e) {
                Log.w(TAG, "Can't read shortcuts meta data for " + componentName.toShortString(), e);
            }
        }
        skip(parser);
        //Log.d(TAG, "        </meta>");
    }

    private String getAttribute(XmlPullParser parser, String attr) {
        return parser.getAttributeValue(NAMESPACE_ANDROID, attr);
    }

    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

    public Map<ComponentName, Integer> getShortcutsMap() {
        return mShortcutsResMap;
    }
}
