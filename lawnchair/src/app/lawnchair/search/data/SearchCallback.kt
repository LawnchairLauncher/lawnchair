package app.lawnchair.search.data

interface SearchCallback {
    fun onSearchLoaded(items: List<Any>)
    fun onSearchFailed(error: String)
    fun onLoading()
}
