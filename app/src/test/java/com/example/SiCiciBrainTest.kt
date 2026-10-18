package com.example

import com.example.ai.download.ModelDownloadManager
import com.example.ai.engine.GemmaCiciBrain
import com.example.ai.model.SlmFamily
import com.example.data.entity.TransactionEntity
import com.example.data.model.DefaultExpenseCategories
import com.example.data.model.TransactionTypes
import com.example.data.model.WalletSources
import com.example.ui.viewmodel.DashboardUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiCiciBrainTest {

    @Test
    fun testIndonesianSlangExtraction() {
        assertEquals(5000L, GemmaCiciBrain.extractAmountFromIndonesian("beli tahu goceng"))
        assertEquals(10000L, GemmaCiciBrain.extractAmountFromIndonesian("beli bakso ceban"))
        assertEquals(50000L, GemmaCiciBrain.extractAmountFromIndonesian("isi bensin gocap"))
        assertEquals(1000L, GemmaCiciBrain.extractAmountFromIndonesian("parkir seceng"))
        assertEquals(20000L, GemmaCiciBrain.extractAmountFromIndonesian("jajan noban"))
        assertEquals(150000L, GemmaCiciBrain.extractAmountFromIndonesian("skincare pego"))
        assertEquals(100000L, GemmaCiciBrain.extractAmountFromIndonesian("belanja cepek"))
        assertEquals(15000L, GemmaCiciBrain.extractAmountFromIndonesian("beli tempe 15rb"))
        assertEquals(25000L, GemmaCiciBrain.extractAmountFromIndonesian("beli telur 25 rebu"))
        assertEquals(72000L, GemmaCiciBrain.extractAmountFromIndonesian("beras 72 ribu"))
        assertEquals(34000L, GemmaCiciBrain.extractAmountFromIndonesian("minyak 34k"))
        assertEquals(2500000L, GemmaCiciBrain.extractAmountFromIndonesian("gaji 2.5 juta"))
    }

    @Test
    fun testMultiItemPurchaseSentence() {
        val prompt = "tadi beli beras 5 kilo harganya 72 ribu sama minyak goreng 2 liter 34 ribu, sisa uang bulanan tinggal berapa?"
        val state = DashboardUiState(
            totalNetBalance = 1_500_000L,
            todayExpense = 50_000L
        )

        val response = GemmaCiciBrain.processOfflineNlp(prompt, state)
        val actions = response.allActions

        // Should extract 2 expense actions + 1 check balance action
        assertTrue("Should have multiple actions, found ${actions.size}", actions.size >= 2)

        val expenseActions = actions.filter { it.type == "ADD_EXPENSE" }
        assertEquals(2, expenseActions.size)

        // Check beras
        val berasAction = expenseActions.find { it.amount == 72000L }
        assertNotNull(berasAction)
        assertTrue(berasAction!!.item?.contains("Beras", ignoreCase = true) == true)

        // Check minyak
        val minyakAction = expenseActions.find { it.amount == 34000L }
        assertNotNull(minyakAction)
        assertTrue(minyakAction!!.item?.contains("Minyak", ignoreCase = true) == true)

        // Check balance action
        val checkBalanceAction = actions.find { it.type == "CHECK_BALANCE" }
        assertNotNull(checkBalanceAction)
    }

    @Test
    fun testCancellationOfRecentItem() {
        // Given recent transactions: Telur 28rb, Sabun 15rb, Roti 12rb
        val recentTransactions = listOf(
            TransactionEntity(id = 101, amount = 12000L, category = DefaultExpenseCategories.ANAK, note = "Roti Tawar", dateTimestamp = 1000L),
            TransactionEntity(id = 102, amount = 15000L, category = DefaultExpenseCategories.BULANAN, note = "Sabun Cuci", dateTimestamp = 900L),
            TransactionEntity(id = 103, amount = 28000L, category = DefaultExpenseCategories.DAPUR, note = "Telur 1kg", dateTimestamp = 800L)
        )
        val state = DashboardUiState(
            totalNetBalance = 1_000_000L,
            allTransactions = recentTransactions
        )

        val prompt = "Eh rotinya kemahalan, gak jadi beli deh"
        val response = GemmaCiciBrain.processOfflineNlp(prompt, state)
        val actions = response.allActions

        val deleteAction = actions.find { it.type == "DELETE_TRANSACTION" }
        assertNotNull("Should find DELETE_TRANSACTION action", deleteAction)
        assertEquals(101L, deleteAction?.transactionId)
        assertEquals(12000L, deleteAction?.amount)
    }

    @Test
    fun testUpdateRecentItemPrice() {
        // Given recent transaction Telur 28rb
        val recentTransactions = listOf(
            TransactionEntity(id = 103, amount = 28000L, category = DefaultExpenseCategories.DAPUR, note = "Telur 1kg", dateTimestamp = 800L)
        )
        val state = DashboardUiState(
            totalNetBalance = 1_000_000L,
            allTransactions = recentTransactions
        )

        val prompt = "Ralat telur tadi bukan 28 ribu tapi 25 ribu ya"
        val response = GemmaCiciBrain.processOfflineNlp(prompt, state)
        val actions = response.allActions

        val updateAction = actions.find { it.type == "UPDATE_TRANSACTION" }
        assertNotNull("Should find UPDATE_TRANSACTION action", updateAction)
        assertEquals(103L, updateAction?.transactionId)
        assertEquals(25000L, updateAction?.newAmount)
    }

    @Test
    fun testModelDownloadManagerAvailableModels() {
        val models = ModelDownloadManager.AVAILABLE_MODELS
        assertTrue(models.isNotEmpty())

        val qwen = models.find { it.family == SlmFamily.QWEN }
        assertNotNull(qwen)
        assertTrue(qwen!!.isRecommended)
        assertTrue(qwen.approximateSizeMb <= 1200L)

        val gemma = models.find { it.family == SlmFamily.GEMMA }
        assertNotNull(gemma)
    }
}
