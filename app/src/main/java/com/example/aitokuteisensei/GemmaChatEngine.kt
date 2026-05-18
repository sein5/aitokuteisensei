package com.example.aitokuteisensei

import android.content.Context
import com.example.aitokuteisensei.data.AppDatabase
import com.example.aitokuteisensei.data.ChatMessageEntity
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

class GemmaChatEngine(private val context: Context) {

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private val knowledgeBaseManager = KnowledgeBaseManager(context)

    // Initialize the Room Dao for chat history persistence
    private val chatMessageDao = AppDatabase.getDatabase(context).chatMessageDao()

    // Expose the database state directly as a streamable flow for the UI
    val historyFlow = chatMessageDao.getAllMessagesFlow()

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

        // Setup the local engine
        val config = EngineConfig(modelPath = modelFile.absolutePath)
        val loadedEngine = Engine(config)
        loadedEngine.initialize()
        engine = loadedEngine

        // Configure sampling behavior to loosen up text generation
        val samplerConfig = SamplerConfig(
            topK = 40,
            topP = 0.95,
            temperature = 0.7 // Higher value = more conversational/loose behavior
        )

        // Wrap it inside the ConversationConfig
        val conversationConfig = ConversationConfig(
            samplerConfig = samplerConfig
        )

        // Instantiate the conversation with customized parameters
        conversation = loadedEngine.createConversation(conversationConfig)
        isInitialized = true
    }

    fun sendMessageStream(message: String): Flow<String> = flow {
        val currentConversation = conversation
            ?: throw IllegalStateException("Engine or Conversation not initialized")

        // 1. Instantly save the user query to the database
        chatMessageDao.insertMessage(ChatMessageEntity(text = message, isUser = true))

        // 2. Retrieve background context using local query embedding (pulling 5 chunks for broader context)
        val contextText = knowledgeBaseManager.retrieveContext(message, topK = 5)

        // 3. Formulate the augmented prompt payload with a Tutor Persona
        val augmentedPrompt = if (contextText.isNotEmpty()) {
            """
            You are a friendly, warm, and encouraging Tokutei Kaigo (Nursing Care) study partner and tutor. 
            Your goal is to help the student learn and pass their exam. 
            
            Using the textbook context provided below, explain the concepts clearly to the student. 
            You can use simple wording, offer conversational encouragement, or rephrase the concept to be easier to digest, but make sure your facts match the text.
            
            [Textbook Context]:
            $contextText
            
            [Student's Question]: 
            $message
            
            [Tutor's Response]:
            """.trimIndent()
        } else {
            // Fallback if no specific textbook context matches
            """
            You are a friendly Tokutei Kaigo study tutor. The student is asking: "$message". 
            Answer warmly, but remind them to verify specific rules with their study guide if you aren't certain.
            """.trimIndent()
        }

        val responseBuffer = StringBuilder()

        // 4. Stream the response back to the UI
        currentConversation.sendMessageAsync(augmentedPrompt).collect { responseMessage ->
            val textChunk = responseMessage.toString()
            responseBuffer.append(textChunk)
            emit(textChunk)
        }

        // 5. Once the stream successfully finishes, save the complete response to the DB
        chatMessageDao.insertMessage(
            ChatMessageEntity(text = responseBuffer.toString(), isUser = false)
        )
    }

    suspend fun clearChatHistory() = withContext(Dispatchers.IO) {
        chatMessageDao.clearHistory()
        // Optional: Re-create the conversation to clear the model's short-term context memory
        conversation?.close()
        val conversationConfig = ConversationConfig(
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7)
        )
        conversation = engine?.createConversation(conversationConfig)
    }

    fun close() {
        runCatching { knowledgeBaseManager.close() } // Ensure your KnowledgeBaseManager has a close() method or remove this line
        runCatching { conversation?.close() }
        runCatching { engine?.close() }
        conversation = null
        engine = null
        isInitialized = false
    }
}