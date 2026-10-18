package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.WishlistCategories
import com.example.data.model.WishlistPriorities

@Entity(tableName = "wishlist")
data class WishlistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val targetAmount: Long = 0L,         // Estimasi harga (opsional, 0 jika tidak diisi)
    val actualPrice: Long = 0L,          // Harga sebenarnya saat dibeli / dicatat (opsional)
    val savedAmount: Long = 0L,          // Tabungan terkumpul (jika isSavingGoal = true)
    val isSavingGoal: Boolean = false,   // True jika butuh tabungan bertahap, False jika daftar belanja dapur/rutin langsung
    val categoryType: String = WishlistCategories.KITCHEN,
    val priority: String = WishlistPriorities.MEDIUM,
    val quantityOrUnit: String = "",     // Contoh: "1 kg", "2 ikat", "1 pcs"
    val note: String = "",
    val isFulfilled: Boolean = false,    // Checkbox status: Sudah dibeli / belum
    val createdAt: Long = System.currentTimeMillis()
)
