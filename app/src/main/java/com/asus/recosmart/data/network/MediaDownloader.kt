package com.asus.recosmart.data.network

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.asus.recosmart.domain.model.CameraFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object MediaDownloader {

    private val mediaTransferMutex = Mutex()

    suspend fun downloadToMediaStore(
        context: Context,
        file: CameraFile,
        onProgress: (Int) -> Unit = {}
    ): Result<Uri> = withContext(Dispatchers.IO) {
        mediaTransferMutex.withLock {
            CameraNetworkManager.bindProcessToWifi(context)
            var insertedUri: Uri? = null
            try {
                val isVideo = file.isVideo
                val resolver = context.contentResolver
                val contentUri = if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val relativePath = if (isVideo) {
                    "${Environment.DIRECTORY_MOVIES}/ASUS RECO Smart"
                } else {
                    "${Environment.DIRECTORY_PICTURES}/ASUS RECO Smart"
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                insertedUri = resolver.insert(contentUri, contentValues)
                    ?: return@withContext Result.failure(Exception("MediaStore URI oluşturulamadı"))

                val resolvedUrl = com.asus.recosmart.domain.model.CameraMediaUrlResolver.resolve(file)
                android.util.Log.d("MEDIA", com.asus.recosmart.domain.model.CameraMediaUrlResolver.formatDiagnosticLog("DOWNLOAD", file, resolvedUrl))

                val url = URL(resolvedUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 15000
                conn.doInput = true
                conn.connect()

                val statusCode = conn.responseCode
                android.util.Log.d("MEDIA", com.asus.recosmart.domain.model.CameraMediaUrlResolver.formatDiagnosticLog("DOWNLOAD", file, resolvedUrl, statusCode))

                if (statusCode != HttpURLConnection.HTTP_OK) {
                    val err = "HTTP $statusCode: ${conn.responseMessage}"
                    conn.disconnect()
                    cleanupIncompleteUri(context, insertedUri)
                    return@withContext Result.failure(Exception(err))
                }

                val contentLength = conn.contentLengthLong.let { if (it > 0) it else file.sizeBytes }
                val inputStream: InputStream = conn.inputStream

                resolver.openOutputStream(insertedUri)?.use { outputStream ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val percent = ((totalRead * 100) / contentLength).toInt().coerceIn(0, 100)
                            onProgress(percent)
                        }
                    }
                    outputStream.flush()
                } ?: run {
                    cleanupIncompleteUri(context, insertedUri)
                    return@withContext Result.failure(Exception("MediaStore çıktısı açılamadı"))
                }

                inputStream.close()
                conn.disconnect()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(insertedUri, contentValues, null, null)
                }

                onProgress(100)
                Result.success(insertedUri)
            } catch (e: Exception) {
                cleanupIncompleteUri(context, insertedUri)
                Result.failure(e)
            }
        }
    }

    suspend fun getOrCacheFile(
        context: Context,
        file: CameraFile,
        onProgress: (Int) -> Unit = {}
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "media_cache").apply { if (!exists()) mkdirs() }
            val cachedFile = File(cacheDir, file.filename)

            if (cachedFile.exists() && cachedFile.length() > 0) {
                onProgress(100)
                val authority = "${context.packageName}.fileprovider"
                val contentUri = FileProvider.getUriForFile(context, authority, cachedFile)
                return@withContext Result.success(contentUri)
            }

            mediaTransferMutex.withLock {
                if (cachedFile.exists() && cachedFile.length() > 0) {
                    onProgress(100)
                    val authority = "${context.packageName}.fileprovider"
                    return@withContext Result.success(FileProvider.getUriForFile(context, authority, cachedFile))
                }

                CameraNetworkManager.bindProcessToWifi(context)
                val resolvedUrl = com.asus.recosmart.domain.model.CameraMediaUrlResolver.resolve(file)
                android.util.Log.d("MEDIA", com.asus.recosmart.domain.model.CameraMediaUrlResolver.formatDiagnosticLog("CACHE", file, resolvedUrl))

                val url = URL(resolvedUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 15000
                conn.doInput = true
                conn.connect()

                val statusCode = conn.responseCode
                android.util.Log.d("MEDIA", com.asus.recosmart.domain.model.CameraMediaUrlResolver.formatDiagnosticLog("CACHE", file, resolvedUrl, statusCode))

                if (statusCode != HttpURLConnection.HTTP_OK) {
                    conn.disconnect()
                    return@withContext Result.failure(Exception("HTTP $statusCode: ${conn.responseMessage}"))
                }

                val contentLength = conn.contentLengthLong.let { if (it > 0) it else file.sizeBytes }
                val inputStream = conn.inputStream
                val tempFile = File(cacheDir, "${file.filename}.tmp")
                val outputStream = FileOutputStream(tempFile)

                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                var totalRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (contentLength > 0) {
                        val percent = ((totalRead * 100) / contentLength).toInt().coerceIn(0, 100)
                        onProgress(percent)
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
                conn.disconnect()

                if (tempFile.renameTo(cachedFile)) {
                    onProgress(100)
                    val authority = "${context.packageName}.fileprovider"
                    val contentUri = FileProvider.getUriForFile(context, authority, cachedFile)
                    Result.success(contentUri)
                } else {
                    Result.failure(Exception("Geçici dosya adlandırılamadı"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openInExternalApp(context: Context, contentUri: Uri, isVideo: Boolean): Result<Unit> {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, if (isVideo) "video/mp4" else "image/jpeg")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Uygulama ile aç").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareMedia(context: Context, contentUri: Uri, isVideo: Boolean): Result<Unit> {
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, contentUri)
                type = if (isVideo) "video/mp4" else "image/jpeg"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val title = if (isVideo) "Videoyu paylaş" else "Fotoğrafı paylaş"
            val chooser = Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun cleanupIncompleteUri(context: Context, uri: Uri?) {
        if (uri != null) {
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }
}
