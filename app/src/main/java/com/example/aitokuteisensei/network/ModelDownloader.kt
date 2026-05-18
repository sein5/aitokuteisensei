package com.example.aitokuteisensei.network

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class ModelConfig(
    val model_name: String,
    val version: String,
    val format: String,
    val download_url: String,
    val filesize_bytes: Long,
    val is_active: Boolean
)

sealed class DownloadState {
    object Idle : DownloadState()
    object FetchingConfig : DownloadState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    object SavingToPublicDownloads : DownloadState()
    object CopyingLocalFile : DownloadState()
    object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class ModelDownloader(private val client: OkHttpClient = OkHttpClient()) {

    private val logTag = "GemmaDownload"
    private val configUrl = "https://raw.githubusercontent.com/sein5/Gemma4Good_Hackathon/main/Configs/gemma4_good_hackathon_config.json"

    suspend fun fetchModelConfig(): ModelConfig = withContext(Dispatchers.IO) {
        Log.d(logTag, "Fetching remote configuration from endpoint: $configUrl")
        val request = Request.Builder().url(configUrl).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errMsg = "HTTP Request failed. Server responded with status code: ${response.code}"
                    Log.e(logTag, errMsg)
                    throw Exception(errMsg)
                }
                val json = response.body?.string() ?: throw Exception("Configuration payload body returned null.")
                Log.d(logTag, "Successfully parsed model data configuration: $json")
                Gson().fromJson(json, ModelConfig::class.java)
            }
        } catch (e: Exception) {
            Log.e(logTag, "Critical failure fetching model metadata config: ${e.localizedMessage}", e)
            throw e
        }
    }

    /**
     * Streams the network payload directly into sandbox storage where write execution permissions are guaranteed.
     */
    fun downloadModelToInternalStorage(downloadUrl: String, internalFile: File): Flow<DownloadState> = flow {
        Log.d(logTag, "Initializing direct stream download. Source: $downloadUrl -> Target Workspace: ${internalFile.absolutePath}")
        emit(DownloadState.Downloading(0f, 0, 1))

        val request = Request.Builder().url(downloadUrl).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errMsg = "Download canceled by host server. Code: ${response.code}"
                    Log.e(logTag, errMsg)
                    emit(DownloadState.Error(errMsg))
                    return@flow
                }

                val body = response.body
                if (body == null) {
                    val errMsg = "Server response body stream initialized empty."
                    Log.e(logTag, errMsg)
                    emit(DownloadState.Error(errMsg))
                    return@flow
                }

                val totalBytes = if (body.contentLength() <= 0) 4200000000L else body.contentLength()
                var bytesDownloaded = 0L

                internalFile.parentFile?.mkdirs()

                body.byteStream().use { input ->
                    internalFile.outputStream().use { output ->
                        val buffer = ByteArray(65536) // Expanded buffer size optimizing high-capacity data transfers
                        var bytesRead: Int
                        var lastProgressUpdate = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead

                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastProgressUpdate > 200) {
                                val progress = bytesDownloaded.toFloat() / totalBytes.toFloat()
                                emit(DownloadState.Downloading(progress, bytesDownloaded, totalBytes))
                                lastProgressUpdate = currentTime
                            }
                        }
                    }
                }
                Log.d(logTag, "Internal sandboxed engine asset compilation complete.")
                emit(DownloadState.Success)
            }
        } catch (e: Exception) {
            Log.e(logTag, "Unexpected system crash during active network execution: ${e.localizedMessage}", e)
            if (internalFile.exists()) {
                internalFile.delete()
                Log.w(logTag, "Corrupted/partial model remnants cleared from internal workspace.")
            }
            emit(DownloadState.Error(e.localizedMessage ?: "Network download execution pipeline interrupted."))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Safely clones the model database file over to the system public Download directory using MediaStore,
     * maintaining compatibility with modern storage restrictions.
     */
    suspend fun copyToPublicDownloads(context: Context, internalFile: File, fileName: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(logTag, "Exporting sandbox binary backup to local device public downloads directory.")
        try {
            val contentResolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }

            val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val publicUri = contentResolver.insert(collectionUri, contentValues)
            if (publicUri == null) {
                Log.e(logTag, "MediaStore insertion request returned null Uri registry pointer.")
                return@withContext false
            }

            contentResolver.openOutputStream(publicUri).use { outputStream ->
                if (outputStream == null) {
                    Log.e(logTag, "Unable to establish open outbound write stream on registered Uri target: $publicUri")
                    return@withContext false
                }
                internalFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Log.d(logTag, "Public local asset duplication verified. Path mapped to MediaStore: $publicUri")
            true
        } catch (e: Exception) {
            Log.e(logTag, "Scoped storage bypass execution halted with exception: ${e.localizedMessage}", e)
            false
        }
    }
}