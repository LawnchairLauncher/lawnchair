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

package app.lawnchair.flowerpot.parser

import app.lawnchair.flowerpot.FlowerpotFormatException
import app.lawnchair.flowerpot.rules.Rules

object LineParser {
    fun parse(line: String, version: Int?): Rules? {
        if (line.isBlank()) {
            // ignore blank lines
            return Rules.NONE
        }
        return when (line[0]) {
            // Comment
            '#' -> Rules.NONE
            // Version declaration
            '$' -> Rules.Version(line.rest.toInt())
            // Intent action
            ':' -> Rules.IntentAction(line.rest)
            // Intent category
            ';' -> Rules.IntentCategory(line.rest)
            // Code rule
            '&' -> {
                val parts = line.rest.split("|")
                val ruleName = parts[0]
                val args = if (parts.size > 1) parts.subList(1, parts.size) else emptyList()
                Rules.CodeRule(ruleName, args.toTypedArray())
            }
            // Package
            else -> if (!line[0].isLetter()) {
                throw FlowerpotFormatException("Unknown rule identifier '${line[0]}' for version $version")
            } else {
                Rules.Package(line)
            }
        }.apply {
            if (version == null && !(this is Rules.None || this is Rules.Version)) {
                throw FlowerpotFormatException("Version has to be specified before any other rules")
            }
        }
    }

    private inline val String.rest get() = this.substring(1)
}
