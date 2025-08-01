package me.rerere.rikkahub.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.deleteChatFiles
import java.time.Instant
import kotlin.uuid.Uuid

class ConversationRepository(
    private val context: Context,
    private val conversationDAO: ConversationDAO,
) {
    fun getAllConversations(): Flow<List<Conversation>> = conversationDAO
        .getAll()
        .map { flow ->
            flow.map { entity ->
                conversationEntityToConversation(entity)
            }
        }

    suspend fun getRecentConversations(assistantId: Uuid, limit: Int = 10): List<Conversation> {
        return conversationDAO.getRecentConversationsOfAssistant(
            assistantId = assistantId.toString(),
            limit = limit
        ).map { conversationEntityToConversation(it) }
    }

    fun getConversationsOfAssistant(assistantId: Uuid): Flow<List<Conversation>> {
        return conversationDAO
            .getConversationsOfAssistant(assistantId.toString())
            .map { flow ->
                flow.map { entity ->
                    conversationEntityToConversation(entity)
                }
            }
    }

    fun searchConversations(titleKeyword: String): Flow<List<Conversation>> {
        return conversationDAO
            .searchConversations(titleKeyword)
            .map { flow ->
                flow.map { entity ->
                    conversationEntityToConversation(entity)
                }
            }
    }

    suspend fun getConversationById(uuid: Uuid): Conversation? {
        val entity = conversationDAO.getConversationById(uuid.toString())
        return if (entity != null) {
            conversationEntityToConversation(entity)
        } else null
    }

    suspend fun upsertConversation(conversation: Conversation) {
        if (getConversationById(conversation.id) != null) {
            updateConversation(conversation)
        } else {
            insertConversation(conversation)
        }
    }

    suspend fun insertConversation(conversation: Conversation) {
        conversationDAO.insert(
            conversationToConversationEntity(conversation)
        )
    }

    suspend fun updateConversation(conversation: Conversation) {
        conversationDAO.update(
            conversationToConversationEntity(conversation)
        )
    }

    suspend fun deleteConversation(conversation: Conversation) {
        conversationDAO.delete(
            conversationToConversationEntity(conversation)
        )
        context.deleteChatFiles(conversation.files)
    }

    suspend fun deleteConversationOfAssistant(assistantId: Uuid) {
        getConversationsOfAssistant(assistantId).first().forEach { conversation ->
            deleteConversation(conversation)
        }
    }

    fun conversationToConversationEntity(conversation: Conversation): ConversationEntity {
        return ConversationEntity(
            id = conversation.id.toString(),
            title = conversation.title,
            nodes = JsonInstant.encodeToString(conversation.messageNodes),
            createAt = conversation.createAt.toEpochMilli(),
            updateAt = conversation.updateAt.toEpochMilli(),
            assistantId = conversation.assistantId.toString(),
            truncateIndex = conversation.truncateIndex,
            chatSuggestions = JsonInstant.encodeToString(conversation.chatSuggestions)
        )
    }

    fun conversationEntityToConversation(conversationEntity: ConversationEntity): Conversation {
        val messageNodes = JsonInstant
            .decodeFromString<List<MessageNode>>(conversationEntity.nodes)
            .filter { it.messages.isNotEmpty() }
        return Conversation(
            id = Uuid.parse(conversationEntity.id),
            title = conversationEntity.title,
            messageNodes = messageNodes,
            createAt = Instant.ofEpochMilli(conversationEntity.createAt),
            updateAt = Instant.ofEpochMilli(conversationEntity.updateAt),
            assistantId = Uuid.parse(conversationEntity.assistantId),
            truncateIndex = conversationEntity.truncateIndex,
            chatSuggestions = JsonInstant.decodeFromString(conversationEntity.chatSuggestions),
        )
    }

    suspend fun deleteAllConversations() = withContext(Dispatchers.IO) {
        conversationDAO.getAll().first().forEach { conversation ->
            conversationDAO.delete(conversation)
            context.deleteChatFiles(conversationEntityToConversation(conversation).files)
        }
    }
}
