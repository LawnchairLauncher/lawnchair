package com.android.launcher3.model;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.os.Process;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.WorkspaceItemInfo;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.util.PackageManagerHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.android.launcher3.LauncherSettings.Favorites.INTENT;
import static com.android.launcher3.LauncherSettings.Favorites.CELLX;
import static com.android.launcher3.LauncherSettings.Favorites.CELLY;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.LauncherSettings.Favorites.ICON;
import static com.android.launcher3.LauncherSettings.Favorites.ICON_PACKAGE;
import static com.android.launcher3.LauncherSettings.Favorites.ICON_RESOURCE;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
import static com.android.launcher3.LauncherSettings.Favorites.PROFILE_ID;
import static com.android.launcher3.LauncherSettings.Favorites.RESTORED;
import static com.android.launcher3.LauncherSettings.Favorites.SCREEN;
import static com.android.launcher3.LauncherSettings.Favorites.TITLE;
import static com.android.launcher3.LauncherSettings.Favorites._ID;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link LoaderCursor}
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class LoaderCursorTest {

    private LauncherAppState mMockApp;
    private IconCache mMockIconCache;

    private MatrixCursor mCursor;
    private InvariantDeviceProfile mIDP;
    private Context mContext;
    private LauncherAppsCompat mLauncherApps;

    private LoaderCursor mLoaderCursor;

    @Before
    public void setup() {
        mIDP = new InvariantDeviceProfile();
        mCursor = new MatrixCursor(new String[] {
                ICON, ICON_PACKAGE, ICON_RESOURCE, TITLE,
                _ID, CONTAINER, ITEM_TYPE, PROFILE_ID,
                SCREEN, CELLX, CELLY, RESTORED, INTENT
        });
        mContext = InstrumentationRegistry.getTargetContext();

        mMockApp = mock(LauncherAppState.class);
        mMockIconCache = mock(IconCache.class);
        when(mMockApp.getIconCache()).thenReturn(mMockIconCache);
        when(mMockApp.getInvariantDeviceProfile()).thenReturn(mIDP);
        when(mMockApp.getContext()).thenReturn(mContext);
        mLauncherApps = LauncherAppsCompat.getInstance(mContext);

        mLoaderCursor = new LoaderCursor(mCursor, mMockApp);
        mLoaderCursor.allUsers.put(0, Process.myUserHandle());
    }

    private void initCursor(int itemType, String title) {
        mCursor.newRow()
                .add(_ID, 1)
                .add(PROFILE_ID, 0)
                .add(ITEM_TYPE, itemType)
                .add(TITLE, title)
                .add(CONTAINER, CONTAINER_DESKTOP);
    }

    @Test
    public void getAppShortcutInfo_dontAllowMissing_invalidComponent() {
        initCursor(ITEM_TYPE_APPLICATION, "");
        assertTrue(mLoaderCursor.moveToNext());
        ComponentName cn = new ComponentName(mContext.getPackageName(), "dummy-do");
        assertNull(mLoaderCursor.getAppShortcutInfo(
                new Intent().setComponent(cn), false /* allowMissingTarget */, true));
    }

    @Test
    public void getAppShortcutInfo_dontAllowMissing_validComponent() {
        initCursor(ITEM_TYPE_APPLICATION, "");
        assertTrue(mLoaderCursor.moveToNext());

        ComponentName cn = mLauncherApps.getActivityList(null, mLoaderCursor.user)
                .get(0).getComponentName();
        WorkspaceItemInfo info = mLoaderCursor.getAppShortcutInfo(
                new Intent().setComponent(cn), false /* allowMissingTarget */, true);
        assertNotNull(info);
        assertTrue(PackageManagerHelper.isLauncherAppTarget(info.intent));
    }

    @Test
    public void getAppShortcutInfo_allowMissing_invalidComponent() {
        initCursor(ITEM_TYPE_APPLICATION, "");
        assertTrue(mLoaderCursor.moveToNext());

        ComponentName cn = new ComponentName(mContext.getPackageName(), "dummy-do");
        WorkspaceItemInfo info = mLoaderCursor.getAppShortcutInfo(
                new Intent().setComponent(cn), true  /* allowMissingTarget */, true);
        assertNotNull(info);
        assertTrue(PackageManagerHelper.isLauncherAppTarget(info.intent));
    }

    @Test
    public void loadSimpleShortcut() {
        initCursor(ITEM_TYPE_SHORTCUT, "my-shortcut");
        assertTrue(mLoaderCursor.moveToNext());

        Bitmap icon = Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8);
        when(mMockIconCache.getDefaultIcon(eq(mLoaderCursor.user)))
                .thenReturn(BitmapInfo.fromBitmap(icon));
        WorkspaceItemInfo info = mLoaderCursor.loadSimpleWorkspaceItem();
        assertEquals(icon, info.iconBitmap);
        assertEquals("my-shortcut", info.title);
        assertEquals(ITEM_TYPE_SHORTCUT, info.itemType);
    }

    @Test
    public void checkItemPlacement_outsideBounds() {
        mIDP.numRows = 4;
        mIDP.numColumns = 4;
        mIDP.numHotseatIcons = 3;

        // Item outside screen bounds are not placed
        assertFalse(mLoaderCursor.checkItemPlacement(
                newItemInfo(4, 4, 1, 1, CONTAINER_DESKTOP, 1)));
    }

    @Test
    public void checkItemPlacement_overlappingItems() {
        mIDP.numRows = 4;
        mIDP.numColumns = 4;
        mIDP.numHotseatIcons = 3;

        // Overlapping items are not placed
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
        mIDP.numHotseatIcons = 3;

        // Hotseat items are only placed based on screenId
        assertTrue(mLoaderCursor.checkItemPlacement(
                newItemInfo(3, 3, 1, 1, CONTAINER_HOTSEAT, 1)));
        assertTrue(mLoaderCursor.checkItemPlacement(
                newItemInfo(3, 3, 1, 1, CONTAINER_HOTSEAT, 2)));

        assertFalse(mLoaderCursor.checkItemPlacement(
                newItemInfo(3, 3, 1, 1, CONTAINER_HOTSEAT, 3)));
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
