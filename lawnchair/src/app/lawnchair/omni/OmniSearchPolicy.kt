/*
 * Copyright 2026 Abdelrahman Hussein (عبدالرحمن حسين)
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

package app.lawnchair.omni

/** Local decision only: evaluating a search never invokes the agent or binds a service. */
object OmniSearchPolicy {
    const val MAX_QUERY_CHARS = 8_192

    fun shouldSuggest(query: String, enabled: Boolean, always: Boolean, hasLocalResults: Boolean): Boolean = enabled && query.isNotBlank() && query.length <= MAX_QUERY_CHARS && (always || !hasLocalResults)
}
