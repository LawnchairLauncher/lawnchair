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

package com.android.launcher3.provider;

import static android.content.pm.LauncherApps.EXTRA_PIN_ITEM_REQUEST;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.launcher3.LauncherSettings.Favorites.INTENT;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.LauncherApps.PinItemRequest;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Process;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.R;
import com.android.launcher3.model.DatabaseHelper;
import com.android.launcher3.model.DbDowngradeHelper;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.settings.SettingsActivity;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.IOUtils;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.InputStream;

/**
 * Test for {@link LauncherDbUtils}.
 */
public class LauncherDbUtilsTest {

    private Context mContext;
    private ArgumentCaptor<ShortcutInfo> mInfoArgumentCaptor;
    private boolean mPinningAllowed = true;

    @Before
    public void setup() {
        PinItemRequest req = mock(PinItemRequest.class);
        doAnswer(i -> mPinningAllowed).when(req).accept();

        mInfoArgumentCaptor = ArgumentCaptor.forClass(ShortcutInfo.class);
        ShortcutManager sm = mock(ShortcutManager.class);
        when(sm.createShortcutResultIntent(mInfoArgumentCaptor.capture()))
                .thenReturn(new Intent().putExtra(EXTRA_PIN_ITEM_REQUEST, req));

        mContext = new ContextWrapper(getInstrumentation().getTargetContext()) {

            @Override
            public Object getSystemService(String name) {
                return SHORTCUT_SERVICE.equals(name) ? sm : super.getSystemService(name);
            }
        };
    }

    @Test
    public void migrateLegacyShortcuts_invalidIntent() throws Exception {
        SQLiteDatabase db = setupLegacyShortcut(null);
        assertEquals(1, getFavoriteDataCount(db));

        LauncherDbUtils.migrateLegacyShortcuts(mContext, db);

        assertEquals(0, getFavoriteDataCount(db));
    }

    @Test
    public void migrateLegacyShortcuts_protectedIntent() throws Exception {
        Intent intent = new Intent(Intent.ACTION_CALL)
                .setData(Uri.parse("tel:0000000000"));
        SQLiteDatabase db = setupLegacyShortcut(intent);
        assertEquals(1, getFavoriteDataCount(db));

        LauncherDbUtils.migrateLegacyShortcuts(mContext, db);

        assertEquals(0, getFavoriteDataCount(db));
    }

    @Test
    public void migrateLegacyShortcuts_pinningDisabled() throws Exception {
        mPinningAllowed = false;
        Intent intent = new Intent(mContext, SettingsActivity.class);
        SQLiteDatabase db = setupLegacyShortcut(intent);
        assertEquals(1, getFavoriteDataCount(db));

        LauncherDbUtils.migrateLegacyShortcuts(mContext, db);

        assertEquals(0, getFavoriteDataCount(db));
    }

    @Test
    public void migrateLegacyShortcuts_success() throws Exception {
        Intent intent = new Intent(mContext, SettingsActivity.class);
        SQLiteDatabase db = setupLegacyShortcut(intent);
        assertEquals(1, getFavoriteDataCount(db));

        LauncherDbUtils.migrateLegacyShortcuts(mContext, db);

        assertEquals(1, getFavoriteDataCount(db));
        ShortcutInfo info = mInfoArgumentCaptor.getValue();
        assertNotNull(info);
        try (Cursor c = db.query(Favorites.TABLE_NAME, null, null, null, null, null, null)) {
            c.moveToNext();
            assertEquals(Favorites.ITEM_TYPE_DEEP_SHORTCUT, c.getInt(c.getColumnIndex(ITEM_TYPE)));

            ShortcutKey key = ShortcutKey.fromIntent(
                    Intent.parseUri(c.getString(c.getColumnIndex(INTENT)), 0),
                    Process.myUserHandle());

            assertEquals(info.getId(), key.getId());
            assertEquals(info.getPackage(), key.getPackageName());
        }
    }

    private SQLiteDatabase setupLegacyShortcut(Intent intent) throws Exception {
        SQLiteDatabase db = new MyDatabaseHelper().getWritableDatabase();
        try (InputStream in = mContext.getResources().openRawResource(R.raw.downgrade_schema)) {
            DbDowngradeHelper.parse(IOUtils.toByteArray(in)).onDowngrade(db, db.getVersion(), 31);
        }

        ContentValues cv = new ContentValues();
        cv.put("itemType", 1);
        cv.put("title", "Hello");
        cv.put("intent", intent == null ? null : intent.toUri(0));
        db.insert(Favorites.TABLE_NAME, null, cv);
        return db;
    }

    public static int getFavoriteDataCount(SQLiteDatabase db) {
        try (Cursor c = db.query(Favorites.TABLE_NAME, null, null, null, null, null, null)) {
            return c.getCount();
        }
    }

    private class MyDatabaseHelper extends DatabaseHelper {

        MyDatabaseHelper() {
            super(mContext, null, UserCache.INSTANCE.get(mContext)::getSerialNumberForUser,
                    () -> { });
        }

        @Override
        protected void handleOneTimeDataUpgrade(SQLiteDatabase db) { }
    }
}
