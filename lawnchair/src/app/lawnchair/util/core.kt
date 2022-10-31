package app.lawnchair.util

import android.content.Context
import androidx.core.content.getSystemService
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

inline fun <reified T : Any> Context.requireSystemService(): T = checkNotNull(getSystemService())

@OptIn(ExperimentalContracts::class)
fun CharSequence?.isNotNullOrEmpty(): Boolean {
    contract {
        returns(true) implies (this@isNotNullOrEmpty != null)
    }
    return this.isNullOrEmpty().not()
}
