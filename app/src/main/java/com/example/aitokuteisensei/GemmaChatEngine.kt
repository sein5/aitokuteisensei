package com.example.aitokuteisensei

import android.content.Context
import android.util.Log
import com.example.aitokuteisensei.data.AppDatabase
import com.example.aitokuteisensei.data.ChatMessageEntity
import com.example.aitokuteisensei.data.UserPreferences
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class GemmaChatEngine(private val context: Context) {

    private val logTag = "GemmaChatEngine"
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    private val knowledgeBaseManager = KnowledgeBaseManager(context)
    private val userPreferences = UserPreferences(context)
    private var userLang: String = "en"

    private val chatMessageDao = AppDatabase.getDatabase(context).chatMessageDao()
    val historyFlow = chatMessageDao.getAllMessagesFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    var isInitialized = false
        private set

    private val langNameMap = mapOf(
        "ja" to "Japanese",
        "en" to "English",
        "zh" to "Chinese",
        "id" to "Indonesian",
        "tl" to "Tagalog"
    )

    suspend fun initialize(modelFile: File) = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext
        try {
            userLang = userPreferences.languageFlow.first()

            // Sync local vectorized matching structures
            knowledgeBaseManager.initialize(userLang)

            val engineConfig = EngineConfig(modelFile.absolutePath)
            val initializedEngine = Engine(engineConfig)
            engine = initializedEngine

            val conversationConfig = ConversationConfig(
                samplerConfig = SamplerConfig(
                    topK = 40,
                    topP = 0.95,
                    temperature = 0.7
                )
            )
            conversation = initializedEngine.createConversation(conversationConfig)
            isInitialized = true
            Log.d(logTag, "Gemma Local Hardware inference engine context bound successfully.")
        } catch (e: Exception) {
            Log.e(logTag, "Failed to instantiate local LLM execution framework: ${e.localizedMessage}", e)
            isInitialized = false
        }
    }

    suspend fun generateResponse(message: String) = withContext(Dispatchers.IO) {
        val currentConversation = conversation ?: return@withContext
        val targetLanguageName = langNameMap[userLang] ?: "English"

        // 1. Commit the user prompt into structural storage layout right now
        chatMessageDao.insertMessage(
            ChatMessageEntity(text = message, isUser = true)
        )

        // 2. Flip system processing execution flag to trigger UI shimmer layout
        _isGenerating.value = true

        try {
            val systemContext = knowledgeBaseManager.retrieveContext(message, topK = 3)

            val augmentedPrompt = if (systemContext.isNotEmpty()) {
                """
                You are a highly qualified Tokutei Kaigo (Nursing Care) training instructor assisting a student.
                
                [Verified Training Materials Context]:
                $systemContext
                
                [Student's Question]: 
                $message
                
                [Instructor Response Guide]:
                Provide a warm, supportive, and precise answer. You MUST write your complete output strictly and entirely in $targetLanguageName.
                """.trimIndent()
            } else {
                """
                You are a friendly Tokutei Kaigo study tutor. The student is asking: "$message". 
                CRITICAL RULE: You must respond entirely in $targetLanguageName.
                Answer warmly, but remind them to verify specific rules with their study guide if you aren't certain.
                """.trimIndent()
            }

            val responseBuffer = StringBuilder()

            // Execute on-device computation context
            currentConversation.sendMessageAsync(augmentedPrompt).collect { responseMessage ->
                val textChunk = responseMessage.toString()
                responseBuffer.append(textChunk)
            }

            val completeOutput = responseBuffer.toString().trim()
            if (completeOutput.isNotEmpty()) {
                // 3. Persist the generated response to room database history
                chatMessageDao.insertMessage(
                    ChatMessageEntity(text = completeOutput, isUser = false)
                )
            }
        } catch (e: Exception) {
            Log.e(logTag, "Error processing model inference pipelines: ${e.localizedMessage}", e)
        } finally {
            // 4. Terminate processing UI layout tracking states
            _isGenerating.value = false
        }
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
        Log.d(logTag, "Gemma compute allocation hooks released.")
    }
}