/**
 * Copyright (c) 2018 The Android Open Source Project
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

package com.android.launcher3;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;

import androidx.fragment.app.DialogFragment;

import com.android.launcher3.R;

/**
 * Callback to be invoked when an app was picked.
 */
interface AppPickedCallback {
    void onAppPicked(AppEntry appEntry);
}

/**
 * Dialog that provides the user with a list of available apps to pin to the home screen.
 */
public class PinnedAppPickerDialog extends DialogFragment {

    private AppListAdapter mAppListAdapter;
    private AppPickedCallback mAppPickerCallback;

    public PinnedAppPickerDialog() {
    }

    public static PinnedAppPickerDialog newInstance(AppListAdapter appListAdapter,
            AppPickedCallback callback) {
        PinnedAppPickerDialog
                frag = new PinnedAppPickerDialog();
        frag.mAppListAdapter = appListAdapter;
        frag.mAppPickerCallback = callback;
        return frag;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.app_picker_dialog, container);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        GridView appGridView = view.findViewById(R.id.picker_app_grid);
        appGridView.setAdapter(mAppListAdapter);
        appGridView.setOnItemClickListener((adapterView, itemView, position, id) -> {
            final AppEntry entry = mAppListAdapter.getItem(position);
            mAppPickerCallback.onAppPicked(entry);
            dismiss();
        });
    }
}