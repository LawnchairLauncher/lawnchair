/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.launcher3.allapps;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_PRIVATE_SPACE_HEADER;
import static com.android.launcher3.allapps.UserProfileManager.STATE_DISABLED;
import static com.android.launcher3.allapps.UserProfileManager.STATE_ENABLED;
import static com.android.launcher3.allapps.UserProfileManager.STATE_TRANSITION;

import static org.junit.Assert.assertEquals;
import static org.mockito.AdditionalAnswers.answer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.Flags;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.util.ActivityContextWrapper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@RunWith(AndroidJUnit4.class)
public class AlphabeticalAppsListTest {

    private static final UserHandle MAIN_HANDLE = Process.myUserHandle();
    private static final UserHandle PRIVATE_HANDLE = new UserHandle(11);

    private static final int PRIVATE_SPACE_HEADER_ITEM_COUNT = 1;
    private static final int MAIN_USER_APP_COUNT = 2;
    private static final int PRIVATE_USER_APP_COUNT = 1;

    private AlphabeticalAppsList<?> mAlphabeticalAppsList;
    @Mock
    private AllAppsStore<?> mAllAppsStore;
    @Mock
    private PrivateProfileManager mPrivateProfileManager;
    private Context mContext;

    @Rule
    public final SetFlagsRule mSetFlagsRule =
            new SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = new ActivityContextWrapper(getApplicationContext());
        when(mPrivateProfileManager.getItemInfoMatcher()).thenReturn(info ->
                info != null && info.user.equals(PRIVATE_HANDLE));
        mAlphabeticalAppsList = new AlphabeticalAppsList<>(mContext, mAllAppsStore,
                null, mPrivateProfileManager);
    }

    @Test
    public void privateProfileEnabled_allPrivateProfileViewsArePresent() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_PRIVATE_SPACE);
        when(mAllAppsStore.getApps()).thenReturn(createAppInfoListForMainAndPrivateUser());
        when(mPrivateProfileManager.addPrivateSpaceHeader(any()))
                .thenAnswer(answer(this::addPrivateSpaceHeader));
        when(mPrivateProfileManager.getCurrentState()).thenReturn(STATE_ENABLED);

        mAlphabeticalAppsList.updateItemFilter(info -> info != null
                && info.user.equals(MAIN_HANDLE));

        assertEquals(MAIN_USER_APP_COUNT + PRIVATE_SPACE_HEADER_ITEM_COUNT
                + PRIVATE_USER_APP_COUNT, mAlphabeticalAppsList.getAdapterItems().size());
        assertEquals(PRIVATE_SPACE_HEADER_ITEM_COUNT,
                mAlphabeticalAppsList.getAdapterItems().stream().filter(item ->
                        item.viewType == VIEW_TYPE_PRIVATE_SPACE_HEADER).toList().size());
        assertEquals(PRIVATE_USER_APP_COUNT,
                mAlphabeticalAppsList.getAdapterItems().stream().filter(item ->
                        item.itemInfo != null
                                && item.itemInfo.user.equals(PRIVATE_HANDLE)).toList().size());
    }

    @Test
    public void privateProfileDisabled_onlyPrivateProfileHeaderViewIsPresent() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_PRIVATE_SPACE);
        when(mAllAppsStore.getApps()).thenReturn(createAppInfoListForMainAndPrivateUser());
        when(mPrivateProfileManager.addPrivateSpaceHeader(any()))
                .thenAnswer(answer(this::addPrivateSpaceHeader));
        when(mPrivateProfileManager.getCurrentState()).thenReturn(STATE_DISABLED);

        mAlphabeticalAppsList.updateItemFilter(info -> info != null
                && info.user.equals(MAIN_HANDLE));

        assertEquals(MAIN_USER_APP_COUNT + PRIVATE_SPACE_HEADER_ITEM_COUNT,
                mAlphabeticalAppsList.getAdapterItems().size());
        assertEquals(PRIVATE_SPACE_HEADER_ITEM_COUNT, mAlphabeticalAppsList
                .getAdapterItems().stream().filter(item ->
                        item.viewType == VIEW_TYPE_PRIVATE_SPACE_HEADER).toList().size());
        assertEquals(0, mAlphabeticalAppsList.getAdapterItems().stream().filter(item ->
                item.itemInfo != null
                        && item.itemInfo.user.equals(PRIVATE_HANDLE)).toList().size());
    }

    @Test
    public void privateProfileTransitioning_onlyPrivateProfileHeaderViewIsPresent() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_PRIVATE_SPACE);
        when(mAllAppsStore.getApps()).thenReturn(createAppInfoListForMainAndPrivateUser());
        when(mPrivateProfileManager.addPrivateSpaceHeader(any()))
                .thenAnswer(answer(this::addPrivateSpaceHeader));
        when(mPrivateProfileManager.getCurrentState()).thenReturn(STATE_TRANSITION);

        mAlphabeticalAppsList.updateItemFilter(info -> info != null
                && info.user.equals(MAIN_HANDLE));

        assertEquals(MAIN_USER_APP_COUNT + PRIVATE_SPACE_HEADER_ITEM_COUNT,
                mAlphabeticalAppsList.getAdapterItems().size());
        assertEquals(PRIVATE_SPACE_HEADER_ITEM_COUNT, mAlphabeticalAppsList
                .getAdapterItems().stream().filter(item ->
                        item.viewType == VIEW_TYPE_PRIVATE_SPACE_HEADER).toList().size());
        assertEquals(0, mAlphabeticalAppsList.getAdapterItems().stream().filter(item ->
                item.itemInfo != null
                        && item.itemInfo.user.equals(PRIVATE_HANDLE)).toList().size());
    }

    @Test
    public void privateProfileHidden_noPrivateProfileViewIsPresent() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_PRIVATE_SPACE);
        when(mAllAppsStore.getApps()).thenReturn(createAppInfoListForMainAndPrivateUser());
        when(mPrivateProfileManager.isPrivateSpaceHidden()).thenReturn(true);

        mAlphabeticalAppsList.updateItemFilter(info -> info != null
                && info.user.equals(MAIN_HANDLE));

        assertEquals(MAIN_USER_APP_COUNT, mAlphabeticalAppsList.getAdapterItems().size());
        assertEquals(0, mAlphabeticalAppsList.getAdapterItems().stream().filter(item ->
                item.viewType == VIEW_TYPE_PRIVATE_SPACE_HEADER).toList().size());
        assertEquals(0, mAlphabeticalAppsList.getAdapterItems().stream().filter(item ->
                item.itemInfo != null
                        && item.itemInfo.user.equals(PRIVATE_HANDLE)).toList().size());
    }

    @Test
    public void privateProfileNotPresent_onlyMainUserViewsArePresent() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_PRIVATE_SPACE);
        when(mAllAppsStore.getApps()).thenReturn(createAppInfoListForMainUser());

        mAlphabeticalAppsList.updateItemFilter(info -> info != null
                && info.user.equals(MAIN_HANDLE));

        assertEquals(2, mAlphabeticalAppsList.getAdapterItems().size());
        assertEquals(0, mAlphabeticalAppsList.getAdapterItems().stream().filter(item ->
                        item.itemInfo != null
                                && item.itemInfo.itemType == VIEW_TYPE_PRIVATE_SPACE_HEADER)
                .toList().size());
        assertEquals(0, mAlphabeticalAppsList.getAdapterItems().stream().filter(item ->
                        item.itemInfo != null && item.itemInfo.user.equals(PRIVATE_HANDLE))
                .toList().size());
    }

    private int addPrivateSpaceHeader(List<BaseAllAppsAdapter.AdapterItem> adapterItemList) {
        adapterItemList.add(new BaseAllAppsAdapter.AdapterItem(VIEW_TYPE_PRIVATE_SPACE_HEADER));
        return adapterItemList.size();
    }

    private AppInfo[] createAppInfoListForMainUser() {
        ComponentName gmailComponentName = new ComponentName(mContext,
                "com.android.launcher3.tests.Activity" + "Gmail");
        AppInfo gmailAppInfo = new
                AppInfo(gmailComponentName, "Gmail", MAIN_HANDLE, new Intent());
        ComponentName driveComponentName = new ComponentName(mContext,
                "com.android.launcher3.tests.Activity" + "Drive");
        AppInfo driveAppInfo = new
                AppInfo(driveComponentName, "Drive", MAIN_HANDLE, new Intent());
        return new AppInfo[]{gmailAppInfo, driveAppInfo};
    }

    private AppInfo[] createAppInfoListForPrivateUser() {
        ComponentName privateMessengercomponentName = new ComponentName(mContext,
                "com.android.launcher3.tests.Activity" + "PrivateMessenger");
        AppInfo privateMessengerAppInfo = new AppInfo(privateMessengercomponentName,
                "Private Messenger", PRIVATE_HANDLE, new Intent());
        return new AppInfo[]{privateMessengerAppInfo};
    }

    private AppInfo[] createAppInfoListForMainAndPrivateUser() {
        return Stream.concat(Arrays.stream(createAppInfoListForMainUser()),
                Arrays.stream(createAppInfoListForPrivateUser())).toArray(AppInfo[]::new);
    }

}
