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

package ch.deletescape.lawnchair

import android.app.Activity
import android.app.Notification
import android.content.ContentResolver
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.*
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.service.notification.StatusBarNotification
import android.support.animation.FloatPropertyCompat
import android.support.annotation.ColorInt
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.ColorUtils
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v4.view.PagerAdapter
import android.support.v7.app.AlertDialog
import android.support.v7.graphics.Palette
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceGroup
import android.support.v7.widget.AppCompatButton
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Property
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Interpolator
import android.widget.*
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.font.CustomFontManager
import ch.deletescape.lawnchair.util.JSONMap
import ch.deletescape.lawnchair.util.hasFlag
import com.android.launcher3.*
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.compat.UserManagerCompat
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.shortcuts.DeepShortcutManager
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.Themes
import com.android.launcher3.views.OptionsPopupView
import com.android.systemui.shared.recents.model.TaskStack
import com.google.android.apps.nexuslauncher.CustomAppPredictor
import com.google.android.apps.nexuslauncher.CustomIconUtils
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.lang.reflect.Field
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import kotlin.collections.ArrayList
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random
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

val Context.hasStoragePermission
    get() = PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.READ_EXTERNAL_STORAGE)

@ColorInt
fun Context.getColorEngineAccent(): Int {
    return ColorEngine.getInstance(this).accent
}

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

fun Context.getDrawableAttrNullable(attr: Int): Drawable? {
    return try {
        getDrawableAttr(attr)
    } catch (e: Resources.NotFoundException) {
        null
    }
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

fun Context.getIntAttr(attr: Int): Int {
    val ta = obtainStyledAttributes(intArrayOf(attr))
    val value = ta.getInt(0, 0)
    ta.recycle()
    return value
}

inline fun ViewGroup.forEachChildIndexed(action: (View, Int) -> Unit) {
    val count = childCount
    for (i in (0 until count)) {
        action(getChildAt(i), i)
    }
}

inline fun ViewGroup.forEachChild(action: (View) -> Unit) {
    forEachChildIndexed { view, _ -> action(view) }
}

inline fun ViewGroup.forEachChildReversedIndexed(action: (View, Int) -> Unit) {
    val count = childCount
    for (i in (0 until count).reversed()) {
        action(getChildAt(i), i)
    }
}

inline fun ViewGroup.forEachChildReversed(action: (View) -> Unit) {
    forEachChildReversedIndexed { view, _ -> action(view) }
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
    if (handler.looper.thread.id == Looper.myLooper()?.thread?.id) {
        r()
    } else {
        handler.post(r)
    }
}

fun TextView.setCustomFont(type: Int) {
    CustomFontManager.getInstance(context).setCustomFont(this, type)
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

fun Activity.hookGoogleSansDialogTitle() {
    val activity = this
    layoutInflater.factory2 = object : LayoutInflater.Factory2 {
        override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? {
            if (name == "com.android.internal.widget.DialogTitle") {
                return (Class.forName(name).getConstructor(Context::class.java, AttributeSet::class.java)
                        .newInstance(context, attrs) as TextView).apply { setCustomFont(CustomFontManager.FONT_DIALOG_TITLE) }
            }
            return activity.onCreateView(parent, name, context, attrs)
        }

        override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
            return onCreateView(null, name, context, attrs)
        }

    }
}

fun openPopupMenu(view: View, rect: RectF?, vararg items: OptionsPopupView.OptionItem) {
    val launcher = Launcher.getLauncher(view.context)
    OptionsPopupView.show(launcher, rect ?: RectF(launcher.getViewBounds(view)), items.toList())
}

fun Context.getLauncherOrNull(): Launcher? {
    return try {
        Launcher.getLauncher(this)
    } catch (e: ClassCastException) {
        null
    }
}

fun Context.getBaseDraggingActivityOrNull(): BaseDraggingActivity? {
    return try {
        BaseDraggingActivity.fromContext(this)
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

fun reloadIconsFromComponents(context: Context, components: Collection<ComponentKey>) {
    reloadIcons(context, components.map { PackageUserKey(it.componentName.packageName, it.user) })
}

fun reloadIcons(context: Context, packages: Collection<PackageUserKey>) {
    LooperExecutor(LauncherModel.getIconPackLooper()).execute {
        val userManagerCompat = UserManagerCompat.getInstance(context)
        val las = LauncherAppState.getInstance(context)
        val model = las.model
        val launcher = las.launcher

        for (user in userManagerCompat.userProfiles) {
            model.onPackagesReload(user)
        }

        val shortcutManager = DeepShortcutManager.getInstance(context)
        packages.forEach {
            CustomIconUtils.reloadIcon(shortcutManager, model, it.mUser, it.mPackageName)
        }
        if (launcher != null) {
            runOnMainThread {
                (launcher.userEventDispatcher as CustomAppPredictor).uiManager.onPredictionsUpdated()
            }
        }
    }
}

fun Context.getIcon():Drawable = packageManager.getApplicationIcon(applicationInfo)

val TaskStack.mostRecentTask
    get() = this.tasks.getOrNull(this.taskCount - 1)

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

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        container.removeView(obj as View)
    }

    override fun getCount() = pages.size

    override fun isViewFromObject(view: View, obj: Any) = (view === obj)

    override fun getPageTitle(position: Int) = pages[position].first
}

fun dpToPx(size: Float): Float {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, size, Resources.getSystem().displayMetrics)
}

