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

package com.android.wm.shell.common.pip;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.Pair;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.common.ShellExecutor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit test against {@link PipAppOpsListener}.
 */
@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidTestingRunner.class)
public class PipAppOpsListenerTest {

    @Mock private Context mMockContext;
    @Mock private PackageManager mMockPackageManager;
    @Mock private AppOpsManager mMockAppOpsManager;
    @Mock private PipAppOpsListener.Callback mMockCallback;
    @Mock private ShellExecutor mMockExecutor;

    private PipAppOpsListener mPipAppOpsListener;

    private ArgumentCaptor<AppOpsManager.OnOpChangedListener> mOnOpChangedListenerCaptor;
    private ArgumentCaptor<Runnable> mRunnableArgumentCaptor;
    private Pair<ComponentName, Integer> mTopPipActivity;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockContext.getSystemService(Context.APP_OPS_SERVICE))
                .thenReturn(mMockAppOpsManager);
        mOnOpChangedListenerCaptor = ArgumentCaptor.forClass(
                AppOpsManager.OnOpChangedListener.class);
        mRunnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);
    }

    @Test
    public void onActivityPinned_registerAppOpsListener() {
        String packageName = "com.android.test.pip";
        mPipAppOpsListener = new PipAppOpsListener(mMockContext, mMockExecutor);
        mPipAppOpsListener.setCallback(mMockCallback);

        mPipAppOpsListener.onActivityPinned(packageName);

        verify(mMockAppOpsManager).startWatchingMode(
                eq(AppOpsManager.OP_PICTURE_IN_PICTURE), eq(packageName),
                any(AppOpsManager.OnOpChangedListener.class));
    }

    @Test
    public void onActivityUnpinned_unregisterAppOpsListener() {
        mPipAppOpsListener = new PipAppOpsListener(mMockContext, mMockExecutor);
        mPipAppOpsListener.setCallback(mMockCallback);

        mPipAppOpsListener.onActivityUnpinned();

        verify(mMockAppOpsManager).stopWatchingMode(any(AppOpsManager.OnOpChangedListener.class));
    }

    @Test
    public void disablePipAppOps_dismissPip() throws PackageManager.NameNotFoundException {
        String packageName = "com.android.test.pip";
        mPipAppOpsListener = new PipAppOpsListener(mMockContext, mMockExecutor);
        mPipAppOpsListener.setCallback(mMockCallback);
        // Set up the top pip activity info as mTopPipActivity
        mTopPipActivity = new Pair<>(new ComponentName(packageName, "PipActivity"), 0);
        mPipAppOpsListener.setTopPipActivityInfoSupplier(this::getTopPipActivity);
        // Set up the application info as mApplicationInfo
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = packageName;
        when(mMockPackageManager.getApplicationInfoAsUser(anyString(), anyInt(), anyInt()))
                .thenReturn(applicationInfo);
        // Mock the mode to be **not** allowed
        when(mMockAppOpsManager.checkOpNoThrow(anyInt(), anyInt(), eq(packageName)))
                .thenReturn(AppOpsManager.MODE_DEFAULT);
        // Set up the initial state
        mPipAppOpsListener.onActivityPinned(packageName);
        verify(mMockAppOpsManager).startWatchingMode(
                eq(AppOpsManager.OP_PICTURE_IN_PICTURE), eq(packageName),
                mOnOpChangedListenerCaptor.capture());
        AppOpsManager.OnOpChangedListener opChangedListener = mOnOpChangedListenerCaptor.getValue();

        opChangedListener.onOpChanged(String.valueOf(AppOpsManager.OP_PICTURE_IN_PICTURE),
                packageName);

        verify(mMockExecutor).execute(mRunnableArgumentCaptor.capture());
        Runnable runnable = mRunnableArgumentCaptor.getValue();
        runnable.run();
        verify(mMockCallback).dismissPip();
    }

    @Test
    public void disablePipAppOps_differentPackage_doNothing()
            throws PackageManager.NameNotFoundException {
        String packageName = "com.android.test.pip";
        mPipAppOpsListener = new PipAppOpsListener(mMockContext, mMockExecutor);
        mPipAppOpsListener.setCallback(mMockCallback);
        // Set up the top pip activity info as mTopPipActivity
        mTopPipActivity = new Pair<>(new ComponentName(packageName, "PipActivity"), 0);
        mPipAppOpsListener.setTopPipActivityInfoSupplier(this::getTopPipActivity);
        // Set up the application info as mApplicationInfo
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = packageName + ".modified";
        when(mMockPackageManager.getApplicationInfoAsUser(anyString(), anyInt(), anyInt()))
                .thenReturn(applicationInfo);
        // Mock the mode to be **not** allowed
        when(mMockAppOpsManager.checkOpNoThrow(anyInt(), anyInt(), eq(packageName)))
                .thenReturn(AppOpsManager.MODE_DEFAULT);
        // Set up the initial state
        mPipAppOpsListener.onActivityPinned(packageName);
        verify(mMockAppOpsManager).startWatchingMode(
                eq(AppOpsManager.OP_PICTURE_IN_PICTURE), eq(packageName),
                mOnOpChangedListenerCaptor.capture());
        AppOpsManager.OnOpChangedListener opChangedListener = mOnOpChangedListenerCaptor.getValue();

        opChangedListener.onOpChanged(String.valueOf(AppOpsManager.OP_PICTURE_IN_PICTURE),
                packageName);

        verifyNoMoreInteractions(mMockExecutor);
    }

    @Test
    public void disablePipAppOps_nameNotFound_unregisterAppOpsListener()
            throws PackageManager.NameNotFoundException {
        String packageName = "com.android.test.pip";
        mPipAppOpsListener = new PipAppOpsListener(mMockContext, mMockExecutor);
        mPipAppOpsListener.setCallback(mMockCallback);
        // Set up the top pip activity info as mTopPipActivity
        mTopPipActivity = new Pair<>(new ComponentName(packageName, "PipActivity"), 0);
        mPipAppOpsListener.setTopPipActivityInfoSupplier(this::getTopPipActivity);
        // Set up the application info as mApplicationInfo
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = packageName;
        when(mMockPackageManager.getApplicationInfoAsUser(anyString(), anyInt(), anyInt()))
                .thenThrow(PackageManager.NameNotFoundException.class);
        // Mock the mode to be **not** allowed
        when(mMockAppOpsManager.checkOpNoThrow(anyInt(), anyInt(), eq(packageName)))
                .thenReturn(AppOpsManager.MODE_DEFAULT);
        // Set up the initial state
        mPipAppOpsListener.onActivityPinned(packageName);
        verify(mMockAppOpsManager).startWatchingMode(
                eq(AppOpsManager.OP_PICTURE_IN_PICTURE), eq(packageName),
                mOnOpChangedListenerCaptor.capture());
        AppOpsManager.OnOpChangedListener opChangedListener = mOnOpChangedListenerCaptor.getValue();

        opChangedListener.onOpChanged(String.valueOf(AppOpsManager.OP_PICTURE_IN_PICTURE),
                packageName);

        verify(mMockAppOpsManager).stopWatchingMode(any(AppOpsManager.OnOpChangedListener.class));
    }

    private Pair<ComponentName, Integer> getTopPipActivity(Context context) {
        return mTopPipActivity;
    }
}
