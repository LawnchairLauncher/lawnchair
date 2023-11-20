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

import static com.android.launcher3.allapps.UserProfileManager.STATE_DISABLED;
import static com.android.launcher3.allapps.UserProfileManager.STATE_ENABLED;
import static com.android.launcher3.allapps.UserProfileManager.STATE_TRANSITION;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.R;
import com.android.launcher3.util.ActivityContextWrapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PrivateSpaceHeaderViewControllerTest {

    private static final int CONTAINER_HEADER_ELEMENT_COUNT = 1;
    private static final int LOCK_UNLOCK_BUTTON_COUNT = 1;
    private static final int PS_SETTINGS_BUTTON_COUNT_VISIBLE = 1;
    private static final int PS_SETTINGS_BUTTON_COUNT_INVISIBLE = 0;
    private static final int PS_TRANSITION_IMAGE_COUNT = 1;

    private Context mContext;
    private LayoutInflater mLayoutInflater;
    private PrivateSpaceHeaderViewController mPsHeaderViewController;
    private RelativeLayout mPsHeaderLayout;
    @Mock
    private PrivateProfileManager mPrivateProfileManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = new ActivityContextWrapper(getApplicationContext());
        mLayoutInflater = LayoutInflater.from(getApplicationContext());
        mPsHeaderViewController = new PrivateSpaceHeaderViewController(mPrivateProfileManager);
        mPsHeaderLayout = (RelativeLayout) mLayoutInflater.inflate(R.layout.private_space_header,
                null);
    }

    @Test
    public void privateProfileDisabled_psHeaderContainsLockedView() throws Exception {
        Bitmap unlockButton = getBitmap(mContext.getDrawable(R.drawable.bg_ps_unlock_button));
        when(mPrivateProfileManager.getCurrentState()).thenReturn(STATE_DISABLED);

        mPsHeaderViewController.addPrivateSpaceHeaderViewElements(mPsHeaderLayout);
        awaitTasksCompleted();

        int totalContainerHeaderView = 0;
        int totalLockUnlockButtonView = 0;
        for (int i = 0; i < mPsHeaderLayout.getChildCount(); i++) {
            View view = mPsHeaderLayout.getChildAt(i);
            if (view.getId() == R.id.ps_container_header) {
                totalContainerHeaderView += 1;
                assertEquals(View.VISIBLE, view.getVisibility());
            } else if (view.getId() == R.id.ps_lock_unlock_button
                    && view instanceof ImageView imageView) {
                totalLockUnlockButtonView += 1;
                assertEquals(View.VISIBLE, view.getVisibility());
                getBitmap(imageView.getDrawable()).sameAs(unlockButton);
            } else {
                assertEquals(View.GONE, view.getVisibility());
            }
        }
        assertEquals(CONTAINER_HEADER_ELEMENT_COUNT, totalContainerHeaderView);
        assertEquals(LOCK_UNLOCK_BUTTON_COUNT, totalLockUnlockButtonView);
    }

    @Test
    public void privateProfileEnabled_psHeaderContainsUnlockedView() throws Exception {
        Bitmap lockImage = getBitmap(mContext.getDrawable(R.drawable.bg_ps_lock_button));
        Bitmap settingsImage = getBitmap(mContext.getDrawable(R.drawable.bg_ps_settings_button));
        when(mPrivateProfileManager.getCurrentState()).thenReturn(STATE_ENABLED);
        when(mPrivateProfileManager.isPrivateSpaceSettingsAvailable()).thenReturn(true);

        mPsHeaderViewController.addPrivateSpaceHeaderViewElements(mPsHeaderLayout);
        awaitTasksCompleted();

        int totalContainerHeaderView = 0;
        int totalLockUnlockButtonView = 0;
        int totalSettingsImageView = 0;
        for (int i = 0; i < mPsHeaderLayout.getChildCount(); i++) {
            View view = mPsHeaderLayout.getChildAt(i);
            if (view.getId() == R.id.ps_container_header) {
                totalContainerHeaderView += 1;
                assertEquals(View.VISIBLE, view.getVisibility());
            } else if (view.getId() == R.id.ps_lock_unlock_button
                    && view instanceof ImageView imageView) {
                totalLockUnlockButtonView += 1;
                assertEquals(View.VISIBLE, view.getVisibility());
                getBitmap(imageView.getDrawable()).sameAs(lockImage);
            } else if (view.getId() == R.id.ps_settings_button
                    && view instanceof ImageView imageView) {
                totalSettingsImageView += 1;
                assertEquals(View.VISIBLE, view.getVisibility());
                getBitmap(imageView.getDrawable()).sameAs(settingsImage);
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
        Bitmap lockImage = getBitmap(mContext.getDrawable(R.drawable.bg_ps_lock_button));
        when(mPrivateProfileManager.getCurrentState()).thenReturn(STATE_ENABLED);
        when(mPrivateProfileManager.isPrivateSpaceSettingsAvailable()).thenReturn(false);

        mPsHeaderViewController.addPrivateSpaceHeaderViewElements(mPsHeaderLayout);
        awaitTasksCompleted();

        int totalContainerHeaderView = 0;
        int totalLockUnlockButtonView = 0;
        int totalSettingsImageView = 0;
        for (int i = 0; i < mPsHeaderLayout.getChildCount(); i++) {
            View view = mPsHeaderLayout.getChildAt(i);
            if (view.getId() == R.id.ps_container_header) {
                totalContainerHeaderView += 1;
                assertEquals(View.VISIBLE, view.getVisibility());
            } else if (view.getId() == R.id.ps_lock_unlock_button
                    && view instanceof ImageView imageView) {
                totalLockUnlockButtonView += 1;
                assertEquals(View.VISIBLE, view.getVisibility());
                getBitmap(imageView.getDrawable()).sameAs(lockImage);
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
        when(mPrivateProfileManager.getCurrentState()).thenReturn(STATE_TRANSITION);

        mPsHeaderViewController.addPrivateSpaceHeaderViewElements(mPsHeaderLayout);
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
                getBitmap(imageView.getDrawable()).sameAs(transitionImage);
            } else {
                assertEquals(View.GONE, view.getVisibility());
            }
        }
        assertEquals(CONTAINER_HEADER_ELEMENT_COUNT, totalContainerHeaderView);
        assertEquals(PS_TRANSITION_IMAGE_COUNT, totalLockUnlockButtonView);
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
}
