/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher2;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.net.Uri;
import android.provider.LiveFolders;
import android.os.AsyncTask;
import android.database.Cursor;

import java.lang.ref.WeakReference;

import com.android.launcher.R;

public class LiveFolder extends Folder {
    private AsyncTask<LiveFolderInfo,Void,Cursor> mLoadingTask;

    public LiveFolder(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    static LiveFolder fromXml(Context context, FolderInfo folderInfo) {
        final int layout = isDisplayModeList(folderInfo) ?
                R.layout.live_folder_list : R.layout.live_folder_grid;
        return (LiveFolder) LayoutInflater.from(context).inflate(layout, null);
    }

    private static boolean isDisplayModeList(FolderInfo folderInfo) {
        return ((LiveFolderInfo) folderInfo).displayMode ==
                LiveFolders.DISPLAY_MODE_LIST;
    }

    @Override
    public void onItemClick(AdapterView parent, View v, int position, long id) {
        LiveFolderAdapter.ViewHolder holder = (LiveFolderAdapter.ViewHolder) v.getTag();

        if (holder.useBaseIntent) {
            final Intent baseIntent = ((LiveFolderInfo) mInfo).baseIntent;
            if (baseIntent != null) {
                final Intent intent = new Intent(baseIntent);
                Uri uri = baseIntent.getData();
                uri = uri.buildUpon().appendPath(Long.toString(holder.id)).build();
                intent.setData(uri);
                mLauncher.startActivitySafely(intent, "(position=" + position + ", id=" + id + ")");
            }
        } else if (holder.intent != null) {
            mLauncher.startActivitySafely(holder.intent,
                    "(position=" + position + ", id=" + id + ")");
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        return false;
    }

    void bind(FolderInfo info) {
        super.bind(info);
        if (mLoadingTask != null && mLoadingTask.getStatus() == AsyncTask.Status.RUNNING) {
            mLoadingTask.cancel(true);
        }
        mLoadingTask = new FolderLoadingTask(this).execute((LiveFolderInfo) info);
    }

    @Override
    void onOpen() {
        super.onOpen();
        requestFocus();
    }

    @Override
    void onClose() {
        super.onClose();
        if (mLoadingTask != null && mLoadingTask.getStatus() == AsyncTask.Status.RUNNING) {
            mLoadingTask.cancel(true);
        }

        // The adapter can be null if onClose() is called before FolderLoadingTask
        // is done querying the provider
        final LiveFolderAdapter adapter = (LiveFolderAdapter) mContent.getAdapter();
        if (adapter != null) {
            adapter.cleanup();
        }
    }

    static class FolderLoadingTask extends AsyncTask<LiveFolderInfo, Void, Cursor> {
        private final WeakReference<LiveFolder> mFolder;
        private LiveFolderInfo mInfo;

        FolderLoadingTask(LiveFolder folder) {
            mFolder = new WeakReference<LiveFolder>(folder);
        }

        protected Cursor doInBackground(LiveFolderInfo... params) {
            final LiveFolder folder = mFolder.get();
            if (folder != null) {
                mInfo = params[0];
                return LiveFolderAdapter.query(folder.mLauncher, mInfo);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Cursor cursor) {
            if (!isCancelled()) {
                if (cursor != null) {
                    final LiveFolder folder = mFolder.get();
                    if (folder != null) {
                        final Launcher launcher = folder.mLauncher;
                        folder.setContentAdapter(new LiveFolderAdapter(launcher, mInfo, cursor));
                    }
                }
            } else if (cursor != null) {
                cursor.close();
            }
        }
    }
}
