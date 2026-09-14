/*
 * Copyright 2026, Renns Project
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

package app.lawnchair

/**
 * Switchboard for Sora Launcher's own online presence.
 *
 * Upstream Lawnchair wired the About screen, the contributor credits and the
 * in-app updater to its own community, GitHub repository and build server.
 * Sora Launcher is an independent fork, so those are switched off rather than
 * left pointing at another project's infrastructure.
 *
 * To bring a section back: fill in the URLs it needs, then flip its flag to
 * `true`. Nothing else has to change -- every call site reads from here.
 */
object SoraBranding {

    /** Community and social links on the About screen. Needs the `*_URL` values below. */
    const val ENABLE_COMMUNITY_LINKS = false

    /** "Product" and "Support & PR" contributor lists on the About screen. */
    const val ENABLE_CONTRIBUTORS = false

    /**
     * In-app updater: polls GitHub releases and side-loads an APK.
     * Also requires a nightly application ID and the user's own opt-in.
     */
    const val ENABLE_AUTO_UPDATER = false

    /** Long-pressing the version string opens this build's commit on the web. */
    const val ENABLE_COMMIT_LINKS = false

    /** Listed under Legal on the About screen. The row stays hidden while blank. */
    const val PRIVACY_POLICY_URL = ""

    /** GitHub repository backing the updater, the changelog and the commit links. */
    const val GITHUB_OWNER = ""
    const val GITHUB_REPO = ""

    /** Web URL of [GITHUB_OWNER]/[GITHUB_REPO]. */
    val githubUrl: String
        get() = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO"

    /** Web URL of a single commit, or `null` when commit links are off. */
    fun commitUrl(sha: String): String? = when {
        !ENABLE_COMMIT_LINKS || GITHUB_OWNER.isEmpty() || GITHUB_REPO.isEmpty() -> null
        else -> "$githubUrl/commit/$sha"
    }

    // Community links, in the order they appear on the About screen.
    const val NEWS_URL = ""
    const val SUPPORT_URL = ""
    const val TRANSLATE_URL = ""
    const val DONATE_URL = ""
    const val TELEGRAM_URL = ""
    const val DISCORD_URL = ""
    const val X_URL = ""
}
