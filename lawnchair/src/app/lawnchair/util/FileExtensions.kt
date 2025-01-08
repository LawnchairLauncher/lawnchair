package app.lawnchair.util

import android.net.Uri
import androidx.core.content.FileProvider
import app.lawnchair.LawnchairApp
import java.io.File
import okio.FileMetadata
import okio.FileSystem
import okio.Path

internal fun Path.list(
    fileSystem: FileSystem = FileSystem.SYSTEM,
    isShowHidden: Boolean = false,
    isRecursively: Boolean = false,
): Sequence<Path> {
    return runCatching {
        if (isRecursively) {
            fileSystem.listRecursively(this)
        } else {
            fileSystem.list(this).asSequence()
        }
    }.getOrDefault(emptySequence()).filter {
        if (isShowHidden) true else !it.isHidden
    }
}

internal fun Path.getMetadata(fileSystem: FileSystem = FileSystem.SYSTEM): FileMetadata? = fileSystem.metadataOrNull(this)

internal fun Path.isDirectory(fileSystem: FileSystem = FileSystem.SYSTEM): Boolean = getMetadata(fileSystem)?.isDirectory == true

internal fun Path.isFile(fileSystem: FileSystem = FileSystem.SYSTEM): Boolean = !isDirectory(fileSystem)

internal fun Path.sizeOrEmpty(fileSystem: FileSystem = FileSystem.SYSTEM): Long = getMetadata(fileSystem)?.size ?: 0

internal fun Path.isRegularFile(fileSystem: FileSystem = FileSystem.SYSTEM): Boolean = getMetadata(fileSystem)?.isRegularFile == true

internal val Path.isHidden: Boolean get() = toString().contains("/.")

internal val Path.exists: Boolean get() = toFile().exists()

internal val Path.extension: String?
    get() {
        val dotIndex = name.lastIndexOf(".")
        return if (dotIndex == -1) null else name.substring(dotIndex + 1)
    }

internal val Path.nameWithoutExtension: String get() = name.substringBeforeLast(".")

internal val Path.mimeType: String? get() = extension?.extension2MimeType()

val fileProviderAuthority: String = "${LawnchairApp.instance.packageName}.fileprovider"

fun String.path2Uri(): Uri? = File(this).file2Uri()

fun File.file2Uri(): Uri? = try {
    FileProvider.getUriForFile(LawnchairApp.instance, fileProviderAuthority, this)
} catch (e: Exception) {
    e.printStackTrace()
    null
}
