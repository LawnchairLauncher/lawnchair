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

package ch.deletescape.lawnchair.flowerpot

import android.content.Context
import ch.deletescape.lawnchair.ensureOnMainThread
import ch.deletescape.lawnchair.flowerpot.parser.FlowerpotReader
import ch.deletescape.lawnchair.flowerpot.rules.Rule
import ch.deletescape.lawnchair.useApplicationContext
import ch.deletescape.lawnchair.util.SingletonHolder
import java.io.InputStream

/**
 * A ruleset for an app category
 */
class Flowerpot(val name: String, private val loader: Flowerpot.() -> Unit) {

    private var loaded = false
    private val rules: MutableList<Rule> = mutableListOf<Rule>()
    val size get() = rules.size

    fun ensureLoaded() {
        if (!loaded) {
            loader(this)
            loaded = true
        }
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
            return Flowerpot(name) {
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
                VERSION_CURRENT
        )
        /**
         * Path relative to assets/ to the directory containing the shipped flowerpot files
         */
        const val ASSETS_PATH = "flowerpot"
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
                pots.computeIfAbsent(it) {
                    Flowerpot.fromAssets(context, "$ASSETS_PATH/$it", it)
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

        companion object: SingletonHolder<Manager, Context>(ensureOnMainThread(useApplicationContext(::Manager))) {

            @JvmStatic
            override fun getInstance(arg: Context): Manager {
                return super.getInstance(arg)
            }
        }
    }
}