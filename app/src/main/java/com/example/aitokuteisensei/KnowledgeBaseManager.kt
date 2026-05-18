package com.example.aitokuteisensei

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import kotlin.math.sqrt

data class KnowledgeChunk(
    val id: String,
    val text: String,
    val lang: String,
    val source: String,
    val embedding: FloatArray
)

class KnowledgeBaseManager(private val context: Context) {
    private val logTag = "KnowledgeBase"
    private var chunks: List<KnowledgeChunk> = emptyList()
    private var textEmbedder: TextEmbedder? = null

    suspend fun initialize(langCode: String) = withContext(Dispatchers.IO) {
        // 1. Safely load JSON
        try {
            val jsonString = context.assets.open("tokutei_kaigo_knowledge_base_updated.json").use { stream ->
                InputStreamReader(stream).readText()
            }
            val itemType = object : TypeToken<List<KnowledgeChunk>>() {}.type
            val allChunks: List<KnowledgeChunk> = Gson().fromJson(jsonString, itemType)
            chunks = allChunks.filter { it.lang.equals(langCode, ignoreCase = true) }
            Log.d(logTag, "Successfully loaded ${chunks.size} knowledge chunks for lang: $langCode")
        } catch (e: Exception) {
            Log.e(logTag, "Failed to load knowledge base JSON: ${e.message}")
            chunks = emptyList()
        }

        // 2. Safely initialize Embedder
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("universal_sentence_encoder.tflite")
                .build()
            val options = TextEmbedder.TextEmbedderOptions.builder()
                .setBaseOptions(baseOptions)
                .build()
            textEmbedder = TextEmbedder.createFromOptions(context, options)
            Log.d(logTag, "Text Embedder initialized successfully.")
        } catch (e: Exception) {
            Log.e(logTag, "Failed to initialize Text Embedder (Missing TFLite file?): ${e.message}")
            textEmbedder = null
        }
    }

    fun retrieveContext(query: String, topK: Int = 3): String {
        val embedder = textEmbedder
        if (embedder == null || chunks.isEmpty()) {
            Log.w(logTag, "Retrieval skipped: Embedder or Chunks missing.")
            return ""
        }

        try {
            val embeddingResult = embedder.embed(query)
            val queryVector = embeddingResult.embeddingResult().embeddings().firstOrNull()?.floatEmbedding() ?: return ""

            val scoredChunks = chunks.map { chunk -> chunk to cosineSimilarity(queryVector, chunk.embedding) }
            return scoredChunks.sortedByDescending { it.second }.take(topK).joinToString("\n\n") { it.first.text }
        } catch (e: Exception) {
            Log.e(logTag, "Error during RAG retrieval: ${e.message}")
            return ""
        }
    }

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        return if (normA == 0.0f || normB == 0.0f) 0.0f else (dotProduct / (sqrt(normA) * sqrt(normB)))
    }

    fun close() {
        textEmbedder?.close()
        textEmbedder = null
    }
}