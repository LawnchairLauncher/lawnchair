package com.android.launcher3.model;

import android.content.Context;

import com.android.launcher3.AppInfo;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.compat.UserManagerCompat;
import java.text.Collator;
import java.util.Comparator;
import java.util.HashMap;

/**
 * Class to manage access to an app name comparator.
 * <p>
 * Used to sort application name in all apps view and widget tray view.
 */
public class AppNameComparator {
    private final UserManagerCompat mUserManager;
    private final Collator mCollator;
    private final Comparator<ItemInfo> mAppInfoComparator;
    private final Comparator<String> mSectionNameComparator;
    private HashMap<UserHandleCompat, Long> mUserSerialCache = new HashMap<>();

    public AppNameComparator(Context context) {
        mCollator = Collator.getInstance();
        mUserManager = UserManagerCompat.getInstance(context);
        mAppInfoComparator = new Comparator<ItemInfo>() {

            public final int compare(ItemInfo a, ItemInfo b) {
                // Order by the title in the current locale
                int result = compareTitles(a.title.toString(), b.title.toString());
                if (result == 0 && a instanceof AppInfo && b instanceof AppInfo) {
                    AppInfo aAppInfo = (AppInfo) a;
                    AppInfo bAppInfo = (AppInfo) b;
                    // If two apps have the same title, then order by the component name
                    result = aAppInfo.componentName.compareTo(bAppInfo.componentName);
                    if (result == 0) {
                        // If the two apps are the same component, then prioritize by the order that
                        // the app user was created (prioritizing the main user's apps)
                        if (UserHandleCompat.myUserHandle().equals(a.user)) {
                            return -1;
                        } else {
                            Long aUserSerial = getAndCacheUserSerial(a.user);
                            Long bUserSerial = getAndCacheUserSerial(b.user);
                            return aUserSerial.compareTo(bUserSerial);
                        }
                    }
                }
                return result;
            }
        };
        mSectionNameComparator = new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return compareTitles(o1, o2);
            }
        };
    }

    /**
     * Returns a locale-aware comparator that will alphabetically order a list of applications.
     */
    public Comparator<ItemInfo> getAppInfoComparator() {
        // Clear the user serial cache so that we get serials as needed in the comparator
        mUserSerialCache.clear();
        return mAppInfoComparator;
    }

    /**
     * Returns a locale-aware comparator that will alphabetically order a list of section names.
     */
    public Comparator<String> getSectionNameComparator() {
        return mSectionNameComparator;
    }

    /**
     * Compares two titles with the same return value semantics as Comparator.
     */
    private int compareTitles(String titleA, String titleB) {
        // Ensure that we de-prioritize any titles that don't start with a linguistic letter or digit
        boolean aStartsWithLetter = Character.isLetterOrDigit(titleA.codePointAt(0));
        boolean bStartsWithLetter = Character.isLetterOrDigit(titleB.codePointAt(0));
        if (aStartsWithLetter && !bStartsWithLetter) {
            return -1;
        } else if (!aStartsWithLetter && bStartsWithLetter) {
            return 1;
        }

        // Order by the title in the current locale
        return mCollator.compare(titleA, titleB);
    }

    /**
     * Returns the user serial for this user, using a cached serial if possible.
     */
    private Long getAndCacheUserSerial(UserHandleCompat user) {
        Long userSerial = mUserSerialCache.get(user);
        if (userSerial == null) {
            userSerial = mUserManager.getSerialNumberForUser(user);
            mUserSerialCache.put(user, userSerial);
        }
        return userSerial;
    }
}
