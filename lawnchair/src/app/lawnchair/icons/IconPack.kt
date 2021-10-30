package app.lawnchair.icons

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.util.Xml
import kotlinx.coroutines.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.util.concurrent.Semaphore

class IconPack(
    private val context: Context,
    val packPackageName: String,
    private val packResources: Resources
) {
    private var waiter: Semaphore? = Semaphore(0)
    private val deferredLoad: Deferred<Unit>

    private val componentMap = mutableMapOf<ComponentName, IconEntry>()
    private val calendarMap = mutableMapOf<ComponentName, CalendarIconEntry>()
    private val clockMap = mutableMapOf<ComponentName, IconEntry>()
    private val clockMetas = mutableMapOf<IconEntry, ClockMetadata>()

    private val idCache = mutableMapOf<String, Int>()

    init {
        deferredLoad = scope.async(Dispatchers.IO) {
            loadInternal()
            waiter?.release()
            waiter = null
        }
    }

    suspend fun load() {
        return deferredLoad.await()
    }

    fun loadBlocking() {
        waiter?.run {
            acquireUninterruptibly()
            release()
        }
    }

    fun getIcon(componentName: ComponentName) = componentMap[componentName]
    fun getCalendar(componentName: ComponentName) = calendarMap[componentName]
    fun getClock(entry: IconEntry) = clockMetas[entry]

    fun getCalendars(): MutableSet<ComponentName> = calendarMap.keys
    fun getClocks(): MutableSet<ComponentName> = clockMap.keys

    fun getIcon(iconEntry: IconEntry, iconDpi: Int): Drawable? {
        val id = getDrawableId(iconEntry.name)
        if (id == 0) return null
        return try {
            packResources.getDrawableForDensity(id, iconDpi, null)
        } catch (e: Resources.NotFoundException) {
            null
        }
    }

    private fun getDrawableId(name: String) = idCache.getOrPut(name) {
        packResources.getIdentifier(name, "drawable", packPackageName)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun loadInternal() {
        val parseXml = getXml("appfilter") ?: return
        val compStart = "ComponentInfo{"
        val compStartLength = compStart.length
        val compEnd = "}"
        val compEndLength = compEnd.length
        try {
            while (parseXml.next() != XmlPullParser.END_DOCUMENT) {
                if (parseXml.eventType != XmlPullParser.START_TAG) continue
                val name = parseXml.name
                val isCalendar = name == "calendar"
                when (name) {
                    "item", "calendar" -> {
                        var componentName: String? = parseXml["component"]
                        val drawableName = parseXml[if (isCalendar) "prefix" else "drawable"]
                        if (componentName != null && drawableName != null) {
                            if (componentName.startsWith(compStart) && componentName.endsWith(compEnd)) {
                                componentName = componentName.substring(compStartLength, componentName.length - compEndLength)
                            }
                            val parsed = ComponentName.unflattenFromString(componentName)
                            if (parsed != null) {
                                if (isCalendar) {
                                    calendarMap[parsed] = CalendarIconEntry(this, drawableName)
                                } else {
                                    componentMap[parsed] = IconEntry(this, drawableName)
                                }
                            }
                        }
                    }
                    "dynamic-clock" -> {
                        val drawableName = parseXml["drawable"]
                        if (drawableName != null) {
                            if (parseXml is XmlResourceParser) {
                                clockMetas[IconEntry(this, drawableName)] = ClockMetadata(
                                    parseXml.getAttributeIntValue(null, "hourLayerIndex", -1),
                                    parseXml.getAttributeIntValue(null, "minuteLayerIndex", -1),
                                    parseXml.getAttributeIntValue(null, "secondLayerIndex", -1),
                                    parseXml.getAttributeIntValue(null, "defaultHour", 0),
                                    parseXml.getAttributeIntValue(null, "defaultMinute", 0),
                                    parseXml.getAttributeIntValue(null, "defaultSecond", 0))
                            }
                        }
                    }
                }
            }
            componentMap.forEach { (componentName, iconEntry) ->
                if (clockMetas.containsKey(iconEntry)) {
                    clockMap[componentName] = iconEntry
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }

    private fun getXml(name: String): XmlPullParser? {
        val res: Resources
        try {
            res = context.packageManager.getResourcesForApplication(packPackageName)
            val resourceId = res.getIdentifier(name, "xml", packPackageName)
            return if (0 != resourceId) {
                context.packageManager.getXml(packPackageName, resourceId, null)
            } else {
                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(res.assets.open("$name.xml"), Xml.Encoding.UTF_8.toString())
                parser
            }
        } catch (e: PackageManager.NameNotFoundException) {
        } catch (e: IOException) {
        } catch (e: XmlPullParserException) {
        }
        return null
    }

    companion object {
        private val scope = CoroutineScope(Dispatchers.IO) + CoroutineName("IconPack")
    }
}

private operator fun XmlPullParser.get(key: String): String? = this.getAttributeValue(null, key)
