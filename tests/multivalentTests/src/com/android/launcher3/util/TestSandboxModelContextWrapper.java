/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.util;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.launcher3.util.MainThreadInitializedObject.SandboxContext;

import android.content.ContextWrapper;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.allapps.AlphabeticalAppsList;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.popup.PopupDataProvider;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * {@link ContextWrapper} around {@link ActivityContextWrapper} with internal Launcher interface for
 * testing.
 *
 * There are 2 constructors in this class. The base context can be {@link SandboxContext} or
 * Instrumentation target context.
 * Using {@link SandboxContext} as base context allows custom implementations for
 * MainThreadInitializedObject providers.
 */

public class TestSandboxModelContextWrapper extends ActivityContextWrapper implements
        BgDataModel.Callbacks {

    protected AllAppsStore<ActivityContextWrapper> mAllAppsStore;
    protected AlphabeticalAppsList<ActivityContextWrapper> mAppsList;

    public final CountDownLatch mBindCompleted = new CountDownLatch(1);

    protected ActivityAllAppsContainerView<ActivityContextWrapper> mAppsView;

    private final PopupDataProvider mPopupDataProvider = new PopupDataProvider(i -> {});
    protected final UserCache mUserCache;

    public TestSandboxModelContextWrapper(SandboxContext base) {
        super(base);
        mUserCache = base.getObject(UserCache.INSTANCE);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                mAppsView = new ActivityAllAppsContainerView<>(this));
        mAppsList = mAppsView.getPersonalAppList();
        mAllAppsStore = mAppsView.getAppsStore();
    }

    public TestSandboxModelContextWrapper() {
        super(getInstrumentation().getTargetContext());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                mAppsView = new ActivityAllAppsContainerView<>(this));
        mUserCache = UserCache.getInstance(this);
        mAppsList = mAppsView.getPersonalAppList();
        mAllAppsStore = mAppsView.getAppsStore();
    }
    @Nullable
    @Override
    public PopupDataProvider getPopupDataProvider() {
        return mPopupDataProvider;
    }

    @Override
    public ActivityAllAppsContainerView<ActivityContextWrapper> getAppsView() {
        return mAppsView;
    }

    @Override
    public void bindAllApplications(AppInfo[] apps, int flags,
            Map<PackageUserKey, Integer> packageUserKeytoUidMap) {
        mAllAppsStore.setApps(apps, flags, packageUserKeytoUidMap);
        mBindCompleted.countDown();
    }
}
