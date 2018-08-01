package ch.deletescape.lawnchair

import android.annotation.TargetApi
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.support.animation.FloatPropertyCompat
import android.support.annotation.ColorInt
import android.support.v4.content.ContextCompat
import android.support.v4.view.PagerAdapter
import android.support.v7.app.AppCompatActivity
import android.util.AttributeSet
import android.util.Log
import android.util.Property
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import ch.deletescape.lawnchair.iconpack.IconPackManager
import com.android.launcher3.*
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.compat.UserManagerCompat
import com.android.launcher3.popup.PopupContainerWithArrow
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.shortcuts.DeepShortcutManager
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.LooperExecutor
import com.google.android.apps.nexuslauncher.CustomIconUtils
import java.lang.reflect.Field
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

/*
 * Copyright (C) 2018 paphonb@xda
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

val Context.launcherAppState get() = LauncherAppState.getInstance(this)
val Context.lawnchairPrefs get() = Utilities.getLawnchairPrefs(this)
val Context.blurWallpaperProvider get() = launcherAppState.launcher.blurWallpaperProvider

val Context.hasStoragePermission
    get() = PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.READ_EXTERNAL_STORAGE)

@ColorInt
fun Context.getColorAccent(): Int {
    return getColorAttr(android.R.attr.colorAccent)
}

@ColorInt
fun Context.getDisabled(inputColor: Int): Int {
    return applyAlphaAttr(android.R.attr.disabledAlpha, inputColor)
}

@ColorInt
fun Context.applyAlphaAttr(attr: Int, inputColor: Int): Int {
    val ta = obtainStyledAttributes(intArrayOf(attr))
    val alpha = ta.getFloat(0, 0f)
    ta.recycle()
    return applyAlpha(alpha, inputColor)
}

@ColorInt
fun applyAlpha(a: Float, inputColor: Int): Int {
    var alpha = a
    alpha *= Color.alpha(inputColor)
    return Color.argb(alpha.toInt(), Color.red(inputColor), Color.green(inputColor),
            Color.blue(inputColor))
}

@ColorInt
fun Context.getColorAttr(attr: Int): Int {
    val ta = obtainStyledAttributes(intArrayOf(attr))
    @ColorInt val colorAccent = ta.getColor(0, 0)
    ta.recycle()
    return colorAccent
}

fun Context.getThemeAttr(attr: Int): Int {
    val ta = obtainStyledAttributes(intArrayOf(attr))
    val theme = ta.getResourceId(0, 0)
    ta.recycle()
    return theme
}

fun Context.getDrawableAttr(attr: Int): Drawable? {
    val ta = obtainStyledAttributes(intArrayOf(attr))
    val drawable = ta.getDrawable(0)
    ta.recycle()
    return drawable
}

fun Context.getDimenAttr(attr: Int): Int {
    val ta = obtainStyledAttributes(intArrayOf(attr))
    val size = ta.getDimensionPixelSize(0, 0)
    ta.recycle()
    return size
}

fun Context.getBooleanAttr(attr: Int): Boolean {
    val ta = obtainStyledAttributes(intArrayOf(attr))
    val value = ta.getBoolean(0, false)
    ta.recycle()
    return value
}

inline fun ViewGroup.forEachChild(action: (View) -> Unit) {
    val count = childCount
    for (i in (0 until count)) {
        action(getChildAt(i))
    }
}

fun ComponentKey.getLauncherActivityInfo(context: Context): LauncherActivityInfo? {
    return LauncherAppsCompat.getInstance(context).getActivityList(componentName.packageName, user)
            .firstOrNull { it.componentName == componentName }
}

@Suppress("UNCHECKED_CAST")
class JavaField<T>(private val targetObject: Any, fieldName: String, targetClass: Class<*> = targetObject::class.java) {

    private val field: Field = targetClass.getDeclaredField(fieldName).apply { isAccessible = true }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return field.get(targetObject) as T
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        field.set(targetObject, value)
    }
}

class KFloatPropertyCompat(private val property: KMutableProperty0<Float>, name: String) : FloatPropertyCompat<Any>(name) {

    override fun getValue(`object`: Any) = property.get()

    override fun setValue(`object`: Any, value: Float) {
        property.set(value)
    }
}

class KFloatProperty(private val property: KMutableProperty0<Float>, name: String) : Property<Any, Float>(Float::class.java, name) {

    override fun get(`object`: Any) = property.get()

    override fun set(`object`: Any, value: Float) {
        property.set(value)
    }
}

val SCALE_XY: Property<View, Float> = object : Property<View, Float>(Float::class.java, "scaleXY") {
    override fun set(view: View, value: Float) {
        view.scaleX = value
        view.scaleY = value
    }

    override fun get(view: View): Float {
        return view.scaleX
    }
}

fun Float.clamp(min: Float, max: Float): Float {
    if (this <= min) return min
    if (this >= max) return max
    return this
}

fun Float.round() = roundToInt().toFloat()

fun Float.ceilToInt() = ceil(this).toInt()

fun Double.ceilToInt() = ceil(this).toInt()

class PropertyDelegate<T>(private val property: KMutableProperty0<T>) {

    operator fun getValue(thisRef: Any?, prop: KProperty<*>): T {
        return property.get()
    }

    operator fun setValue(thisRef: Any?, prop: KProperty<*>, value: T) {
        property.set(value)
    }
}

val mainHandler by lazy { Handler(Looper.getMainLooper()) }
val uiWorkerHandler by lazy { Handler(LauncherModel.getUiWorkerLooper()) }
val iconPackUiHandler by lazy { Handler(LauncherModel.getIconPackUiLooper()) }

fun runOnUiWorkerThread(r: () -> Unit) {
    runOnThread(uiWorkerHandler, r)
}

fun runOnMainThread(r: () -> Unit) {
    runOnThread(mainHandler, r)
}

fun runOnThread(handler: Handler, r: () -> Unit) {
    if (handler.looper.thread.id == Looper.myLooper().thread.id) {
        r()
    } else {
        handler.post(r)
    }
}

@JvmOverloads
fun TextView.setGoogleSans(style: Int = Typeface.NORMAL) {
    context.lawnchairApp.fontLoader.loadGoogleSans(this, style)
}

fun ViewGroup.getAllChilds() = ArrayList<View>().also { getAllChilds(it) }

fun ViewGroup.getAllChilds(list: MutableList<View>) {
    for (i in (0 until childCount)) {
        val child = getChildAt(i)
        if (child is ViewGroup) {
            child.getAllChilds(list)
        } else {
            list.add(child)
        }
    }
}

fun AppCompatActivity.hookGoogleSansDialogTitle() {
    layoutInflater.factory2 = object : LayoutInflater.Factory2 {
        override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? {
            if (name == "android.support.v7.widget.DialogTitle") {
                return (Class.forName(name).getConstructor(Context::class.java, AttributeSet::class.java)
                        .newInstance(context, attrs) as TextView).apply { setGoogleSans(Typeface.BOLD) }
            }
            return delegate.createView(parent, name, context, attrs)
        }

        override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
            return onCreateView(null, name, context, attrs)
        }

    }
}

fun openPopupMenu(icon: BubbleTextView, vararg shortcuts: SystemShortcut) {
    val launcher = Launcher.getLauncher(icon.context)
    (launcher.layoutInflater.inflate(R.layout.popup_container,
            launcher.dragLayer, false) as PopupContainerWithArrow).apply {
        disableDividers()
        visibility = View.INVISIBLE
        launcher.dragLayer.addView(this)
        populateAndShow(icon, emptyList(), emptyList(), listOf(*shortcuts), false)
    }
}

fun Context.getLauncherOrNull(): Launcher? {
    return try {
        Launcher.getLauncher(this)
    } catch (e: ClassCastException) {
        null
    }
}

var View.isVisible: Boolean
    get() = visibility == View.VISIBLE
    set(value) {
        visibility = if (value) View.VISIBLE else View.GONE
    }

private val MAX_UNICODE = '\uFFFF'

/**
 * Returns true if {@param target} is a search result for {@param query}
 */
