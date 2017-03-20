package ch.deletescape.lawnchair;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.util.Pair;

import java.util.HashSet;
import java.util.Set;

public class HideDropTarget extends ButtonDropTarget {

    public HideDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HideDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // Get the hover color
        mHoverColor = getResources().getColor(R.color.uninstall_target_hover_tint);

        setDrawable(R.drawable.ic_remove_launcher);
    }

    @Override
    protected boolean supportsDrop(DragSource source, ItemInfo info) {
        return true;
    }

    /**
     * @return the component name and flags if {@param info} is an AppInfo or an app shortcut.
     */
    private static Pair<ComponentName, Integer> getAppInfoFlags(Object item) {
        if (item instanceof AppInfo) {
            AppInfo info = (AppInfo) item;
            return Pair.create(info.componentName, info.flags);
        } else if (item instanceof ShortcutInfo) {
            ShortcutInfo info = (ShortcutInfo) item;
            ComponentName component = info.getTargetComponent();
            if (info.itemType == LauncherSettings.BaseLauncherColumns.ITEM_TYPE_APPLICATION
                    && component != null) {
                return Pair.create(component, info.flags);
            }
        }
        return null;
    }

    @Override
    void completeDrop(final DragObject d) {
        addToHideList(mLauncher, d.dragInfo);
    }

    public static boolean addToHideList(Launcher launcher, ItemInfo info) {
        Pair<ComponentName, Integer> componentInfo = getAppInfoFlags(info);
        ComponentName cn = componentInfo.first;
        SharedPreferences prefs = Utilities.getPrefs(launcher.getApplicationContext());
        Set<String> hiddenApps = prefs.getStringSet("pref_hiddenApps", null);
        if(hiddenApps == null){
            hiddenApps = new HashSet<>();
        }
        hiddenApps.add(cn.flattenToString());
        prefs.edit().putStringSet("pref_hiddenApps",hiddenApps).apply();
        return true;
    }
}
