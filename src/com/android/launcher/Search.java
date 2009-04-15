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

import android.app.ISearchManager;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.database.Cursor;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.server.search.SearchableInfo;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnLongClickListener;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.CursorAdapter;
import android.widget.Filter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;

public class Search extends LinearLayout implements OnClickListener, OnKeyListener,
        OnLongClickListener, TextWatcher, OnItemClickListener, OnItemSelectedListener {

    private final String TAG = "SearchWidget";

    private AutoCompleteTextView mSearchText;
    private ImageButton mGoButton;
    private ImageButton mVoiceButton;
    private OnLongClickListener mLongClickListener;
    
    // Support for suggestions
    private SuggestionsAdapter mSuggestionsAdapter;
    private SearchableInfo mSearchable;
    private String mSuggestionAction = null;
    private Uri mSuggestionData = null;
    private String mSuggestionQuery = null;
    private int mItemSelected = -1;
    
    // For voice searching
    private Intent mVoiceSearchIntent;

    private Rect mTempRect = new Rect();
    private boolean mRestoreFocus = false;

    /**
     * Used to inflate the Workspace from XML.
     *
     * @param context The application's context.
     * @param attrs The attributes set containing the Workspace's customization values.
     */
    public Search(Context context, AttributeSet attrs) {
        super(context, attrs);
        
        mVoiceSearchIntent = new Intent(android.speech.RecognizerIntent.ACTION_WEB_SEARCH);
        mVoiceSearchIntent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
    }
    
    /**
     * Implements OnClickListener (for button)
     */
    public void onClick(View v) {
        if (v == mGoButton) {
            query();
        } else if (v == mVoiceButton) {
            try {
                getContext().startActivity(mVoiceSearchIntent);
            } catch (ActivityNotFoundException ex) {
                // Should not happen, since we check the availability of
                // voice search before showing the button. But just in case...
                Log.w(TAG, "Could not find voice search activity");
            }
        }
    }

    private void query() {
        String query = mSearchText.getText().toString();
        if (TextUtils.getTrimmedLength(mSearchText.getText()) == 0) {
            return;
        }
        Bundle appData = new Bundle();
        appData.putString(SearchManager.SOURCE, "launcher-widget");
        sendLaunchIntent(Intent.ACTION_SEARCH, null, query, appData, 0, null, mSearchable);
        clearQuery();
    }
    
    /**
     * Assemble a search intent and send it.
     * 
     * This is copied from SearchDialog.
     *
     * @param action The intent to send, typically Intent.ACTION_SEARCH
     * @param data The data for the intent
     * @param query The user text entered (so far)
     * @param appData The app data bundle (if supplied)
     * @param actionKey If the intent was triggered by an action key, e.g. KEYCODE_CALL, it will
     * be sent here.  Pass KeyEvent.KEYCODE_UNKNOWN for no actionKey code.
     * @param actionMsg If the intent was triggered by an action key, e.g. KEYCODE_CALL, the
     * corresponding tag message will be sent here.  Pass null for no actionKey message.
     * @param si Reference to the current SearchableInfo.  Passed here so it can be used even after
     * we've called dismiss(), which attempts to null mSearchable.
     */
    private void sendLaunchIntent(final String action, final Uri data, final String query,
            final Bundle appData, int actionKey, final String actionMsg, final SearchableInfo si) {
        Intent launcher = new Intent(action);
        launcher.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (query != null) {
            launcher.putExtra(SearchManager.QUERY, query);
        }

        if (data != null) {
            launcher.setData(data);
        }

        if (appData != null) {
            launcher.putExtra(SearchManager.APP_DATA, appData);
        }

        // add launch info (action key, etc.)
        if (actionKey != KeyEvent.KEYCODE_UNKNOWN) {
            launcher.putExtra(SearchManager.ACTION_KEY, actionKey);
            launcher.putExtra(SearchManager.ACTION_MSG, actionMsg);
        }

        // attempt to enforce security requirement (no 3rd-party intents)
        if (si != null) {
            launcher.setComponent(si.mSearchActivity);
        }

        getContext().startActivity(launcher);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (!hasWindowFocus && hasFocus()) {
            mRestoreFocus = true;
        }

        super.onWindowFocusChanged(hasWindowFocus);

        if (hasWindowFocus && mRestoreFocus) {
            if (isInTouchMode()) {
                final AutoCompleteTextView searchText = mSearchText;
                searchText.setSelectAllOnFocus(false);
                searchText.requestFocusFromTouch();
                searchText.setSelectAllOnFocus(true);
            }
            mRestoreFocus = false;
        }
    }

    /**
     * Implements TextWatcher (for EditText)
     */
    public void beforeTextChanged(CharSequence s, int start, int before, int after) { 
    }

    /**
     * Implements TextWatcher (for EditText)
     */
    public void onTextChanged(CharSequence s, int start, int before, int after) {
        // enable the button if we have one or more non-space characters
        boolean enabled = TextUtils.getTrimmedLength(mSearchText.getText()) != 0;
        mGoButton.setEnabled(enabled);
        mGoButton.setFocusable(enabled);
    }

    /**
     * Implements TextWatcher (for EditText)
     */
    public void afterTextChanged(Editable s) {
    }

    /**
     * Implements OnKeyListener (for EditText and for button)
     * 
     * This plays some games with state in order to "soften" the strength of suggestions
     * presented.  Suggestions should not be used unless the user specifically navigates to them
     * (or clicks them, in which case it's obvious).  This is not the way that AutoCompleteTextBox
     * normally works.
     */
    public final boolean onKey(View v, int keyCode, KeyEvent event) {
        if (v == mSearchText) {
            boolean searchTrigger = (keyCode == KeyEvent.KEYCODE_ENTER || 
                    keyCode == KeyEvent.KEYCODE_SEARCH ||
                    keyCode == KeyEvent.KEYCODE_DPAD_CENTER);
            if (event.getAction() == KeyEvent.ACTION_UP) {
//              Log.d(TAG, "onKey() ACTION_UP isPopupShowing:" + mSearchText.isPopupShowing());
                if (!mSearchText.isPopupShowing()) {
                    if (searchTrigger) {
                        query();
                        return true;
                    }
                }
            } else {
//              Log.d(TAG, "onKey() ACTION_DOWN isPopupShowing:" + mSearchText.isPopupShowing() +
//                      " mItemSelected="+ mItemSelected);
                if (searchTrigger && mItemSelected < 0) {
                    query();
                    return true;
                }
            }
        } else if (v == mGoButton || v == mVoiceButton) {
            boolean handled = false;
            if (!event.isSystem() && 
                    (keyCode != KeyEvent.KEYCODE_DPAD_UP) &&
                    (keyCode != KeyEvent.KEYCODE_DPAD_DOWN) &&
                    (keyCode != KeyEvent.KEYCODE_DPAD_LEFT) &&
                    (keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) &&
                    (keyCode != KeyEvent.KEYCODE_DPAD_CENTER)) {
                if (mSearchText.requestFocus()) {
                    handled = mSearchText.dispatchKeyEvent(event);
                }
            }
            return handled;
        }

        return false;
    }
    
    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        super.setOnLongClickListener(l);
        mLongClickListener = l;
    }
    
    /**
     * Implements OnLongClickListener (for button)
     */
    public boolean onLongClick(View v) {
        // Pretend that a long press on a child view is a long press on the search widget
        if (mLongClickListener != null) {
            return mLongClickListener.onLongClick(this);
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // Request focus unless the user tapped on the voice search button
        final int x = (int) ev.getX();
        final int y = (int) ev.getY();
        final Rect frame = mTempRect;
        mVoiceButton.getHitRect(frame);
        if (!frame.contains(x, y)) {
            requestFocusFromTouch();
        }
        return super.onInterceptTouchEvent(ev);
    }
    
    /**
     * In order to keep things simple, the external trigger will clear the query just before
     * focusing, so as to give you a fresh query.  This way we eliminate any sources of
     * accidental query launching.
     */
    public void clearQuery() {
        mSearchText.setText(null);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mSearchText = (AutoCompleteTextView) findViewById(R.id.input);
        // TODO: This can be confusing when the user taps the text field to give the focus
        // (it is not necessary but I ran into this issue several times myself)
        // mTitleInput.setOnClickListener(this);
        mSearchText.setOnKeyListener(this);
        mSearchText.addTextChangedListener(this);

        mGoButton = (ImageButton) findViewById(R.id.search_go_btn);
        mVoiceButton = (ImageButton) findViewById(R.id.search_voice_btn);
        mGoButton.setOnClickListener(this);
        mVoiceButton.setOnClickListener(this);
        mGoButton.setOnKeyListener(this);
        mVoiceButton.setOnKeyListener(this);
        
        mSearchText.setOnLongClickListener(this);
        mGoButton.setOnLongClickListener(this);
        mVoiceButton.setOnLongClickListener(this);
        
        // disable the button since we start out w/empty input
        mGoButton.setEnabled(false);
        mGoButton.setFocusable(false);
        
        configureSearchableInfo();
        configureSuggestions();
        configureVoiceSearchButton();
    }
    
    /**
     * Cache of popup padding value after read from {@link Resources}.
     */
    private static float mPaddingInset = -1;
    
    /**
     * When our size is changed, pass down adjusted width and offset values to
     * correctly center the {@link AutoCompleteTextView} popup and include our
     * padding.
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            if (mPaddingInset == -1) {
                mPaddingInset = getResources().getDimension(R.dimen.search_widget_inset);
            }
            
            // Fill entire width of widget, minus padding inset
            float paddedWidth = getWidth() - (mPaddingInset * 2);
            float paddedOffset = -(mSearchText.getLeft() - mPaddingInset);
                
            mSearchText.setDropDownWidth((int) paddedWidth);
            mSearchText.setDropDownHorizontalOffset((int) paddedOffset);
        }
    }
    
    /**
     * Read the searchable info from the search manager
     */
    private void configureSearchableInfo() {
        ISearchManager sms;
        SearchableInfo searchable;
        sms = ISearchManager.Stub.asInterface(ServiceManager.getService(Context.SEARCH_SERVICE));
        try {
            // TODO null isn't the published use of this API, but it works when global=true
            // TODO better implementation:  defer all of this, let Home set it up 
            searchable = sms.getSearchableInfo(null, true);
        } catch (RemoteException e) {
            searchable = null;
        }
        if (searchable == null) {
            // no suggestions so just get out (no need to continue)
            return;
        }
        mSearchable = searchable;
    }
    
    /**
     * If appropriate & available, configure voice search
     * 
     * Note:  Because the home screen search widget is always web search, we only check for
     * getVoiceSearchLaunchWebSearch() modes.  We don't support the alternate form of app-specific
     * voice search.
     */
    private void configureVoiceSearchButton() {
        boolean voiceSearchVisible = false;
        if (mSearchable.getVoiceSearchEnabled() && mSearchable.getVoiceSearchLaunchWebSearch()) {
            // Enable the voice search button if there is an activity that can handle it
            PackageManager pm = getContext().getPackageManager();
            ResolveInfo ri = pm.resolveActivity(mVoiceSearchIntent,
                    PackageManager.MATCH_DEFAULT_ONLY);
            voiceSearchVisible = ri != null;
        }
        
        // finally, set visible state of voice search button, as appropriate
        mVoiceButton.setVisibility(voiceSearchVisible ? View.VISIBLE : View.GONE);
    }
     
    /** The rest of the class deals with providing search suggestions */
    
    /**
     * Set up the suggestions provider mechanism
     */
    private void configureSuggestions() {
        // get SearchableInfo
        
        mSearchText.setOnItemClickListener(this);
        mSearchText.setOnItemSelectedListener(this);
        
        // attach the suggestions adapter
        mSuggestionsAdapter = new SuggestionsAdapter(mContext, 
                com.android.internal.R.layout.search_dropdown_item_2line, null,
                SuggestionsAdapter.TWO_LINE_FROM, SuggestionsAdapter.TWO_LINE_TO, mSearchable);
        mSearchText.setAdapter(mSuggestionsAdapter);
    }
    
    /**
     * Remove internal cursor references when detaching from window which
     * prevents {@link Context} leaks.
     */
    @Override
    public void onDetachedFromWindow() {
        if (mSuggestionsAdapter != null) {
            mSuggestionsAdapter.changeCursor(null);
            mSuggestionsAdapter = null;
        }
    }
    
    /**
     * Implements OnItemClickListener
     */
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//      Log.d(TAG, "onItemClick() position " + position);
        launchSuggestion(mSuggestionsAdapter, position);
    }
    
    /** 
     * Implements OnItemSelectedListener
     */
     public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//       Log.d(TAG, "onItemSelected() position " + position);
         mItemSelected = position;
     }

     /** 
      * Implements OnItemSelectedListener
      */
     public void onNothingSelected(AdapterView<?> parent) {
//       Log.d(TAG, "onNothingSelected()");
         mItemSelected = -1;
     }

    /**
     * Code to launch a suggestion query.  
     * 
     * This is copied from SearchDialog.
     * 
     * @param ca The CursorAdapter containing the suggestions
     * @param position The suggestion we'll be launching from
     * 
     * @return Returns true if a successful launch, false if could not (e.g. bad position)
     */
    private boolean launchSuggestion(CursorAdapter ca, int position) {
        if (ca != null) {
            Cursor c = ca.getCursor();
            if ((c != null) && c.moveToPosition(position)) {
                setupSuggestionIntent(c, mSearchable);
                
                SearchableInfo si = mSearchable;
                String suggestionAction = mSuggestionAction;
                Uri suggestionData = mSuggestionData;
                String suggestionQuery = mSuggestionQuery;
                sendLaunchIntent(suggestionAction, suggestionData, suggestionQuery, null,
                                    KeyEvent.KEYCODE_UNKNOWN, null, si);
                clearQuery();
                return true;
            }
        }
        return false;
    }
    
    /**
     * When a particular suggestion has been selected, perform the various lookups required
     * to use the suggestion.  This includes checking the cursor for suggestion-specific data,
     * and/or falling back to the XML for defaults;  It also creates REST style Uri data when
     * the suggestion includes a data id.
     * 
     * NOTE:  Return values are in member variables mSuggestionAction, mSuggestionData and
     * mSuggestionQuery.
     * 
     * This is copied from SearchDialog.
     * 
     * @param c The suggestions cursor, moved to the row of the user's selection
     * @param si The searchable activity's info record
     */
    void setupSuggestionIntent(Cursor c, SearchableInfo si) {
        try {
            // use specific action if supplied, or default action if supplied, or fixed default
            mSuggestionAction = null;
            int column = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_INTENT_ACTION);
            if (column >= 0) {
                final String action = c.getString(column);
                if (action != null) {
                    mSuggestionAction = action;
                }
            }
            if (mSuggestionAction == null) {
                mSuggestionAction = si.getSuggestIntentAction();
            }
            if (mSuggestionAction == null) {
                mSuggestionAction = Intent.ACTION_SEARCH;
            }
            
            // use specific data if supplied, or default data if supplied
            String data = null;
            column = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_INTENT_DATA);
            if (column >= 0) {
                final String rowData = c.getString(column);
                if (rowData != null) {
                    data = rowData;
                }
            }
            if (data == null) {
                data = si.getSuggestIntentData();
            }
            
            // then, if an ID was provided, append it.
            if (data != null) {
                column = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID);
                if (column >= 0) {
                    final String id = c.getString(column);
                    if (id != null) {
                        data = data + "/" + Uri.encode(id);
                    }
                }
            }
            mSuggestionData = (data == null) ? null : Uri.parse(data);
            
            mSuggestionQuery = null;
            column = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_QUERY);
            if (column >= 0) {
                final String query = c.getString(column);
                if (query != null) {
                    mSuggestionQuery = query;
                }
            }
        } catch (RuntimeException e ) {
            int rowNum;
            try {                       // be really paranoid now
                rowNum = c.getPosition();
            } catch (RuntimeException e2 ) {
                rowNum = -1;
            }
            Log.w(TAG, "Search Suggestions cursor at row " + rowNum + 
                            " returned exception" + e.toString());
        }
    }

    SearchAutoCompleteTextView getSearchInputField() {
        return (SearchAutoCompleteTextView) mSearchText;
    }

    /**
     * This class provides the filtering-based interface to suggestions providers.
     * It is hardwired in a couple of places to support GoogleSearch - for example, it supports
     * two-line suggestions, but it does not support icons.
     */
    private static class SuggestionsAdapter extends SimpleCursorAdapter {
        public final static String[] TWO_LINE_FROM =    {SearchManager.SUGGEST_COLUMN_TEXT_1,
                                                         SearchManager.SUGGEST_COLUMN_TEXT_2 };
        public final static int[] TWO_LINE_TO =         {com.android.internal.R.id.text1, 
                                                         com.android.internal.R.id.text2};
        
        private final String TAG = "SuggestionsAdapter";
        
        Filter mFilter;
        SearchableInfo mSearchable;
        private Resources mProviderResources;
        String[] mFromStrings;

        public SuggestionsAdapter(Context context, int layout, Cursor c,
                String[] from, int[] to, SearchableInfo searchable) {
            super(context, layout, c, from, to);
            mFromStrings = from;
            mSearchable = searchable;
            
            // set up provider resources (gives us icons, etc.)
            Context activityContext = mSearchable.getActivityContext(mContext);
            Context providerContext = mSearchable.getProviderContext(mContext, activityContext);
            mProviderResources = providerContext.getResources();
        }
        
        /**
         * Use the search suggestions provider to obtain a live cursor.  This will be called
         * in a worker thread, so it's OK if the query is slow (e.g. round trip for suggestions).
         * The results will be processed in the UI thread and changeCursor() will be called.
         */
        @Override
        public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
            String query = (constraint == null) ? "" : constraint.toString();
            return getSuggestions(mSearchable, query);
        }
        
        /**
         * Overriding this allows us to write the selected query back into the box.
         * NOTE:  This is a vastly simplified version of SearchDialog.jamQuery() and does
         * not universally support the search API.  But it is sufficient for Google Search.
         */
        @Override
        public CharSequence convertToString(Cursor cursor) {
            CharSequence result = null;
            if (cursor != null) {
                int column = cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_QUERY);
                if (column >= 0) {
                    final String query = cursor.getString(column);
                    if (query != null) {
                        result = query;
                    }
                }
            }
            return result;
        }

        /**
         * Get the query cursor for the search suggestions.
         * 
         * TODO this is functionally identical to the version in SearchDialog.java.  Perhaps it 
         * could be hoisted into SearchableInfo or some other shared spot.
         * 
         * @param query The search text entered (so far)
         * @return Returns a cursor with suggestions, or null if no suggestions 
         */
        private Cursor getSuggestions(final SearchableInfo searchable, final String query) {
            Cursor cursor = null;
            if (searchable.getSuggestAuthority() != null) {
                try {
                    StringBuilder uriStr = new StringBuilder("content://");
                    uriStr.append(searchable.getSuggestAuthority());

                    // if content path provided, insert it now
                    final String contentPath = searchable.getSuggestPath();
                    if (contentPath != null) {
                        uriStr.append('/');
                        uriStr.append(contentPath);
                    }

                    // append standard suggestion query path 
                    uriStr.append('/' + SearchManager.SUGGEST_URI_PATH_QUERY);

                    // inject query, either as selection args or inline
                    String[] selArgs = null;
                    if (searchable.getSuggestSelection() != null) {    // use selection if provided
                        selArgs = new String[] {query};
                    } else {
                        uriStr.append('/');                             // no sel, use REST pattern
                        uriStr.append(Uri.encode(query));
                    }

                    // finally, make the query
                    cursor = mContext.getContentResolver().query(
                                                        Uri.parse(uriStr.toString()), null, 
                                                        searchable.getSuggestSelection(), selArgs,
                                                        null);
                } catch (RuntimeException e) {
                    Log.w(TAG, "Search Suggestions query returned exception " + e.toString());
                    cursor = null;
                }
            }
            
            return cursor;
        }

        /**
         * Overriding this allows us to affect the way that an icon is loaded.  Specifically,
         * we can be more controlling about the resource path (and allow icons to come from other
         * packages).
         * 
         * TODO: This is 100% identical to the version in SearchDialog.java
         *
         * @param v ImageView to receive an image
         * @param value the value retrieved from the cursor
         */
        @Override
        public void setViewImage(ImageView v, String value) {
            int resID;
            Drawable img = null;

            try {
                resID = Integer.parseInt(value);
                if (resID != 0) {
                    img = mProviderResources.getDrawable(resID);
                }
            } catch (NumberFormatException nfe) {
                // img = null;
            } catch (NotFoundException e2) {
                // img = null;
            }
            
            // finally, set the image to whatever we've gotten
            v.setImageDrawable(img);
        }
        
        /**
         * This method is overridden purely to provide a bit of protection against
         * flaky content providers.
         * 
         * TODO: This is 100% identical to the version in SearchDialog.java
         * 
         * @see android.widget.ListAdapter#getView(int, View, ViewGroup)
         */
        @Override 
        public View getView(int position, View convertView, ViewGroup parent) {
            try {
                return super.getView(position, convertView, parent);
            } catch (RuntimeException e) {
                Log.w(TAG, "Search Suggestions cursor returned exception " + e.toString());
                // what can I return here?
                View v = newView(mContext, mCursor, parent);
                if (v != null) {
                    TextView tv = (TextView) v.findViewById(com.android.internal.R.id.text1);
                    tv.setText(e.toString());
                }
                return v;
            }
        }

    }
}
