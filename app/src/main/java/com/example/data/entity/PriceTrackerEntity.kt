package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "price_tracker")
data class PriceTrackerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val itemName: String,                 // Cth: "Minyak Goreng 2L", "Telur Ayam 1kg", "Beras Ramos 5kg", "Bawang Merah"
    val latestPrice: Long,                // Cth: 34_000L
    val previousPrice: Long = 0L,         // Harga sebelumnya untuk deteksi naik/turun
    val unit: String = "1 kg",            // "1 kg", "2 Liter", "1 tabung", dll
    val storeLocation: String = "",       // Cth: "Pasar Tradisional", "Warung Bu Siti", "Superindo"
    val lastUpdatedDate: String = "",     // Cth: "25 Sep 2026"
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
