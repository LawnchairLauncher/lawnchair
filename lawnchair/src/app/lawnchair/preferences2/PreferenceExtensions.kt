package app.lawnchair.preferences2

import com.patrykmichalik.opto.core.firstBlocking
import com.patrykmichalik.opto.domain.Preference

fun <C, S> Preference<C, S, *>.firstBlocking() = firstBlocking()
