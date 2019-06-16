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

package ch.deletescape.lawnchair.flowerpot.parser

import ch.deletescape.lawnchair.flowerpot.FlowerpotFormatException
import ch.deletescape.lawnchair.flowerpot.rules.Rule

object LineParser {
    fun parse(line: String, version: Int?): Rule? {
        if (line.isBlank()) {
            // ignore blank lines
            return Rule.NONE
        }
        return when (line[0]) {
            // Comment
            '#' -> Rule.NONE
            // Version declaration
            '$' -> Rule.Version(line.rest.toInt())
            // Intent action
            ':' -> Rule.IntentAction(line.rest)
            // Intent category
            ';' -> Rule.IntentCategory(line.rest)
            // Code rule
            '&' -> {
                val parts = line.rest.split("|")
                val ruleName = parts[0]
                val args = if (parts.size > 1) parts.subList(1, parts.size) else emptyList()
                Rule.CodeRule(ruleName, args.toTypedArray())
            }
            // Package
            else -> if (!line[0].isLetter()) {
                throw FlowerpotFormatException("Unknown rule identifier '${line[0]}' for version $version")
            } else {
                Rule.Package(line)
            }
        }.apply {
            if (version == null && !(this is Rule.None || this is Rule.Version)) {
                throw FlowerpotFormatException("Version has to be specified before any other rules")
            }
        }
    }

    private inline val String.rest get() = this.substring(1)
}