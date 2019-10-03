/**
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.launcher3.ui;

import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.testcomponent.TestCommandReceiver;
import com.android.launcher3.util.LauncherLayoutBuilder;
import com.android.launcher3.util.rule.ShellCommandRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.OutputStreamWriter;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class DefaultLayoutProviderTest extends AbstractLauncherUiTest {

    @Rule
    public ShellCommandRule mGrantWidgetRule = ShellCommandRule.grantWidgetBind();

    private static final String SETTINGS_APP = "com.android.settings";

    private Context mContext;
    private String mAuthority;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        mContext = InstrumentationRegistry.getContext();

        PackageManager pm = mTargetContext.getPackageManager();
        ProviderInfo pi = pm.getProviderInfo(new ComponentName(mContext,
                TestCommandReceiver.class), 0);
        mAuthority = pi.authority;
    }

    @Test
    public void testCustomProfileLoaded_with_icon_on_hotseat() throws Exception {
        writeLayout(new LauncherLayoutBuilder().atHotseat(0).putApp(SETTINGS_APP, SETTINGS_APP));

        // Launch the home activity
        mDevice.pressHome();

        mLauncher.getWorkspace().getHotseatAppIcon(getSettingsApp().getLabel().toString());
    }

    @Test
    public void testCustomProfileLoaded_with_widget() throws Exception {
        // A non-restored widget with no config screen gets restored automatically.
        LauncherAppWidgetProviderInfo info = TestViewHelpers.findWidgetProvider(this, false);

        writeLayout(new LauncherLayoutBuilder().atWorkspace(0, 1, 0)
                .putWidget(info.getComponent().getPackageName(),
                        info.getComponent().getClassName(), 2, 2));

        // Launch the home activity
        mDevice.pressHome();

        // Verify widget present
        assertTrue("Widget is not present",
                mLauncher.getWorkspace().tryGetWidget(info.label, DEFAULT_UI_TIMEOUT) != null);
    }

    @Test
    public void testCustomProfileLoaded_with_folder() throws Exception {
        writeLayout(new LauncherLayoutBuilder().atHotseat(0).putFolder(android.R.string.copy)
                .addApp(SETTINGS_APP, SETTINGS_APP)
                .addApp(SETTINGS_APP, SETTINGS_APP)
                .addApp(SETTINGS_APP, SETTINGS_APP)
                .build());

        // Launch the home activity
        mDevice.pressHome();

        mLauncher.getWorkspace().getHotseatFolder("Folder: Copy");
    }

    @After
    public void cleanup() throws Exception {
        mDevice.executeShellCommand("settings delete secure launcher3.layout.provider");
    }

    private void writeLayout(LauncherLayoutBuilder builder) throws Exception {
        mDevice.executeShellCommand("settings put secure launcher3.layout.provider " + mAuthority);
        ParcelFileDescriptor pfd = mTargetContext.getContentResolver().openFileDescriptor(
                Uri.parse("content://" + mAuthority + "/launcher_layout"), "w");

        try (OutputStreamWriter writer = new OutputStreamWriter(new AutoCloseOutputStream(pfd))) {
            builder.build(writer);
        }
        clearLauncherData();
    }
}
