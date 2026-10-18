package com.example.data.model

import com.example.data.entity.BillEntity
import com.example.data.entity.BudgetCategoryEntity
import com.example.data.entity.DebtLoanEntity
import com.example.data.entity.PriceTrackerEntity
import com.example.data.entity.TransactionEntity
import com.example.data.entity.WishlistEntity

data class BackupData(
    val appVersion: String = "2.0",
    val exportedAtJakarta: String,
    val exportedTimestamp: Long,
    val transactions: List<TransactionEntity>,
    val budgetCategories: List<BudgetCategoryEntity>,
    val wishlist: List<WishlistEntity>,
    val bills: List<BillEntity> = emptyList(),
    val debts: List<DebtLoanEntity> = emptyList(),
    val priceTracks: List<PriceTrackerEntity> = emptyList()
)
