package com.google.android.apps.nexuslauncher.search;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import ch.deletescape.lawnchair.globalsearch.SearchProvider;
import ch.deletescape.lawnchair.globalsearch.SearchProviderController;
import ch.deletescape.lawnchair.globalsearch.providers.web.WebSearchProvider;
import com.android.launcher3.BuildConfig;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.launcher3.allapps.search.SearchAlgorithm;
import java.util.Collections;
import java.util.List;

public class SearchThread implements SearchAlgorithm, Handler.Callback {
    private static HandlerThread handlerThread;
    private final Handler mHandler;
    private final Context mContext;
    private final Handler mUiHandler;
    private boolean mInterruptActiveRequests;

    public SearchThread(Context context) {
        mContext = context;
        mUiHandler = new Handler(this);
        if (handlerThread == null) {
            handlerThread = new HandlerThread("search-thread", -2);
            handlerThread.start();
        }
        mHandler = new Handler(SearchThread.handlerThread.getLooper(), this);
    }

    private void dj(SearchResult result) {
        Uri uri = new Uri.Builder()
                .scheme("content")
                .authority(BuildConfig.APPLICATION_ID + ".appssearch")
                .appendPath(result.mQuery)
                .build();

        Cursor cursor = null;
        try {
            cursor = mContext.getContentResolver().query(uri, null, null, null, null);
            int suggestIntentData = cursor.getColumnIndex("suggest_intent_data");
            while (cursor.moveToNext()) {
                result.mApps.add(AppSearchProvider.uriToComponent(Uri.parse(cursor.getString(suggestIntentData)), mContext));
            }
        } catch (NullPointerException ignored) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        result.mSuggestions.addAll(getSuggestions(result.mQuery));

        Message.obtain(mUiHandler, 200, result).sendToTarget();
    }

    public void cancel(boolean interruptActiveRequests) {
        mInterruptActiveRequests = interruptActiveRequests;
        mHandler.removeMessages(100);
        if (interruptActiveRequests) {
            mUiHandler.removeMessages(200);
        }
    }

    public void doSearch(String query, AllAppsSearchBarController.Callbacks callback) {
        mHandler.removeMessages(100);
        Message.obtain(mHandler, 100, new SearchResult(query, callback)).sendToTarget();
    }

    private List<String> getSuggestions(String query) {
        SearchProvider provider = SearchProviderController.Companion
                .getInstance(mContext).getSearchProvider();
        if (provider instanceof WebSearchProvider) {
            return ((WebSearchProvider) provider).getSuggestions(query);
        }
        return Collections.emptyList();
    }

    public boolean handleMessage(final Message message) {
        switch (message.what) {
            default: {
                return false;
            }
            case 100: {
                dj((SearchResult) message.obj);
                break;
            }
            case 200: {
                if (!mInterruptActiveRequests) {
                    SearchResult searchResult = (SearchResult) message.obj;
                    searchResult.mCallbacks.onSearchResult(searchResult.mQuery, searchResult.mApps, searchResult.mSuggestions);
                }
                break;
            }
        }
        return true;
    }
}
