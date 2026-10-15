/*
 * Copyright 2026 Abdelrahman Hussein (عبدالرحمن حسين)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.omni

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniLauncherAccessPolicyTest {
    @Test
    fun enforcesFlavorCeilingsOnTheReceiver() {
        assertTrue(OmniLauncherAccessPolicy.allows("com.omnidev.workspace", "launcher.health"))
        assertFalse(OmniLauncherAccessPolicy.allows("com.omnidev.workspace", "launcher.list_apps"))
        assertTrue(OmniLauncherAccessPolicy.allows("com.omnidev.workspace.norm", "launcher.open_app"))
        assertFalse(OmniLauncherAccessPolicy.allows("com.omnidev.workspace.norm", "launcher.set_preference"))
        listOf("admin", "pro", "oem").forEach {
            assertTrue(OmniLauncherAccessPolicy.allows("com.omnidev.workspace.$it", "launcher.set_preference"))
        }
    }

    @Test
    fun rejectsImpersonatedPackagesAndUnknownCapabilities() {
        assertFalse(OmniLauncherAccessPolicy.allows("com.omnidev.workspace.evil", "launcher.health"))
        assertFalse(OmniLauncherAccessPolicy.allows("com.omnidev.workspace.admin", "launcher.clear_home"))
        assertFalse(OmniLauncherAccessPolicy.allows("other.app", "launcher.set_preference"))
    }
}
