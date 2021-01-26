/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.search;


import android.app.search.SearchTarget;
import android.content.Context;
import android.util.AttributeSet;

import java.util.List;

/**
 * A view representing a high confidence app search result that includes shortcuts
 */
public class ThumbnailSearchResultView extends androidx.appcompat.widget.AppCompatImageView
        implements SearchTargetHandler {

    public ThumbnailSearchResultView(Context context) {
        super(context);
    }

    public ThumbnailSearchResultView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ThumbnailSearchResultView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void applySearchTarget(SearchTarget parentTarget, List<SearchTarget> children) {

    }
}
