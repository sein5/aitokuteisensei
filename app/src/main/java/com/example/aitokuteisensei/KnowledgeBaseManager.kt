package com.example.aitokuteisensei

import android.content.Context
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
    private var chunks: List<KnowledgeChunk> = emptyList()
    private var textEmbedder: TextEmbedder? = null

    suspend fun initialize() = withContext(Dispatchers.IO) {
        // 1. Load JSON from assets and filter by language code "en"
        val jsonString = context.assets.open("tokutei_kaigo_knowledge_base_updated.json").use { stream ->
            InputStreamReader(stream).readText()
        }
        val itemType = object : TypeToken<List<KnowledgeChunk>>() {}.type
        val allChunks: List<KnowledgeChunk> = Gson().fromJson(jsonString, itemType)
        chunks = allChunks.filter { it.lang.equals("en", ignoreCase = true) }

        // 2. Initialize MediaPipe Text Embedder with your custom asset
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("universal_sentence_encoder.tflite")
            .build()
        val options = TextEmbedder.TextEmbedderOptions.builder()
            .setBaseOptions(baseOptions)
            .build()
        textEmbedder = TextEmbedder.createFromOptions(context, options)
    }

    fun retrieveContext(query: String, topK: Int = 3): String {
        val embedder = textEmbedder ?: return ""
        if (chunks.isEmpty()) return ""

        // Generate embedding vector for the user query
        val embeddingResult = embedder.embed(query)
        val queryVector = embeddingResult.embeddingResult().embeddings().firstOrNull()?.floatEmbedding() ?: return ""

        // Score chunks using Cosine Similarity
        val scoredChunks = chunks.map { chunk ->
            val score = cosineSimilarity(queryVector, chunk.embedding)
            chunk to score
        }

        // Return top matches concatenated together
        return scoredChunks
            .sortedByDescending { it.second }
            .take(topK)
            .joinToString("\n\n") { it.first.text }
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