fun java.text.Collator.matches(query: String, target: String): Boolean {
    return when (this.compare(query, target)) {
        0 -> true
        -1 ->
            // The target string can contain a modifier which would make it larger than
            // the query string (even though the length is same). If the query becomes
            // larger after appending a unicode character, it was originally a prefix of
            // the target string and hence should match.
            this.compare(query + MAX_UNICODE, target) > -1 || target.contains(query, ignoreCase = true)
        else -> false
    }
}

fun String.toTitleCase(): String = splitToSequence(" ").map { it.capitalize() }.joinToString(" ")

fun reloadIcons(context: Context) {
    LooperExecutor(LauncherModel.getIconPackLooper()).execute {
        val userManagerCompat = UserManagerCompat.getInstance(context)
        val model = LauncherAppState.getInstance(context).model

        for (user in userManagerCompat.userProfiles) {
            model.onPackagesReload(user)
        }

        IconPackManager.getInstance(context).onPackChanged()

        val shortcutManager = DeepShortcutManager.getInstance(context)
        val launcherApps = LauncherAppsCompat.getInstance(context)
        userManagerCompat.userProfiles.forEach { user ->
            launcherApps.getActivityList(null, user).forEach { CustomIconUtils.reloadIcon(shortcutManager, model, user, it.componentName.packageName) }
        }
    }
}

fun Context.getIcon():Drawable = packageManager.getApplicationIcon(applicationInfo)

fun <T, A>ensureOnMainThread(creator: (A) -> T): (A) -> T {
    return { it ->
        if (Looper.myLooper() == Looper.getMainLooper()) {
            creator(it)
        } else {
            try {
                MainThreadExecutor().submit(Callable { creator(it) }).get()
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            } catch (e: ExecutionException) {
                throw RuntimeException(e)
            }

        }
    }
}

fun <T>useApplicationContext(creator: (Context) -> T): (Context) -> T {
    return { it -> creator(it.applicationContext) }
}

class ViewPagerAdapter(private val pages: List<Pair<String, View>>) : PagerAdapter() {

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val view = pages[position].second
        container.addView(view)
        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeAllViews()
    }

    override fun getCount() = pages.size

    override fun isViewFromObject(view: View, obj: Any) = (view === obj)

    override fun getPageTitle(position: Int) = pages[position].first
}
