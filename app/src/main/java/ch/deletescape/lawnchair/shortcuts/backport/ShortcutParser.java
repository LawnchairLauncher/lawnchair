package ch.deletescape.lawnchair.shortcuts.backport;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;

import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.shortcuts.ShortcutInfoCompat;
import ch.deletescape.lawnchair.util.ResourceUtilsKt;

public class ShortcutParser {

    private static final String TAG = "ShortcutParser";

    private static final String NAMESPACE_ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String TAG_SHORTCUTS = "shortcuts";
    private static final String TAG_SHORTCUT = "shortcut";
    private static final String ATTRIBUTE_SHORTCUT_ICON = "icon";
    private static final String ATTRIBUTE_SHORTCUT_DISABLED_MESSAGE = "shortcutDisabledMessage";
    private static final String ATTRIBUTE_SHORTCUT_ID = "shortcutId";
    private static final String ATTRIBUTE_SHORTCUT_LONG_LABEL = "shortcutLongLabel";
    private static final String ATTRIBUTE_SHORTCUT_SHORT_LABEL = "shortcutShortLabel";
    private static final String TAG_INTENT = "intent";
    private static final String ATTRIBUTE_ACTION = "action";
    private static final String ATTRIBUTE_DATA = "data";
    private static final String ATTRIBUTE_TARGET_CLASS = "targetClass";
    private static final String ATTRIBUTE_TARGET_PACKAGE = "targetPackage";

    private final Resources mResources;
    private final String mPackageName;
    private final ComponentName mComponentName;
    private final ArrayList<ShortcutInfoCompat> mShortcutsList = new ArrayList<>();
    private final PackageInfo mPackageInfo;
    private final int mResId;

    public ShortcutParser(Context context, Resources resources, String packageName, ComponentName componentName, int resId) throws PackageManager.NameNotFoundException {
        mResources = Utilities.ATLEAST_NOUGAT ? resources : ResourceUtilsKt.setResSdk(resources, Build.VERSION_CODES.N);
        mPackageName = packageName;
        mComponentName = componentName;
        mPackageInfo = context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
        mResId = resId;

        try {
            parseShortcuts(resId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void parseShortcuts(int resId) throws IOException, XmlPullParserException {
        XmlPullParser parser = mResources.getXml(resId);
        parser.next();
        parser.next();
        parser.require(XmlPullParser.START_TAG, null, TAG_SHORTCUTS);
        while (parser.next() != XmlPullParser.END_TAG) {
            parseShortcut(parser);
        }
    }

    private void parseShortcut(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, null, TAG_SHORTCUT);
        String id = getAttribute(parser, ATTRIBUTE_SHORTCUT_ID);
        CharSequence shortLabel = getCharSequence(parser, ATTRIBUTE_SHORTCUT_SHORT_LABEL);
        CharSequence longLabel = getCharSequence(parser, ATTRIBUTE_SHORTCUT_LONG_LABEL);
        CharSequence disabledMessage = getCharSequence(parser, ATTRIBUTE_SHORTCUT_DISABLED_MESSAGE);
        Drawable icon = mResources.getDrawable(getResourceAttribute(parser, ATTRIBUTE_SHORTCUT_ICON));
        Intent activity = null;
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    if (TAG_INTENT.equals(parser.getName())) {
                        Intent intent = parseIntent(parser);
                        if (activity == null) {
                            activity = intent;
                        }
                        depth--;
                    }
                    break;
            }
        }
        if (activity == null) return;
        if (id == null) {
            id = activity.getComponent().toString() + "_shortcut" + mShortcutsList.size();
        }
        if (shortLabel == null && longLabel == null) {
            shortLabel = "null";
        }
        if (isComponentExported(activity.getComponent())) {
            mShortcutsList.add(new ShortcutInfoCompat(mPackageName, id, shortLabel, longLabel, mComponentName, activity, Utilities.myUserHandle(), 0, true, disabledMessage, icon));
        }
    }

    private boolean isComponentExported(ComponentName componentName) {
        ActivityInfo[] activities = mPackageInfo.activities;
        for (ActivityInfo activity : activities) {
            if (componentName.getClassName().equals(activity.name)) {
                return activity.exported;
            }
        }
        return false;
    }

    private Intent parseIntent(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, null, TAG_INTENT);
        //readAllAttribute(parser);
        String action = getAttribute(parser, ATTRIBUTE_ACTION);
        String data = getAttribute(parser, ATTRIBUTE_DATA);
        String targetClass = getAttribute(parser, ATTRIBUTE_TARGET_CLASS);
        String targetPackage = getAttribute(parser, ATTRIBUTE_TARGET_PACKAGE);
        ComponentName componentName;
        if (targetClass == null || targetPackage == null) {
            componentName = mComponentName;
        } else {
            componentName = new ComponentName(targetPackage, targetClass);
        }
        Intent intent = new Intent();
        intent.setComponent(componentName);
        if (action != null) {
            intent.setAction(action);
        } else {
            intent.setAction(Intent.ACTION_MAIN);
        }
        if (data != null) {
            intent.setData(Uri.parse(data));
        }
        skip(parser);
        return intent;
    }

    private void readAllAttribute(XmlPullParser parser) {
        int count = parser.getAttributeCount();
        for (int i = 0; i < count; i++) {
            String name = parser.getAttributeName(i);
            String value = parser.getAttributeValue(i);
            Log.d(TAG, name + "=" + "\"" + value + "\"");
        }
    }

    private String getAttribute(XmlPullParser parser, String attr) {
        return parser.getAttributeValue(NAMESPACE_ANDROID, attr);
    }

    private CharSequence getCharSequence(XmlPullParser parser, String attr) {
        int resId = getResourceAttribute(parser, attr);
        if (resId == 0) return null;
        if (resId == -1) return getAttribute(parser, attr);
        return mResources.getString(resId);
    }

    private int getResourceAttribute(XmlPullParser parser, String attr) {
        String value = getAttribute(parser, attr);
        if (value == null) return 0;
        if (!value.startsWith("@")) return -1;
        return Integer.parseInt(value.substring(1));
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

    ArrayList<ShortcutInfoCompat> getShortcutsList() {
        return mShortcutsList;
    }

    int getResId() {
        return mResId;
    }
}
