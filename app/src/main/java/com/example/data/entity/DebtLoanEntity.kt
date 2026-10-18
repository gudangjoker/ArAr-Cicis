package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.DebtTypes

@Entity(tableName = "debt_loans")
data class DebtLoanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val personName: String,               // Nama orang/kerabat
    val type: String = DebtTypes.PAYABLE, // "HUTANG_SAYA" atau "PIUTANG_SAYA"
    val totalAmount: Long,                // Total nominal hutang / piutang
    val paidAmount: Long = 0L,            // Yang sudah dicicil/dibayar
    val dueDateString: String = "",       // Cth: "30 Okt 2026"
    val note: String = "",
    val isSettled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
