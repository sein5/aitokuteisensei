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
    private val knowledgeBaseManager = KnowledgeBaseManager(context)
    var isInitialized = false
        private set

    suspend fun initialize(modelPath: String) = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext

        // Initialize RAG knowledge base first
        knowledgeBaseManager.initialize()

        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            throw IllegalArgumentException("Model file not found at: $modelPath")
        }

        val config = EngineConfig(modelPath = modelFile.absolutePath)
        val loadedEngine = Engine(config)
        loadedEngine.initialize()

        engine = loadedEngine
        conversation = loadedEngine.createConversation()
        isInitialized = true
    }

    fun sendMessageStream(message: String): Flow<String> = flow {
        val currentConversation = conversation
            ?: throw IllegalStateException("Engine or Conversation not initialized")

        // Retrieve background context using local query embedding
        val contextText = knowledgeBaseManager.retrieveContext(message)

        // Formulate the augmented prompt payload
        val augmentedPrompt = if (contextText.isNotEmpty()) {
            "Use the following pieces of context to answer the question at the end. If you don't know the answer, say that you don't know.\n\nContext:\n$contextText\n\nQuestion: $message\nAnswer:"
        } else {
            message
        }

        currentConversation.sendMessageAsync(augmentedPrompt).collect { responseMessage ->
            val textChunk = responseMessage.toString()
            emit(textChunk)
        }
    }

    fun close() {
        runCatching { knowledgeBaseManager.close() }
        runCatching { conversation?.close() }
        runCatching { engine?.close() }
        conversation = null
        engine = null
        isInitialized = false
    }


}