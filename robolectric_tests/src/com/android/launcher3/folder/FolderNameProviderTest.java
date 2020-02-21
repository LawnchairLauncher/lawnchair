/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.folder;

import static org.junit.Assert.assertEquals;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.android.launcher3.AppInfo;
import com.android.launcher3.WorkspaceItemInfo;
import com.android.launcher3.shadows.LShadowUserManager;
import com.android.launcher3.util.LauncherRoboTestRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;

@RunWith(LauncherRoboTestRunner.class)
public final class FolderNameProviderTest {
    private Context mContext;
    private WorkspaceItemInfo mItem1;
    private WorkspaceItemInfo mItem2;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mItem1 = new WorkspaceItemInfo(new AppInfo(
                new ComponentName("a.b.c", "a.b.c/a.b.c.d"),
                "title1",
                LShadowUserManager.newUserHandle(10),
                new Intent().setComponent(new ComponentName("a.b.c", "a.b.c/a.b.c.d"))
        ));
        mItem2 = new WorkspaceItemInfo(new AppInfo(
                new ComponentName("a.b.c", "a.b.c/a.b.c.d"),
                "title2",
                LShadowUserManager.newUserHandle(10),
                new Intent().setComponent(new ComponentName("a.b.c", "a.b.c/a.b.c.d"))
        ));
    }

    @Test
    public void getSuggestedFolderName_workAssignedToEnd() {
        ArrayList<WorkspaceItemInfo> list = new ArrayList<>();
        list.add(mItem1);
        list.add(mItem2);
        FolderNameInfo[] nameInfos =
                new FolderNameInfo[FolderNameProvider.SUGGEST_MAX];
        new FolderNameProvider().getSuggestedFolderName(mContext, list, nameInfos);
        assertEquals("Work", nameInfos[0].getLabel());

        nameInfos[0] = new FolderNameInfo("candidate1", 0.9);
        nameInfos[1] = new FolderNameInfo("candidate2", 0.8);
        nameInfos[2] = new FolderNameInfo("candidate3", 0.7);
        new FolderNameProvider().getSuggestedFolderName(mContext, list, nameInfos);
        assertEquals("Work", nameInfos[3].getLabel());

    }
}
