package app.lawnchair.search.algorithms.engine.provider.textclassifier

import android.content.Context
import android.util.Log
import android.view.textclassifier.TextClassification
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.search.algorithms.engine.SearchProvider
import app.lawnchair.search.algorithms.engine.SearchResult
import com.android.launcher3.Utilities
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object TextClassifierSearchProvider : SearchProvider {
    private const val TAG = "TextClassifierSearchProvider"

    /** Enables detailed logging, **Warning: Contain search query data** */
    private const val SENSITIVE_LOGGING = false

    private const val MIN_QUERY_LENGTH = 3

    /** Note: Most devices can classify text within less than a second, a value higher than that is being generous */
    private const val CLASSIFICATION_TIMEOUT_MS = 1000L

    override val id = "textclassifier"

    override fun search(
        context: Context,
        query: String,
    ): Flow<List<SearchResult>> = flow {
        val legacyPrefs = PreferenceManager.INSTANCE.get(context)
        if (!Utilities.ATLEAST_P || !legacyPrefs.searchResultTextClassifier.get() || query.length < MIN_QUERY_LENGTH) {
            emit(emptyList())
            return@flow
        }

        val results = withContext(Dispatchers.IO) {
            val searchResults = mutableListOf<SearchResult>()
            val tcm = context.getSystemService(TextClassificationManager::class.java)
            val textClassifier = tcm?.textClassifier ?: return@withContext emptyList()

            try {
                val request = TextClassification.Request.Builder(query, 0, query.length)
                    .build()
                val classification = withTimeoutOrNull(CLASSIFICATION_TIMEOUT_MS.milliseconds) {
                    textClassifier.classifyText(request)
                }
                val entityType = classification?.topEntityTypeOrNull()
                if ((entityType != null) && !isTypeEnabled(legacyPrefs, entityType)) {
                    return@withContext emptyList()
                }
                if (classification == null) {
                    Log.w(TAG, "Classification timed out after ${CLASSIFICATION_TIMEOUT_MS}ms")
                }
                if (SENSITIVE_LOGGING) {
                    Log.d(TAG, "Classification result: $classification")
                }
                if (classification?.actions?.isNotEmpty() == true) {
                    classification.actions.forEach { action ->
                        searchResults.add(
                            SearchResult.Action.TextAction(
                                title = action.title.toString(),
                                subtitle = action.contentDescription.toString(),
                                entityType = entityType,
                                pendingIntent = action.actionIntent,
                                icon = if (action.shouldShowIcon()) action.icon else null,
                            ),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Classification failed", e)
            }

            return@withContext searchResults
        }

        emit(results)
    }

    private fun isTypeEnabled(prefs: PreferenceManager, type: String): Boolean {
        return when (type) {
            // SDK 28
            TextClassifier.TYPE_EMAIL -> prefs.searchResultTextClassifierEmail.get()

            // SDK 26
            TextClassifier.TYPE_PHONE -> prefs.searchResultTextClassifierPhone.get()

            TextClassifier.TYPE_ADDRESS -> prefs.searchResultTextClassifierAddress.get()

            TextClassifier.TYPE_URL -> prefs.searchResultTextClassifierUrl.get()

            // SDK 28
            TextClassifier.TYPE_DATE, TextClassifier.TYPE_DATE_TIME -> prefs.searchResultTextClassifierDate.get()

            TextClassifier.TYPE_FLIGHT_NUMBER -> prefs.searchResultTextClassifierFlight.get()

            // SDK 28
            TextClassifier.TYPE_OTHER -> prefs.searchResultTextClassifierOthers.get()

            else -> false // TYPE_UNKNOWN and OTP related
        }
    }

    private fun TextClassification.topEntityTypeOrNull(): String? {
        if (entityCount <= 0) {
            return null
        }

        return getEntity(0).takeIf { it.isNotBlank() }
    }
}