fun pxToDp(size: Float): Float {
    return size / dpToPx(1f)
}

fun Drawable.toBitmap(forceCreate: Boolean = true, fallbackSize: Int = 0): Bitmap? {
    return Utilities.drawableToBitmap(this, forceCreate, fallbackSize)
}

fun AlertDialog.applyAccent() {
    val fontManager = CustomFontManager.getInstance(context)
    val color = ColorEngine.getInstance(context).accent

    getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
        fontManager.setCustomFont(this, CustomFontManager.FONT_BUTTON)
        setTextColor(color)
    }
    getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
        fontManager.setCustomFont(this, CustomFontManager.FONT_BUTTON)
        setTextColor(color)
    }
    getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
        fontManager.setCustomFont(this, CustomFontManager.FONT_BUTTON)
        setTextColor(color)
    }
}

fun android.app.AlertDialog.applyAccent() {
    val color = ColorEngine.getInstance(context).accent
    val buttons = listOf(
            getButton(AlertDialog.BUTTON_NEGATIVE),
            getButton(AlertDialog.BUTTON_NEUTRAL),
            getButton(AlertDialog.BUTTON_POSITIVE))
    buttons.forEach {
        it.setTextColor(color)
        it.setCustomFont(CustomFontManager.FONT_DIALOG_TITLE)
    }
}

fun BgDataModel.workspaceContains(packageName: String): Boolean {
    return this.workspaceItems.any { it.targetComponent?.packageName == packageName }
}

fun findInViews(op: Workspace.ItemOperator, vararg views: ViewGroup?): View? {
    views.forEach { view ->
        if (view == null || view.width == 0 || view.height == 0) return@forEach
        view.forEachChild { item ->
            val info = item.tag as ItemInfo?
            if (op.evaluate(info, item)) {
                return item
            }
        }
    }
    return null
}

class ReverseOutputInterpolator(private val base: Interpolator) : Interpolator {

    override fun getInterpolation(input: Float): Float {
        return 1 - base.getInterpolation(input)
    }
}

class ReverseInputInterpolator(private val base: Interpolator) : Interpolator {

    override fun getInterpolation(input: Float): Float {
        return base.getInterpolation(1 - input)
    }
}

fun Switch.applyColor(color: Int) {
    val colorForeground = Themes.getAttrColor(context, android.R.attr.colorForeground)
    val alphaDisabled = Themes.getAlpha(context, android.R.attr.disabledAlpha)
    val switchThumbNormal = context.resources.getColor(android.support.v7.preference.R.color.switch_thumb_normal_material_light)
    val switchThumbDisabled = context.resources.getColor(android.support.v7.appcompat.R.color.switch_thumb_disabled_material_light)
    val thstateList = ColorStateList(arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()),
            intArrayOf(
                    switchThumbDisabled,
                    color,
                    switchThumbNormal))
    val trstateList = ColorStateList(arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()),
            intArrayOf(
                    ColorUtils.setAlphaComponent(colorForeground, alphaDisabled),
                    color,
                    colorForeground))
    DrawableCompat.setTintList(thumbDrawable, thstateList)
    DrawableCompat.setTintList(trackDrawable, trstateList)
}

fun Button.applyColor(color: Int) {
    val rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 31))
    (background as RippleDrawable).setColor(rippleColor)
    DrawableCompat.setTint(background, color)
    val tintList = ColorStateList.valueOf(color)
    if (this is RadioButton) {
        buttonTintList = tintList
    }
}

inline fun <T> Iterable<T>.safeForEach(action: (T) -> Unit) {
    val tmp = ArrayList<T>()
    tmp.addAll(this)
    for (element in tmp) action(element)
}

operator fun PreferenceGroup.get(index: Int): Preference = getPreference(index)
inline fun PreferenceGroup.forEachIndexed(action: (i: Int, pref: Preference) -> Unit) {
    for (i in 0 until preferenceCount) action(i, this[i])
}

