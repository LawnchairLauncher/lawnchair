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
package com.android.launcher3.util;

import static com.android.launcher3.widget.WidgetSections.NO_CATEGORY;

import static com.google.common.truth.Truth.assertThat;

import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.model.data.PackageItemInfo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class PackageUserKeyTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    private static final String TEST_PACKAGE = "com.android.test.package";
    private static final int CONVERSATIONS = 0;
    private static final int WEATHER = 1;

    @Test
    public void fromPackageItemInfo_shouldCreateExpectedObject() {
        PackageUserKey packageUserKey = PackageUserKey.fromPackageItemInfo(
                new PackageItemInfo(TEST_PACKAGE, UserHandle.CURRENT));

        assertThat(packageUserKey.mPackageName).isEqualTo(TEST_PACKAGE);
        assertThat(packageUserKey.mWidgetCategory).isEqualTo(NO_CATEGORY);
        assertThat(packageUserKey.mUser).isEqualTo(UserHandle.CURRENT);
    }

    @Test
    public void constructor_packageNameAndUserHandle_shouldCreateExpectedObject() {
        PackageUserKey packageUserKey = new PackageUserKey(TEST_PACKAGE, UserHandle.CURRENT);

        assertThat(packageUserKey.mPackageName).isEqualTo(TEST_PACKAGE);
        assertThat(packageUserKey.mWidgetCategory).isEqualTo(NO_CATEGORY);
        assertThat(packageUserKey.mUser).isEqualTo(UserHandle.CURRENT);
    }

    @Test
    public void constructor_widgetCategoryAndUserHandle_shouldCreateExpectedObject() {
        PackageUserKey packageUserKey = new PackageUserKey(CONVERSATIONS, UserHandle.CURRENT);

        assertThat(packageUserKey.mPackageName).isEqualTo("");
        assertThat(packageUserKey.mWidgetCategory).isEqualTo(CONVERSATIONS);
        assertThat(packageUserKey.mUser).isEqualTo(UserHandle.CURRENT);
    }

    @Test
    public void equals_sameObject_shouldReturnTrue() {
        PackageUserKey packageUserKey = new PackageUserKey(TEST_PACKAGE, UserHandle.CURRENT);
        PackageUserKey otherPackageUserKey = packageUserKey;

        assertThat(packageUserKey).isEqualTo(otherPackageUserKey);
    }

    @Test
    public void equals_differentObjectSameContent_shouldReturnTrue() {
        PackageUserKey packageUserKey = new PackageUserKey(TEST_PACKAGE, UserHandle.CURRENT);
        PackageUserKey otherPackageUserKey = new PackageUserKey(TEST_PACKAGE, UserHandle.CURRENT);

        assertThat(packageUserKey).isEqualTo(otherPackageUserKey);
    }

    @Test
    public void equals_compareAgainstNull_shouldReturnFalse() {
        PackageUserKey packageUserKey = new PackageUserKey(TEST_PACKAGE, UserHandle.CURRENT);

        assertThat(packageUserKey).isNotEqualTo(null);
    }

    @Test
    public void equals_differentPackage_shouldReturnFalse() {
        PackageUserKey packageUserKey = new PackageUserKey(TEST_PACKAGE, UserHandle.CURRENT);
        PackageUserKey otherPackageUserKey = new PackageUserKey(TEST_PACKAGE + "1",
                UserHandle.CURRENT);

        assertThat(packageUserKey).isNotEqualTo(otherPackageUserKey);
    }


    @Test
    public void equals_differentCategory_shouldReturnFalse() {
        PackageUserKey packageUserKey = new PackageUserKey(WEATHER, UserHandle.CURRENT);
        PackageUserKey otherPackageUserKey = new PackageUserKey(CONVERSATIONS, UserHandle.CURRENT);

        assertThat(packageUserKey).isNotEqualTo(otherPackageUserKey);
    }

    @Test
    public void equals_differentUser_shouldReturnFalse() {
        PackageUserKey packageUserKey = new PackageUserKey(TEST_PACKAGE, UserHandle.of(1));
        PackageUserKey otherPackageUserKey = new PackageUserKey(TEST_PACKAGE, UserHandle.of(2));

        assertThat(packageUserKey).isNotEqualTo(otherPackageUserKey);
    }

    @Test
    public void hashCode_sameObject_shouldBeTheSame() {
        PackageUserKey packageUserKey = new PackageUserKey(WEATHER, UserHandle.CURRENT);
        PackageUserKey otherPackageUserKey = packageUserKey;

        assertThat(packageUserKey.hashCode()).isEqualTo(otherPackageUserKey.hashCode());
    }

    @Test
    public void hashCode_differentObjectSameContent_shouldBeTheSame() {
        PackageUserKey packageUserKey = new PackageUserKey(TEST_PACKAGE, UserHandle.CURRENT);
        PackageUserKey otherPackageUserKey = new PackageUserKey(TEST_PACKAGE, UserHandle.CURRENT);

        assertThat(packageUserKey.hashCode()).isEqualTo(otherPackageUserKey.hashCode());
    }

    @Test
    public void hashCode_differentPackage_shouldBeDifferent() {
        PackageUserKey packageUserKey = new PackageUserKey(TEST_PACKAGE, UserHandle.CURRENT);
        PackageUserKey otherPackageUserKey = new PackageUserKey(TEST_PACKAGE + "1",
                UserHandle.CURRENT);

        assertThat(packageUserKey.hashCode()).isNotEqualTo(otherPackageUserKey.hashCode());
    }


    @Test
    public void hashCode_differentCategory_shouldBeDifferent() {
        PackageUserKey packageUserKey = new PackageUserKey(WEATHER, UserHandle.CURRENT);
        PackageUserKey otherPackageUserKey = new PackageUserKey(CONVERSATIONS, UserHandle.CURRENT);

        assertThat(packageUserKey.hashCode()).isNotEqualTo(otherPackageUserKey.hashCode());
    }

    @Test
    public void hashCode_differentUser_shouldBeDifferent() {
        PackageUserKey packageUserKey = new PackageUserKey(TEST_PACKAGE, UserHandle.of(1));
        PackageUserKey otherPackageUserKey = new PackageUserKey(TEST_PACKAGE, UserHandle.of(2));

        assertThat(packageUserKey.hashCode()).isNotEqualTo(otherPackageUserKey.hashCode());
    }
}
