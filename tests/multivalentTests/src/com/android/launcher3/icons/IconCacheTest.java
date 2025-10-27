/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.icons;

import static android.os.Process.myUserHandle;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.launcher3.icons.IconCache.EXTRA_SHORTCUT_BADGE_OVERRIDE_PACKAGE;
import static com.android.launcher3.icons.IconCacheUpdateHandlerTestKt.waitForUpdateHandlerToFinish;
import static com.android.launcher3.icons.cache.CacheLookupFlag.DEFAULT_LOOKUP_FLAG;
import static com.android.launcher3.model.data.AppInfo.makeLaunchIntent;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY;
import static com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY2;
import static com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE;
import static com.android.launcher3.util.TestUtil.runOnExecutorSync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutInfo.Builder;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.drawable.Icon;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.icons.cache.CachingLogic;
import com.android.launcher3.icons.cache.IconCacheUpdateHandler;
import com.android.launcher3.icons.cache.LauncherActivityCachingLogic;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.settings.SettingsActivity;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.ApplicationInfoWrapper;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.SandboxApplication;

import com.google.common.truth.Truth;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class IconCacheTest {

    @Rule public SandboxApplication mContext = new SandboxApplication();

    private IconCache mIconCache;

    private ComponentName mMyComponent;

    @Before
    public void setup() {
        mMyComponent = new ComponentName(mContext, SettingsActivity.class);

        // In memory icon cache
        mIconCache = LauncherAppState.getInstance(mContext).getIconCache();
    }

    @After
    public void tearDown() {
        mIconCache.close();
    }

    @Test
    public void getShortcutInfoBadge_nullComponent_overrideAllowed() throws Exception {
        String overridePackage = "com.android.settings";
        ItemInfoWithIcon item = getBadgingInfo(mContext, null, overridePackage);
        assertTrue(item instanceof PackageItemInfo);
        assertEquals(((PackageItemInfo) item).packageName, overridePackage);
    }

    @Test
    public void getShortcutInfoBadge_withComponent_overrideAllowed() throws Exception {
        String overridePackage = "com.android.settings";
        ItemInfoWithIcon item = getBadgingInfo(mContext, mMyComponent, overridePackage);
        assertTrue(item instanceof PackageItemInfo);
        assertEquals(((PackageItemInfo) item).packageName, overridePackage);
    }

    @Test
    public void getShortcutInfoBadge_nullComponent() throws Exception {
        ItemInfoWithIcon item = getBadgingInfo(mContext, null, null);
        assertTrue(item instanceof PackageItemInfo);
        assertEquals(((PackageItemInfo) item).packageName, mContext.getPackageName());
    }

    @Test
    public void getShortcutInfoBadge_withComponent() throws Exception {
        ItemInfoWithIcon item = getBadgingInfo(mContext, mMyComponent, null);
        assertTrue(item instanceof AppInfo);
        assertEquals(((AppInfo) item).componentName, mMyComponent);
    }

    @Test
    public void getShortcutInfoBadge_overrideNotAllowed() throws Exception {
        String overridePackage = "com.android.settings";
        String otherPackage = mContext.getPackageName() + ".does.not.exist";
        Context otherContext = new ContextWrapper(mContext) {
            @Override
            public String getPackageName() {
                return otherPackage;
            }
        };
        ItemInfoWithIcon item = getBadgingInfo(otherContext, null, overridePackage);
        assertTrue(item instanceof PackageItemInfo);
        // Badge is set to the original package, and not the override package
        assertEquals(((PackageItemInfo) item).packageName, otherPackage);
    }

    @Test
    public void launcherActivityInfo_cached_in_memory() {
        ComponentName cn = new ComponentName(TEST_PACKAGE, TEST_ACTIVITY);
        UserHandle user = myUserHandle();
        ComponentKey cacheKey = new ComponentKey(cn, user);

        LauncherActivityInfo lai = mContext.getSystemService(LauncherApps.class)
                .resolveActivity(makeLaunchIntent(cn), user);
        assertNotNull(lai);

        WorkspaceItemInfo info = new WorkspaceItemInfo();
        info.intent = makeLaunchIntent(cn);
        runOnExecutorSync(MODEL_EXECUTOR,
                () -> mIconCache.getTitleAndIcon(info, lai, DEFAULT_LOOKUP_FLAG));
        assertNotNull(info.bitmap);
        assertFalse(info.bitmap.isLowRes());

        // Verify that icon is in memory cache
        runOnExecutorSync(MODEL_EXECUTOR,
                () -> assertNotNull(mIconCache.getInMemoryEntryLocked(cacheKey)));

        // Schedule async update and wait for it to complete
        Set<PackageUserKey> updates =
                executeIconUpdate(lai, LauncherActivityCachingLogic.INSTANCE);

        // Verify that the icon was not updated and is still in memory cache
        Truth.assertThat(updates).isEmpty();
        runOnExecutorSync(MODEL_EXECUTOR,
                () -> assertNotNull(mIconCache.getInMemoryEntryLocked(cacheKey)));
    }

    @Test
    public void shortcutInfo_not_cached_in_memory() {
        CacheableShortcutInfo si = mockShortcutInfo(0);
        ShortcutKey cacheKey = ShortcutKey.fromInfo(si.getShortcutInfo());

        WorkspaceItemInfo info = new WorkspaceItemInfo();
        runOnExecutorSync(MODEL_EXECUTOR, () -> mIconCache.getShortcutIcon(info, si));
        assertNotNull(info.bitmap);
        assertFalse(info.bitmap.isLowRes());

        // Verify that icon is in memory cache
        runOnExecutorSync(MODEL_EXECUTOR,
                () -> assertNull(mIconCache.getInMemoryEntryLocked(cacheKey)));

        Set<PackageUserKey> updates =
                executeIconUpdate(si, CacheableShortcutCachingLogic.INSTANCE);
        // Verify that the icon was not updated and is still in memory cache
        Truth.assertThat(updates).isEmpty();
        runOnExecutorSync(MODEL_EXECUTOR,
                () -> assertNull(mIconCache.getInMemoryEntryLocked(cacheKey)));

        // Now update the shortcut with a newer version
        updates = executeIconUpdate(
                mockShortcutInfo(System.currentTimeMillis() + 2000),
                CacheableShortcutCachingLogic.INSTANCE);

        // Verify that icon was updated but it is still not in mem-cache
        Truth.assertThat(updates).containsExactly(
                new PackageUserKey(cacheKey.getPackageName(), cacheKey.user));
        runOnExecutorSync(MODEL_EXECUTOR,
                () -> assertNull(mIconCache.getInMemoryEntryLocked(cacheKey)));
    }

    @Test
    public void item_kept_in_db_if_nothing_changes() {
        ComponentName cn = new ComponentName(TEST_PACKAGE, TEST_ACTIVITY);
        UserHandle user = myUserHandle();

        LauncherActivityInfo lai = mContext.getSystemService(LauncherApps.class)
                .resolveActivity(makeLaunchIntent(cn), user);
        assertNotNull(lai);

        // Since this is a new update, there should not be any update
        Truth.assertThat(executeIconUpdate(lai, LauncherActivityCachingLogic.INSTANCE)).isEmpty();
        assertTrue(mIconCache.isItemInDb(new ComponentKey(cn, user)));

        // Another update should not cause any changes
        Truth.assertThat(executeIconUpdate(lai, LauncherActivityCachingLogic.INSTANCE)).isEmpty();
        assertTrue(mIconCache.isItemInDb(new ComponentKey(cn, user)));
    }

    @Test
    public void item_updated_in_db_if_appInfo_changes() {
        ComponentName cn = new ComponentName(TEST_PACKAGE, TEST_ACTIVITY);
        UserHandle user = myUserHandle();

        LauncherActivityInfo lai = mContext.getSystemService(LauncherApps.class)
                .resolveActivity(makeLaunchIntent(cn), user);
        assertNotNull(lai);

        // Since this is a new update, there should not be any update
        Truth.assertThat(executeIconUpdate(lai, LauncherActivityCachingLogic.INSTANCE)).isEmpty();
        assertTrue(mIconCache.isItemInDb(new ComponentKey(cn, user)));

        // Another update should trigger an update
        lai.getApplicationInfo().sourceDir = "some-random-source-dir";
        Truth.assertThat(executeIconUpdate(lai, LauncherActivityCachingLogic.INSTANCE))
                .containsExactly(new PackageUserKey(TEST_PACKAGE, user));
        assertTrue(mIconCache.isItemInDb(new ComponentKey(cn, user)));
    }

    @Test
    public void item_removed_in_db_if_item_removed() {
        ComponentName cn = new ComponentName(TEST_PACKAGE, TEST_ACTIVITY);
        UserHandle user = myUserHandle();

        LauncherActivityInfo lai = mContext.getSystemService(LauncherApps.class)
                .resolveActivity(makeLaunchIntent(cn), user);
        assertNotNull(lai);

        // Since this is a new update, there should not be any update
        Truth.assertThat(executeIconUpdate(lai, LauncherActivityCachingLogic.INSTANCE)).isEmpty();
        assertTrue(mIconCache.isItemInDb(new ComponentKey(cn, user)));

        // Another update should trigger an update
        ComponentName cn2 = new ComponentName(TEST_PACKAGE, TEST_ACTIVITY2);
        LauncherActivityInfo lai2 = mContext.getSystemService(LauncherApps.class)
                .resolveActivity(makeLaunchIntent(cn2), user);

        Truth.assertThat(executeIconUpdate(lai2, LauncherActivityCachingLogic.INSTANCE)).isEmpty();
        assertFalse(mIconCache.isItemInDb(new ComponentKey(cn, user)));
        assertTrue(mIconCache.isItemInDb(new ComponentKey(cn2, user)));
    }

    /**
     * Executes the icon update for the provided entry and returns the updated packages
     */
    private <T> Set<PackageUserKey> executeIconUpdate(T object, CachingLogic<T> cachingLogic) {
        HashSet<PackageUserKey> updates = new HashSet<>();

        runOnExecutorSync(MODEL_EXECUTOR, () -> {
            IconCacheUpdateHandler updateHandler = mIconCache.getUpdateHandler();
            updateHandler.updateIcons(
                    Collections.singletonList(object),
                    cachingLogic,
                    (a, b) -> a.forEach(p -> updates.add(new PackageUserKey(p, b))));
            updateHandler.finish();
        });
        waitForUpdateHandlerToFinish(mIconCache);
        return updates;
    }

    private CacheableShortcutInfo mockShortcutInfo(long updateTime) {
        ShortcutInfo info = new ShortcutInfo.Builder(
                        getInstrumentation().getContext(), "test-shortcut")
                .setIntent(new Intent(Intent.ACTION_VIEW))
                .setShortLabel("Test")
                .setIcon(Icon.createWithBitmap(Bitmap.createBitmap(200, 200, Config.ARGB_8888)))
                .build();
        ShortcutInfo spied = spy(info);
        doReturn(updateTime).when(spied).getLastChangedTimestamp();
        return new CacheableShortcutInfo(spied,
                new ApplicationInfoWrapper(getInstrumentation().getContext().getApplicationInfo()));
    }

    private ItemInfoWithIcon getBadgingInfo(Context context,
            @Nullable ComponentName cn, @Nullable String badgeOverride) throws Exception {
        Builder builder = new Builder(context, "test-shortcut")
                .setIntent(new Intent(Intent.ACTION_VIEW))
                .setShortLabel("Test");
        if (cn != null) {
            builder.setActivity(cn);
        }
        if (!TextUtils.isEmpty(badgeOverride)) {
            PersistableBundle extras = new PersistableBundle();
            extras.putString(EXTRA_SHORTCUT_BADGE_OVERRIDE_PACKAGE, badgeOverride);
            builder.setExtras(extras);
        }
        ShortcutInfo info = builder.build();
        return MODEL_EXECUTOR.submit(() -> mIconCache.getShortcutInfoBadgeItem(info)).get();
    }
}
