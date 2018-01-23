package com.google.android.apps.nexuslauncher.search;

import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.launcher3.util.ComponentKey;

import java.util.ArrayList;

class SearchResult {
    final AllAppsSearchBarController.Callbacks mCallbacks;
    final String mQuery;
    final ArrayList<ComponentKey> mApps;

    SearchResult(String query, AllAppsSearchBarController.Callbacks callbacks) {
        mApps = new ArrayList<>();
        mQuery = query;
        mCallbacks = callbacks;
    }
}