operator fun XmlPullParser.get(index: Int): String? = getAttributeValue(index)
operator fun XmlPullParser.get(namespace: String?, key: String): String? = getAttributeValue(namespace, key)
operator fun XmlPullParser.get(key: String): String? = this[null, key]

val Configuration.usingNightMode get() = uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

fun <T, U : Comparable<U>> comparing(extractKey: (T) -> U): Comparator<T> {
    return Comparator { o1, o2 -> extractKey(o1).compareTo(extractKey(o2)) }
}

fun <T, U : Comparable<U>> Comparator<T>.then(extractKey: (T) -> U): Comparator<T> {
    return kotlin.Comparator { o1, o2 ->
        val res = compare(o1, o2)
        if (res != 0) res else extractKey(o1).compareTo(extractKey(o2))
    }
}

fun <E> MutableSet<E>.addOrRemove(obj: E, exists: Boolean): Boolean {
    if (contains(obj) != exists) {
        if (exists) add(obj)
        else remove(obj)
        return true
    }
    return false
}

fun CheckedTextView.applyAccent() {
    val tintList = ColorStateList.valueOf(ColorEngine.getInstance(context).accent)
    if (Utilities.ATLEAST_MARSHMALLOW) {
        compoundDrawableTintList = tintList
    }
    backgroundTintList = tintList
}

fun ViewGroup.isChild(view: View): Boolean = indexOfChild(view) != -1

fun ImageView.tintDrawable(color: Int) {
    val drawable = drawable.mutate()
    drawable.setTint(color)
    setImageDrawable(drawable)
}

fun View.runOnAttached(runnable: Runnable) {
    if (isAttachedToWindow) {
        runnable.run()
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {

            override fun onViewAttachedToWindow(v: View?) {
                runnable.run()
                removeOnAttachStateChangeListener(this)
            }

            override fun onViewDetachedFromWindow(v: View?) {
                removeOnAttachStateChangeListener(this)
            }
        })

    }
}

@Suppress("UNCHECKED_CAST")
fun <T>JSONArray.toArrayList(): ArrayList<T> {
    val arrayList = ArrayList<T>()
    for (i in (0 until length())) {
        arrayList.add(get(i) as T)
    }
    return arrayList
}

fun Collection<String>.toJsonStringArray(): JSONArray {
    val array = JSONArray()
    forEach { array.put(it) }
    return array
}

fun Context.resourcesForApplication(packageName: String): Resources? {
    return try {
        packageManager.getResourcesForApplication(packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}

fun ViewGroup.setCustomFont(type: Int, allCaps: Boolean? = null) {
    forEachChild {
        if (it is ViewGroup) {
            it.setCustomFont(type, allCaps)
        } else if (it is TextView) {
            it.setCustomFont(type)
            if (allCaps != null) {
                it.isAllCaps = allCaps
            }
        }
    }
}

fun getTabRipple(context: Context, accent: Int): ColorStateList {
    return ColorStateList(arrayOf(
            intArrayOf(android.R.attr.state_selected),
            intArrayOf()),
            intArrayOf(
                    ColorUtils.setAlphaComponent(accent, 31),
                    context.getColorAttr(android.R.attr.colorControlHighlight)))
}

fun JSONObject.getNullable(key: String): Any? {
    return opt(key)
}

fun JSONObject.asMap() = JSONMap(this)

fun String.asNonEmpty(): String? {
    if (TextUtils.isEmpty(this)) return null
    return this
}

fun createRipple(foreground: Int, background: Int): RippleDrawable {
    val rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(foreground, 31))
    return RippleDrawable(rippleColor, ShapeDrawable().apply { paint.color = background }, ShapeDrawable())
}

fun Context.createColoredButtonBackground(color: Int): Drawable {
    val shape = getDrawable(R.drawable.colored_button_shape)!!
    shape.setTintList(ColorStateList(arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf()),
            intArrayOf(
                    getDisabled(getColorAttr(R.attr.colorButtonNormal)),
                    color)))
    val highlight = getColorAttr(R.attr.colorControlHighlight)
    val ripple = RippleDrawable(ColorStateList.valueOf(highlight), shape, null)
    val insetHorizontal = resources.getDimensionPixelSize(R.dimen.abc_button_inset_horizontal_material)
    val insetVertical = resources.getDimensionPixelSize(R.dimen.abc_button_inset_vertical_material)
    return InsetDrawable(ripple, insetHorizontal, insetVertical, insetHorizontal, insetVertical)
}

fun Context.createDisabledColor(color: Int): ColorStateList {
    return ColorStateList(arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf()),
            intArrayOf(
                    getDisabled(getColorAttr(android.R.attr.colorForeground)),
                    color))
}

