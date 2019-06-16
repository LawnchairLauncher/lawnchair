/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.flowerpot.rules

sealed class Rule {
    class None: Rule()
    data class Version(val version: Int): Rule()
    data class Package(val filter: String): Rule()
    data class IntentAction(val action: String): Rule()
    data class IntentCategory(val category: String): Rule()
    data class CodeRule(val rule: String, val args: Array<String>) : Rule()

    companion object {
        val NONE = None()
    }
}
