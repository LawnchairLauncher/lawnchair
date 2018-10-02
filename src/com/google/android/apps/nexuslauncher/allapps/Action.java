package com.google.android.apps.nexuslauncher.allapps;

import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;

public class Action {
    public final String badgePackage;
    public final CharSequence contentDescription;
    public final long expirationTimeMillis;
    public final String id;
    public boolean isEnabled = false;
    public final CharSequence openingPackageDescription;
    public final long position;
    public final String publisherPackage;
    public final ShortcutInfoCompat shortcut;
    public final String shortcutId;
    public final ShortcutInfo shortcutInfo;

    public Action(String str, String str2, long j, String str3, String str4, CharSequence charSequence, ShortcutInfoCompat shortcutInfoCompat, ShortcutInfo shortcutInfo, long j2) {
        this.id = str;
        this.shortcutId = str2;
        this.expirationTimeMillis = j;
        this.publisherPackage = str3;
        this.badgePackage = str4;
        this.openingPackageDescription = charSequence;
        this.shortcut = shortcutInfoCompat;
        this.shortcutInfo = shortcutInfo;
        this.position = j2;
        this.contentDescription = shortcutInfo.contentDescription;
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("{");
        stringBuilder.append(this.id);
        stringBuilder.append(",");
        stringBuilder.append(this.shortcut.getShortLabel());
        stringBuilder.append(",");
        stringBuilder.append(this.position);
        stringBuilder.append("}");
        return stringBuilder.toString();
    }
}