class ViewGroupChildIterator(private val viewGroup: ViewGroup, private var current: Int) : ListIterator<View> {

    override fun hasNext() = current < viewGroup.childCount

    override fun next() = viewGroup.getChildAt(current++)!!

    override fun nextIndex() = current

    override fun hasPrevious() = current > 0

    override fun previous() = viewGroup.getChildAt(current--)!!

    override fun previousIndex() = current - 1
}

class ViewGroupChildList(private val viewGroup: ViewGroup) : List<View> {

    override val size get() = viewGroup.childCount

    override fun isEmpty() = size == 0

    override fun contains(element: View): Boolean {
        return any { it === element }
    }

    override fun containsAll(elements: Collection<View>): Boolean {
        return elements.all { contains(it) }
    }

    override fun get(index: Int) = viewGroup.getChildAt(index)!!

    override fun indexOf(element: View) = indexOfFirst { it === element }

    override fun lastIndexOf(element: View) = indexOfLast { it === element }

    override fun iterator() = listIterator()

    override fun listIterator() = listIterator(0)

    override fun listIterator(index: Int) = ViewGroupChildIterator(viewGroup, index)

    override fun subList(fromIndex: Int, toIndex: Int) = ArrayList(this).subList(fromIndex, toIndex)
}

val ViewGroup.childs get() = ViewGroupChildList(this)

fun ContentResolver.getDisplayName(uri: Uri): String? {
    query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            return cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
        }
    }
    return null
}

inline fun avg(vararg of: Float) = of.average()
inline fun avg(vararg of: Int) = of.average()
inline fun avg(vararg of: Long) = of.average()
inline fun avg(vararg of: Double) = of.average()

fun Context.checkLocationAccess(): Boolean {
    return Utilities.hasPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) ||
            Utilities.hasPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
}

val Int.foregroundColor
    get() = Palette.Swatch(ColorUtils.setAlphaComponent(this, 0xFF), 1).bodyTextColor

val Int.luminance get() = ColorUtils.calculateLuminance(this)

val Int.isDark get() = luminance < 0.5f

inline fun <E> createWeakSet(): MutableSet<E> = Collections.newSetFromMap(WeakHashMap<E, Boolean>())

inline fun <T> listWhileNotNull(generator: () -> T?): List<T> = mutableListOf<T>().apply {
    while (true) {
        add(generator() ?: break)
    }
}

inline infix fun Int.hasFlag(flag: Int) = (this and flag) != 0

fun String.hash(type: String): String {
    val chars = "0123456789abcdef"
    val bytes = MessageDigest
            .getInstance(type)
            .digest(toByteArray())
    val result = StringBuilder(bytes.size * 2)

    bytes.forEach {
        val i = it.toInt()
        result.append(chars[i shr 4 and 0x0f])
        result.append(chars[i and 0x0f])
    }

    return result.toString()
}

val Context.locale: Locale
    get() {
        return if (Utilities.ATLEAST_NOUGAT) {
            this.resources.configuration.locales[0] ?: this.resources.configuration.locale
        } else {
            this.resources.configuration.locale
        }
    }

fun createRipplePill(context: Context, color: Int, radius: Float): Drawable {
    return RippleDrawable(
            ContextCompat.getColorStateList(context, R.color.focused_background)!!,
            createPill(color, radius),
            createPill(color, radius)
    )
}

fun createPill(color: Int, radius: Float): Drawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radius
    }
}

val Long.Companion.random get() = Random.nextLong()

fun StatusBarNotification.loadSmallIcon(context: Context): Drawable? {
    return if (Utilities.ATLEAST_MARSHMALLOW) {
        notification.smallIcon?.loadDrawable(context)
    } else {
        context.resourcesForApplication(packageName)?.getDrawable(notification.icon)
    }
}

fun Context.checkPackagePermission(packageName: String, permissionName: String): Boolean {
    try {
        val info = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        info.requestedPermissions.forEachIndexed { index, s ->
            if (s == permissionName) {
                return info.requestedPermissionsFlags[index].hasFlag(REQUESTED_PERMISSION_GRANTED)
            }
        }
    } catch (e: PackageManager.NameNotFoundException) {
    }
    return false
}

inline val Calendar.hourOfDay get() = get(Calendar.HOUR_OF_DAY)
inline val Calendar.dayOfYear get() = get(Calendar.DAY_OF_YEAR)

inline val Int.red get() = Color.red(this)
inline val Int.green get() = Color.green(this)
inline val Int.blue get() = Color.blue(this)
inline val Int.alpha get() = Color.alpha(this)
