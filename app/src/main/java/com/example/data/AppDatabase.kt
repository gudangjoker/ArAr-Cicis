package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.FinanceDao
import com.example.data.entity.BillEntity
import com.example.data.entity.BudgetCategoryEntity
import com.example.data.entity.DebtLoanEntity
import com.example.data.entity.PriceTrackerEntity
import com.example.data.entity.TransactionEntity
import com.example.data.entity.WishlistEntity
import com.example.data.model.DebtTypes
import com.example.data.model.TransactionTypes
import com.example.data.model.WalletSources
import com.example.data.model.WishlistCategories
import com.example.data.model.WishlistPriorities
import com.example.util.DateTimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        TransactionEntity::class,
        BudgetCategoryEntity::class,
        WishlistEntity::class,
        BillEntity::class,
        DebtLoanEntity::class,
        PriceTrackerEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun financeDao(): FinanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope? = null): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dompet_rumah_tangga.db"
                )
                    .addCallback(DatabaseCallback(scope ?: CoroutineScope(Dispatchers.IO)))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateInitialData(database.financeDao())
                }
            }
        }

        suspend fun populateInitialData(dao: FinanceDao) {
            // Bersih tanpa data permanen dari sistem, user bebas membuat dan mengelola pos anggaran sendiri
        }
    }
}
