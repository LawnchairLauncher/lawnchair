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

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnLongClickListener;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.view.animation.Transformation;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

public class Search extends LinearLayout 
        implements OnClickListener, OnKeyListener, OnLongClickListener {

    // Speed at which the widget slides up/down, in pixels/ms.
    private static final float ANIMATION_VELOCITY = 1.0f;

    /** The distance in dips between the optical top of the widget and the top if its bounds */
    private static final float WIDGET_TOP_OFFSET = 9;

    
    private final String TAG = "Launcher.SearchWidget";

    private Launcher mLauncher;

    private TextView mSearchText;
    private ImageButton mVoiceButton;

    /** The animation that morphs the search widget to the search dialog. */
    private Animation mMorphAnimation;

    /** The animation that morphs the search widget back to its normal position. */
    private Animation mUnmorphAnimation;

    // These four are passed to Launcher.startSearch() when the search widget
    // has finished morphing. They are instance variables to make it possible to update
    // them while the widget is morphing.
    private String mInitialQuery;
    private boolean mSelectInitialQuery;    
    private Bundle mAppSearchData;
    private boolean mGlobalSearch;

    // For voice searching
    private Intent mVoiceSearchIntent;
    
    private int mWidgetTopOffset;

    /**
     * Used to inflate the Workspace from XML.
     *
     * @param context The application's context.
     * @param attrs The attributes set containing the Workspace's customization values.
     */
    public Search(Context context, AttributeSet attrs) {
        super(context, attrs);

        final float scale = context.getResources().getDisplayMetrics().density;
        mWidgetTopOffset = Math.round(WIDGET_TOP_OFFSET * scale);
        
        Interpolator interpolator = new AccelerateDecelerateInterpolator();

        mMorphAnimation = new ToParentOriginAnimation();
        // no need to apply transformation before the animation starts,
        // since the gadget is already in its normal place.
        mMorphAnimation.setFillBefore(false);
        // stay in the top position after the animation finishes
        mMorphAnimation.setFillAfter(true);
        mMorphAnimation.setInterpolator(interpolator);
        mMorphAnimation.setAnimationListener(new Animation.AnimationListener() {
            // The amount of time before the animation ends to show the search dialog.
            private static final long TIME_BEFORE_ANIMATION_END = 80;
            
            // The runnable which we'll pass to our handler to show the search dialog.
            private final Runnable mShowSearchDialogRunnable = new Runnable() {
                public void run() {
                    showSearchDialog();
                }
            };
            
            public void onAnimationEnd(Animation animation) { }
            public void onAnimationRepeat(Animation animation) { }
            public void onAnimationStart(Animation animation) {
                // Make the search dialog show up ideally *just* as the animation reaches
                // the top, to aid the illusion that the widget becomes the search dialog.
                // Otherwise, there is a short delay when the widget reaches the top before
                // the search dialog shows. We do this roughly 80ms before the animation ends.
                getHandler().postDelayed(
                        mShowSearchDialogRunnable,
                        Math.max(mMorphAnimation.getDuration() - TIME_BEFORE_ANIMATION_END, 0));
            }
        });

        mUnmorphAnimation = new FromParentOriginAnimation();
        // stay in the top position until the animation starts
        mUnmorphAnimation.setFillBefore(true);
        // no need to apply transformation after the animation finishes,
        // since the gadget is now back in its normal place.
        mUnmorphAnimation.setFillAfter(false);
        mUnmorphAnimation.setInterpolator(interpolator);
        mUnmorphAnimation.setAnimationListener(new Animation.AnimationListener(){
            public void onAnimationEnd(Animation animation) {
                clearAnimation();
            }
            public void onAnimationRepeat(Animation animation) { }
            public void onAnimationStart(Animation animation) { }
        });
        
        mVoiceSearchIntent = new Intent(android.speech.RecognizerIntent.ACTION_WEB_SEARCH);
        mVoiceSearchIntent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
    }

    /**
     * Implements OnClickListener.
     */
    public void onClick(View v) {
        if (v == mVoiceButton) {
            startVoiceSearch();
        } else {
            mLauncher.onSearchRequested();
        }
    }

    private void startVoiceSearch() {
        try {
            getContext().startActivity(mVoiceSearchIntent);
        } catch (ActivityNotFoundException ex) {
            // Should not happen, since we check the availability of
            // voice search before showing the button. But just in case...
            Log.w(TAG, "Could not find voice search activity");
        }
    }

    /**
     * Sets the query text. The query field is not editable, instead we forward
     * the key events to the launcher, which keeps track of the text, 
     * calls setQuery() to show it, and gives it to the search dialog.
     */
    public void setQuery(String query) {
        mSearchText.setText(query, TextView.BufferType.NORMAL);
    }

    /**
     * Morph the search gadget to the search dialog.
     * See {@link Activity#startSearch()} for the arguments.
     */
    public void startSearch(String initialQuery, boolean selectInitialQuery, 
            Bundle appSearchData, boolean globalSearch) {
        mInitialQuery = initialQuery;
        mSelectInitialQuery = selectInitialQuery;
        mAppSearchData = appSearchData;
        mGlobalSearch = globalSearch;
        
        if (isAtTop()) {
            showSearchDialog();
        } else {
            // Call up the keyboard before we actually call the search dialog so that it
            // (hopefully) animates in at about the same time as the widget animation, and
            // so that it becomes available as soon as possible. Only do this if a hard
            // keyboard is not currently available.
            if (getContext().getResources().getConfiguration().hardKeyboardHidden ==
                    Configuration.HARDKEYBOARDHIDDEN_YES) {
                InputMethodManager inputManager = (InputMethodManager)
                        getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                inputManager.showSoftInputUnchecked(0, null);
            }
            
            // Start the animation, unless it has already started.
            if (getAnimation() != mMorphAnimation) {
                mMorphAnimation.setDuration(getAnimationDuration());
                startAnimation(mMorphAnimation);
            }
        }
    }

    /**
     * Shows the system search dialog immediately, without any animation.
     */
    private void showSearchDialog() {
        mLauncher.showSearchDialog(
                mInitialQuery, mSelectInitialQuery, mAppSearchData, mGlobalSearch);
    }

    /**
     * Restore the search gadget to its normal position.
     * 
     * @param animate Whether to animate the movement of the gadget.
     */
    public void stopSearch(boolean animate) {
        setQuery("");
        
        // Only restore if we are not already restored.
        if (getAnimation() == mMorphAnimation) {
            if (animate && !isAtTop()) {
                mUnmorphAnimation.setDuration(getAnimationDuration());
                startAnimation(mUnmorphAnimation);
            } else {
                clearAnimation();
            }
        }
    }

    private boolean isAtTop() {
        return getWidgetTop() == 0;
    }

    private int getAnimationDuration() {
        return (int) (getWidgetTop() / ANIMATION_VELOCITY);
    }

    /**
     * Modify clearAnimation() to invalidate the parent. This works around
     * an issue where the region where the end of the animation placed the view
     * was not redrawn after clearing the animation.
     */
    @Override
    public void clearAnimation() {
        Animation animation = getAnimation();
        if (animation != null) {
            super.clearAnimation();
            if (animation.hasEnded() 
                    && animation.getFillAfter()
                    && animation.willChangeBounds()) {
                View parent = (View) getParent();
                if (parent != null) parent.invalidate();
            } else {
                invalidate();
            }
        }
    }
    
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (!event.isSystem() && 
                (keyCode != KeyEvent.KEYCODE_DPAD_UP) &&
                (keyCode != KeyEvent.KEYCODE_DPAD_DOWN) &&
                (keyCode != KeyEvent.KEYCODE_DPAD_LEFT) &&
                (keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) &&
                (keyCode != KeyEvent.KEYCODE_DPAD_CENTER)) {
            // Forward key events to Launcher, which will forward text 
            // to search dialog
            switch (event.getAction()) {
                case KeyEvent.ACTION_DOWN:
                    return mLauncher.onKeyDown(keyCode, event);
                case KeyEvent.ACTION_MULTIPLE:
                    return mLauncher.onKeyMultiple(keyCode, event.getRepeatCount(), event);
                case KeyEvent.ACTION_UP:
                    return mLauncher.onKeyUp(keyCode, event);
            }
        }
        return false;
    }

    /**
     * Implements OnLongClickListener to pass long clicks on child views 
     * to the widget. This makes it possible to pick up the widget by long
     * clicking on the text field or a button.
     */
    public boolean onLongClick(View v) {
        return performLongClick();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mSearchText = (TextView) findViewById(R.id.search_src_text);
        mVoiceButton = (ImageButton) findViewById(R.id.search_voice_btn);
        
        mSearchText.setOnKeyListener(this);

        mSearchText.setOnClickListener(this);
        mVoiceButton.setOnClickListener(this);
        setOnClickListener(this);        

        mSearchText.setOnLongClickListener(this);
        mVoiceButton.setOnLongClickListener(this);

        // Set the placeholder text to be the Google logo within the search widget.
        Drawable googlePlaceholder =
                getContext().getResources().getDrawable(R.drawable.placeholder_google);
        mSearchText.setCompoundDrawablesWithIntrinsicBounds(googlePlaceholder, null, null, null);

        configureVoiceSearchButton();
    }
    
    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    /**
     * If appropriate &amp; available, configure voice search
     * 
     * Note:  Because the home screen search widget is always web search, we only check for
     * getVoiceSearchLaunchWebSearch() modes.  We don't support the alternate form of app-specific
     * voice search.
     */
    private void configureVoiceSearchButton() {
        // Enable the voice search button if there is an activity that can handle it
        PackageManager pm = getContext().getPackageManager();
        ResolveInfo ri = pm.resolveActivity(mVoiceSearchIntent,
                PackageManager.MATCH_DEFAULT_ONLY);
        boolean voiceSearchVisible = ri != null;

        // finally, set visible state of voice search button, as appropriate
        mVoiceButton.setVisibility(voiceSearchVisible ? View.VISIBLE : View.GONE);
    }
    
    /**
     * Sets the {@link Launcher} that this gadget will call on to display the search dialog. 
     */
    public void setLauncher(Launcher launcher) {
        mLauncher = launcher;
    }
        
    /** 
     * Moves the view to the top left corner of its parent.
     */
    private class ToParentOriginAnimation extends Animation {
        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            float dx = -getLeft() * interpolatedTime;
            float dy = -getWidgetTop() * interpolatedTime;
            t.getMatrix().setTranslate(dx, dy);
        }
    }

    /** 
     * Moves the view from the top left corner of its parent.
     */
    private class FromParentOriginAnimation extends Animation {
        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            float dx = -getLeft() * (1.0f - interpolatedTime);
            float dy = -getWidgetTop() * (1.0f - interpolatedTime);
            t.getMatrix().setTranslate(dx, dy);
        }
    }

    /**
     * The widget is centered vertically within it's 4x1 slot. This is accomplished by nesting
     * the actual widget inside another view. For animation purposes, we care about the top of the
     * actual widget rather than it's container. This method return the top of the actual widget.
     */
    private int getWidgetTop() {
        return getTop() + getChildAt(0).getTop() + mWidgetTopOffset;
    }
}
