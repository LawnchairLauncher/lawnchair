/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.launcher3.model;

import static android.graphics.BitmapFactory.decodeByteArray;
import static android.platform.test.flag.junit.SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.android.launcher3.LauncherPrefs.IS_FIRST_LOAD_AFTER_RESTORE;
import static com.android.launcher3.LauncherSettings.Favorites.APPWIDGET_ID;
import static com.android.launcher3.LauncherSettings.Favorites.APPWIDGET_PROVIDER;
import static com.android.launcher3.LauncherSettings.Favorites.APPWIDGET_SOURCE;
import static com.android.launcher3.LauncherSettings.Favorites.CELLX;
import static com.android.launcher3.LauncherSettings.Favorites.CELLY;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.LauncherSettings.Favorites.ICON;
import static com.android.launcher3.LauncherSettings.Favorites.INTENT;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
import static com.android.launcher3.LauncherSettings.Favorites.OPTIONS;
import static com.android.launcher3.LauncherSettings.Favorites.PROFILE_ID;
import static com.android.launcher3.LauncherSettings.Favorites.RANK;
import static com.android.launcher3.LauncherSettings.Favorites.RESTORED;
import static com.android.launcher3.LauncherSettings.Favorites.SCREEN;
import static com.android.launcher3.LauncherSettings.Favorites.SPANX;
import static com.android.launcher3.LauncherSettings.Favorites.SPANY;
import static com.android.launcher3.LauncherSettings.Favorites.TITLE;
import static com.android.launcher3.LauncherSettings.Favorites._ID;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_ARCHIVED;
import static com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Intent;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.os.Process;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.Flags;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.SandboxApplication;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link LoaderCursor}
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class LoaderCursorTest {

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule(DEVICE_DEFAULT);

    @Rule
    public final SandboxApplication mContext = new SandboxApplication();

    private LauncherAppState mApp;
    private LauncherPrefs mPrefs;

    private MatrixCursor mCursor;
    private InvariantDeviceProfile mIDP;

    private LoaderCursor mLoaderCursor;

    private static byte[] sTestBlob = new byte[] {
            -119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 1, 0, 0, 0, 1,
            8, 4, 0, 0, 0, -75, 28, 12, 2, 0, 0, 0, 11, 73, 68, 65, 84, 120, -38, 99, 100, 96, 0,
            0, 0, 6, 0, 2, 48, -127, -48, 47, 0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126
    };

    @Before
    public void setup() {
        mPrefs = LauncherPrefs.get(mContext);
        mIDP = InvariantDeviceProfile.INSTANCE.get(mContext);
        mApp = LauncherAppState.getInstance(mContext);

        mCursor = new MatrixCursor(new String[] {
                ICON, TITLE, _ID, CONTAINER, ITEM_TYPE,
                PROFILE_ID, SCREEN, CELLX, CELLY, RESTORED,
                INTENT, APPWIDGET_ID, APPWIDGET_PROVIDER,
                SPANX, SPANY, RANK, OPTIONS, APPWIDGET_SOURCE
        });

        UserManagerState ums = new UserManagerState();
        mLoaderCursor = mContext.getAppComponent().getLoaderCursorFactory()
                .createLoaderCursor(mCursor, ums, null);
        ums.allUsers.put(0, Process.myUserHandle());
    }

    @After
    public void tearDown() {
        mPrefs.putSync(IS_FIRST_LOAD_AFTER_RESTORE.to(false));
        mCursor.close();
    }

    private void initCursor(int itemType, String title) {
        mCursor.newRow()
                .add(_ID, 1)
                .add(PROFILE_ID, 0)
                .add(ITEM_TYPE, itemType)
                .add(TITLE, title)
                .add(CONTAINER, CONTAINER_DESKTOP)
                .add(ICON, sTestBlob);
    }

    @Test
    public void getAppShortcutInfo_dontAllowMissing_invalidComponent() {
        initCursor(ITEM_TYPE_APPLICATION, "");
        assertTrue(mLoaderCursor.moveToNext());
        ComponentName cn = new ComponentName(mContext.getPackageName(), "placeholder-do");
        assertNull(mLoaderCursor.getAppShortcutInfo(
                new Intent().setComponent(cn), false /* allowMissingTarget */, true));
    }

    @Test
    public void getAppShortcutInfo_dontAllowMissing_validComponent() throws Exception {
        ComponentName cn = new ComponentName(getContext(), TEST_ACTIVITY);
        initCursor(ITEM_TYPE_APPLICATION, "");
        assertTrue(mLoaderCursor.moveToNext());

        WorkspaceItemInfo info = Executors.MODEL_EXECUTOR.submit(() ->
                mLoaderCursor.getAppShortcutInfo(
                        new Intent().setComponent(cn), false  /* allowMissingTarget */, true))
                .get();
        assertNotNull(info);
        assertTrue(PackageManagerHelper.isLauncherAppTarget(info.getIntent()));
    }

    @Test
    public void getAppShortcutInfo_allowMissing_invalidComponent() throws Exception {
        initCursor(ITEM_TYPE_APPLICATION, "");
        assertTrue(mLoaderCursor.moveToNext());

        ComponentName cn = new ComponentName(mContext.getPackageName(), "placeholder-do");
        WorkspaceItemInfo info = Executors.MODEL_EXECUTOR.submit(() ->
                mLoaderCursor.getAppShortcutInfo(
                        new Intent().setComponent(cn), true  /* allowMissingTarget */, true))
                .get();
        assertNotNull(info);
        assertTrue(PackageManagerHelper.isLauncherAppTarget(info.getIntent()));
    }

    @Test
    public void loadSimpleShortcut() {
        mCursor.newRow()
                .add(_ID, 1)
                .add(PROFILE_ID, 0)
                .add(ITEM_TYPE, ITEM_TYPE_DEEP_SHORTCUT)
                .add(TITLE, "my-shortcut")
                .add(CONTAINER, CONTAINER_DESKTOP);
        assertTrue(mLoaderCursor.moveToNext());

        WorkspaceItemInfo info = mLoaderCursor.loadSimpleWorkspaceItem();
        assertTrue(mApp.getIconCache().isDefaultIcon(info.bitmap, info.user));
        assertEquals("my-shortcut", info.title);
        assertEquals(ITEM_TYPE_DEEP_SHORTCUT, info.itemType);
    }

    @Test
    public void checkItemPlacement_outsideBounds() {
        mIDP.numRows = 4;
        mIDP.numColumns = 4;
        mIDP.numDatabaseHotseatIcons = 3;

        // Item outside screen bounds are not placed
        assertFalse(mLoaderCursor.checkItemPlacement(
                newItemInfo(4, 4, 1, 1, CONTAINER_DESKTOP, 1)));
    }

    @Test
    public void checkItemPlacement_overlappingItems() {
        mIDP.numRows = 4;
        mIDP.numColumns = 4;
        mIDP.numDatabaseHotseatIcons = 3;

        // Overlapping mItems are not placed
        assertTrue(mLoaderCursor.checkItemPlacement(
                newItemInfo(0, 0, 1, 1, CONTAINER_DESKTOP, 1)));
        assertFalse(mLoaderCursor.checkItemPlacement(
                newItemInfo(0, 0, 1, 1, CONTAINER_DESKTOP, 1)));

        assertTrue(mLoaderCursor.checkItemPlacement(
                newItemInfo(0, 0, 1, 1, CONTAINER_DESKTOP, 2)));
        assertFalse(mLoaderCursor.checkItemPlacement(
                newItemInfo(0, 0, 1, 1, CONTAINER_DESKTOP, 2)));

        assertTrue(mLoaderCursor.checkItemPlacement(
                newItemInfo(1, 1, 1, 1, CONTAINER_DESKTOP, 1)));
        assertTrue(mLoaderCursor.checkItemPlacement(
                newItemInfo(2, 2, 2, 2, CONTAINER_DESKTOP, 1)));

        assertFalse(mLoaderCursor.checkItemPlacement(
                newItemInfo(3, 2, 1, 2, CONTAINER_DESKTOP, 1)));
    }

    @Test
    public void checkItemPlacement_hotseat() {
        mIDP.numRows = 4;
        mIDP.numColumns = 4;
        mIDP.numDatabaseHotseatIcons = 3;

        // Hotseat mItems are only placed based on screenId
        assertTrue(mLoaderCursor.checkItemPlacement(
                newItemInfo(3, 3, 1, 1, CONTAINER_HOTSEAT, 1)));
        assertTrue(mLoaderCursor.checkItemPlacement(
                newItemInfo(3, 3, 1, 1, CONTAINER_HOTSEAT, 2)));

        assertFalse(mLoaderCursor.checkItemPlacement(
                newItemInfo(3, 3, 1, 1, CONTAINER_HOTSEAT, 3)));
    }

    @Test
    @EnableFlags(Flags.FLAG_RESTORE_ARCHIVED_APP_ICONS_FROM_DB)
    public void ifArchivedWithFlagAndRestore_whenloadWorkspaceTitleAndIcon_thenLoadIconFromDb() {
        // Given
        mPrefs.putSync(IS_FIRST_LOAD_AFTER_RESTORE.to(true));
        initCursor(ITEM_TYPE_APPLICATION, "title");
        assertTrue(mLoaderCursor.moveToNext());
        WorkspaceItemInfo itemInfo = new WorkspaceItemInfo();
        itemInfo.runtimeStatusFlags |= FLAG_ARCHIVED;
        Bitmap expectedBitmap = LauncherIcons.obtain(mContext)
                .createIconBitmap(decodeByteArray(sTestBlob, 0, sTestBlob.length))
                .icon;

        // When
        mLoaderCursor.loadWorkspaceTitleAndIcon(false, true, itemInfo);
        // Then
        assertThat(itemInfo.bitmap.icon).isNotNull();
        assertThat(itemInfo.bitmap.icon.sameAs(expectedBitmap)).isTrue();
    }

    @Test
    @EnableFlags(Flags.FLAG_RESTORE_ARCHIVED_APP_ICONS_FROM_DB)
    public void ifArchivedWithFlagAndNotRestore_whenloadWorkspaceTitleAndIcon_thenLoadIconFromDb() {
        // Given
        mPrefs.putSync(IS_FIRST_LOAD_AFTER_RESTORE.to(false));
        initCursor(ITEM_TYPE_APPLICATION, "title");
        assertTrue(mLoaderCursor.moveToNext());
        WorkspaceItemInfo itemInfo = new WorkspaceItemInfo();
        BitmapInfo original = itemInfo.bitmap;
        itemInfo.runtimeStatusFlags |= FLAG_ARCHIVED;

        // When
        mLoaderCursor.loadWorkspaceTitleAndIcon(false, true, itemInfo);
        // Then
        assertThat(itemInfo.bitmap).isEqualTo(original);
    }

    @Test
    @EnableFlags(Flags.FLAG_RESTORE_ARCHIVED_APP_ICONS_FROM_DB)
    public void ifArchivedWithFlag_whenLoadIconFromDb_thenLoadIconFromBlob() {
        // Given
        initCursor(ITEM_TYPE_APPLICATION, "title");
        assertTrue(mLoaderCursor.moveToNext());
        WorkspaceItemInfo itemInfo = new WorkspaceItemInfo();
        itemInfo.runtimeStatusFlags |= FLAG_ARCHIVED;
        // Then
        assertTrue(mLoaderCursor.loadIconFromDb(itemInfo));
    }

    @Test
    @DisableFlags(Flags.FLAG_RESTORE_ARCHIVED_APP_ICONS_FROM_DB)
    public void ifArchivedWithoutFlag_whenLoadWorkspaceTitleAndIcon_thenDoNotLoadFromDb() {
        // Given
        initCursor(ITEM_TYPE_APPLICATION, "title");
        assertTrue(mLoaderCursor.moveToNext());
        WorkspaceItemInfo itemInfo = new WorkspaceItemInfo();
        BitmapInfo original = itemInfo.bitmap;
        itemInfo.runtimeStatusFlags |= FLAG_ARCHIVED;
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("package", "class"));
        itemInfo.intent = intent;
        // When
        mLoaderCursor.loadWorkspaceTitleAndIcon(false, false, itemInfo);
        // Then
        assertThat(itemInfo.bitmap).isEqualTo(original);
    }

    @Test
    @DisableFlags(Flags.FLAG_RESTORE_ARCHIVED_APP_ICONS_FROM_DB)
    public void ifArchivedWithoutFlag_whenLoadIconFromDb_thenDoNotLoadFromBlob() {
        // Given
        initCursor(ITEM_TYPE_APPLICATION, "title");
        assertTrue(mLoaderCursor.moveToNext());
        WorkspaceItemInfo itemInfo = new WorkspaceItemInfo();
        itemInfo.runtimeStatusFlags |= FLAG_ARCHIVED;
        // Then
        assertFalse(mLoaderCursor.loadIconFromDb(itemInfo));
    }


    private ItemInfo newItemInfo(int cellX, int cellY, int spanX, int spanY,
            int container, int screenId) {
        ItemInfo info = new ItemInfo();
        info.cellX = cellX;
        info.cellY = cellY;
        info.spanX = spanX;
        info.spanY = spanY;
        info.container = container;
        info.screenId = screenId;
        return info;
    }
}
