package com.example.aitokuteisensei

import android.content.Context
import com.example.aitokuteisensei.data.AppDatabase
import com.example.aitokuteisensei.data.ChatMessageEntity
import com.example.aitokuteisensei.data.UserPreferences
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

class GemmaChatEngine(private val context: Context) {

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private val knowledgeBaseManager = KnowledgeBaseManager(context)
    private val userPreferences = UserPreferences(context)
    private var userLang: String = "en"

    private val chatMessageDao = AppDatabase.getDatabase(context).chatMessageDao()
    val historyFlow = chatMessageDao.getAllMessagesFlow()

    var isInitialized = false
        private set

    // Helper map to convert language codes to full names for prompt guidance
    private val langNameMap = mapOf(
        "cn" to "Chinese (中文)",
        "en" to "English",
        "ind" to "Indonesian (Bahasa Indonesia)",
        "jp" to "Japanese (日本語)",
        "tgl" to "Tagalog"
    )

    suspend fun initialize(modelPath: String) = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext

        // Fetch user selected language from DataStore
        userLang = userPreferences.languageFlow.first()

        // Initialize RAG knowledge base with dynamic language code
        knowledgeBaseManager.initialize(userLang)

        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            throw IllegalArgumentException("Model file not found at: $modelPath")
        }

        val config = EngineConfig(modelPath = modelFile.absolutePath)
        val loadedEngine = Engine(config)
        loadedEngine.initialize()
        engine = loadedEngine

        val samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7)
        val conversationConfig = ConversationConfig(samplerConfig = samplerConfig)

        conversation = loadedEngine.createConversation(conversationConfig)
        isInitialized = true
    }

    fun sendMessageStream(message: String): Flow<String> = flow {
        val currentConversation = conversation ?: throw IllegalStateException("Engine not initialized")

        chatMessageDao.insertMessage(ChatMessageEntity(text = message, isUser = true))
        val contextText = knowledgeBaseManager.retrieveContext(message, topK = 5)

        val targetLanguageName = langNameMap[userLang] ?: "English"

        // Augment system instructions to force-reply in the correct language
        val augmentedPrompt = if (contextText.isNotEmpty()) {
            """
            You are a friendly, warm, and encouraging Tokutei Kaigo (Nursing Care) study partner and tutor.
            CRITICAL RULE: You must respond and explain entirely in $targetLanguageName.
            
            Using the textbook context provided below, explain the concepts clearly to the student.
            
            [Textbook Context]:
            $contextText
            
            [Student's Question]: 
            $message
            
            [Tutor's Response in $targetLanguageName]:
            """.trimIndent()
        } else {
            """
            You are a friendly Tokutei Kaigo study tutor. The student is asking: "$message". 
            CRITICAL RULE: You must respond entirely in $targetLanguageName.
            Answer warmly, but remind them to verify specific rules with their study guide if you aren't certain.
            """.trimIndent()
        }

        val responseBuffer = StringBuilder()
        currentConversation.sendMessageAsync(augmentedPrompt).collect { responseMessage ->
            val textChunk = responseMessage.toString()
            responseBuffer.append(textChunk)
            emit(textChunk)
        }

        chatMessageDao.insertMessage(ChatMessageEntity(text = responseBuffer.toString(), isUser = false))
    }

    suspend fun clearChatHistory() = withContext(Dispatchers.IO) {
        chatMessageDao.clearHistory()
        conversation?.close()
        val conversationConfig = ConversationConfig(samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7))
        conversation = engine?.createConversation(conversationConfig)
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