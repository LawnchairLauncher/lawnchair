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

package ch.deletescape.lawnchair.util.extensions

inline fun <T> whenEnabled(flag: Boolean, block: () -> T) = if (flag) block() else null

inline fun <T> Boolean.whenTrue(block: () -> T) = if (this) block() else null

inline infix fun <T: Any> Boolean.then(block: () -> T) = if (this) block() else null

inline infix fun <T: Any> Boolean.then(value: T) = if (this) value else null

inline infix fun <T> T.or(block: () -> T) = this ?: block()

inline infix fun <T> T.or(value: T) = this ?: value