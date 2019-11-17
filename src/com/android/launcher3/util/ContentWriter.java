package com.android.launcher3.util;

/**
 * Copyright (C) 2016 The Android Open Source Project
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

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.UserHandle;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.icons.GraphicsUtils;

/**
 * A wrapper around {@link ContentValues} with some utility methods.
 */
public class ContentWriter {

    private final ContentValues mValues;
    private final Context mContext;

    private CommitParams mCommitParams;
    private Bitmap mIcon;
    private UserHandle mUser;

    public ContentWriter(Context context, CommitParams commitParams) {
        this(context);
        mCommitParams = commitParams;
    }

    public ContentWriter(Context context) {
        this(new ContentValues(), context);
    }

    public ContentWriter(ContentValues values, Context context) {
        mValues = values;
        mContext = context;
    }

    public ContentWriter put(String key, Integer value) {
        mValues.put(key, value);
        return this;
    }

    public ContentWriter put(String key, Long value) {
        mValues.put(key, value);
        return this;
    }

    public ContentWriter put(String key, String value) {
        mValues.put(key, value);
        return this;
    }

    public ContentWriter put(String key, CharSequence value) {
        mValues.put(key, value == null ? null : value.toString());
        return this;
    }

    public ContentWriter put(String key, Intent value) {
        mValues.put(key, value == null ? null : value.toUri(0));
        return this;
    }

    public ContentWriter putIcon(Bitmap value, UserHandle user) {
        mIcon = value;
        mUser = user;
        return this;
    }

    public ContentWriter put(String key, UserHandle user) {
        return put(key, UserManagerCompat.getInstance(mContext).getSerialNumberForUser(user));
    }

    /**
     * Commits any pending validation and returns the final values.
     * Must not be called on UI thread.
     */
    public ContentValues getValues(Context context) {
        Preconditions.assertNonUiThread();
        if (mIcon != null && !LauncherAppState.getInstance(context).getIconCache()
                .isDefaultIcon(mIcon, mUser)) {
            mValues.put(LauncherSettings.Favorites.ICON, GraphicsUtils.flattenBitmap(mIcon));
            mIcon = null;
        }
        return mValues;
    }

    public int commit() {
        if (mCommitParams != null) {
            return mContext.getContentResolver().update(mCommitParams.mUri, getValues(mContext),
                    mCommitParams.mWhere, mCommitParams.mSelectionArgs);
        }
        return 0;
    }

    public static final class CommitParams {

        final Uri mUri = LauncherSettings.Favorites.CONTENT_URI;
        String mWhere;
        String[] mSelectionArgs;

        public CommitParams(String where, String[] selectionArgs) {
            mWhere = where;
            mSelectionArgs = selectionArgs;
        }

    }
}
