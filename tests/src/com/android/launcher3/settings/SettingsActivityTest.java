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

package com.android.launcher3.settings;

import static androidx.preference.PreferenceFragmentCompat.ARG_PREFERENCE_ROOT;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.RecyclerViewActions.actionOnItem;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.matcher.BundleMatchers.hasEntry;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.android.launcher3.settings.SettingsActivity.DEVELOPER_OPTIONS_KEY;
import static com.android.launcher3.settings.SettingsActivity.EXTRA_FRAGMENT_ARGS;
import static com.android.launcher3.settings.SettingsActivity.EXTRA_FRAGMENT_ROOT_KEY;

import static com.google.common.truth.Truth.assertThat;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.launcher3.R;
import com.android.systemui.shared.plugins.PluginPrefs;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SettingsActivityTest {

    private Context mApplicationContext;

    @Before
    public void setUp() {
        mApplicationContext = ApplicationProvider.getApplicationContext();
        Intents.init();
    }

    @After
    public void tearDown() {
        Intents.release();
    }

    @Test
    @Ignore  // b/199309785
    public void testSettings_aboutTap_launchesActivity() {
        ActivityScenario.launch(SettingsActivity.class);
        onView(withId(R.id.recycler_view)).perform(
                actionOnItem(hasDescendant(withText("About")), click()));

        intended(allOf(
                hasComponent(SettingsActivity.class.getName()),
                hasExtra(
                        equalTo(EXTRA_FRAGMENT_ARGS),
                        hasEntry(ARG_PREFERENCE_ROOT, "about_screen"))));
    }

    @Test
    @Ignore  // b/199309785
    public void testSettings_developerOptionsTap_launchesActivityWithFragment() {
        PluginPrefs.setHasPlugins(mApplicationContext);
        ActivityScenario.launch(SettingsActivity.class);
        onView(withId(R.id.recycler_view)).perform(
                actionOnItem(hasDescendant(withText("Developer Options")), click()));

        intended(allOf(
                hasComponent(SettingsActivity.class.getName()),
                hasExtra(EXTRA_FRAGMENT_ROOT_KEY, DEVELOPER_OPTIONS_KEY)));
    }

    @Test
    @Ignore  // b/199309785
    public void testSettings_aboutScreenIntent() {
        Bundle fragmentArgs = new Bundle();
        fragmentArgs.putString(ARG_PREFERENCE_ROOT, "about_screen");

        Intent intent = new Intent(mApplicationContext, SettingsActivity.class)
                .putExtra(EXTRA_FRAGMENT_ARGS, fragmentArgs);
        ActivityScenario.launch(intent);

        onView(withText("About")).check(matches(isDisplayed()));
        onView(withText("Version")).check(matches(isDisplayed()));
        onView(withContentDescription("Navigate up")).check(matches(isDisplayed()));
    }

    @Test
    @Ignore  // b/199309785
    public void testSettings_developerOptionsFragmentIntent() {
        Intent intent = new Intent(mApplicationContext, SettingsActivity.class)
                .putExtra(EXTRA_FRAGMENT_ROOT_KEY, DEVELOPER_OPTIONS_KEY);
        ActivityScenario.launch(intent);

        onView(withText("Developer Options")).check(matches(isDisplayed()));
        onView(withId(R.id.filter_box)).check(matches(isDisplayed()));
        onView(withContentDescription("Navigate up")).check(matches(isDisplayed()));
    }

    @Test
    @Ignore  // b/199309785
    public void testSettings_backButtonFinishesActivity() {
        Bundle fragmentArgs = new Bundle();
        fragmentArgs.putString(ARG_PREFERENCE_ROOT, "about_screen");
        Intent intent = new Intent(mApplicationContext, SettingsActivity.class)
                .putExtra(EXTRA_FRAGMENT_ARGS, fragmentArgs);
        ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(intent);

        onView(withContentDescription("Navigate up")).perform(click());
        scenario.onActivity(activity -> assertThat(activity.isFinishing()).isTrue());
    }
}
