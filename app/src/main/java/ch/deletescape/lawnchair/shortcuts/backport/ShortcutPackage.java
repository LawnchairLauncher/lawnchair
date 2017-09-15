package ch.deletescape.lawnchair.shortcuts.backport;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import ch.deletescape.lawnchair.shortcuts.ShortcutInfoCompat;

public class ShortcutPackage {

    private final Context mContext;
    private final String mPackageName;
    private final Resources mResources;

    private final Map<ComponentName, ShortcutParser> mShortcutsMap = new HashMap<>();
    private final ArrayList<ShortcutInfoCompat> mShortcutsList = new ArrayList<>();

    public ShortcutPackage(Context context, String packageName) throws Exception {
        mContext = context;
        mPackageName = packageName;

        mResources = mContext.createPackageContext(mPackageName, Context.CONTEXT_IGNORE_SECURITY)
                .getResources();

        Map<ComponentName, Integer> resMap = new ShortcutPackageParser(context, packageName).getShortcutsMap();
        for (Map.Entry<ComponentName, Integer> entry : resMap.entrySet()) {
            ShortcutParser shortcutParser = new ShortcutParser(mContext, mResources, mPackageName, entry.getKey(), entry.getValue());
            mShortcutsMap.put(entry.getKey(), shortcutParser);
            mShortcutsList.addAll(shortcutParser.getShortcutsList());
        }
    }

    Map<ComponentName, ShortcutParser> getShortcutsMap() {
        return mShortcutsMap;
    }

    ArrayList<ShortcutInfoCompat> getAllShortcuts() {
        return mShortcutsList;
    }
}
