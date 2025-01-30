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

package app.lawnchair.flowerpot

import android.content.Context
import app.lawnchair.flowerpot.parser.FlowerpotReader
import app.lawnchair.flowerpot.rules.Rules
import app.lawnchair.util.SingletonHolder
import app.lawnchair.util.ensureOnMainThread
import app.lawnchair.util.toTitleCase
import app.lawnchair.util.useApplicationContext
import com.android.launcher3.model.data.AppInfo
import java.io.InputStream
import java.util.Locale

/**
 * A ruleset for an app category
 */
class Flowerpot(private val context: Context, val name: String, private val loader: Flowerpot.() -> Unit) {

    val displayName: String by lazy {
        val id = context.resources.getIdentifier("category_${name.lowercase(Locale.getDefault())}", "string", context.packageName)
        if (id != 0) {
            context.getString(id)
        } else {
            beautifyName(name)
        }
    }
    private var loaded = false
    val rules: MutableSet<Rules> = mutableSetOf()
    val size get() = rules.size
    lateinit var apps: FlowerpotApps

    fun ensureLoaded() {
        if (!loaded) {
            load()
            loaded = true
        }
    }

    private fun load() {
        loader(this)
        apps = FlowerpotApps(context, this)
    }

    fun categorizeApps(appList: List<AppInfo?>?): Map<String, List<AppInfo>> {
        ensureLoaded()
        apps.updateAppList(appList)
        return apps.categorizedApps.toSortedMap()
    }

    /**
     * Load all data from
     */
    private fun loadFromInputStream(inputStream: InputStream) {
        rules.addAll(FlowerpotReader(inputStream).readRules())
    }

    companion object {
        /**
         * Load a flowerpot from an assets file
         */
        fun fromAssets(context: Context, path: String, name: String): Flowerpot {
            return Flowerpot(context, name) {
                loadFromInputStream(context.assets.open(path))
            }
        }

        /**
         * The current Flowerpot format version
         */
        const val VERSION_CURRENT = Version.AZALEA

        /**
         * List of all currently supported versions
         */
        val SUPPORTED_VERSIONS = arrayOf(
            VERSION_CURRENT,
        )

        /**
         * Path relative to assets/ to the directory containing the shipped flowerpot files
         */
        const val ASSETS_PATH = "flowerpot"

        private fun beautifyName(name: String): String {
            return name.replace('_', ' ').lowercase(Locale.getDefault()).toTitleCase()
        }
    }

    object Version {
        /**
         * Azalea - A beautiful pink flower, known in China as "thinking of home bush" (sixiang shu).
         *
         * Changes:
         *  - The very first version of the format
         */
        const val AZALEA = 1
    }

    /**
     * Class used to interact with Pots as a whole, load hem
     */
    class Manager private constructor(private val context: Context) {

        private val pots = mutableMapOf<String, Flowerpot>()

        init {
            loadAssets()
        }

        /**
         * Load flowerpot files located in assets/
         */
        private fun loadAssets() {
            context.assets.list(ASSETS_PATH)?.forEach {
                pots.getOrPut(it) {
                    fromAssets(context, "$ASSETS_PATH/$it", it)
                }
            }
        }

        /**
         * Get a pot by its name, returns null if no pot with this name has been loaded
         *
         * @param name (code) name of the flowerpot, usually the filename of a flowerpot file
         * @param forceLoad Whether or not the pot should be loaded if it hasn't already been
         * @return the pot or null if none exists with this name
         */
        fun getPot(name: String, forceLoad: Boolean = true) = pots[name]?.apply {
            if (forceLoad) {
                ensureLoaded()
            }
        }

        fun getAllPots() = pots.values

        fun categorizeApps(appList: List<AppInfo?>?): Map<String, List<AppInfo>> {
            val categorizedApps = mutableMapOf<String, MutableList<AppInfo>>()
            pots.values.forEach { pot ->
                val potCategories = pot.categorizeApps(appList)
                potCategories.forEach { (category, apps) ->
                    categorizedApps.getOrPut(category) { mutableListOf() }.addAll(apps)
                }
            }
            return categorizedApps.toSortedMap()
        }

        companion object : SingletonHolder<Manager, Context>(
            ensureOnMainThread(
                useApplicationContext(
                    Flowerpot::Manager,
                ),
            ),
        ) {

            @JvmStatic
            override fun getInstance(arg: Context): Manager {
                return super.getInstance(arg)
            }
        }
    }
}
