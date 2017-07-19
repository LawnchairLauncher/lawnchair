package ch.deletescape.lawnchair.shortcuts.backport;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;

import java.util.Collections;
import java.util.List;

import ch.deletescape.lawnchair.shortcuts.DeepShortcutManager;
import ch.deletescape.lawnchair.shortcuts.ShortcutInfoCompat;
import ch.deletescape.lawnchair.shortcuts.ShortcutKey;

public class DeepShortcutManagerBackport extends DeepShortcutManager {

    private final Context mContext;

    public DeepShortcutManagerBackport(Context context) {
        mContext = context;
    }

    @Override
    public boolean wasLastCallSuccess() {
        return true;
    }

    @Override
    public void onShortcutsChanged(List list) {

    }

    @Override
    public List<ShortcutInfoCompat> queryForFullDetails(String str, List<String> list, UserHandle userHandle) {
        return query(11, str, null, list, userHandle);
    }

    @Override
    public List<ShortcutInfoCompat> queryForShortcutsContainer(ComponentName componentName, List<String> list, UserHandle userHandle) {
        return query(9, componentName.getPackageName(), componentName, list, userHandle);
    }

    @Override
    public void unpinShortcut(ShortcutKey shortcutKey) {
        // Do nothing
    }

    @Override
    public void pinShortcut(ShortcutKey shortcutKey) {
        // Do nothing
    }

    @Override
    public void startShortcut(String packageName, String shortcutId, Rect sourceBounds, Bundle startActivityOptions, UserHandle user) {

    }

    @Override
    public Drawable getShortcutIconDrawable(ShortcutInfoCompat shortcutInfoCompat, int i) {
        return null;
    }

    @Override
    protected List<String> extractIds(List<ShortcutInfoCompat> list) {
        return Collections.EMPTY_LIST;
    }

    @Override
    protected List<ShortcutInfoCompat> query(int flags, String packageName, ComponentName componentName, List<String> list, UserHandle userHandle) {
        return Collections.EMPTY_LIST;
    }

    @Override
    public boolean hasHostPermission() {
        return false;
    }
}
