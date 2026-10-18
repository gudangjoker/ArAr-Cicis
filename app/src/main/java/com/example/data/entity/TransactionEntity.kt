package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.TransactionTypes
import com.example.data.model.WalletSources

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String = TransactionTypes.EXPENSE,
    val amount: Long,
    val category: String,
    val walletSource: String = WalletSources.CASH,
    val dateTimestamp: Long,
    val note: String = ""
)
