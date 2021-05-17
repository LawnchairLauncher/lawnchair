package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PreferenceLayout(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        content()
    }
}

@Composable
fun PreferenceLayoutLazyColumn(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    LazyColumn(modifier = modifier.fillMaxHeight()) {
        content()
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
