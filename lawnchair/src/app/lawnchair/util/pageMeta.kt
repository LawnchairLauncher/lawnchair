package app.lawnchair.util

class MetaState(val title: String)
class Meta(val title: String?)

private fun getTitleFromPropsList(propsList: List<Meta>): String {
    return propsList.lastOrNull()?.title ?: ""
}

val pageMeta = createSideEffect<MetaState, Meta> { propsList ->
    MetaState(title = getTitleFromPropsList(propsList))
}
