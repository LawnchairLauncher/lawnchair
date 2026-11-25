package app.lawnchair.data.wallpaper.service

import android.app.WallpaperManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.AtomicFile
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import app.lawnchair.data.AppDatabase
import app.lawnchair.data.wallpaper.Wallpaper
import app.lawnchair.util.bitmapToByteArray
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@LauncherAppSingleton
class WallpaperService @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    val dao = AppDatabase.Companion.INSTANCE.get(context).wallpaperDao()

    private val mutex = Mutex()

    suspend fun saveWallpaper(wallpaperManager: WallpaperManager) {
        try {
            // Live wallpapers have no static bitmap to store for the quick picker.
            if (wallpaperManager.wallpaperInfo != null) {
                return
            }
            val wallpaperDrawable = wallpaperManager.drawable ?: return
            val currentBitmap = (wallpaperDrawable as? BitmapDrawable)?.bitmap
                ?: wallpaperDrawable.toBitmap()

            val byteArray = bitmapToByteArray(currentBitmap)

            saveWallpaper(byteArray)
        } catch (e: Exception) {
            Log.e("WallpaperChange", "Error detecting wallpaper change: ${e.message}")
        }
    }

    private fun calculateChecksum(imageData: ByteArray): String {
        return MessageDigest.getInstance("MD5")
            .digest(imageData)
            .joinToString("") { "%02x".format(it) }
    }

    private suspend fun saveWallpaper(imageData: ByteArray) {
        mutex.withLock {
            val timestamp = System.currentTimeMillis()

            val checksum = calculateChecksum(imageData)

            val existingWallpapers = dao.getTopWallpapers()

            if (existingWallpapers.any { it.checksum == checksum }) {
                Log.d("WallpaperService", "Wallpaper already exists with checksum: $checksum")
                return
            }
            val imagePath = saveImageToAppStorage(imageData, checksum)
            if (existingWallpapers.size < 4) {
                val wallpaper = Wallpaper(
                    imagePath = imagePath,
                    rank = existingWallpapers.size,
                    timestamp = timestamp,
                    checksum = checksum,
                )
                dao.insert(wallpaper)
            } else {
                val lowestRankedWallpaper = existingWallpapers.minByOrNull { it.timestamp }

                if (lowestRankedWallpaper != null) {
                    dao.deleteWallpaper(lowestRankedWallpaper.id)
                    deleteWallpaperFile(lowestRankedWallpaper.imagePath)
                }

                for (wallpaper in existingWallpapers) {
                    if (wallpaper.rank >= (lowestRankedWallpaper?.rank ?: 0)) {
                        dao.updateRank(wallpaper.rank)
                    }
                }

                val wallpaper = Wallpaper(
                    imagePath = imagePath,
                    rank = 0,
                    timestamp = timestamp,
                    checksum = checksum,
                )
                dao.insert(wallpaper)
            }
        }
    }

    suspend fun updateWallpaperRank(selectedWallpaper: Wallpaper) {
        val topWallpapers = dao.getTopWallpapers()
        val currentTime = System.currentTimeMillis()

        dao.updateWallpaper(selectedWallpaper.id, rank = 0, timestamp = currentTime)

        for (wallpaper in topWallpapers) {
            if (wallpaper.id != selectedWallpaper.id) {
                dao.updateRank(wallpaper.rank)
            }
        }
    }

    fun getTopWallpapers(): List<Wallpaper> = runBlocking {
        val wallpapers = dao.getTopWallpapers()
        wallpapers.ifEmpty { emptyList() }
    }

    private fun deleteWallpaperFile(imagePath: String) {
        val file = File(imagePath)
        if (file.exists()) {
            file.delete()
        }
    }

    private fun saveImageToAppStorage(imageData: ByteArray, checksum: String): String {
        val storageDir = File(context.filesDir, "wallpapers")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        val imageFile = File(storageDir, "wallpaper_$checksum.jpg")

        if (!imageFile.exists()) {
            // Write through AtomicFile so a failed write never leaves a partial
            // file under the final checksum name that later saves would reuse.
            val atomicFile = AtomicFile(imageFile)
            val fos = atomicFile.startWrite()
            try {
                fos.write(imageData)
                atomicFile.finishWrite(fos)
            } catch (e: Exception) {
                atomicFile.failWrite(fos)
                throw e
            }
        }

        if (!imageFile.isFile) {
            throw IOException("Failed to publish wallpaper file ${imageFile.name}")
        }

        return imageFile.absolutePath
    }

    override fun close() {
        TODO("Not yet implemented")
    }
    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getWallpaperService)
    }
}
