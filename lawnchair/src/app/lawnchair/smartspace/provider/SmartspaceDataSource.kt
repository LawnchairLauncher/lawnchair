package app.lawnchair.smartspace.provider

import app.lawnchair.smartspace.model.SmartspaceTarget
import kotlinx.coroutines.flow.Flow

interface SmartspaceDataSource {
    val targets: Flow<List<SmartspaceTarget>>

    fun destroy()
}
