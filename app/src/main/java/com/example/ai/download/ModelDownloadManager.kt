package com.example.ai.download

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.example.ai.model.DownloadStatus
import com.example.ai.model.ModelDownloadProgress
import com.example.ai.model.SlmFamily
import com.example.ai.model.SlmModelInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ModelDownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val PREFS_NAME = "cici_slm_prefs"
        private const val KEY_SELECTED_MODEL_ID = "selected_model_id"

        val AVAILABLE_MODELS = listOf(
            SlmModelInfo(
                id = "qwen2.5-1.5b-instruct",
                displayName = "Qwen 2.5 (1.5B Instruct)",
                family = SlmFamily.QWEN,
                description = "Raja model kecil: Super cepat, hemat memori (~1.0 GB), paling fasih memahami bahasa pasar & slang Indonesia ('goceng', 'ceban', '15rb'), serta eksekusi JSON function calling sangat stabil.",
                approximateSizeMb = 1050L,
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-1.5b-instruct-q4_k_m.bin",
                isRecommended = true,
                parameterCount = "1.5B"
            ),
            SlmModelInfo(
                id = "gemma2-2b-it",
                displayName = "Gemma 2 (2B IT)",
                family = SlmFamily.GEMMA,
                description = "Model resmi Google (~1.4 GB): Gaya bahasa sangat ramah dan santun untuk pendamping belanja Bunda, serta rapi saat diberi system prompt terstruktur.",
                approximateSizeMb = 1450L,
                downloadUrl = "https://huggingface.co/google/gemma-2-2b-it/resolve/main/gemma-2-2b-it-cpu-int4.bin",
                fileName = "gemma2-2b-it-cpu-int4.bin",
                isRecommended = false,
                parameterCount = "2B"
            )
        )
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _selectedModel = MutableStateFlow(loadSelectedModel())
    val selectedModel: StateFlow<SlmModelInfo> = _selectedModel.asStateFlow()

    private val _downloadProgress = MutableStateFlow(
        ModelDownloadProgress(
            modelId = _selectedModel.value.id,
            status = if (isModelDownloaded(_selectedModel.value)) DownloadStatus.DOWNLOADED else DownloadStatus.NOT_DOWNLOADED,
            progressPercent = if (isModelDownloaded(_selectedModel.value)) 100 else 0,
            localFilePath = getModelFile(_selectedModel.value).takeIf { it.exists() }?.absolutePath
        )
    )
    val downloadProgress: StateFlow<ModelDownloadProgress> = _downloadProgress.asStateFlow()

    private var downloadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    fun getModelsDirectory(): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getModelFile(model: SlmModelInfo): File {
        return File(getModelsDirectory(), model.fileName)
    }

    fun isModelDownloaded(model: SlmModelInfo): Boolean {
        val file = getModelFile(model)
        // Check if file exists and has reasonable size (at least 50 MB to consider complete/valid)
        return file.exists() && file.length() > 50 * 1024 * 1024
    }

    fun isCurrentModelReady(): Boolean {
        return isModelDownloaded(_selectedModel.value)
    }

    fun selectModel(model: SlmModelInfo) {
        _selectedModel.value = model
        prefs.edit().putString(KEY_SELECTED_MODEL_ID, model.id).apply()
        refreshStatus()
    }

    fun refreshStatus() {
        val current = _selectedModel.value
        val downloaded = isModelDownloaded(current)
        val file = getModelFile(current)
        _downloadProgress.value = ModelDownloadProgress(
            modelId = current.id,
            status = if (downloaded) DownloadStatus.DOWNLOADED else DownloadStatus.NOT_DOWNLOADED,
            progressPercent = if (downloaded) 100 else 0,
            downloadedBytes = if (downloaded) file.length() else 0L,
            totalBytes = if (downloaded) file.length() else current.approximateSizeMb * 1024 * 1024,
            localFilePath = if (downloaded) file.absolutePath else null
        )
    }

    fun getAvailableStorageBytes(): Long {
        return try {
            val stat = StatFs(getModelsDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            -1L
        }
    }

    fun startDownload(customUrl: String? = null) {
        val targetModel = _selectedModel.value
        val destinationFile = getModelFile(targetModel)
        val tempFile = File(getModelsDirectory(), "${targetModel.fileName}.download")
        val downloadUrl = customUrl?.takeIf { it.isNotBlank() } ?: targetModel.downloadUrl

        if (downloadJob?.isActive == true) return

        downloadJob = scope.launch {
            try {
                _downloadProgress.value = ModelDownloadProgress(
                    modelId = targetModel.id,
                    status = DownloadStatus.DOWNLOADING,
                    progressPercent = 0,
                    downloadedBytes = 0L,
                    totalBytes = targetModel.approximateSizeMb * 1024 * 1024
                )

                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "ArArCicis-Mobile/2.0")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Gagal mengunduh (HTTP ${response.code}): ${response.message}")
                    }

                    val body = response.body ?: throw Exception("Respons server kosong")
                    val totalContentLength = body.contentLength().takeIf { it > 0 }
                        ?: (targetModel.approximateSizeMb * 1024 * 1024)

                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(tempFile)

                    val buffer = ByteArray(64 * 1024)
                    var totalRead = 0L
                    var bytesRead: Int
                    var lastUpdateTime = System.currentTimeMillis()
                    var bytesSinceLastUpdate = 0L
                    var currentSpeed = 0L

                    inputStream.use { input ->
                        outputStream.use { output ->
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                bytesSinceLastUpdate += bytesRead

                                val now = System.currentTimeMillis()
                                val timeDelta = now - lastUpdateTime
                                if (timeDelta >= 400) {
                                    currentSpeed = (bytesSinceLastUpdate * 1000) / timeDelta
                                    lastUpdateTime = now
                                    bytesSinceLastUpdate = 0L

                                    val percent = if (totalContentLength > 0) {
                                        ((totalRead * 100) / totalContentLength).toInt().coerceIn(0, 99)
                                    } else {
                                        0
                                    }

                                    _downloadProgress.value = ModelDownloadProgress(
                                        modelId = targetModel.id,
                                        status = DownloadStatus.DOWNLOADING,
                                        progressPercent = percent,
                                        downloadedBytes = totalRead,
                                        totalBytes = totalContentLength,
                                        speedBytesPerSec = currentSpeed
                                    )
                                }
                            }
                        }
                    }

                    // Download completed successfully: atomically rename
                    if (destinationFile.exists()) {
                        destinationFile.delete()
                    }
                    if (tempFile.renameTo(destinationFile)) {
                        _downloadProgress.value = ModelDownloadProgress(
                            modelId = targetModel.id,
                            status = DownloadStatus.DOWNLOADED,
                            progressPercent = 100,
                            downloadedBytes = destinationFile.length(),
                            totalBytes = destinationFile.length(),
                            localFilePath = destinationFile.absolutePath
                        )
                        Log.i(TAG, "Model ${targetModel.displayName} successfully downloaded to ${destinationFile.absolutePath}")
                    } else {
                        throw Exception("Gagal memindahkan file model yang telah diunduh")
                    }
                }
            } catch (e: CancellationException) {
                if (tempFile.exists()) tempFile.delete()
                _downloadProgress.value = ModelDownloadProgress(
                    modelId = targetModel.id,
                    status = DownloadStatus.NOT_DOWNLOADED,
                    progressPercent = 0
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading model: ${e.message}", e)
                if (tempFile.exists()) tempFile.delete()
                _downloadProgress.value = ModelDownloadProgress(
                    modelId = targetModel.id,
                    status = DownloadStatus.ERROR,
                    errorMessage = e.message ?: "Terjadi kesalahan saat mengunduh model"
                )
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        val tempFile = File(getModelsDirectory(), "${_selectedModel.value.fileName}.download")
        if (tempFile.exists()) tempFile.delete()
        refreshStatus()
    }

    fun deleteModel(model: SlmModelInfo): Boolean {
        cancelDownload()
        val file = getModelFile(model)
        val success = if (file.exists()) file.delete() else true
        refreshStatus()
        return success
    }

    private fun loadSelectedModel(): SlmModelInfo {
        val savedId = prefs.getString(KEY_SELECTED_MODEL_ID, null)
        return AVAILABLE_MODELS.find { it.id == savedId } ?: AVAILABLE_MODELS.first()
    }

    fun getCustomApiKey(): String {
        return prefs.getString("custom_api_key", "") ?: ""
    }

    fun setCustomApiKey(key: String) {
        prefs.edit().putString("custom_api_key", key.trim()).apply()
    }
}
