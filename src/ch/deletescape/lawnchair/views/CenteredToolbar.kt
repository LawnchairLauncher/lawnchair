package ch.deletescape.lawnchair.views

import android.content.Context
import android.support.v7.widget.AppCompatTextView
import android.support.v7.widget.Toolbar
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import com.android.launcher3.R

/*
 * Copyright (C) 2018 paphonb@xda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class CenteredToolbar @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.toolbarStyle
) : Toolbar(context, attrs, defStyleAttr) {

    private var mTitleTextView: AppCompatTextView? = null
    private var mSubtitleTextView: AppCompatTextView? = null
    private var mTitleText: CharSequence? = null
    private var mSubtitleText: CharSequence? = null

    private fun inflateTitle() {
        LayoutInflater.from(context).inflate(R.layout.toolbar_title, this)
        mTitleTextView = findViewById(R.id.toolbar_title)
        mSubtitleTextView = findViewById(R.id.toolbar_subtitle)
    }

    override fun setTitle(title: CharSequence) {
        if (!TextUtils.isEmpty(title)) {
            if (mTitleTextView == null) {
                inflateTitle()
            }
        }
        if (mTitleTextView != null) {
            mTitleTextView!!.text = title
        }
        mTitleText = title
    }

    override fun getTitle(): CharSequence? {
        return mTitleText
    }

    override fun setSubtitle(subtitle: CharSequence) {
        if (!TextUtils.isEmpty(subtitle)) {
            if (mSubtitleTextView == null) {
                inflateTitle()
            }
        }
        if (mSubtitleTextView != null) {
            mSubtitleTextView!!.text = subtitle
        }
        mSubtitleText = subtitle
    }

    override fun getSubtitle(): CharSequence? {
        return mSubtitleText
    }
}
