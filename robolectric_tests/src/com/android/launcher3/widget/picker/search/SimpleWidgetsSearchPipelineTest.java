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

package com.android.launcher3.widget.picker.search;

import static android.os.Looper.getMainLooper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.robolectric.Shadows.shadowOf;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.UserHandle;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.ComponentWithLabel;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.model.WidgetsListContentEntry;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class SimpleWidgetsSearchPipelineTest {
    private static final SimpleWidgetsSearchPipeline.StringMatcher MATCHER =
            SimpleWidgetsSearchPipeline.StringMatcher.getInstance();

    @Mock private IconCache mIconCache;

    private InvariantDeviceProfile mTestProfile;
    private WidgetsListHeaderEntry mCalendarHeaderEntry;
    private WidgetsListContentEntry mCalendarContentEntry;
    private WidgetsListHeaderEntry mCameraHeaderEntry;
    private WidgetsListContentEntry mCameraContentEntry;
    private WidgetsListHeaderEntry mClockHeaderEntry;
    private WidgetsListContentEntry mClockContentEntry;
    private Context mContext;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        doAnswer(invocation -> ((ComponentWithLabel) invocation.getArgument(0))
                .getComponent().getPackageName())
                .when(mIconCache).getTitleNoCache(any());
        mTestProfile = new InvariantDeviceProfile();
        mTestProfile.numRows = 5;
        mTestProfile.numColumns = 5;
        mContext = RuntimeEnvironment.application;

        mCalendarHeaderEntry =
                createWidgetsHeaderEntry("com.example.android.Calendar", "Calendar", 2);
        mCalendarContentEntry =
                createWidgetsContentEntry("com.example.android.Calendar", "Calendar", 2);
        mCameraHeaderEntry = createWidgetsHeaderEntry("com.example.android.Camera", "Camera", 5);
        mCameraContentEntry = createWidgetsContentEntry("com.example.android.Camera", "Camera", 5);
        mClockHeaderEntry = createWidgetsHeaderEntry("com.example.android.Clock", "Clock", 3);
        mClockContentEntry = createWidgetsContentEntry("com.example.android.Clock", "Clock", 3);
    }

    @Test
    public void query_shouldInformCallbackWithResultsMatchedOnAppName() {
        SimpleWidgetsSearchPipeline pipeline = new SimpleWidgetsSearchPipeline(
                List.of(mCalendarHeaderEntry, mCalendarContentEntry, mCameraHeaderEntry,
                        mCameraContentEntry, mClockHeaderEntry, mClockContentEntry));

        pipeline.query("Ca", results ->
                assertEquals(results, List.of(mCalendarHeaderEntry, mCalendarContentEntry,
                        mCameraHeaderEntry, mCameraContentEntry)));
        shadowOf(getMainLooper()).idle();
    }

    @Test
    public void testMatches() {
        assertTrue(MATCHER.matches("q", "Q"));
        assertTrue(MATCHER.matches("q", "  Q"));
        assertTrue(MATCHER.matches("e", "elephant"));
        assertTrue(MATCHER.matches("eL", "Elephant"));
        assertTrue(MATCHER.matches("elephant ", "elephant"));
        assertTrue(MATCHER.matches("whitec", "white cow"));
        assertTrue(MATCHER.matches("white  c", "white cow"));
        assertTrue(MATCHER.matches("white ", "white cow"));
        assertTrue(MATCHER.matches("white c", "white cow"));
        assertTrue(MATCHER.matches("电", "电子邮件"));
        assertTrue(MATCHER.matches("电子", "电子邮件"));
        assertTrue(MATCHER.matches("다", "다운로드"));
        assertTrue(MATCHER.matches("드", "드라이브"));
        assertTrue(MATCHER.matches("åbç", "abc"));
        assertTrue(MATCHER.matches("ål", "Alpha"));

        assertFalse(MATCHER.matches("phant", "elephant"));
        assertFalse(MATCHER.matches("elephants", "elephant"));
        assertFalse(MATCHER.matches("cow", "white cow"));
        assertFalse(MATCHER.matches("cow", "whiteCow"));
        assertFalse(MATCHER.matches("dog", "cats&Dogs"));
        assertFalse(MATCHER.matches("ba", "Bot"));
        assertFalse(MATCHER.matches("ba", "bot"));
        assertFalse(MATCHER.matches("子", "电子邮件"));
        assertFalse(MATCHER.matches("邮件", "电子邮件"));
        assertFalse(MATCHER.matches("ㄷ", "다운로드 드라이브"));
        assertFalse(MATCHER.matches("ㄷㄷ", "다운로드 드라이브"));
        assertFalse(MATCHER.matches("åç", "abc"));
    }

    private WidgetsListHeaderEntry createWidgetsHeaderEntry(String packageName, String appName,
            int numOfWidgets) {
        List<WidgetItem> widgetItems = generateWidgetItems(packageName, numOfWidgets);
        PackageItemInfo pInfo = createPackageItemInfo(packageName, appName,
                widgetItems.get(0).user);

        return new WidgetsListHeaderEntry(pInfo, /* titleSectionName= */ "", widgetItems);
    }

    private WidgetsListContentEntry createWidgetsContentEntry(String packageName, String appName,
            int numOfWidgets) {
        List<WidgetItem> widgetItems = generateWidgetItems(packageName, numOfWidgets);
        PackageItemInfo pInfo = createPackageItemInfo(packageName, appName,
                widgetItems.get(0).user);

        return new WidgetsListContentEntry(pInfo, /* titleSectionName= */ "", widgetItems);
    }

    private PackageItemInfo createPackageItemInfo(String packageName, String appName,
            UserHandle userHandle) {
        PackageItemInfo pInfo = new PackageItemInfo(packageName);
        pInfo.title = appName;
        pInfo.user = userHandle;
        pInfo.bitmap = BitmapInfo.of(Bitmap.createBitmap(10, 10, Bitmap.Config.ALPHA_8), 0);
        return pInfo;
    }

    private List<WidgetItem> generateWidgetItems(String packageName, int numOfWidgets) {
        ShadowPackageManager packageManager = shadowOf(mContext.getPackageManager());
        ArrayList<WidgetItem> widgetItems = new ArrayList<>();
        for (int i = 0; i < numOfWidgets; i++) {
            ComponentName cn = ComponentName.createRelative(packageName, ".SampleWidget" + i);
            AppWidgetProviderInfo widgetInfo = new AppWidgetProviderInfo();
            widgetInfo.provider = cn;
            ReflectionHelpers.setField(widgetInfo, "providerInfo",
                    packageManager.addReceiverIfNotPresent(cn));

            WidgetItem widgetItem = new WidgetItem(
                    LauncherAppWidgetProviderInfo.fromProviderInfo(mContext, widgetInfo),
                    mTestProfile, mIconCache);
            widgetItems.add(widgetItem);
        }
        return widgetItems;
    }
}
