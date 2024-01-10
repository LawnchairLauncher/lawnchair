package app.lawnchair.studyplanner.data

import android.content.Context
import android.util.Log
import app.lawnchair.preferences2.PreferenceManager2
import com.patrykmichalik.opto.core.onEach
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Created by shubhampandey
 */
class QuotesDataSource(context: Context) {

    private val prefs = PreferenceManager2.getInstance(context)

    val coroutineScope = CoroutineScope(context = Dispatchers.IO)

    var lastShownTimestamp = 0L
    var lastShownQuote = 0

    val quotes = listOf(
        "There are three schoolmasters for everybody that will employ them - the senses, intelligent companions, and books.",
        "It is not knowledge, but the act of learning, not possession but the act of getting there, which grants the greatest enjoyment.",
        "If at first you don't succeed, you must be doing something wrong.",
        "No bird soars too high if he soars with his own wings.",
        "The successful people are the ones who can think up things for the rest of the world to keep busy at.",
        "Success is not forever, and failure is not fatal.",
        "The reward of a thing well done is to have done it."
    )

    init {
        prefs.lastTimeStampQuoteShown.onEach(launchIn = coroutineScope) {
            lastShownTimestamp = it
        }

        prefs.lastQuoteShown.onEach(launchIn = coroutineScope) {
            lastShownQuote = it
        }
    }

    fun setLastTimeShown(timestamp: Long) {
        coroutineScope.launch {
            prefs.lastTimeStampQuoteShown.set(timestamp)
        }
    }

    fun setLastQuoteShown(index: Int) {
        coroutineScope.launch {
            prefs.lastQuoteShown.set(index)
        }
    }
}
