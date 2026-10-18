package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.DefaultExpenseCategories
import com.example.data.model.WalletSources

@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,                    // Contoh: "Listrik PLN", "Wi-Fi Indihome", "Air PDAM", "BPJS", "SPP Sekolah", "Arisan"
    val estimatedAmount: Long,           // Contoh: 250_000L
    val dueDayOfMonth: Int,              // 1 s/d 31
    val category: String = DefaultExpenseCategories.LISTRIK,
    val walletSource: String = WalletSources.BANK,
    val lastPaidMonthYear: String = "",  // Format "MM-yyyy" (cth: "09-2026")
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
