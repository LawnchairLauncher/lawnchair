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

@file:Suppress("NOTHING_TO_INLINE", "PLATFORM_CLASS_MAPPED_TO_KOTLIN", "HasPlatformType")

package ch.deletescape.lawnchair.util.extensions

import android.util.Log
import com.google.gson.Gson
import kotlin.system.measureTimeMillis

// Utilities for simpler debug logging without having to bother with tags
// The instance variants (Any.*) are faster and also produce significantly less overhead when compiled

inline val currentStackTrace get() = Throwable().stackTrace
// Let's assume stack array is never empty
inline val callingClass get() = currentStackTrace[0].className.substringAfterLast('.')

inline val <reified T> T.TAG: String get() = T::class.java.simpleName

inline fun v(message: String, t: Throwable) = Log.v(callingClass, message, t)

inline fun v(message: String) = Log.v(callingClass, message)

inline fun <reified T> T.v(): T {
    Log.v(callingClass, Gson().toJson(this))
    return this
}

inline fun <reified T> T.v(message: String, t: Throwable) = Log.v(TAG, message, t)

inline fun <reified T> T.v(message: String) = Log.v(TAG, message)

inline fun d(message: String, t: Throwable) = Log.d(callingClass, message, t)

inline fun d(message: String) = Log.d(callingClass, message)

inline fun <reified T> T.d(): T{
    Log.d(callingClass, Gson().toJson(this))
    return this
}

inline fun <reified T> T.d(message: String, t: Throwable) = Log.d(TAG, message, t)

inline fun <reified T> T.d(message: String) = Log.d(TAG, message)

inline fun i(message: String, t: Throwable) = Log.i(callingClass, message, t)

inline fun i(message: String) = Log.i(callingClass, message)

inline fun <reified T> T.i(): T {
    Log.i(callingClass, Gson().toJson(this))
    return this
}

inline fun <reified T> T.i(message: String, t: Throwable) = Log.i(TAG, message, t)

inline fun <reified T> T.i(message: String) = Log.i(TAG, message)

inline fun w(message: String, t: Throwable) = Log.w(callingClass, message, t)

inline fun w(message: String) = Log.w(callingClass, message)

inline fun <reified T> T.w(): T {
    Log.w(callingClass, Gson().toJson(this))
    return this
}

inline fun <reified T> T.w(message: String, t: Throwable) = Log.w(TAG, message, t)

inline fun <reified T> T.w(message: String) = Log.w(TAG, message)

inline fun e(message: String, t: Throwable) = Log.e(callingClass, message, t)

inline fun e(message: String) = Log.e(callingClass, message)

inline fun <reified T> T.e(): T {
    Log.e(callingClass, Gson().toJson(this))
    return this
}

inline fun <reified T> T.e(message: String, t: Throwable) = Log.e(TAG, message, t)

inline fun <reified T> measure(method: String, block: () -> T): T {
    var ret: T? = null
    val time = measureTimeMillis {
        ret = block()
    }
    d("$method took ${time}ms")
    return ret!!
}

inline fun <reified I, reified T> I.measure(method: String, block: () -> T): T {
    var ret: T? = null
    val time = measureTimeMillis {
        ret = block()
    }
    d("$method() took ${time}ms")
    return ret!!
}