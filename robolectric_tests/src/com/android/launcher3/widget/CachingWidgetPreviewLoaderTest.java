/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.widget;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.os.CancellationSignal;
import android.os.UserHandle;
import android.util.Size;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.testing.TestActivity;
import com.android.launcher3.widget.WidgetPreviewLoader.WidgetPreviewLoadedCallback;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
public class CachingWidgetPreviewLoaderTest {
    private static final Size SIZE_10_10 = new Size(10, 10);
    private static final Size SIZE_20_20 = new Size(20, 20);
    private static final String TEST_PACKAGE = "com.example.test";
    private static final ComponentName TEST_PROVIDER =
            new ComponentName(TEST_PACKAGE, ".WidgetProvider");
    private static final ComponentName TEST_PROVIDER2 =
            new ComponentName(TEST_PACKAGE, ".WidgetProvider2");
    private static final Bitmap BITMAP = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
    private static final Bitmap BITMAP2 = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888);


    @Mock private CancellationSignal mCancellationSignal;
    @Mock private WidgetPreviewLoader mDelegate;
    @Mock private IconCache mIconCache;
    @Mock private DeviceProfile mDeviceProfile;
    @Mock private LauncherAppWidgetProviderInfo mProviderInfo;
    @Mock private LauncherAppWidgetProviderInfo mProviderInfo2;
    @Mock private WidgetPreviewLoadedCallback mPreviewLoadedCallback;
    @Mock private WidgetPreviewLoadedCallback mPreviewLoadedCallback2;
    @Captor private ArgumentCaptor<WidgetPreviewLoadedCallback> mCallbackCaptor;

    private TestActivity mTestActivity;
    private CachingWidgetPreviewLoader mLoader;
    private WidgetItem mWidgetItem;
    private WidgetItem mWidgetItem2;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLoader = new CachingWidgetPreviewLoader(mDelegate);

        mTestActivity = Robolectric.buildActivity(TestActivity.class).setup().get();
        mTestActivity.setDeviceProfile(mDeviceProfile);

        when(mDelegate.loadPreview(any(), any(), any(), any())).thenReturn(mCancellationSignal);

        mProviderInfo.provider = TEST_PROVIDER;
        when(mProviderInfo.getProfile()).thenReturn(new UserHandle(0));

        mProviderInfo2.provider = TEST_PROVIDER2;
        when(mProviderInfo2.getProfile()).thenReturn(new UserHandle(0));

        InvariantDeviceProfile testProfile = new InvariantDeviceProfile();
        testProfile.numRows = 5;
        testProfile.numColumns = 5;

        mWidgetItem = new WidgetItem(mProviderInfo, testProfile, mIconCache);
        mWidgetItem2 = new WidgetItem(mProviderInfo2, testProfile, mIconCache);
    }

    @Test
    public void getPreview_notInCache_shouldReturnNull() {
        assertThat(mLoader.getPreview(mWidgetItem, SIZE_10_10)).isNull();
    }

    @Test
    public void getPreview_notInCache_shouldNotCallDelegate() {
        mLoader.getPreview(mWidgetItem, SIZE_10_10);

        verifyZeroInteractions(mDelegate);
    }

    @Test
    public void getPreview_inCache_shouldReturnCachedBitmap() {
        loadPreviewIntoCache(mWidgetItem, SIZE_10_10, BITMAP);

        assertThat(mLoader.getPreview(mWidgetItem, SIZE_10_10)).isEqualTo(BITMAP);
    }

    @Test
    public void getPreview_otherSizeInCache_shouldReturnNull() {
        loadPreviewIntoCache(mWidgetItem, SIZE_10_10, BITMAP);

        assertThat(mLoader.getPreview(mWidgetItem, SIZE_20_20)).isNull();
    }

    @Test
    public void getPreview_otherItemInCache_shouldReturnNull() {
        loadPreviewIntoCache(mWidgetItem, SIZE_10_10, BITMAP);

        assertThat(mLoader.getPreview(mWidgetItem2, SIZE_10_10)).isNull();
    }

    @Test
    public void getPreview_shouldStoreMultipleSizesPerItem() {
        loadPreviewIntoCache(mWidgetItem, SIZE_10_10, BITMAP);
        loadPreviewIntoCache(mWidgetItem, SIZE_20_20, BITMAP2);

        assertThat(mLoader.getPreview(mWidgetItem, SIZE_10_10)).isEqualTo(BITMAP);
        assertThat(mLoader.getPreview(mWidgetItem, SIZE_20_20)).isEqualTo(BITMAP2);
    }

    @Test
    public void loadPreview_notInCache_shouldStartLoading() {
        mLoader.loadPreview(mTestActivity, mWidgetItem, SIZE_10_10, mPreviewLoadedCallback);

        verify(mDelegate).loadPreview(eq(mTestActivity), eq(mWidgetItem), eq(SIZE_10_10), any());
        verifyZeroInteractions(mPreviewLoadedCallback);
    }

    @Test
    public void loadPreview_thenLoaded_shouldCallBack() {
        mLoader.loadPreview(mTestActivity, mWidgetItem, SIZE_10_10, mPreviewLoadedCallback);
        verify(mDelegate).loadPreview(any(), any(), any(), mCallbackCaptor.capture());
        WidgetPreviewLoadedCallback loaderCallback = mCallbackCaptor.getValue();

        loaderCallback.onPreviewLoaded(BITMAP);

        verify(mPreviewLoadedCallback).onPreviewLoaded(BITMAP);
    }

    @Test
    public void loadPreview_thenCancelled_shouldCancelDelegateRequest() {
        CancellationSignal cancellationSignal =
                mLoader.loadPreview(mTestActivity, mWidgetItem, SIZE_10_10, mPreviewLoadedCallback);

        cancellationSignal.cancel();

        verify(mCancellationSignal).cancel();
        verifyZeroInteractions(mPreviewLoadedCallback);
        assertThat(mLoader.getPreview(mWidgetItem, SIZE_10_10)).isNull();
    }

    @Test
    public void loadPreview_thenCancelled_otherCallListening_shouldNotCancelDelegateRequest() {
        CancellationSignal cancellationSignal1 =
                mLoader.loadPreview(mTestActivity, mWidgetItem, SIZE_10_10, mPreviewLoadedCallback);
        mLoader.loadPreview(mTestActivity, mWidgetItem, SIZE_10_10, mPreviewLoadedCallback2);

        cancellationSignal1.cancel();

        verifyZeroInteractions(mCancellationSignal);
    }

    @Test
    public void loadPreview_thenCancelled_otherCallListening_loaded_shouldCallBackToNonCancelled() {
        CancellationSignal cancellationSignal1 =
                mLoader.loadPreview(mTestActivity, mWidgetItem, SIZE_10_10, mPreviewLoadedCallback);
        mLoader.loadPreview(mTestActivity, mWidgetItem, SIZE_10_10, mPreviewLoadedCallback2);
        verify(mDelegate).loadPreview(any(), any(), any(), mCallbackCaptor.capture());
        WidgetPreviewLoadedCallback loaderCallback = mCallbackCaptor.getValue();

        cancellationSignal1.cancel();
        loaderCallback.onPreviewLoaded(BITMAP);

        verifyZeroInteractions(mPreviewLoadedCallback);
        verify(mPreviewLoadedCallback2).onPreviewLoaded(BITMAP);
        assertThat(mLoader.getPreview(mWidgetItem, SIZE_10_10)).isEqualTo(BITMAP);
    }

    @Test
    public void loadPreview_thenCancelled_bothCallsCancelled_shouldCancelDelegateRequest() {
        CancellationSignal cancellationSignal1 =
                mLoader.loadPreview(mTestActivity, mWidgetItem, SIZE_10_10, mPreviewLoadedCallback);
        CancellationSignal cancellationSignal2 =
                mLoader.loadPreview(
                        mTestActivity, mWidgetItem, SIZE_10_10, mPreviewLoadedCallback2);

        cancellationSignal1.cancel();
        cancellationSignal2.cancel();

        verify(mCancellationSignal).cancel();
        verifyZeroInteractions(mPreviewLoadedCallback);
        verifyZeroInteractions(mPreviewLoadedCallback2);
        assertThat(mLoader.getPreview(mWidgetItem, SIZE_10_10)).isNull();
    }

    @Test
    public void loadPreview_multipleCallbacks_shouldOnlyCallDelegateOnce() {
        mLoader.loadPreview(mTestActivity, mWidgetItem, SIZE_10_10, mPreviewLoadedCallback);
        mLoader.loadPreview(mTestActivity, mWidgetItem, SIZE_10_10, mPreviewLoadedCallback2);

        verify(mDelegate).loadPreview(any(), any(), any(), any());
    }

    @Test
    public void loadPreview_multipleCallbacks_shouldForwardResultToEachCallback() {
        mLoader.loadPreview(mTestActivity, mWidgetItem, SIZE_10_10, mPreviewLoadedCallback);
        mLoader.loadPreview(mTestActivity, mWidgetItem, SIZE_10_10, mPreviewLoadedCallback2);

        verify(mDelegate).loadPreview(any(), any(), any(), mCallbackCaptor.capture());
        WidgetPreviewLoadedCallback loaderCallback = mCallbackCaptor.getValue();

        loaderCallback.onPreviewLoaded(BITMAP);

        verify(mPreviewLoadedCallback).onPreviewLoaded(BITMAP);
        verify(mPreviewLoadedCallback2).onPreviewLoaded(BITMAP);
    }

    @Test
    public void loadPreview_inCache_shouldCallBackImmediately() {
        loadPreviewIntoCache(mWidgetItem, SIZE_10_10, BITMAP);
        reset(mDelegate);

        mLoader.loadPreview(mTestActivity, mWidgetItem, SIZE_10_10, mPreviewLoadedCallback);

        verify(mPreviewLoadedCallback).onPreviewLoaded(BITMAP);
        verifyZeroInteractions(mDelegate);
    }

    @Test
    public void loadPreview_thenLoaded_thenCancelled_shouldNotRemovePreviewFromCache() {
        CancellationSignal cancellationSignal =
                mLoader.loadPreview(mTestActivity, mWidgetItem, SIZE_10_10, mPreviewLoadedCallback);
        verify(mDelegate).loadPreview(any(), any(), any(), mCallbackCaptor.capture());
        WidgetPreviewLoadedCallback loaderCallback = mCallbackCaptor.getValue();
        loaderCallback.onPreviewLoaded(BITMAP);

        cancellationSignal.cancel();

        assertThat(mLoader.getPreview(mWidgetItem, SIZE_10_10)).isEqualTo(BITMAP);
    }

    @Test
    public void isPreviewLoaded_notLoaded_shouldReturnFalse() {
        assertThat(mLoader.isPreviewLoaded(mWidgetItem, SIZE_10_10)).isFalse();
    }

    @Test
    public void isPreviewLoaded_otherSizeLoaded_shouldReturnFalse() {
        loadPreviewIntoCache(mWidgetItem, SIZE_20_20, BITMAP);

        assertThat(mLoader.isPreviewLoaded(mWidgetItem, SIZE_10_10)).isFalse();
    }

    @Test
    public void isPreviewLoaded_otherItemLoaded_shouldReturnFalse() {
        loadPreviewIntoCache(mWidgetItem2, SIZE_10_10, BITMAP);

        assertThat(mLoader.isPreviewLoaded(mWidgetItem, SIZE_10_10)).isFalse();
    }

    @Test
    public void isPreviewLoaded_loaded_shouldReturnTrue() {
        loadPreviewIntoCache(mWidgetItem, SIZE_10_10, BITMAP);

        assertThat(mLoader.isPreviewLoaded(mWidgetItem, SIZE_10_10)).isTrue();
    }

    @Test
    public void clearPreviews_notInCache_shouldBeNoOp() {
        mLoader.clearPreviews(Collections.singletonList(mWidgetItem));

        assertThat(mLoader.isPreviewLoaded(mWidgetItem, SIZE_10_10)).isFalse();
    }

    @Test
    public void clearPreviews_inCache_shouldRemovePreview() {
        loadPreviewIntoCache(mWidgetItem, SIZE_10_10, BITMAP);

        mLoader.clearPreviews(Collections.singletonList(mWidgetItem));

        assertThat(mLoader.isPreviewLoaded(mWidgetItem, SIZE_10_10)).isFalse();
    }

    @Test
    public void clearPreviews_inCache_multipleSizes_shouldRemoveAllSizes() {
        loadPreviewIntoCache(mWidgetItem, SIZE_10_10, BITMAP);
        loadPreviewIntoCache(mWidgetItem, SIZE_20_20, BITMAP);

        mLoader.clearPreviews(Collections.singletonList(mWidgetItem));

        assertThat(mLoader.isPreviewLoaded(mWidgetItem, SIZE_10_10)).isFalse();
        assertThat(mLoader.isPreviewLoaded(mWidgetItem, SIZE_20_20)).isFalse();
    }

    @Test
    public void clearPreviews_inCache_otherItems_shouldOnlyRemoveSpecifiedItems() {
        loadPreviewIntoCache(mWidgetItem, SIZE_10_10, BITMAP);
        loadPreviewIntoCache(mWidgetItem2, SIZE_10_10, BITMAP);

        mLoader.clearPreviews(Collections.singletonList(mWidgetItem));

        assertThat(mLoader.isPreviewLoaded(mWidgetItem, SIZE_10_10)).isFalse();
        assertThat(mLoader.isPreviewLoaded(mWidgetItem2, SIZE_10_10)).isTrue();
    }

    @Test
    public void clearPreviews_inCache_otherItems_shouldRemoveAllSpecifiedItems() {
        loadPreviewIntoCache(mWidgetItem, SIZE_10_10, BITMAP);
        loadPreviewIntoCache(mWidgetItem2, SIZE_10_10, BITMAP);

        mLoader.clearPreviews(Arrays.asList(mWidgetItem, mWidgetItem2));

        assertThat(mLoader.isPreviewLoaded(mWidgetItem, SIZE_10_10)).isFalse();
        assertThat(mLoader.isPreviewLoaded(mWidgetItem2, SIZE_10_10)).isFalse();
    }

    @Test
    public void clearPreviews_loading_shouldCancelLoad() {
        mLoader.loadPreview(mTestActivity, mWidgetItem, SIZE_10_10, mPreviewLoadedCallback);

        mLoader.clearPreviews(Collections.singletonList(mWidgetItem));

        verify(mCancellationSignal).cancel();
    }

    @Test
    public void clearAll_cacheEmpty_shouldBeNoOp() {
        mLoader.clearAll();

        assertThat(mLoader.isPreviewLoaded(mWidgetItem, SIZE_10_10)).isFalse();
    }

    @Test
    public void clearAll_inCache_shouldRemovePreview() {
        loadPreviewIntoCache(mWidgetItem, SIZE_10_10, BITMAP);

        mLoader.clearAll();

        assertThat(mLoader.isPreviewLoaded(mWidgetItem, SIZE_10_10)).isFalse();
    }

    @Test
    public void clearAll_inCache_multipleSizes_shouldRemoveAllSizes() {
        loadPreviewIntoCache(mWidgetItem, SIZE_10_10, BITMAP);
        loadPreviewIntoCache(mWidgetItem, SIZE_20_20, BITMAP);

        mLoader.clearAll();

        assertThat(mLoader.isPreviewLoaded(mWidgetItem, SIZE_10_10)).isFalse();
        assertThat(mLoader.isPreviewLoaded(mWidgetItem, SIZE_20_20)).isFalse();
    }

    @Test
    public void clearAll_inCache_multipleItems_shouldRemoveAll() {
        loadPreviewIntoCache(mWidgetItem, SIZE_10_10, BITMAP);
        loadPreviewIntoCache(mWidgetItem, SIZE_20_20, BITMAP);
        loadPreviewIntoCache(mWidgetItem2, SIZE_20_20, BITMAP);

        mLoader.clearAll();

        assertThat(mLoader.isPreviewLoaded(mWidgetItem, SIZE_10_10)).isFalse();
        assertThat(mLoader.isPreviewLoaded(mWidgetItem, SIZE_20_20)).isFalse();
        assertThat(mLoader.isPreviewLoaded(mWidgetItem2, SIZE_20_20)).isFalse();
    }

    @Test
    public void clearAll_loading_shouldCancelLoad() {
        mLoader.loadPreview(mTestActivity, mWidgetItem, SIZE_10_10, mPreviewLoadedCallback);

        mLoader.clearAll();

        verify(mCancellationSignal).cancel();
    }

    private void loadPreviewIntoCache(WidgetItem widgetItem, Size size, Bitmap bitmap) {
        reset(mDelegate);
        mLoader.loadPreview(mTestActivity, widgetItem, size, ignored -> {});
        verify(mDelegate).loadPreview(any(), any(), any(), mCallbackCaptor.capture());
        WidgetPreviewLoadedCallback loaderCallback = mCallbackCaptor.getValue();

        loaderCallback.onPreviewLoaded(bitmap);
    }
}
