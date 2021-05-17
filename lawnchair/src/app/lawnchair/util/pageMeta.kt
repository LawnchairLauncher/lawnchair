package app.lawnchair.util

class MetaState(val title: String, val topBarFloating: Boolean)
class Meta(val title: String? = null, val topBarFloating: Boolean? = null)

private fun <T> getFromPropsList(propsList: List<Meta>, defaultValue: T, extractor: (meta: Meta) -> T?): T =
    propsList
        .asSequence()
        .map(extractor)
        .lastOrNull { it != null } ?: defaultValue

val pageMeta = createSideEffect<MetaState, Meta> { propsList ->
    MetaState(
        title = getFromPropsList(propsList, "", Meta::title),
        topBarFloating = getFromPropsList(propsList, false, Meta::topBarFloating),
    )
}
