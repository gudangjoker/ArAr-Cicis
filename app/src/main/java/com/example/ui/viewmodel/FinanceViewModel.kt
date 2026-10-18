package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.entity.BillEntity
import com.example.data.entity.BudgetCategoryEntity
import com.example.data.entity.DebtLoanEntity
import com.example.data.entity.PriceTrackerEntity
import com.example.data.entity.TransactionEntity
import com.example.data.entity.WishlistEntity
import com.example.data.model.DebtTypes
import com.example.data.model.DefaultExpenseCategories
import com.example.data.model.DefaultIncomeCategories
import com.example.data.model.TransactionTypes
import com.example.data.model.WalletSources
import com.example.data.model.WishlistCategories
import com.example.data.model.WishlistPriorities
import com.example.data.repository.FinanceRepository
import com.example.util.CurrencyFormatter
import com.example.util.DateTimeUtils
import com.example.util.MathExpressionEvaluator
import com.example.util.ReminderScheduler
import com.example.BuildConfig
import com.example.ai.download.ModelDownloadManager
import com.example.ai.engine.GemmaCiciBrain
import com.example.ai.model.ChatMessage
import com.example.ai.model.CiciActionResult
import com.example.ai.model.CiciActionType
import com.example.ai.model.CiciRawAction
import com.example.ai.model.CiciStructuredAction
import com.example.ai.model.SlmModelInfo
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FinanceViewModel(
    application: Application,
    private val repository: FinanceRepository
) : AndroidViewModel(application) {

    private val _timeFilter = MutableStateFlow(TimeFilter.THIS_MONTH)
    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _selectedWallet = MutableStateFlow<String?>(null)
    private val _userMessage = MutableStateFlow<String?>(null)
    private val _reminderEnabled = MutableStateFlow(ReminderScheduler.isReminderEnabled(application))

    // Si Cici (Gemma AI) Chat State
    private val initialWelcomeMessage = ChatMessage(
        isUser = false,
        text = "Halo Bunda tersayang! ❤️ Aku **Si Cici**, asisten keuangan dan belanja keluarga ArAr Cicis yang ditenagai oleh kecerdasan Gemma AI.\n\nCici siap bantu Bunda:\n• Mencatat pengeluaran (*cth: 'Beli bayam 10 ribu uang tunai'*)\n• Mengecek sisa saldo kas (*'Berapa sisa uang kita?'*)\n• Menambahkan daftar belanjaan (*'Catat minyak goreng di belanjaan'*)\n• Membayar tagihan rutin (*'Bayar tagihan listrik'*)\n• Konsultasi tips belanja hemat dapur mingguan!\n\nAda yang bisa Cici bantu sekarang, Bun?"
    )

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(listOf(initialWelcomeMessage))
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isCiciThinking = MutableStateFlow(false)
    val isCiciThinking: StateFlow<Boolean> = _isCiciThinking.asStateFlow()

    private val _isTtsVoiceEnabled = MutableStateFlow(true)
    val isTtsVoiceEnabled: StateFlow<Boolean> = _isTtsVoiceEnabled.asStateFlow()

    // Model Download Manager for On-Device SLM (Qwen / Gemma)
    val modelDownloadManager = ModelDownloadManager(application)
    val modelDownloadProgress = modelDownloadManager.downloadProgress
    val selectedModel = modelDownloadManager.selectedModel
    val availableModels = ModelDownloadManager.AVAILABLE_MODELS

    // Helper flows from repository
    private val allTransactions = repository.allTransactions
    private val budgetEnvelopes = repository.getBudgetEnvelopesThisMonth()
    private val wishlistFlow = repository.allWishlist
    private val budgetCategoriesFlow = repository.allBudgetCategories
    private val billsFlow = repository.allBills
    private val debtsFlow = repository.allDebtsAndLoans
    private val pricesFlow = repository.allPriceTracks

    // Date boundaries
    private val startOfDay = DateTimeUtils.getStartOfDay()
    private val endOfDay = DateTimeUtils.getEndOfDay()
    private val startOfMonth = DateTimeUtils.getStartOfMonth()
    private val endOfMonth = DateTimeUtils.getEndOfMonth()
    private val startOfLastMonth = DateTimeUtils.getStartOfLastMonth()
    private val endOfLastMonth = DateTimeUtils.getEndOfLastMonth()

    private val todayExpenseFlow = repository.getTotalExpenseBetween(startOfDay, endOfDay)
    private val thisMonthExpenseFlow = repository.getTotalExpenseBetween(startOfMonth, endOfMonth)
    private val thisMonthIncomeFlow = repository.getTotalIncomeBetween(startOfMonth, endOfMonth)
    private val lastMonthExpenseFlow = repository.getTotalExpenseBetween(startOfLastMonth, endOfLastMonth)
    private val totalBalanceFlow = repository.totalNetBalance

    val uiState: StateFlow<DashboardUiState> = combine(
        listOf(
            allTransactions,
            budgetEnvelopes,
            wishlistFlow,
            budgetCategoriesFlow,
            billsFlow,
            debtsFlow,
            pricesFlow,
            todayExpenseFlow,
            thisMonthExpenseFlow,
            thisMonthIncomeFlow,
            lastMonthExpenseFlow,
            totalBalanceFlow,
            _timeFilter,
            _selectedCategory,
            _selectedWallet,
            _reminderEnabled,
            _userMessage
        )
    ) { params ->
        @Suppress("UNCHECKED_CAST")
        val transactions = params[0] as List<TransactionEntity>
        @Suppress("UNCHECKED_CAST")
        val envelopes = params[1] as List<com.example.data.model.BudgetEnvelopeItem>
        @Suppress("UNCHECKED_CAST")
        val wishlists = params[2] as List<WishlistEntity>
        @Suppress("UNCHECKED_CAST")
        val categories = params[3] as List<BudgetCategoryEntity>
        @Suppress("UNCHECKED_CAST")
        val bills = params[4] as List<BillEntity>
        @Suppress("UNCHECKED_CAST")
        val debts = params[5] as List<DebtLoanEntity>
        @Suppress("UNCHECKED_CAST")
        val prices = params[6] as List<PriceTrackerEntity>
        val todayExp = params[7] as Long
        val monthExp = params[8] as Long
        val monthInc = params[9] as Long
        val lastMonthExp = params[10] as Long
        val netBal = params[11] as Long
        val timeFilt = params[12] as TimeFilter
        val catFilt = params[13] as String?
        val wallFilt = params[14] as String?
        val reminderOn = params[15] as Boolean
        val message = params[16] as String?

        // Compute filtered transactions
        val filtered = transactions.filter { tx ->
            // Time filter
            val timeMatch = when (timeFilt) {
                TimeFilter.TODAY -> tx.dateTimestamp in startOfDay..endOfDay
                TimeFilter.THIS_MONTH -> tx.dateTimestamp in startOfMonth..endOfMonth
                TimeFilter.ALL -> true
            }
            // Category filter
            val categoryMatch = catFilt == null || tx.category == catFilt
            // Wallet filter
            val walletMatch = wallFilt == null || tx.walletSource == wallFilt

            timeMatch && categoryMatch && walletMatch
        }

        val (remHour, remMin) = ReminderScheduler.getReminderTime(getApplication())
        val reminderTimeStr = String.format("%02d:%02d WIB", remHour, remMin)

        DashboardUiState(
            totalNetBalance = netBal,
            todayExpense = todayExp,
            thisMonthExpense = monthExp,
            thisMonthIncome = monthInc,
            lastMonthExpense = lastMonthExp,
            budgetEnvelopes = envelopes,
            allTransactions = transactions,
            filteredTransactions = filtered,
            wishlistItems = wishlists,
            budgetCategories = categories,
            bills = bills,
            debtLoans = debts,
            priceTracks = prices,
            timeFilter = timeFilt,
            selectedCategory = catFilt,
            selectedWallet = wallFilt,
            isDailyReminderActive = reminderOn,
            reminderTimeString = reminderTimeStr,
            isLoading = false,
            userMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(isLoading = true)
    )

    // === MINI CALCULATOR EXPRESSION EVALUATOR ===

    fun parseMathExpression(input: String): Long {
        return MathExpressionEvaluator.evaluateMathExpression(input)
    }

    // === FILTER CONTROLS ===

    fun setTimeFilter(filter: TimeFilter) {
        _timeFilter.value = filter
    }

    fun setSelectedCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun setSelectedWallet(wallet: String?) {
        _selectedWallet.value = wallet
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    // === TRANSACTION ACTIONS ===

    fun addTransaction(
        type: String,
        amountInput: String,
        category: String,
        walletSource: String,
        note: String,
        dateTimestamp: Long = DateTimeUtils.getCurrentJakartaTimeMillis()
    ) {
        viewModelScope.launch {
            val evaluatedAmount = parseMathExpression(amountInput)
            if (evaluatedAmount <= 0) {
                _userMessage.value = "Nominal transaksi harus lebih dari Rp 0"
                return@launch
            }

            val newTx = TransactionEntity(
                type = type,
                amount = evaluatedAmount,
                category = category,
                walletSource = walletSource,
                dateTimestamp = dateTimestamp,
                note = note.trim()
            )
            repository.insertTransaction(newTx)
            _userMessage.value = "Transaksi berhasil dicatat!"
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
            _userMessage.value = "Transaksi berhasil dihapus"
        }
    }

    // === BUDGET CATEGORY ACTIONS ===

    fun saveBudgetCategory(
        originalCategoryName: String?,
        newCategoryName: String,
        limitInput: String,
        iconEmoji: String = "💰"
    ) {
        viewModelScope.launch {
            val cleanName = newCategoryName.trim()
            if (cleanName.isBlank()) {
                _userMessage.value = "Nama pos anggaran tidak boleh kosong"
                return@launch
            }

            val limit = parseMathExpression(limitInput).coerceAtLeast(0L)

            val existing = if (!originalCategoryName.isNullOrBlank()) {
                uiState.value.budgetCategories.find { it.categoryName.equals(originalCategoryName, ignoreCase = true) }
            } else {
                uiState.value.budgetCategories.find { it.categoryName.equals(cleanName, ignoreCase = true) }
            }

            if (existing != null) {
                val oldName = existing.categoryName
                repository.updateBudgetCategory(
                    existing.copy(categoryName = cleanName, monthlyLimit = limit, iconEmoji = iconEmoji)
                )
                if (!oldName.equals(cleanName, ignoreCase = true)) {
                    repository.updateTransactionCategory(oldName, cleanName)
                }
                _userMessage.value = "Pos anggaran '$cleanName' berhasil diperbarui!"
            } else {
                repository.insertBudgetCategory(
                    BudgetCategoryEntity(
                        categoryName = cleanName,
                        monthlyLimit = limit,
                        iconEmoji = iconEmoji
                    )
                )
                _userMessage.value = "Pos anggaran baru '$cleanName' berhasil dibuat!"
            }
        }
    }

    fun setBudgetLimit(categoryName: String, newLimitInput: String, iconEmoji: String = "💰") {
        saveBudgetCategory(categoryName, categoryName, newLimitInput, iconEmoji)
    }

    fun deleteBudgetCategory(category: BudgetCategoryEntity) {
        viewModelScope.launch {
            repository.deleteBudgetCategory(category)
            _userMessage.value = "Pos anggaran '${category.categoryName}' berhasil dihapus"
        }
    }

    fun deleteBudgetCategoryByName(categoryName: String) {
        viewModelScope.launch {
            val target = uiState.value.budgetCategories.find { it.categoryName.equals(categoryName, ignoreCase = true) }
            if (target != null) {
                repository.deleteBudgetCategory(target)
                _userMessage.value = "Pos anggaran '$categoryName' berhasil dihapus"
            }
        }
    }

    // === WISHLIST & DAFTAR BELANJA ACTIONS ===

    fun addWishlistItem(
        name: String,
        targetAmountInput: String = "",
        actualPriceInput: String = "",
        initialSavedInput: String = "0",
        isSavingGoal: Boolean = false,
        categoryType: String = WishlistCategories.KITCHEN,
        priority: String = WishlistPriorities.MEDIUM,
        quantityOrUnit: String = "",
        note: String = ""
    ) {
        viewModelScope.launch {
            if (name.isBlank()) {
                _userMessage.value = "Nama barang belanja tidak boleh kosong"
                return@launch
            }
            // Estimasi harga is optional
            val target = if (targetAmountInput.isNotBlank()) parseMathExpression(targetAmountInput) else 0L
            val actual = if (actualPriceInput.isNotBlank()) parseMathExpression(actualPriceInput) else 0L
            val saved = if (isSavingGoal && initialSavedInput.isNotBlank()) parseMathExpression(initialSavedInput).coerceAtLeast(0L) else 0L

            val item = WishlistEntity(
                name = name.trim(),
                targetAmount = target,
                actualPrice = actual,
                savedAmount = saved,
                isSavingGoal = isSavingGoal,
                categoryType = categoryType,
                priority = priority,
                quantityOrUnit = quantityOrUnit.trim(),
                note = note.trim(),
                isFulfilled = if (isSavingGoal && target > 0) saved >= target else false
            )
            repository.insertWishlist(item)
            _userMessage.value = if (isSavingGoal) "Target impian ditambahkan!" else "Barang ditambahkan ke daftar belanja!"
        }
    }

    fun allocateSavings(wishlistId: Long, addAmountInput: String) {
        viewModelScope.launch {
            val amount = parseMathExpression(addAmountInput)
            if (amount <= 0) {
                _userMessage.value = "Nominal alokasi harus lebih dari Rp 0"
                return@launch
            }

            val item = uiState.value.wishlistItems.find { it.id == wishlistId } ?: return@launch
            val newSaved = item.savedAmount + amount
            val isFulfilled = if (item.targetAmount > 0) newSaved >= item.targetAmount else false

            repository.updateWishlist(
                item.copy(
                    savedAmount = newSaved,
                    isFulfilled = isFulfilled
                )
            )

            if (isFulfilled && !item.isFulfilled) {
                _userMessage.value = "🎉 Alhamdulillah! Target '${item.name}' tercapai!"
            } else {
                _userMessage.value = "Tabungan untuk '${item.name}' bertambah Rp $amount!"
            }
        }
    }

    fun toggleWishlistFulfilled(
        item: WishlistEntity,
        actualPriceInput: String? = null,
        recordAsTransaction: Boolean = false,
        walletSource: String = WalletSources.CASH
    ) {
        viewModelScope.launch {
            val newFulfilled = !item.isFulfilled
            val actual = if (actualPriceInput != null && actualPriceInput.isNotBlank()) {
                parseMathExpression(actualPriceInput)
            } else item.actualPrice

            val updated = item.copy(
                isFulfilled = newFulfilled,
                actualPrice = actual
            )
            repository.updateWishlist(updated)

            if (newFulfilled && recordAsTransaction && actual > 0) {
                val txCategory = when (item.categoryType) {
                    WishlistCategories.KITCHEN -> DefaultExpenseCategories.DAPUR
                    WishlistCategories.HOUSEHOLD -> DefaultExpenseCategories.RUMAH
                    else -> DefaultExpenseCategories.HIBURAN
                }
                repository.insertTransaction(
                    TransactionEntity(
                        type = TransactionTypes.EXPENSE,
                        amount = actual,
                        category = txCategory,
                        walletSource = walletSource,
                        dateTimestamp = DateTimeUtils.getCurrentJakartaTimeMillis(),
                        note = "Beli: ${item.name}${if (item.quantityOrUnit.isNotBlank()) " (${item.quantityOrUnit})" else ""}".trim()
                    )
                )
                _userMessage.value = "✓ Ditandai dibeli & otomatis dicatat ke belanja dapur!"
            } else {
                _userMessage.value = if (newFulfilled) "✓ '${item.name}' ditandai sudah dibeli" else "'${item.name}' belum dibeli"
            }
        }
    }

    fun updateActualPrice(item: WishlistEntity, actualPriceInput: String) {
        viewModelScope.launch {
            val price = parseMathExpression(actualPriceInput)
            repository.updateWishlist(item.copy(actualPrice = price))
            _userMessage.value = "Harga sebenarnya diperbarui: Rp $price"
        }
    }

    fun deleteWishlist(item: WishlistEntity) {
        viewModelScope.launch {
            repository.deleteWishlist(item)
            _userMessage.value = "Item dihapus dari daftar"
        }
    }

    // === BILLS (TAGIHAN RUTIN) ACTIONS ===

    fun addBill(
        title: String,
        amountInput: String,
        dueDay: Int,
        category: String = DefaultExpenseCategories.LISTRIK,
        walletSource: String = WalletSources.BANK,
        note: String = ""
    ) {
        viewModelScope.launch {
            if (title.isBlank()) {
                _userMessage.value = "Nama tagihan tidak boleh kosong"
                return@launch
            }
            val amount = parseMathExpression(amountInput)
            val bill = BillEntity(
                title = title.trim(),
                estimatedAmount = amount,
                dueDayOfMonth = dueDay.coerceIn(1, 31),
                category = category,
                walletSource = walletSource,
                lastPaidMonthYear = "",
                note = note.trim()
            )
            repository.insertBill(bill)
            _userMessage.value = "Tagihan '${bill.title}' berhasil ditambahkan!"
        }
    }

    fun payBillNow(
        bill: BillEntity,
        paidAmountInput: String? = null,
        walletSource: String = bill.walletSource
    ) {
        viewModelScope.launch {
            val amount = if (!paidAmountInput.isNullOrBlank()) {
                parseMathExpression(paidAmountInput)
            } else bill.estimatedAmount

            val currentMonthKey = DateTimeUtils.getCurrentMonthYearKey()
            val updated = bill.copy(
                lastPaidMonthYear = currentMonthKey,
                walletSource = walletSource
            )
            repository.updateBill(updated)

            // Insert transaction
            repository.insertTransaction(
                TransactionEntity(
                    type = TransactionTypes.EXPENSE,
                    amount = amount,
                    category = bill.category,
                    walletSource = walletSource,
                    dateTimestamp = DateTimeUtils.getCurrentJakartaTimeMillis(),
                    note = "Bayar Tagihan: ${bill.title}".trim()
                )
            )
            _userMessage.value = "✓ Tagihan '${bill.title}' lunas bulan ini & tercatat di pengeluaran!"
        }
    }

    fun toggleBillStatusManual(bill: BillEntity) {
        viewModelScope.launch {
            val currentMonthKey = DateTimeUtils.getCurrentMonthYearKey()
            val isPaidThisMonth = bill.lastPaidMonthYear == currentMonthKey
            val updated = bill.copy(
                lastPaidMonthYear = if (isPaidThisMonth) "" else currentMonthKey
            )
            repository.updateBill(updated)
            _userMessage.value = if (!isPaidThisMonth) "✓ Tagihan '${bill.title}' ditandai Lunas" else "Status tagihan '${bill.title}' diubah ke Belum Dibayar"
        }
    }

    fun deleteBill(bill: BillEntity) {
        viewModelScope.launch {
            repository.deleteBill(bill)
            _userMessage.value = "Tagihan '${bill.title}' dihapus"
        }
    }

    // === DEBT & LOAN (HUTANG PIUTANG) ACTIONS ===

    fun addDebtLoan(
        personName: String,
        type: String,
        amountInput: String,
        dueDateString: String = "",
        note: String = ""
    ) {
        viewModelScope.launch {
            if (personName.isBlank()) {
                _userMessage.value = "Nama orang/kerabat tidak boleh kosong"
                return@launch
            }
            val amount = parseMathExpression(amountInput)
            if (amount <= 0) {
                _userMessage.value = "Nominal harus lebih dari Rp 0"
                return@launch
            }
            val item = DebtLoanEntity(
                personName = personName.trim(),
                type = type,
                totalAmount = amount,
                paidAmount = 0L,
                dueDateString = dueDateString.trim(),
                note = note.trim(),
                isSettled = false
            )
            repository.insertDebtLoan(item)
            _userMessage.value = if (type == DebtTypes.PAYABLE) "Catatan hutang tersimpan" else "Catatan piutang tersimpan"
        }
    }

    fun recordDebtPayment(
        item: DebtLoanEntity,
        paymentAmountInput: String,
        recordAsTransaction: Boolean = false,
        walletSource: String = WalletSources.CASH
    ) {
        viewModelScope.launch {
            val payment = parseMathExpression(paymentAmountInput)
            if (payment <= 0) return@launch
            val newPaid = (item.paidAmount + payment).coerceAtMost(item.totalAmount)
            val isSettled = newPaid >= item.totalAmount
            val updated = item.copy(paidAmount = newPaid, isSettled = isSettled)
            repository.updateDebtLoan(updated)

            if (recordAsTransaction) {
                val isPayable = item.type == DebtTypes.PAYABLE
                repository.insertTransaction(
                    TransactionEntity(
                        type = if (isPayable) TransactionTypes.EXPENSE else TransactionTypes.INCOME,
                        amount = payment,
                        category = if (isPayable) DefaultExpenseCategories.LAINNYA else DefaultIncomeCategories.LAINNYA,
                        walletSource = walletSource,
                        dateTimestamp = DateTimeUtils.getCurrentJakartaTimeMillis(),
                        note = "${if (isPayable) "Cicil/Bayar Hutang ke" else "Terima Piutang dari"}: ${item.personName}"
                    )
                )
            }
            _userMessage.value = if (isSettled) "✓ Lunas! '${item.personName}' selesai" else "Cicilan ${CurrencyFormatter.formatRupiah(payment)} tercatat"
        }
    }

    fun toggleDebtSettled(item: DebtLoanEntity) {
        viewModelScope.launch {
            val newSettled = !item.isSettled
            val updated = item.copy(
                isSettled = newSettled,
                paidAmount = if (newSettled) item.totalAmount else 0L
            )
            repository.updateDebtLoan(updated)
            _userMessage.value = if (newSettled) "✓ Ditandai Lunas" else "Status diubah ke Belum Lunas"
        }
    }

    fun deleteDebtLoan(item: DebtLoanEntity) {
        viewModelScope.launch {
            repository.deleteDebtLoan(item)
            _userMessage.value = "Catatan hutang/piutang dihapus"
        }
    }

    // === PRICE TRACKER (PANTAU HARGA PASAR) ACTIONS ===

    fun addPriceTrack(
        itemName: String,
        priceInput: String,
        unit: String = "1 kg",
        storeLocation: String = "",
        note: String = ""
    ) {
        viewModelScope.launch {
            if (itemName.isBlank()) {
                _userMessage.value = "Nama bahan/barang tidak boleh kosong"
                return@launch
            }
            val price = parseMathExpression(priceInput)
            val nowStr = DateTimeUtils.formatShortDate(System.currentTimeMillis())
            val item = PriceTrackerEntity(
                itemName = itemName.trim(),
                latestPrice = price,
                previousPrice = price,
                unit = unit.trim(),
                storeLocation = storeLocation.trim(),
                lastUpdatedDate = nowStr,
                note = note.trim()
            )
            repository.insertPriceTrack(item)
            _userMessage.value = "Harga pasar '${item.itemName}' tercatat!"
        }
    }

    fun updatePriceTrack(
        item: PriceTrackerEntity,
        newPriceInput: String,
        storeLocation: String = item.storeLocation,
        note: String = item.note
    ) {
        viewModelScope.launch {
            val newPrice = parseMathExpression(newPriceInput)
            val nowStr = DateTimeUtils.formatShortDate(System.currentTimeMillis())
            val updated = item.copy(
                previousPrice = item.latestPrice,
                latestPrice = newPrice,
                storeLocation = storeLocation.trim(),
                lastUpdatedDate = nowStr,
                note = note.trim()
            )
            repository.updatePriceTrack(updated)
            _userMessage.value = "Harga '${item.itemName}' diperbarui menjadi ${CurrencyFormatter.formatRupiah(newPrice)}"
        }
    }

    fun deletePriceTrack(item: PriceTrackerEntity) {
        viewModelScope.launch {
            repository.deletePriceTrack(item)
            _userMessage.value = "Pantauan harga dihapus"
        }
    }

    // === REKAP SHARE WHATSAPP GENERATOR ===

    fun generateWhatsAppRekapText(): String {
        val state = uiState.value
        val dateStr = DateTimeUtils.formatDateWithDay(System.currentTimeMillis())
        val currentMonth = DateTimeUtils.formatCurrentMonth()
        val currentMonthKey = DateTimeUtils.getCurrentMonthYearKey()

        val unpaidBills = state.bills.filter { it.lastPaidMonthYear != currentMonthKey }
        val kitchenEnvelope = state.budgetEnvelopes.find { it.categoryName == DefaultExpenseCategories.DAPUR }

        val sb = StringBuilder()
        sb.append("📋 *Laporan Kas ArAr Cicis*\n")
        sb.append("🗓️ $dateStr\n\n")

        sb.append("💰 *Ringkasan Saldo Saat Ini:*\n")
        sb.append("• Total Sisa Kas: ${CurrencyFormatter.formatRupiah(state.totalNetBalance)}\n")
        sb.append("• Pengeluaran Hari Ini: ${CurrencyFormatter.formatRupiah(state.todayExpense)}\n")
        sb.append("• Pengeluaran $currentMonth: ${CurrencyFormatter.formatRupiah(state.thisMonthExpense)}\n")
        sb.append("• Pemasukan $currentMonth: ${CurrencyFormatter.formatRupiah(state.thisMonthIncome)}\n\n")

        if (kitchenEnvelope != null) {
            val statusEmoji = when {
                kitchenEnvelope.isSafe -> "🟢 Aman"
                kitchenEnvelope.isWarning -> "🟡 Waspada"
                else -> "🔴 Over Budget"
            }
            sb.append("🥬 *Pos Belanja Dapur ($currentMonth):*\n")
            sb.append("• Terpakai: ${CurrencyFormatter.formatRupiah(kitchenEnvelope.spentAmount)} / ${CurrencyFormatter.formatRupiah(kitchenEnvelope.monthlyLimit)} ($statusEmoji)\n")
            sb.append("• Sisa Kuota Dapur: ${CurrencyFormatter.formatRupiah(kitchenEnvelope.remainingAmount)}\n\n")
        }

        if (unpaidBills.isNotEmpty()) {
            sb.append("⚡ *Tagihan Rutin Belum Dibayar Bulan Ini:*\n")
            unpaidBills.take(4).forEach { b ->
                sb.append("• ${b.title} (Tgl ${b.dueDayOfMonth}): ~${CurrencyFormatter.formatRupiah(b.estimatedAmount)}\n")
            }
            if (unpaidBills.size > 4) {
                sb.append("• dan ${unpaidBills.size - 4} tagihan lainnya\n")
            }
            sb.append("\n")
        }

        sb.append("Semoga berkah & terkendali selalu rezeki keluarga kita ❤️🤲")
        return sb.toString()
    }

    // === BACKUP & RESTORE ACTIONS ===

    suspend fun getJsonBackupContent(): String {
        return repository.exportDataToJson()
    }

    fun restoreBackup(jsonContent: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.restoreDataFromJson(jsonContent)
            result.fold(
                onSuccess = { count ->
                    _userMessage.value = "Data berhasil dipulihkan! ($count catatan)"
                    onComplete(true, "Berhasil memulihkan $count catatan.")
                },
                onFailure = { err ->
                    val errMsg = err.message ?: "Format file tidak valid"
                    _userMessage.value = "Gagal memulihkan data: $errMsg"
                    onComplete(false, errMsg)
                }
            )
        }
    }

    // === DAILY REMINDER ACTIONS ===

    fun toggleDailyReminder(enabled: Boolean) {
        ReminderScheduler.setReminderEnabled(getApplication(), enabled)
        _reminderEnabled.value = enabled
        _userMessage.value = if (enabled) {
            "Pengingat harian aktif sebelum jam 9 malam (20:30 WIB) ✨"
        } else {
            "Pengingat harian dimatikan"
        }
    }

    // === CLEAR DATA ACTION ===

    fun clearAllData(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.clearAllUserData()
            _userMessage.value = "✓ Seluruh data transaksi, tagihan, hutang, dan belanja telah dibersihkan!"
            onComplete()
        }
    }

    // === SI CICI (GEMMA AI) ACTIONS ===

    fun toggleTtsVoice() {
        _isTtsVoiceEnabled.value = !_isTtsVoiceEnabled.value
    }

    fun clearChatHistory() {
        _chatMessages.value = listOf(initialWelcomeMessage)
    }

    // SLM Model Management Functions
    fun selectSlmModel(model: SlmModelInfo) {
        modelDownloadManager.selectModel(model)
    }

    fun startModelDownload(customUrl: String? = null) {
        modelDownloadManager.startDownload(customUrl)
    }

    fun cancelModelDownload() {
        modelDownloadManager.cancelDownload()
    }

    fun deleteDownloadedModel(model: SlmModelInfo) {
        modelDownloadManager.deleteModel(model)
    }

    fun getCustomApiKey(): String = modelDownloadManager.getCustomApiKey()

    fun saveCustomApiKey(key: String) {
        modelDownloadManager.setCustomApiKey(key)
        _userMessage.value = if (key.isNotBlank()) "Kunci API berhasil disimpan!" else "Kunci API dihapus"
    }

    fun askSiCici(prompt: String, onSpeak: ((String) -> Unit)? = null) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) return

        // 1. Add User Message
        val userMsg = ChatMessage(isUser = true, text = cleanPrompt)
        _chatMessages.update { it + userMsg }
        _isCiciThinking.value = true

        viewModelScope.launch {
            try {
                val apiKey = modelDownloadManager.getCustomApiKey().ifBlank {
                    try {
                        BuildConfig.GEMINI_API_KEY
                    } catch (t: Throwable) {
                        ""
                    }
                }

                val currentModel = modelDownloadManager.selectedModel.value
                val isModelDownloaded = modelDownloadManager.isCurrentModelReady()
                val downloadedFile = if (isModelDownloaded) modelDownloadManager.getModelFile(currentModel) else null

                // 2. Query GemmaCiciBrain with live financial state and downloaded model if available
                val response = GemmaCiciBrain.processMessage(
                    userPrompt = cleanPrompt,
                    currentState = uiState.value,
                    apiKey = apiKey,
                    downloadedModelFile = downloadedFile,
                    activeModel = currentModel
                )

                // 3. Execute CRUD actions locally in Room Database (Multi-Action Function Calling)
                val executedResults = mutableListOf<CiciActionResult>()
                val actionsToExecute = response.allActions

                var totalAddedExpenseThisTurn = 0L
                var totalAddedIncomeThisTurn = 0L
                var requestedBalanceCheck = false

                for (action in actionsToExecute) {
                    when (action.type) {
                        "ADD_EXPENSE" -> {
                            val amount = action.amount ?: 0L
                            if (amount > 0L) {
                                val txCategory = action.category ?: DefaultExpenseCategories.DAPUR
                                val wallet = action.wallet ?: WalletSources.CASH
                                val note = action.item ?: cleanPrompt

                                repository.insertTransaction(
                                    TransactionEntity(
                                        type = TransactionTypes.EXPENSE,
                                        amount = amount,
                                        category = txCategory,
                                        walletSource = wallet,
                                        dateTimestamp = DateTimeUtils.getCurrentJakartaTimeMillis(),
                                        note = note
                                    )
                                )
                                totalAddedExpenseThisTurn += amount
                                executedResults.add(
                                    CiciActionResult(
                                        actionType = CiciActionType.ADD_EXPENSE,
                                        title = "Pengeluaran Dicatat",
                                        details = "$note • ${CurrencyFormatter.formatRupiah(amount)} ($txCategory, $wallet)",
                                        amount = amount,
                                        category = txCategory,
                                        wallet = wallet
                                    )
                                )
                            }
                        }

                        "ADD_INCOME" -> {
                            val amount = action.amount ?: 0L
                            if (amount > 0L) {
                                val txCategory = action.category ?: DefaultIncomeCategories.NAFKAH
                                val wallet = action.wallet ?: WalletSources.BANK
                                val note = action.item ?: cleanPrompt

                                repository.insertTransaction(
                                    TransactionEntity(
                                        type = TransactionTypes.INCOME,
                                        amount = amount,
                                        category = txCategory,
                                        walletSource = wallet,
                                        dateTimestamp = DateTimeUtils.getCurrentJakartaTimeMillis(),
                                        note = note
                                    )
                                )
                                totalAddedIncomeThisTurn += amount
                                executedResults.add(
                                    CiciActionResult(
                                        actionType = CiciActionType.ADD_INCOME,
                                        title = "Pemasukan Dicatat",
                                        details = "$note • ${CurrencyFormatter.formatRupiah(amount)} ($wallet)",
                                        amount = amount,
                                        category = txCategory,
                                        wallet = wallet
                                    )
                                )
                            }
                        }

                        "ADD_TRANSACTION" -> {
                            val amount = action.amount ?: 0L
                            if (amount > 0L) {
                                val txCategory = action.category ?: DefaultExpenseCategories.DAPUR
                                val wallet = action.wallet ?: WalletSources.CASH
                                val note = action.item ?: cleanPrompt

                                repository.insertTransaction(
                                    TransactionEntity(
                                        type = TransactionTypes.EXPENSE,
                                        amount = amount,
                                        category = txCategory,
                                        walletSource = wallet,
                                        dateTimestamp = DateTimeUtils.getCurrentJakartaTimeMillis(),
                                        note = note
                                    )
                                )
                                totalAddedExpenseThisTurn += amount
                                executedResults.add(
                                    CiciActionResult(
                                        actionType = CiciActionType.ADD_TRANSACTION,
                                        title = "Pengeluaran Dicatat",
                                        details = "$note • ${CurrencyFormatter.formatRupiah(amount)}",
                                        amount = amount,
                                        category = txCategory,
                                        wallet = wallet
                                    )
                                )
                            }
                        }

                        "UPDATE_TRANSACTION" -> {
                            val targetItemName = action.targetItem ?: action.item ?: ""
                            val newAmount = action.newAmount ?: action.amount
                            val newNote = action.newNote

                            val txToUpdate = if (action.transactionId != null) {
                                uiState.value.allTransactions.find { it.id == action.transactionId }
                            } else {
                                uiState.value.allTransactions.take(8).find { tx ->
                                    val txDesc = tx.note.lowercase()
                                    targetItemName.isNotBlank() && txDesc.contains(targetItemName.lowercase())
                                } ?: uiState.value.allTransactions.firstOrNull()
                            }

                            if (txToUpdate != null && newAmount != null && newAmount > 0L) {
                                val updated = txToUpdate.copy(
                                    amount = newAmount,
                                    note = newNote ?: txToUpdate.note
                                )
                                repository.updateTransaction(updated)
                                executedResults.add(
                                    CiciActionResult(
                                        actionType = CiciActionType.UPDATE_TRANSACTION,
                                        title = "Transaksi Diralat",
                                        details = "${txToUpdate.note.ifBlank { txToUpdate.category }}: ralat harga jadi ${CurrencyFormatter.formatRupiah(newAmount)}",
                                        amount = newAmount,
                                        isUpdated = true
                                    )
                                )
                            }
                        }

                        "DELETE_TRANSACTION" -> {
                            val targetItemName = action.targetItem ?: action.item ?: ""
                            val txToDelete = if (action.transactionId != null) {
                                uiState.value.allTransactions.find { it.id == action.transactionId }
                            } else {
                                uiState.value.allTransactions.take(8).find { tx ->
                                    val txDesc = tx.note.lowercase()
                                    targetItemName.isNotBlank() && txDesc.contains(targetItemName.lowercase())
                                } ?: uiState.value.allTransactions.firstOrNull()
                            }

                            if (txToDelete != null) {
                                repository.deleteTransaction(txToDelete)
                                executedResults.add(
                                    CiciActionResult(
                                        actionType = CiciActionType.DELETE_TRANSACTION,
                                        title = "Transaksi Dibatalkan",
                                        details = "${txToDelete.note.ifBlank { txToDelete.category }} (${CurrencyFormatter.formatRupiah(txToDelete.amount)}) dihapus dari kas",
                                        amount = txToDelete.amount,
                                        isDeleted = true
                                    )
                                )
                            }
                        }

                        "ADD_WISHLIST" -> {
                            val itemName = action.item ?: cleanPrompt
                            val targetPrice = action.amount ?: 0L
                            repository.insertWishlist(
                                WishlistEntity(
                                    name = itemName,
                                    targetAmount = targetPrice,
                                    categoryType = WishlistCategories.KITCHEN
                                )
                            )
                            executedResults.add(
                                CiciActionResult(
                                    actionType = CiciActionType.ADD_WISHLIST,
                                    title = "Ditambahkan ke Belanjaan",
                                    details = "$itemName${if (targetPrice > 0L) " (~${CurrencyFormatter.formatRupiah(targetPrice)})" else ""}",
                                    amount = targetPrice
                                )
                            )
                        }

                        "PAY_BILL" -> {
                            val billTitle = action.billTitle ?: ""
                            val bill = uiState.value.bills.find {
                                it.title.contains(billTitle, ignoreCase = true) ||
                                (billTitle.contains("listrik", ignoreCase = true) && it.title.contains("listrik", ignoreCase = true)) ||
                                (billTitle.contains("air", ignoreCase = true) && it.title.contains("air", ignoreCase = true)) ||
                                (billTitle.contains("wifi", ignoreCase = true) && it.title.contains("wifi", ignoreCase = true))
                            }
                            if (bill != null) {
                                val currentMonthKey = DateTimeUtils.getCurrentMonthYearKey()
                                repository.updateBill(bill.copy(lastPaidMonthYear = currentMonthKey))
                                repository.insertTransaction(
                                    TransactionEntity(
                                        type = TransactionTypes.EXPENSE,
                                        amount = bill.estimatedAmount,
                                        category = bill.category,
                                        walletSource = bill.walletSource,
                                        dateTimestamp = DateTimeUtils.getCurrentJakartaTimeMillis(),
                                        note = "Pelunasan tagihan: ${bill.title}"
                                    )
                                )
                                totalAddedExpenseThisTurn += bill.estimatedAmount
                                executedResults.add(
                                    CiciActionResult(
                                        actionType = CiciActionType.PAY_BILL,
                                        title = "Tagihan Ditandai Lunas",
                                        details = "${bill.title} • ${CurrencyFormatter.formatRupiah(bill.estimatedAmount)}",
                                        amount = bill.estimatedAmount
                                    )
                                )
                            }
                        }

                        "CHECK_BALANCE" -> {
                            requestedBalanceCheck = true
                            executedResults.add(
                                CiciActionResult(
                                    actionType = CiciActionType.CHECK_BALANCE,
                                    title = "Informasi Sisa Kas",
                                    details = "Total Saldo: ${CurrencyFormatter.formatRupiah(uiState.value.totalNetBalance)}",
                                    amount = uiState.value.totalNetBalance
                                )
                            )
                        }
                    }
                }

                // 4. EXACT MATHEMATICAL CALCULATION (Zero Arithmetic Hallucination)
                val exactUpdatedBalance = uiState.value.totalNetBalance - totalAddedExpenseThisTurn + totalAddedIncomeThisTurn
                var finalSpeechText = response.replyText

                if (totalAddedExpenseThisTurn > 0L && requestedBalanceCheck) {
                    val mathSummary = "\n\n💰 Hitungan Kas Eksak: Total belanja barusan ${CurrencyFormatter.formatRupiah(totalAddedExpenseThisTurn)}. Sisa saldo kas Bunda menjadi ${CurrencyFormatter.formatRupiah(exactUpdatedBalance)}."
                    finalSpeechText += mathSummary
                } else if (totalAddedExpenseThisTurn > 0L && executedResults.size > 1) {
                    val mathSummary = "\n\n💡 Total belanjaan barusan: ${CurrencyFormatter.formatRupiah(totalAddedExpenseThisTurn)}."
                    finalSpeechText += mathSummary
                }

                // 5. Append Si Cici's message to chat
                val ciciMsg = ChatMessage(
                    isUser = false,
                    text = finalSpeechText,
                    actionResult = executedResults.firstOrNull(),
                    actionResults = executedResults,
                    engineBadge = response.engineUsed
                )
                _chatMessages.update { it + ciciMsg }

                // 6. Trigger voice TTS if enabled
                if (_isTtsVoiceEnabled.value) {
                    onSpeak?.invoke(finalSpeechText)
                }
            } catch (e: Exception) {
                val errorMsg = ChatMessage(
                    isUser = false,
                    text = "Maaf Bunda, terjadi sedikit kendala teknis: ${e.message}. Cici siap coba lagi ya Bun!",
                    isError = true
                )
                _chatMessages.update { it + errorMsg }
            } finally {
                _isCiciThinking.value = false
            }
        }
    }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = AppDatabase.getDatabase(application)
                    val repository = FinanceRepository(db.financeDao())
                    return FinanceViewModel(application, repository) as T
                }
            }
    }
}
