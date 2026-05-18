package com.example.aitokuteisensei

import android.content.Context
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

class GemmaChatEngine(private val context: Context) {

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    var isInitialized = false
        private set

    /**
     * Initializes the LiteRT engine.
     * Pass the absolute path where your downloaded .litertlm file is stored.
     */
    suspend fun initialize(modelPath: String) = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext

        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            throw IllegalArgumentException("Model file not found at: $modelPath")
        }

        // Configure and load the model into memory
        val config = EngineConfig(modelPath = modelFile.absolutePath)
        val loadedEngine = Engine(config)
        loadedEngine.initialize()

        engine = loadedEngine
        // Create a persistent multi-turn chat session
        conversation = loadedEngine.createConversation()
        isInitialized = true
    }

    /**
     * Sends a message and streams the text response chunk by chunk.
     */
    fun sendMessageStream(message: String): Flow<String> = flow {
        val currentConversation = conversation
            ?: throw IllegalStateException("Engine or Conversation not initialized")

        // 1. Collect the streamed Message objects from LiteRT-LM
        currentConversation.sendMessageAsync(message).collect { responseMessage ->
            // 2. The Message object's toString() method extracts the current
            // string chunk/token emitted by the LLM layer.
            val textChunk = responseMessage.toString()

            // 3. Emit the raw string token so the Jetpack Compose UI streams perfectly
            emit(textChunk)
        }
    }
    /**
     * Always free up memory when your Activity or ViewModel is destroyed!
     * Local LLMs take significant background memory footprint (approx 1.5 - 2GB).
     */
    fun close() {
        runCatching { conversation?.close() }
        runCatching { engine?.close() }
        conversation = null
        engine = null
        isInitialized = false
    }
}