package app.lawnchair.search.algorithms.data

data class ContactInfo(
    val contactId: String,
    val name: String,
    var number: String,
    val phoneBookLabel: String,
    val uri: String,
    var packages: String,
)
