package com.example.ui.viewmodel

import com.example.data.entity.BillEntity
import com.example.data.entity.BudgetCategoryEntity
import com.example.data.entity.DebtLoanEntity
import com.example.data.entity.PriceTrackerEntity
import com.example.data.entity.TransactionEntity
import com.example.data.entity.WishlistEntity
import com.example.data.model.BudgetEnvelopeItem

enum class TimeFilter(val label: String) {
    TODAY("Hari Ini"),
    THIS_MONTH("Bulan Berjalan"),
    ALL("Semua")
}

data class DashboardUiState(
    val totalNetBalance: Long = 0L,
    val todayExpense: Long = 0L,
    val thisMonthExpense: Long = 0L,
    val thisMonthIncome: Long = 0L,
    val lastMonthExpense: Long = 0L,
    val budgetEnvelopes: List<BudgetEnvelopeItem> = emptyList(),
    val allTransactions: List<TransactionEntity> = emptyList(),
    val filteredTransactions: List<TransactionEntity> = emptyList(),
    val wishlistItems: List<WishlistEntity> = emptyList(),
    val budgetCategories: List<BudgetCategoryEntity> = emptyList(),
    val bills: List<BillEntity> = emptyList(),
    val debtLoans: List<DebtLoanEntity> = emptyList(),
    val priceTracks: List<PriceTrackerEntity> = emptyList(),
    val timeFilter: TimeFilter = TimeFilter.THIS_MONTH,
    val selectedCategory: String? = null,
    val selectedWallet: String? = null,
    val isDailyReminderActive: Boolean = true,
    val reminderTimeString: String = "20:30 WIB",
    val isLoading: Boolean = false,
    val userMessage: String? = null
)
