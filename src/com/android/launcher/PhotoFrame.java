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

package com.android.launcher;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;


/**
 * Desktop widget that holds a user folder
 *
 */
public class PhotoFrame extends ImageView implements OnClickListener {
    
    public PhotoFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        setOnClickListener(this);
        setWillNotCacheDrawing(true);
    }
    
    public void onClick(View v) {
        ((Launcher) mContext).updatePhotoFrame((Widget) getTag());
    }
}
