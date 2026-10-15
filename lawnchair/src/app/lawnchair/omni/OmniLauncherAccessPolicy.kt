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

/** Receiver-owned flavor ceiling; a client manifest or client-side check cannot grant authority. */
object OmniLauncherAccessPolicy {
    private val read = setOf("launcher.health", "launcher.get_settings", "launcher.list_apps")
    private val open = setOf("launcher.open_app", "launcher.open_drawer")
    private val control = read + open + "launcher.set_preference"

    fun allows(workspacePackage: String, capability: String): Boolean = when (workspacePackage) {
        "com.omnidev.workspace" -> capability == "launcher.health"
        "com.omnidev.workspace.norm" -> capability in read || capability in open
        "com.omnidev.workspace.pro", "com.omnidev.workspace.oem", "com.omnidev.workspace.admin" -> capability in control
        else -> false
    }
}
