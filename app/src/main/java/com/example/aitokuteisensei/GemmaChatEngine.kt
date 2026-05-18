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
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.SamplerConfig

class GemmaChatEngine(private val context: Context) {

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private val knowledgeBaseManager = KnowledgeBaseManager(context)
    var isInitialized = false
        private set

    suspend fun initialize(modelPath: String) = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext

        knowledgeBaseManager.initialize()

        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            throw IllegalArgumentException("Model file not found at: $modelPath")
        }

        val config = EngineConfig(modelPath = modelFile.absolutePath)
        val loadedEngine = Engine(config)
        loadedEngine.initialize()

        engine = loadedEngine

        // 1. Configure sampling behavior to loosen up text generation
        val samplerConfig = SamplerConfig(
            topK = 40,
            topP = 0.95,
            temperature = 0.5 // Higher value = more conversational/loose behavior
        )

        // 2. Wrap it inside the ConversationConfig
        val conversationConfig = ConversationConfig(
            samplerConfig = samplerConfig
        )

        // 3. Instantiate the conversation with your customized parameters
        conversation = loadedEngine.createConversation(conversationConfig)
        isInitialized = true
    }

    fun sendMessageStream(message: String): Flow<String> = flow {
        val currentConversation = conversation
            ?: throw IllegalStateException("Engine or Conversation not initialized")

        val contextText = knowledgeBaseManager.retrieveContext(message, 5)

        // Revamped prompt for an educational, loose, yet grounded tone
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