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

    private val _currentGeneration = MutableStateFlow<String?>(null)
    val currentGeneration = _currentGeneration.asStateFlow()

    var isInitialized = false
        private set

    private val langNameMap = mapOf(
        "ja" to "Japanese", "en" to "English", "zh" to "Chinese",
        "id" to "Indonesian", "tl" to "Tagalog"
    )

    suspend fun initialize(modelFile: File): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true
        try {
            Log.d(logTag, "Starting Engine Initialization with file: ${modelFile.absolutePath}")
            userLang = userPreferences.languageFlow.first()
            knowledgeBaseManager.initialize(userLang)

            // Setup configuration pointing to the downloaded model bundle
            val engineConfig = EngineConfig(modelPath = modelFile.absolutePath)
            val newEngine = Engine(engineConfig)

            // FIX: Explicitly invoke the native engine initialization lifecycle step
            newEngine.initialize()

            engine = newEngine

            // Once fully initialized, it is safe to set up the default conversation session
            resetConversation()

            isInitialized = true
            Log.d(logTag, "✅ Gemma Engine successfully loaded into memory and verified operational!")
            return@withContext true
        } catch (e: Exception) {
            Log.e(logTag, "❌ Critical Failure instantiating LLM: ${e.localizedMessage}", e)
            isInitialized = false
            return@withContext false
        }
    }

    private fun resetConversation() {
        try {
            conversation?.close()
        } catch (e: Exception) {
            Log.w(logTag, "Error closing previous conversation channel state: ${e.message}")
        }

        val targetEngine = engine ?: throw IllegalStateException("Cannot reset conversation: Engine target is unassigned.")
        val conversationConfig = ConversationConfig(
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.5)
        )
        conversation = targetEngine.createConversation(conversationConfig)
        Log.d(logTag, "Conversation session initialized successfully.")
    }

    private fun cleanGemma4E2bOutput(raw: String): String {
        return raw.replace(Regex("<\\|channel>thought\n.*?<channel\\|>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<\\|channel>thought\n.*", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<\\|think\\|>.*?</\\|think\\|>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<\\|think\\|>.*", RegexOption.DOT_MATCHES_ALL), "")
            .trim()
    }

    suspend fun generateResponse(message: String) = withContext(Dispatchers.IO) {
        val targetLanguageName = langNameMap[userLang] ?: "English"

        Log.d(logTag, "User initiated prompt: $message")
        chatMessageDao.insertMessage(ChatMessageEntity(text = message, isUser = true))

        _isGenerating.value = true
        _currentGeneration.value = ""

        try {
            if (!isInitialized || engine == null) {
                Log.e(logTag, "Inference rejected: Engine is uninitialized.")
                chatMessageDao.insertMessage(ChatMessageEntity(text = "[System Error]: AI Engine uninitialized.", isUser = false))
                return@withContext
            }

            resetConversation()
            val currentConversation = conversation
            if (currentConversation == null) {
                Log.e(logTag, "Conversation instance is unavailable.")
                chatMessageDao.insertMessage(ChatMessageEntity(text = "[System Error]: Conversational layout state mapping failure.", isUser = false))
                return@withContext
            }

            Log.d(logTag, "Retrieving RAG context...")
            val systemContext = knowledgeBaseManager.retrieveContext(message, topK = 4)

            val recentHistory = historyFlow.first().takeLast(4)
            val historyText = recentHistory.joinToString("\n") {
                (if (it.isUser) "Student" else "Instructor") + ": " + it.text
            }

            val augmentedPrompt = if (systemContext.isNotEmpty()) {
                "You are a Tokutei Kaigo instructor.\n[Context]: $systemContext\n[History]: $historyText\n[Student]: $message\nReply strictly in $targetLanguageName."
            } else {
                "You are a friendly Tokutei Kaigo tutor.\n[History]: $historyText\n[Student]: $message\nReply strictly in $targetLanguageName."
            }

            Log.d(logTag, "Sending prompt to model pipeline...")
            val rawBuffer = StringBuilder()

            currentConversation.sendMessageAsync(augmentedPrompt).collect { responseMessage ->
                val chunk = responseMessage.toString()
                rawBuffer.append(chunk)

                val cleanedText = cleanGemma4E2bOutput(rawBuffer.toString())
                _currentGeneration.value = cleanedText
            }

            val finalCleanOutput = _currentGeneration.value?.trim() ?: ""
            Log.d(logTag, "Generation Complete. Output Length: ${finalCleanOutput.length}")

            if (finalCleanOutput.isNotEmpty()) {
                chatMessageDao.insertMessage(ChatMessageEntity(text = finalCleanOutput, isUser = false))
            } else {
                chatMessageDao.insertMessage(ChatMessageEntity(text = "[System Note]: The model generated an empty response.", isUser = false))
            }

        } catch (e: Exception) {
            Log.e(logTag, "❌ Inference crashed: ${e.localizedMessage}", e)
            chatMessageDao.insertMessage(ChatMessageEntity(text = "An error occurred during inference: ${e.localizedMessage}", isUser = false))
        } finally {
            _isGenerating.value = false
            _currentGeneration.value = null
        }
    }

    suspend fun clearChatHistory() = withContext(Dispatchers.IO) {
        chatMessageDao.clearHistory()
        runCatching { resetConversation() }
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