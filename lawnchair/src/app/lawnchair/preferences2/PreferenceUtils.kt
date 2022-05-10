package app.lawnchair.preferences2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.patrykmichalik.preferencemanager.Preference
import com.patrykmichalik.preferencemanager.firstBlocking

@Composable
fun <C, S> Preference<C, S>.asState() = get().collectAsState(initial = firstBlocking())
