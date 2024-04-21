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
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.answer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.R;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.ActivityContextWrapper;
import com.android.launcher3.util.UserIconInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PrivateSpaceHeaderViewTest {

    private static final UserHandle MAIN_HANDLE = Process.myUserHandle();
    private static final UserHandle PRIVATE_HANDLE = new UserHandle(11);
    private static final UserIconInfo MAIN_ICON_INFO =
            new UserIconInfo(MAIN_HANDLE, UserIconInfo.TYPE_MAIN);
    private static final UserIconInfo PRIVATE_ICON_INFO =
            new UserIconInfo(PRIVATE_HANDLE, UserIconInfo.TYPE_PRIVATE);
    private static final String CAMERA_PACKAGE_NAME = "com.android.launcher3.tests.camera";
    private static final int CONTAINER_HEADER_ELEMENT_COUNT = 1;
    private static final int LOCK_UNLOCK_BUTTON_COUNT = 1;
    private static final int PS_SETTINGS_BUTTON_COUNT_VISIBLE = 1;
    private static final int PS_SETTINGS_BUTTON_COUNT_INVISIBLE = 0;
    private static final int PS_TRANSITION_IMAGE_COUNT = 1;
    private static final int NUM_APP_COLS = 4;
    private static final int NUM_PRIVATE_SPACE_APPS = 50;
    private static final int ALL_APPS_HEIGHT = 10;
    private static final int ALL_APPS_CELL_HEIGHT = 1;
    private static final int PS_HEADER_HEIGHT = 1;
    private static final int BIGGER_PS_HEADER_HEIGHT = 2;
    private static final int SCROLL_NO_WHERE = -1;
    private static final float HEADER_PROTECTION_HEIGHT = 1F;

    private Context mContext;
    private RelativeLayout mPsHeaderLayout;
    private AlphabeticalAppsList<?> mAlphabeticalAppsList;
    private PrivateProfileManager mPrivateProfileManager;
    @Mock
    private ActivityAllAppsContainerView mAllApps;
    @Mock
    private AllAppsStore<?> mAllAppsStore;
    @Mock
    private UserCache mUserCache;
    @Mock
    private UserManager mUserManager;
    @Mock
    private StatsLogManager mStatsLogManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = new ActivityContextWrapper(getApplicationContext());
        when(mAllApps.getContext()).thenReturn(mContext);
        when(mUserCache.getUserInfo(PRIVATE_HANDLE)).thenReturn(PRIVATE_ICON_INFO);
        when(mUserCache.getUserProfiles())
                .thenReturn(Arrays.asList(MAIN_HANDLE, PRIVATE_HANDLE));
        when(mUserCache.getUserInfo(Process.myUserHandle())).thenReturn(MAIN_ICON_INFO);
        mPrivateProfileManager = new PrivateProfileManager(mUserManager,
                mAllApps, mStatsLogManager, mUserCache);
        mPsHeaderLayout = (RelativeLayout) LayoutInflater.from(mContext).inflate(
                R.layout.private_space_header, null);
    }

    @Test
    public void privateProfileDisabled_psHeaderContainsLockedView() throws Exception {
        Bitmap unlockButton = getBitmap(mContext.getDrawable(R.drawable.ic_lock));
        PrivateProfileManager privateProfileManager = spy(mPrivateProfileManager);
        when(privateProfileManager.getCurrentState()).thenReturn(STATE_DISABLED);
        privateProfileManager.addPrivateSpaceHeaderViewElements(mPsHeaderLayout);
        awaitTasksCompleted();

        int totalContainerHeaderView = 0;
        int totalLockUnlockButtonView = 0;
        for (int i = 0; i < mPsHeaderLayout.getChildCount(); i++) {
            View view = mPsHeaderLayout.getChildAt(i);
            if (view.getId() == R.id.ps_container_header) {
                totalContainerHeaderView += 1;
                assertEquals(View.VISIBLE, view.getVisibility());
            } else if (view.getId() == R.id.settingsAndLockGroup) {
                ImageView lockIcon = view.findViewById(R.id.lock_icon);
                assertTrue(getBitmap(lockIcon.getDrawable()).sameAs(unlockButton));
                assertEquals(View.VISIBLE, lockIcon.getVisibility());

                // Verify textView shouldn't be showing when disabled.
                TextView lockText = view.findViewById(R.id.lock_text);
                assertEquals(View.GONE, lockText.getVisibility());
                totalLockUnlockButtonView += 1;
            } else {
                assertEquals(View.GONE, view.getVisibility());
            }
        }

        assertEquals(CONTAINER_HEADER_ELEMENT_COUNT, totalContainerHeaderView);
        assertEquals(LOCK_UNLOCK_BUTTON_COUNT, totalLockUnlockButtonView);
    }

    @Test
    public void privateProfileEnabled_psHeaderContainsUnlockedView() throws Exception {
        Bitmap lockImage = getBitmap(mContext.getDrawable(R.drawable.ic_lock));
        Bitmap settingsImage = getBitmap(mContext.getDrawable(R.drawable.ic_ps_settings));
        PrivateProfileManager privateProfileManager = spy(mPrivateProfileManager);
        when(privateProfileManager.getCurrentState()).thenReturn(STATE_ENABLED);
        when(privateProfileManager.isPrivateSpaceSettingsAvailable()).thenReturn(true);
        privateProfileManager.addPrivateSpaceHeaderViewElements(mPsHeaderLayout);
        awaitTasksCompleted();

        int totalContainerHeaderView = 0;
        int totalLockUnlockButtonView = 0;
        int totalSettingsImageView = 0;
        for (int i = 0; i < mPsHeaderLayout.getChildCount(); i++) {
            View view = mPsHeaderLayout.getChildAt(i);
            if (view.getId() == R.id.ps_container_header) {
                totalContainerHeaderView += 1;
                assertEquals(View.VISIBLE, view.getVisibility());
            } else if (view.getId() == R.id.settingsAndLockGroup) {
                // Look for settings button.
                ImageButton settingsButton = view.findViewById(R.id.ps_settings_button);
                assertEquals(View.VISIBLE, settingsButton.getVisibility());
                totalSettingsImageView += 1;
                assertTrue(getBitmap(settingsButton.getDrawable()).sameAs(settingsImage));

                // Look for lock_icon and lock_text.
                ImageView lockIcon = view.findViewById(R.id.lock_icon);
                assertTrue(getBitmap(lockIcon.getDrawable()).sameAs(lockImage));
                assertEquals(View.VISIBLE, lockIcon.getVisibility());
                TextView lockText = view.findViewById(R.id.lock_text);
                assertEquals(View.VISIBLE, lockText.getVisibility());
                totalLockUnlockButtonView += 1;
            } else {
                assertEquals(View.GONE, view.getVisibility());
            }
        }

        assertEquals(CONTAINER_HEADER_ELEMENT_COUNT, totalContainerHeaderView);
        assertEquals(LOCK_UNLOCK_BUTTON_COUNT, totalLockUnlockButtonView);
        assertEquals(PS_SETTINGS_BUTTON_COUNT_VISIBLE, totalSettingsImageView);
    }

    @Test
    public void privateProfileEnabledAndNoSettingsIntent_psHeaderContainsUnlockedView()
            throws Exception {
        Bitmap lockImage = getBitmap(mContext.getDrawable(R.drawable.ic_lock));
        PrivateProfileManager privateProfileManager = spy(mPrivateProfileManager);
        when(privateProfileManager.getCurrentState()).thenReturn(STATE_ENABLED);
        when(privateProfileManager.isPrivateSpaceSettingsAvailable()).thenReturn(false);
        privateProfileManager.addPrivateSpaceHeaderViewElements(mPsHeaderLayout);
        awaitTasksCompleted();

        int totalContainerHeaderView = 0;
        int totalLockUnlockButtonView = 0;
        int totalSettingsImageView = 0;
        for (int i = 0; i < mPsHeaderLayout.getChildCount(); i++) {
            View view = mPsHeaderLayout.getChildAt(i);
            if (view.getId() == R.id.ps_container_header) {
                totalContainerHeaderView += 1;
                assertEquals(View.VISIBLE, view.getVisibility());
            } else if (view.getId() == R.id.settingsAndLockGroup) {
                // Ensure there is no settings button.
                ImageButton settingsImage = view.findViewById(R.id.ps_settings_button);
                assertEquals(View.GONE, settingsImage.getVisibility());

                // Check lock icon and lock text is there.
                ImageView lockIcon = view.findViewById(R.id.lock_icon);
                assertTrue(getBitmap(lockIcon.getDrawable()).sameAs(lockImage));
                assertEquals(View.VISIBLE, lockIcon.getVisibility());
                TextView lockText = view.findViewById(R.id.lock_text);
                assertEquals(View.VISIBLE, lockText.getVisibility());
                totalLockUnlockButtonView += 1;
            } else {
                assertEquals(View.GONE, view.getVisibility());
            }
        }

        assertEquals(CONTAINER_HEADER_ELEMENT_COUNT, totalContainerHeaderView);
        assertEquals(LOCK_UNLOCK_BUTTON_COUNT, totalLockUnlockButtonView);
        assertEquals(PS_SETTINGS_BUTTON_COUNT_INVISIBLE, totalSettingsImageView);
    }

    @Test
    public void privateProfileTransitioning_psHeaderContainsTransitionView() throws Exception {
        Bitmap transitionImage = getBitmap(mContext.getDrawable(R.drawable.bg_ps_transition_image));
        PrivateProfileManager privateProfileManager = spy(mPrivateProfileManager);
        when(privateProfileManager.getCurrentState()).thenReturn(STATE_TRANSITION);
        privateProfileManager.addPrivateSpaceHeaderViewElements(mPsHeaderLayout);
        awaitTasksCompleted();

        int totalContainerHeaderView = 0;
        int totalLockUnlockButtonView = 0;
        for (int i = 0; i < mPsHeaderLayout.getChildCount(); i++) {
            View view = mPsHeaderLayout.getChildAt(i);
            if (view.getId() == R.id.ps_container_header) {
                totalContainerHeaderView += 1;
                assertEquals(View.VISIBLE, view.getVisibility());
            } else if (view.getId() == R.id.ps_transition_image
                    && view instanceof ImageView imageView) {
                totalLockUnlockButtonView += 1;
                assertEquals(View.VISIBLE, view.getVisibility());
                assertTrue(getBitmap(imageView.getDrawable()).sameAs(transitionImage));
            } else if (view.getId() == R.id.settingsAndLockGroup) {
                LinearLayout lockUnlockButton = view.findViewById(R.id.ps_lock_unlock_button);
                assertEquals(View.GONE, lockUnlockButton.getVisibility());
            } else {
                assertEquals(View.GONE, view.getVisibility());
            }
        }

        assertEquals(CONTAINER_HEADER_ELEMENT_COUNT, totalContainerHeaderView);
        assertEquals(PS_TRANSITION_IMAGE_COUNT, totalLockUnlockButtonView);
    }

    @Test
    public void scrollForViewToBeVisibleInContainer_withHeader() {
        when(mAllAppsStore.getApps()).thenReturn(createAppInfoList());
        PrivateProfileManager privateProfileManager = spy(mPrivateProfileManager);
        when(privateProfileManager.getCurrentState()).thenReturn(STATE_ENABLED);
        doReturn(splitIntoUserInstalledAndSystemApps()).when(privateProfileManager)
                .splitIntoUserInstalledAndSystemApps(any());
        doReturn(0).when(privateProfileManager).addPrivateSpaceHeader(any());
        doAnswer(answer(this::addPrivateSpaceHeader)).when(privateProfileManager)
                .addPrivateSpaceHeader(any());
        doNothing().when(privateProfileManager).addPrivateSpaceInstallAppButton(any());
        doReturn(0).when(privateProfileManager).addSystemAppsDivider(any());
        when(mAllApps.getHeight()).thenReturn(ALL_APPS_HEIGHT);
        when(mAllApps.getHeaderProtectionHeight()).thenReturn(HEADER_PROTECTION_HEIGHT);
        when(mAllApps.isUsingTabs()).thenReturn(true);
        mAlphabeticalAppsList = new AlphabeticalAppsList<>(mContext, mAllAppsStore,
                null, privateProfileManager);
        mAlphabeticalAppsList.setNumAppsPerRowAllApps(NUM_APP_COLS);
        mAlphabeticalAppsList.updateItemFilter(info -> info != null
                && info.user.equals(MAIN_HANDLE));

        int rows = (int) (ALL_APPS_HEIGHT - PS_HEADER_HEIGHT - HEADER_PROTECTION_HEIGHT);
        int position = rows * NUM_APP_COLS - (NUM_APP_COLS-1) + 1;

        // The number of adapterItems should be the private space apps + one main app + header.
        assertEquals(NUM_PRIVATE_SPACE_APPS + 1 + 1,
                mAlphabeticalAppsList.getAdapterItems().size());
        assertEquals(position,
                privateProfileManager.scrollForHeaderToBeVisibleInContainer(
                        new AllAppsRecyclerView(mContext),
                        mAlphabeticalAppsList.getAdapterItems(),
                        PS_HEADER_HEIGHT,
                        ALL_APPS_CELL_HEIGHT));
    }

    @Test
    public void scrollForViewToBeVisibleInContainer_withHeaderNoTabs() {
        when(mAllAppsStore.getApps()).thenReturn(createAppInfoList());
        PrivateProfileManager privateProfileManager = spy(mPrivateProfileManager);
        when(privateProfileManager.getCurrentState()).thenReturn(STATE_ENABLED);
        doReturn(splitIntoUserInstalledAndSystemApps()).when(privateProfileManager)
                .splitIntoUserInstalledAndSystemApps(any());
        doReturn(0).when(privateProfileManager).addPrivateSpaceHeader(any());
        doAnswer(answer(this::addPrivateSpaceHeader)).when(privateProfileManager)
                .addPrivateSpaceHeader(any());
        doNothing().when(privateProfileManager).addPrivateSpaceInstallAppButton(any());
        doReturn(0).when(privateProfileManager).addSystemAppsDivider(any());
        when(mAllApps.getHeight()).thenReturn(ALL_APPS_HEIGHT);
        when(mAllApps.getHeaderProtectionHeight()).thenReturn(HEADER_PROTECTION_HEIGHT);
        when(mAllApps.isUsingTabs()).thenReturn(false);
        mAlphabeticalAppsList = new AlphabeticalAppsList<>(mContext, mAllAppsStore,
                null, privateProfileManager);
        mAlphabeticalAppsList.setNumAppsPerRowAllApps(NUM_APP_COLS);
        mAlphabeticalAppsList.updateItemFilter(info -> info != null
                && info.user.equals(MAIN_HANDLE));

        int rows = (int) (ALL_APPS_HEIGHT - PS_HEADER_HEIGHT - HEADER_PROTECTION_HEIGHT) - 1;
        int position = rows * NUM_APP_COLS - (NUM_APP_COLS-1) + 1;

        // The number of adapterItems should be the private space apps + one main app + header.
        assertEquals(NUM_PRIVATE_SPACE_APPS + 1 + 1,
                mAlphabeticalAppsList.getAdapterItems().size());
        assertEquals(position,
                privateProfileManager.scrollForHeaderToBeVisibleInContainer(
                        new AllAppsRecyclerView(mContext),
                        mAlphabeticalAppsList.getAdapterItems(),
                        PS_HEADER_HEIGHT,
                        ALL_APPS_CELL_HEIGHT));
    }

    @Test
    public void scrollForViewToBeVisibleInContainer_withHeaderAndLessAppRowSpace() {
        when(mAllAppsStore.getApps()).thenReturn(createAppInfoList());
        PrivateProfileManager privateProfileManager = spy(mPrivateProfileManager);
        when(privateProfileManager.getCurrentState()).thenReturn(STATE_ENABLED);
        doReturn(splitIntoUserInstalledAndSystemApps()).when(privateProfileManager)
                .splitIntoUserInstalledAndSystemApps(any());
        doReturn(0).when(privateProfileManager).addPrivateSpaceHeader(any());
        doAnswer(answer(this::addPrivateSpaceHeader)).when(privateProfileManager)
                .addPrivateSpaceHeader(any());
        doNothing().when(privateProfileManager).addPrivateSpaceInstallAppButton(any());
        doReturn(0).when(privateProfileManager).addSystemAppsDivider(any());
        when(mAllApps.getHeight()).thenReturn(ALL_APPS_HEIGHT);
        when(mAllApps.isUsingTabs()).thenReturn(true);
        when(mAllApps.getHeaderProtectionHeight()).thenReturn(HEADER_PROTECTION_HEIGHT);
        mAlphabeticalAppsList = new AlphabeticalAppsList<>(mContext, mAllAppsStore,
                null, privateProfileManager);
        mAlphabeticalAppsList.setNumAppsPerRowAllApps(NUM_APP_COLS);
        mAlphabeticalAppsList.updateItemFilter(info -> info != null
                && info.user.equals(MAIN_HANDLE));

        int rows = (int) (ALL_APPS_HEIGHT - BIGGER_PS_HEADER_HEIGHT - HEADER_PROTECTION_HEIGHT);
        int position = rows * NUM_APP_COLS - (NUM_APP_COLS-1) + 1;

        // The number of adapterItems should be the private space apps + one main app + header.
        assertEquals(NUM_PRIVATE_SPACE_APPS + 1 + 1,
                mAlphabeticalAppsList.getAdapterItems().size());
        assertEquals(position,
                privateProfileManager.scrollForHeaderToBeVisibleInContainer(
                        new AllAppsRecyclerView(mContext),
                        mAlphabeticalAppsList.getAdapterItems(),
                        BIGGER_PS_HEADER_HEIGHT,
                        ALL_APPS_CELL_HEIGHT));
    }

    @Test
    public void scrollForViewToBeVisibleInContainer_withNoHeader() {
        when(mAllAppsStore.getApps()).thenReturn(createAppInfoList());
        PrivateProfileManager privateProfileManager = spy(mPrivateProfileManager);
        when(privateProfileManager.getCurrentState()).thenReturn(STATE_ENABLED);
        doReturn(splitIntoUserInstalledAndSystemApps()).when(privateProfileManager)
                .splitIntoUserInstalledAndSystemApps(any());
        doReturn(0).when(privateProfileManager).addPrivateSpaceHeader(any());
        doNothing().when(privateProfileManager).addPrivateSpaceInstallAppButton(any());
        doReturn(0).when(privateProfileManager).addSystemAppsDivider(any());
        when(mAllApps.getHeight()).thenReturn(ALL_APPS_HEIGHT);
        when(mAllApps.getHeaderProtectionHeight()).thenReturn(HEADER_PROTECTION_HEIGHT);
        mAlphabeticalAppsList = new AlphabeticalAppsList<>(mContext, mAllAppsStore,
                null, privateProfileManager);
        mAlphabeticalAppsList.setNumAppsPerRowAllApps(NUM_APP_COLS);
        mAlphabeticalAppsList.updateItemFilter(info -> info != null
                && info.user.equals(MAIN_HANDLE));

        // The number of adapterItems should be the private space apps + one main app.
        assertEquals(NUM_PRIVATE_SPACE_APPS + 1,
                mAlphabeticalAppsList.getAdapterItems().size());
        assertEquals(SCROLL_NO_WHERE, privateProfileManager.scrollForHeaderToBeVisibleInContainer(
                new AllAppsRecyclerView(mContext),
                mAlphabeticalAppsList.getAdapterItems(),
                BIGGER_PS_HEADER_HEIGHT,
                ALL_APPS_CELL_HEIGHT));
    }

    private Bitmap getBitmap(Drawable drawable) {
        Bitmap result;
        if (drawable instanceof BitmapDrawable) {
            result = ((BitmapDrawable) drawable).getBitmap();
        } else {
            int width = drawable.getIntrinsicWidth();
            int height = drawable.getIntrinsicHeight();
            // Some drawables have no intrinsic width - e.g. solid colours.
            if (width <= 0) {
                width = 1;
            }
            if (height <= 0) {
                height = 1;
            }

            result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(result);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        }
        return result;
    }

    private static void awaitTasksCompleted() throws Exception {
        UI_HELPER_EXECUTOR.submit(() -> null).get();
    }

    private int addPrivateSpaceHeader(List<BaseAllAppsAdapter.AdapterItem> adapterItemList) {
        BaseAllAppsAdapter.AdapterItem privateSpaceHeader =
                new BaseAllAppsAdapter.AdapterItem(VIEW_TYPE_PRIVATE_SPACE_HEADER);
        adapterItemList.add(privateSpaceHeader);
        return adapterItemList.size();
    }

    private AppInfo[] createAppInfoList() {
        List<AppInfo> appInfos = new ArrayList<>();
        ComponentName gmailComponentName = new ComponentName(mContext,
                "com.android.launcher3.tests.Activity" + "Gmail");
        AppInfo gmailAppInfo = new
                AppInfo(gmailComponentName, "Gmail", MAIN_HANDLE, new Intent());
        appInfos.add(gmailAppInfo);
        ComponentName privateCameraComponentName = new ComponentName(
                CAMERA_PACKAGE_NAME, "CameraActivity");
        for (int i = 0; i < NUM_PRIVATE_SPACE_APPS; i++) {
            AppInfo privateCameraAppInfo = new AppInfo(privateCameraComponentName,
                    "Private Camera " + i, PRIVATE_HANDLE, new Intent());
            appInfos.add(privateCameraAppInfo);
        }
        return appInfos.toArray(AppInfo[]::new);
    }

    private Predicate<AppInfo> splitIntoUserInstalledAndSystemApps() {
        return iteminfo -> iteminfo.componentName == null
                || !iteminfo.componentName.getPackageName()
                .equals(CAMERA_PACKAGE_NAME);
    }
}
