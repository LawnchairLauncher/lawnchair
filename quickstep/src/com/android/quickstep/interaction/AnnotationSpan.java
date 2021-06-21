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

package com.android.quickstep.interaction;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.text.Annotation;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.View;

import java.util.Arrays;
import java.util.Comparator;

/**
 * This class is used to add {@link View.OnClickListener} for the text been wrapped by
 * annotation.
 *
 * Copied from packages/apps/Settings/src/com/android/settings/utils/AnnotationSpan.java.
 */
public class AnnotationSpan extends URLSpan {

    private final View.OnClickListener mClickListener;

    AnnotationSpan(View.OnClickListener lsn) {
        super((String) null);
        mClickListener = lsn;
    }

    @Override
    public void onClick(View widget) {
        if (mClickListener != null) {
            mClickListener.onClick(widget);
        }
    }

    public static CharSequence linkify(CharSequence rawText, LinkInfo... linkInfos) {
        SpannableString msg = new SpannableString(rawText);
        Annotation[] spans = msg.getSpans(0, msg.length(), Annotation.class);
        SpannableStringBuilder builder = new SpannableStringBuilder(msg);
        for (Annotation annotation : spans) {
            final String key = annotation.getValue();
            int start = msg.getSpanStart(annotation);
            int end = msg.getSpanEnd(annotation);
            AnnotationSpan link = null;
            for (LinkInfo linkInfo : linkInfos) {
                if (linkInfo.mAnnotation.equals(key)) {
                    link = linkInfo.mCustomizedSpan != null ? linkInfo.mCustomizedSpan
                            : new AnnotationSpan(linkInfo.mClickListener);
                    break;
                }
            }
            if (link != null) {
                builder.setSpan(link, start, end, msg.getSpanFlags(link));
            }
        }
        return builder;
    }

    /**
     * get the text part without having text for link part
     */
    public static CharSequence textWithoutLink(CharSequence encodedText) {
        SpannableString msg = new SpannableString(encodedText);
        Annotation[] spans = msg.getSpans(0, msg.length(), Annotation.class);
        if (spans == null) {
            return encodedText;
        }
        Arrays.sort(spans, Comparator.comparingInt(span -> -msg.getSpanStart(span)));
        StringBuilder msgWithoutLink = new StringBuilder(msg.toString());
        for (Annotation span : spans) {
            msgWithoutLink.delete(msg.getSpanStart(span), msg.getSpanEnd(span));
        }
        return msgWithoutLink.toString();
    }

    /** Data class to store the annotation and the click action. */
    public static class LinkInfo {
        public static final String DEFAULT_ANNOTATION = "link";
        private static final String TAG = "AnnotationSpan.LinkInfo";
        private final String mAnnotation;
        private final Boolean mActionable;
        private final View.OnClickListener mClickListener;
        private final AnnotationSpan mCustomizedSpan;

        public LinkInfo(String annotation, View.OnClickListener listener) {
            mAnnotation = annotation;
            mClickListener = listener;
            mActionable = true; // assume actionable
            mCustomizedSpan = null;
        }

        public LinkInfo(String annotation, AnnotationSpan customizedSpan) {
            mAnnotation = annotation;
            mClickListener = null;
            mActionable = customizedSpan != null;
            mCustomizedSpan = customizedSpan;
        }

        public LinkInfo(Context context, String annotation, Intent intent) {
            mAnnotation = annotation;
            mCustomizedSpan = null;
            if (intent != null) {
                mActionable = context.getPackageManager().resolveActivity(intent, 0) != null;
            } else {
                mActionable = false;
            }
            if (mActionable) {
                mClickListener =
                        view -> {
                            try {
                                context.startActivity(intent);
                            } catch (ActivityNotFoundException e) {
                                Log.w(TAG, "Activity was not found for intent, " + intent);
                            }
                        };
            } else {
                mClickListener = null;
            }
        }

        public boolean isActionable() {
            return mActionable;
        }
    }
}
