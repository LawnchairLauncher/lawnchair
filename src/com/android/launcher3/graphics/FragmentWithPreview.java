/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.graphics;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;

/**
 * Extension of fragment, with support for preview mode.
 */
public class FragmentWithPreview extends Fragment {

    private Context mPreviewContext;

    public final void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onInit(savedInstanceState);
    }

    public void onInit(Bundle savedInstanceState) { }


    public Context getContext() {
        return mPreviewContext != null ? mPreviewContext : getActivity();
    }

    void enterPreviewMode(Context context) {
        mPreviewContext = context;
    }

    public boolean isInPreviewMode() {
        return mPreviewContext != null;
    }
}
