package com.example.ai.model

import com.google.gson.annotations.SerializedName

/**
 * Message in Si Cici Chat, supporting single or multiple local database CRUD actions.
 */
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionResult: CiciActionResult? = null,
    val actionResults: List<CiciActionResult> = emptyList(),
    val isError: Boolean = false,
    val engineBadge: String? = null
) {
    /**
     * Unified list of all action results executed in Room/SQLite for this message turn.
     */
    val allActionResults: List<CiciActionResult>
        get() = if (actionResults.isNotEmpty()) {
            actionResults
        } else {
            listOfNotNull(actionResult)
        }
}

/**
 * Supported Action Types for local database execution (Function Calling).
 */
enum class CiciActionType {
    NONE,
    ADD_EXPENSE,
    ADD_INCOME,
    ADD_TRANSACTION,
    ADD_WISHLIST,
    UPDATE_TRANSACTION,
    DELETE_TRANSACTION,
    PAY_BILL,
    CHECK_BALANCE,
    FINANCIAL_ADVICE
}

/**
 * Result of executed CRUD database action in local Room database.
 */
data class CiciActionResult(
    val actionType: CiciActionType,
    val title: String,
    val details: String,
    val amount: Long? = null,
    val category: String? = null,
    val wallet: String? = null,
    val targetItem: String? = null,
    val isDeleted: Boolean = false,
    val isUpdated: Boolean = false
)

/**
 * Raw action extracted by SLM / LLM model via JSON Function Calling.
 */
data class CiciRawAction(
    @SerializedName("type")
    val type: String = "NONE", // ADD_EXPENSE, ADD_INCOME, ADD_WISHLIST, UPDATE_TRANSACTION, DELETE_TRANSACTION, PAY_BILL, CHECK_BALANCE

    @SerializedName("item")
    val item: String? = null,

    @SerializedName("amount")
    val amount: Long? = null,

    @SerializedName("category")
    val category: String? = null,

    @SerializedName("wallet")
    val wallet: String? = null,

    @SerializedName("transactionId")
    val transactionId: Long? = null,

    @SerializedName("targetItem")
    val targetItem: String? = null,

    @SerializedName("newAmount")
    val newAmount: Long? = null,

    @SerializedName("newNote")
    val newNote: String? = null,

    @SerializedName("billTitle")
    val billTitle: String? = null
)

/**
 * Format emitted by SLM when performing multiple actions in one turn.
 * e.g.:
 * {
 *   "actions": [
 *     {"type": "ADD_EXPENSE", "item": "Beras 5kg", "amount": 72000, "category": "Kebutuhan Pokok"},
 *     {"type": "ADD_EXPENSE", "item": "Minyak Goreng 2L", "amount": 34000, "category": "Kebutuhan Pokok"},
 *     {"type": "CHECK_BALANCE"}
 *   ],
 *   "reply_to_user": "Siap! Beras Rp72.000 dan minyak goreng Rp34.000 sudah saya catat..."
 * }
 */
data class CiciMultiActionResponse(
    @SerializedName("actions")
    val actions: List<CiciRawAction> = emptyList(),

    @SerializedName("reply_to_user")
    val replyToUser: String? = null
)

/**
 * Legacy single-action model kept for backward compatibility.
 */
data class CiciStructuredAction(
    val action: String = "NONE",
    val type: String = "EXPENSE",
    val amount: Long? = null,
    val category: String? = null,
    val walletSource: String? = null,
    val note: String? = null,
    val itemName: String? = null,
    val targetPrice: Long? = null,
    val billTitle: String? = null,
    val transactionId: Long? = null,
    val targetItem: String? = null
) {
    fun toRawAction(): CiciRawAction {
        val mappedType = when (action) {
            "ADD_TRANSACTION" -> if (type.equals("INCOME", ignoreCase = true)) "ADD_INCOME" else "ADD_EXPENSE"
            else -> action
        }
        return CiciRawAction(
            type = mappedType,
            item = itemName ?: note,
            amount = amount ?: targetPrice,
            category = category,
            wallet = walletSource,
            transactionId = transactionId,
            targetItem = targetItem,
            billTitle = billTitle
        )
    }
}

/**
 * Overall response from Si Cici brain before local database execution.
 */
data class CiciResponse(
    val replyText: String,
    val structuredAction: CiciStructuredAction? = null,
    val actions: List<CiciRawAction> = emptyList(),
    val isFromGemmaAi: Boolean = false,
    val engineUsed: String = "Offline Local NLP"
) {
    /**
     * All actions combining multi-action list and legacy single action.
     */
    val allActions: List<CiciRawAction>
        get() {
            if (actions.isNotEmpty()) return actions
            return structuredAction?.let { listOf(it.toRawAction()) } ?: emptyList()
        }
}

// ==========================================
// ON-DEVICE SLM MODEL REGISTRY & DOWNLOAD
// ==========================================

enum class SlmFamily {
    QWEN,
    GEMMA
}

data class SlmModelInfo(
    val id: String,
    val displayName: String,
    val family: SlmFamily,
    val description: String,
    val approximateSizeMb: Long,
    val downloadUrl: String,
    val fileName: String,
    val isRecommended: Boolean = false,
    val contextLength: Int = 2048,
    val parameterCount: String = "1.5B"
)

enum class DownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    PAUSED,
    DOWNLOADED,
    ERROR
}

data class ModelDownloadProgress(
    val modelId: String,
    val status: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    val progressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val errorMessage: String? = null,
    val localFilePath: String? = null
)
