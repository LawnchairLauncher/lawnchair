package app.lawnchair.search.algorithms.data

data class RecentKeyword(
    val data: Map<String, String>,
) {
    fun getValueByKey(key: String): String? {
        return data[key]
    }
}
