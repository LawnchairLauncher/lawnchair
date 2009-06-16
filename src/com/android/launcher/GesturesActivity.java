/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.launcher;

import android.app.ListActivity;
import android.app.Dialog;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.AsyncTask;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.AdapterView;
import android.widget.Toast;
import android.widget.EditText;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.gesture.GestureLibrary;
import android.gesture.Gesture;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.text.TextUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Collections;
import java.util.Map;

public class GesturesActivity extends ListActivity {
    private static final int MENU_ID_RENAME = 1;
    private static final int MENU_ID_REMOVE = 2;

    private static final int DIALOG_RENAME_GESTURE = 1;

    // Type: long (id)
    private static final String GESTURES_INFO_ID = "gestures.info_id";

    private final Comparator<ApplicationInfo> mSorter =
            new LauncherModel.ApplicationInfoComparator();

    private GesturesAdapter mAdapter;
    private GestureLibrary mStore;
    private GesturesLoadTask mTask;
    private TextView mEmpty;

    private Dialog mRenameDialog;
    private EditText mInput;
    private ApplicationInfo mCurrentRenameInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.gestures_settings);

        mAdapter = new GesturesAdapter(this);
        setListAdapter(mAdapter);

        mStore = Launcher.getGestureLibrary();
        mEmpty = (TextView) findViewById(android.R.id.empty);
        mTask = (GesturesLoadTask) new GesturesLoadTask().execute();

        registerForContextMenu(getListView());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mTask != null && mTask.getStatus() != GesturesLoadTask.Status.FINISHED) {
            mTask.cancel(true);
            mTask = null;
        }

        cleanupRenameDialog();
    }

    private void checkForEmpty() {
        if (mAdapter.getCount() == 0) {
            mEmpty.setText(R.string.gestures_empty);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mCurrentRenameInfo != null) {
            outState.putLong(GESTURES_INFO_ID, mCurrentRenameInfo.id);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);

        long id = state.getLong(GESTURES_INFO_ID, -1);
        if (id != -1) {
            mCurrentRenameInfo = Launcher.getModel().queryGesture(this, String.valueOf(id));
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenu.ContextMenuInfo menuInfo) {

        super.onCreateContextMenu(menu, v, menuInfo);

        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        menu.setHeaderTitle(((TextView) info.targetView).getText());

        menu.add(0, MENU_ID_RENAME, 0, R.string.gestures_rename);
        menu.add(0, MENU_ID_REMOVE, 0, R.string.gestures_delete);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo)
                item.getMenuInfo();
        final ApplicationInfo info = (ApplicationInfo) menuInfo.targetView.getTag();

        switch (item.getItemId()) {
            case MENU_ID_RENAME:
                renameGesture(info);
                return true;
            case MENU_ID_REMOVE:
                deleteGesture(info);
                return true;
        }

        return super.onContextItemSelected(item);
    }

    private void renameGesture(ApplicationInfo info) {
        mCurrentRenameInfo = info;
        showDialog(DIALOG_RENAME_GESTURE);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        if (id == DIALOG_RENAME_GESTURE) {
            return createRenameDialog();
        }
        return super.onCreateDialog(id);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        super.onPrepareDialog(id, dialog);
        if (id == DIALOG_RENAME_GESTURE) {
            mInput.setText(mCurrentRenameInfo.title);
        }
    }

    private Dialog createRenameDialog() {
        final View layout = View.inflate(this, R.layout.rename_folder, null);
        mInput = (EditText) layout.findViewById(R.id.folder_name);
        ((TextView) layout.findViewById(R.id.label)).setText(R.string.gestures_rename_label);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(0);
        builder.setTitle(getString(R.string.gestures_rename_title));
        builder.setCancelable(true);
        builder.setOnCancelListener(new Dialog.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                cleanupRenameDialog();
            }
        });
        builder.setNegativeButton(getString(R.string.cancel_action),
            new Dialog.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    cleanupRenameDialog();
                }
            }
        );
        builder.setPositiveButton(getString(R.string.rename_action),
            new Dialog.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    changeGestureName();
                }
            }
        );
        builder.setView(layout);
        return builder.create();
    }

    private void changeGestureName() {
        final String name = mInput.getText().toString();
        if (!TextUtils.isEmpty(name)) {
            final ApplicationInfo renameInfo = mCurrentRenameInfo;
            final GesturesActivity.GesturesAdapter adapter = mAdapter;
            final int count = adapter.getCount();

            // Simple linear search, there should not be enough items to warrant
            // a more sophisticated search
            for (int i = 0; i < count; i++) {
                final ApplicationInfo info = adapter.getItem(i);
                if (info.id == renameInfo.id) {
                    info.title = mInput.getText();
                    LauncherModel.updateGestureInDatabase(this, info);
                    break;
                }
            }

            adapter.notifyDataSetChanged();
        }
        mCurrentRenameInfo = null;
    }

    private void cleanupRenameDialog() {
        if (mRenameDialog != null) {
            mRenameDialog.dismiss();
            mRenameDialog = null;
        }
        mCurrentRenameInfo = null;
    }

    private void deleteGesture(ApplicationInfo info) {
        mStore.removeEntry(String.valueOf(info.id));
        // TODO: On a thread?
        mStore.save();

        final GesturesActivity.GesturesAdapter adapter = mAdapter;
        adapter.setNotifyOnChange(false);
        adapter.remove(info);
        adapter.sort(mSorter);
        checkForEmpty();
        adapter.notifyDataSetChanged();

        LauncherModel.deleteGestureFromDatabase(this, info);

        Toast.makeText(this, R.string.gestures_delete_success, Toast.LENGTH_SHORT).show();
    }

    private class GesturesLoadTask extends AsyncTask<Void, ApplicationInfo, Boolean> {
        private int mThumbnailSize;
        private int mThumbnailInset;
        private int mPathColor;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            final Resources resources = getResources();
            mPathColor = resources.getColor(R.color.gesture_color);
            mThumbnailInset = (int) resources.getDimension(R.dimen.gesture_thumbnail_inset);
            mThumbnailSize = (int) resources.getDimension(R.dimen.gesture_thumbnail_size);
        }

        protected Boolean doInBackground(Void... params) {
            if (isCancelled()) return Boolean.FALSE;

            final GestureLibrary store = mStore;

            if (store.load()) {
                final LauncherModel model = Launcher.getModel();

                for (String name : store.getGestureEntries()) {
                    if (isCancelled()) break;

                    final Gesture gesture = store.getGestures(name).get(0);
                    final Bitmap bitmap = gesture.toBitmap(mThumbnailSize, mThumbnailSize,
                            mThumbnailInset, mPathColor);
                    final ApplicationInfo info = model.queryGesture(GesturesActivity.this, name);

                    mAdapter.addBitmap(info.id, bitmap);
                    publishProgress(info);
                }

                return Boolean.TRUE;
            }

            return Boolean.FALSE;
        }

        @Override
        protected void onProgressUpdate(ApplicationInfo... values) {
            super.onProgressUpdate(values);

            final GesturesActivity.GesturesAdapter adapter = mAdapter;
            adapter.setNotifyOnChange(false);

            for (ApplicationInfo info : values) {
                adapter.add(info);
            }

            adapter.sort(mSorter);
            adapter.notifyDataSetChanged();
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            checkForEmpty();
        }
    }

    private class GesturesAdapter extends ArrayAdapter<ApplicationInfo> {
        private final LayoutInflater mInflater;
        private final Map<Long, Drawable> mThumbnails = Collections.synchronizedMap(
                new HashMap<Long, Drawable>());

        public GesturesAdapter(Context context) {
            super(context, 0);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        void addBitmap(Long id, Bitmap bitmap) {
            mThumbnails.put(id, new BitmapDrawable(bitmap));
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.gestures_settings_item, parent, false);
            }

            final ApplicationInfo info = getItem(position);
            final TextView label = (TextView) convertView;

            label.setTag(info);
            label.setText(info.title);
            label.setCompoundDrawablesWithIntrinsicBounds(info.icon, null,
                    mThumbnails.get(info.id), null);

            return convertView;
        }
    }
}
