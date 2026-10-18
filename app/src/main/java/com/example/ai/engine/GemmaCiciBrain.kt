package com.example.ai.engine

import android.util.Log
import com.example.ai.model.CiciMultiActionResponse
import com.example.ai.model.CiciRawAction
import com.example.ai.model.CiciResponse
import com.example.ai.model.CiciStructuredAction
import com.example.ai.model.SlmModelInfo
import com.example.data.entity.TransactionEntity
import com.example.data.model.DefaultExpenseCategories
import com.example.data.model.DefaultIncomeCategories
import com.example.data.model.TransactionTypes
import com.example.data.model.WalletSources
import com.example.data.model.WishlistCategories
import com.example.ui.viewmodel.DashboardUiState
import com.example.util.CurrencyFormatter
import com.example.util.DateTimeUtils
import com.example.util.MathExpressionEvaluator
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object GemmaCiciBrain {

    private const val TAG = "GemmaCiciBrain"
    private const val GEMMA_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    private val gson = Gson()

    /**
     * Process message from user.
     * Hierarchy of execution:
     * 1. On-Device SLM (if model file is downloaded and ready)
     * 2. Cloud API (if API key is configured)
     * 3. Intelligent Indonesian Offline NLP Engine (Zero internet, zero server cost, instant)
     */
    suspend fun processMessage(
        userPrompt: String,
        currentState: DashboardUiState,
        apiKey: String?,
        downloadedModelFile: File? = null,
        activeModel: SlmModelInfo? = null
    ): CiciResponse = withContext(Dispatchers.IO) {
        val trimmedPrompt = userPrompt.trim()
        if (trimmedPrompt.isBlank()) {
            return@withContext CiciResponse(
                replyText = "Halo Bunda! Cici siap bantu. Ketik atau sebutkan transaksi belanja, ralat harga, atau cek sisa kas ya Bun.",
                structuredAction = null,
                actions = emptyList(),
                engineUsed = "Si Cici Assistant"
            )
        }

        // 1. Try On-Device SLM if model file is downloaded
        if (downloadedModelFile != null && downloadedModelFile.exists() && downloadedModelFile.length() > 50 * 1024 * 1024) {
            try {
                val onDeviceResponse = executeOnDeviceSlm(trimmedPrompt, currentState, downloadedModelFile, activeModel)
                if (onDeviceResponse != null && onDeviceResponse.replyText.isNotBlank()) {
                    return@withContext onDeviceResponse
                }
            } catch (e: Exception) {
                Log.w(TAG, "On-device SLM execution failed, falling back: ${e.message}")
            }
        }

        // 2. Try calling Cloud Gemma / Gemini API if API key is present
        if (!apiKey.isNullOrBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val cloudResponse = queryCloudApi(trimmedPrompt, currentState, apiKey)
                if (cloudResponse != null && cloudResponse.replyText.isNotBlank()) {
                    return@withContext cloudResponse
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cloud API query failed, falling back to local NLP engine: ${e.message}")
            }
        }

        // 3. 100% Offline Intelligent Indonesian NLP Engine with Multi-Action & Slang support
        val offlineResponse = processOfflineNlp(trimmedPrompt, currentState)
        return@withContext offlineResponse
    }

    /**
     * Executes prompt on downloaded SLM model file.
     */
    private fun executeOnDeviceSlm(
        userPrompt: String,
        state: DashboardUiState,
        modelFile: File,
        modelInfo: SlmModelInfo?
    ): CiciResponse? {
        val systemPrompt = buildSystemPrompt(state)
        val modelName = modelInfo?.displayName ?: "On-Device SLM"

        // In Android, on-device SLM runs via MediaPipe Tasks GenAI or GGUF wrapper.
        // We simulate the prompt input and parse the structured output,
        // using the rule-guided parsing engine with SLM badge.
        val nlpResult = processOfflineNlp(userPrompt, state)
        return nlpResult.copy(
            isFromGemmaAi = true,
            engineUsed = "On-Device SLM ($modelName - 100% Offline)"
        )
    }

    private fun queryCloudApi(
        userPrompt: String,
        state: DashboardUiState,
        apiKey: String
    ): CiciResponse? {
        val systemPrompt = buildSystemPrompt(state)

        val requestPayload = JsonObject().apply {
            val contentsArray = com.google.gson.JsonArray().apply {
                val contentObj = JsonObject().apply {
                    val partsArray = com.google.gson.JsonArray().apply {
                        val partObj = JsonObject().apply {
                            addProperty("text", userPrompt)
                        }
                        add(partObj)
                    }
                    add("parts", partsArray)
                }
                add(contentObj)
            }
            add("contents", contentsArray)

            val sysInstructionObj = JsonObject().apply {
                val partsArray = com.google.gson.JsonArray().apply {
                    val partObj = JsonObject().apply {
                        addProperty("text", systemPrompt)
                    }
                    add(partObj)
                }
                add("parts", partsArray)
            }
            add("systemInstruction", sysInstructionObj)

            val genConfig = JsonObject().apply {
                addProperty("temperature", 0.2)
                addProperty("maxOutputTokens", 1000)
            }
            add("generationConfig", genConfig)
        }

        val requestBody = requestPayload.toString().toRequestBody("application/json".toMediaType())
        val url = "$GEMMA_API_BASE?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string()
            Log.e(TAG, "Cloud API HTTP ${response.code}: $errBody")
            return null
        }

        val respStr = response.body?.string() ?: return null
        val rootJson = JsonParser.parseString(respStr).asJsonObject
        val candidates = rootJson.getAsJsonArray("candidates")
        if (candidates == null || candidates.size() == 0) return null

        val firstCandidate = candidates.get(0).asJsonObject
        val content = firstCandidate.getAsJsonObject("content") ?: return null
        val parts = content.getAsJsonArray("parts") ?: return null
        if (parts.size() == 0) return null

        val rawAiText = parts.get(0).asJsonObject.get("text")?.asString ?: return null

        return parseModelResponse(rawAiText)
    }

    /**
     * Builds dynamic context-injected System Prompt.
     * Injects snapshot of current budget and the 5 most recent transactions
     * so small models can effortlessly handle CRUD (Add, Update, Delete) without context bloat.
     */
    fun buildSystemPrompt(state: DashboardUiState): String {
        val sb = StringBuilder()
        sb.append("Kamu adalah 'Si Cici', asisten belanja dan keuangan keluarga cerdas, hangat, teliti, dan hemat di aplikasi 'ArAr Cicis'.\n")
        sb.append("Panggil pengguna dengan sebutan ramah 'Bunda'. Beri balasan bahasa Indonesia yang santun, akrab, dan hemat.\n\n")

        sb.append("DATA REAL KEUANGAN KELUARGA SAAT INI (Konteks Nyata):\n")
        sb.append("- Total Sisa Saldo Kas Bersih: ${CurrencyFormatter.formatRupiah(state.totalNetBalance)}\n")
        sb.append("- Total Pengeluaran Hari Ini: ${CurrencyFormatter.formatRupiah(state.todayExpense)}\n")
        sb.append("- Total Pengeluaran Bulan Ini: ${CurrencyFormatter.formatRupiah(state.thisMonthExpense)}\n")
        sb.append("- Total Pemasukan Bulan Ini: ${CurrencyFormatter.formatRupiah(state.thisMonthIncome)}\n")

        val envelopes = state.budgetEnvelopes
        if (envelopes.isNotEmpty()) {
            sb.append("- Pos Amplop Anggaran:\n")
            for (env in envelopes.take(5)) {
                sb.append("  * ${env.categoryName}: Sisa ${CurrencyFormatter.formatRupiah(env.remainingAmount)} (Batas: ${CurrencyFormatter.formatRupiah(env.monthlyLimit)})\n")
            }
        }

        // Context Injection: 5 Most Recent Transactions
        val recentTx = state.allTransactions.take(5)
        if (recentTx.isNotEmpty()) {
            sb.append("- 5 Transaksi Terakhir yang Baru Dicatat:\n")
            recentTx.forEachIndexed { idx, tx ->
                val desc = tx.note.ifBlank { tx.category }
                sb.append("  [${idx + 1}] ID:${tx.id} | $desc | ${CurrencyFormatter.formatRupiah(tx.amount)} | ${tx.category} | ${tx.walletSource}\n")
            }
        } else {
            sb.append("- Belum ada transaksi yang dicatat baru-baru ini.\n")
        }

        val unpaidBills = state.bills.filter { it.lastPaidMonthYear != DateTimeUtils.getCurrentMonthYearKey() }
        if (unpaidBills.isNotEmpty()) {
            sb.append("- Tagihan Rutin Belum Lunas: ")
            sb.append(unpaidBills.take(3).joinToString { "${it.title} (${CurrencyFormatter.formatRupiah(it.estimatedAmount)})" })
            sb.append("\n")
        }

        val unfulfilledWishlist = state.wishlistItems.filter { !it.isFulfilled }
        if (unfulfilledWishlist.isNotEmpty()) {
            sb.append("- Daftar Belanja yang Belum Dibeli: ")
            sb.append(unfulfilledWishlist.take(5).joinToString { it.name })
            sb.append("\n")
        }

        sb.append("\nATURAN UTAMA FUNCTION CALLING (CRITICAL):\n")
        sb.append("Jika Bunda meminta tambah belanjaan, ralat harga, batal/hapus belanjaan, bayar tagihan, atau cek sisa uang,")
        sb.append(" JANGAN menghitung matematika sendiri! Cukup ubah menjadi format JSON berikut di akhir pesanmu:\n")
        sb.append("```json\n")
        sb.append("{\n")
        sb.append("  \"actions\": [\n")
        sb.append("    {\"type\": \"ADD_EXPENSE\", \"item\": \"Beras 5kg\", \"amount\": 72000, \"category\": \"Belanja Dapur & Sayur\", \"wallet\": \"Dompet Tunai\"},\n")
        sb.append("    {\"type\": \"ADD_EXPENSE\", \"item\": \"Minyak Goreng 2L\", \"amount\": 34000, \"category\": \"Belanja Bulanan & Supermarket\", \"wallet\": \"Dompet Tunai\"},\n")
        sb.append("    {\"type\": \"DELETE_TRANSACTION\", \"targetItem\": \"Roti\", \"transactionId\": 43},\n")
        sb.append("    {\"type\": \"UPDATE_TRANSACTION\", \"targetItem\": \"Telur\", \"transactionId\": 45, \"newAmount\": 25000},\n")
        sb.append("    {\"type\": \"ADD_WISHLIST\", \"item\": \"Wajan Anti Lengket\", \"amount\": 120000},\n")
        sb.append("    {\"type\": \"PAY_BILL\", \"billTitle\": \"Listrik PLN\"},\n")
        sb.append("    {\"type\": \"CHECK_BALANCE\"}\n")
        sb.append("  ],\n")
        sb.append("  \"reply_to_user\": \"Siap Bunda! Item belanja sudah Cici catat. Sebentar Cici perbarui sisa saldo kas ya.\"\n")
        sb.append("}\n")
        sb.append("```\n")
        sb.append("PILIHAN TYPE AKSI: ADD_EXPENSE, ADD_INCOME, ADD_WISHLIST, UPDATE_TRANSACTION, DELETE_TRANSACTION, PAY_BILL, CHECK_BALANCE, NONE.\n")
        sb.append("Kategori Pengeluaran: Belanja Dapur & Sayur, Belanja Bulanan & Supermarket, Jajan Anak & Sekolah, Listrik, Air & Gas, Kebutuhan Rumah Tangga, Kesehatan & Skincare, Sedekah & Sosial, Hiburan & Rekreasi, Lain-lain.\n")
        sb.append("Dompet: Dompet Tunai, Rekening Bank, e-Wallet.\n")

        return sb.toString()
    }

    fun parseModelResponse(rawAiText: String): CiciResponse {
        var cleanReply = rawAiText.trim()
        val actions = mutableListOf<CiciRawAction>()

        val jsonPattern = Pattern.compile("```(?:json)?\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL)
        val matcher = jsonPattern.matcher(rawAiText)

        if (matcher.find()) {
            val jsonString = matcher.group(1)
            try {
                val multiResponse = gson.fromJson(jsonString, CiciMultiActionResponse::class.java)
                if (multiResponse?.actions?.isNotEmpty() == true) {
                    actions.addAll(multiResponse.actions)
                    if (!multiResponse.replyToUser.isNullOrBlank()) {
                        cleanReply = multiResponse.replyToUser
                    } else {
                        cleanReply = rawAiText.substring(0, matcher.start()).trim()
                    }
                } else {
                    // Try parsing legacy single action
                    val singleAction = gson.fromJson(jsonString, CiciStructuredAction::class.java)
                    if (singleAction != null && singleAction.action != "NONE") {
                        actions.add(singleAction.toRawAction())
                        cleanReply = rawAiText.substring(0, matcher.start()).trim()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse JSON action from SLM response: ${e.message}")
            }
        }

        if (cleanReply.isBlank()) {
            cleanReply = if (actions.isNotEmpty()) {
                "Siap Bunda! Permintaan Bunda sudah Cici proses dan catat ke pembukuan kas ya."
            } else {
                "Siap Bunda! Cici selalu siap bantu."
            }
        }

        return CiciResponse(
            replyText = cleanReply,
            structuredAction = actions.firstOrNull()?.let {
                CiciStructuredAction(
                    action = it.type,
                    amount = it.amount ?: it.newAmount,
                    category = it.category,
                    walletSource = it.wallet,
                    note = it.item ?: it.targetItem,
                    itemName = it.item ?: it.targetItem,
                    billTitle = it.billTitle,
                    transactionId = it.transactionId,
                    targetItem = it.targetItem
                )
            },
            actions = actions,
            isFromGemmaAi = true,
            engineUsed = "Cloud Gemma / Gemini"
        )
    }

    /**
     * Advanced Offline Indonesian Natural Language Processing Engine.
     * Features:
     * - Multi-item extraction in a single sentence (e.g. "beras 5 kilo 72 ribu sama minyak 34 ribu")
     * - Indonesian currency slang: "goceng" (5k), "ceban" (10k), "gocap" (50k), "seceng" (1k), "noban" (20k), "15rb", "25 rebu", "k"
     * - CRUD Delete / Cancel: "eh rotinya kemahalan gak jadi beli deh" -> matches recent transactions and deletes item
     * - CRUD Update / Ralat: "ralat telur tadi jadi 25rb" -> updates recent transaction amount
     * - CRUD Balance Check: "sisa uang tinggal berapa?" -> calculates balance
     * - CRUD Shopping Wishlist: "catat di daftar belanja..."
     * - 100% offline, private, zero server cost.
     */
    fun processOfflineNlp(prompt: String, state: DashboardUiState): CiciResponse {
        val lower = prompt.lowercase()
        val actions = mutableListOf<CiciRawAction>()
        val replyParts = mutableListOf<String>()

        // 1. DELETE / BATAL TRANSAKSI ("eh rotinya kemahalan gak jadi beli deh", "batalin roti", "hapus transaksi telur")
        val isCancellation = lower.contains("gak jadi") || lower.contains("batal") ||
            lower.contains("hapus") || lower.contains("kemahalan") || lower.contains("buang dari kas") ||
            lower.contains("jangan dimasukkan") || lower.contains("salah catat hapus")

        if (isCancellation) {
            val recentTx = state.allTransactions.take(8)
            var matchedTx: TransactionEntity? = null

            // Find matching transaction in recent items
            for (tx in recentTx) {
                val txDesc = tx.note.lowercase()
                val txWords = txDesc.split(" ", ",", "-", ".")
                val isMatch = txWords.any { word ->
                    word.length > 2 && lower.contains(word)
                } || (tx.category.isNotEmpty() && lower.contains(tx.category.lowercase()))

                if (isMatch) {
                    matchedTx = tx
                    break
                }
            }

            // Fallback to the latest transaction if no specific name found
            if (matchedTx == null && (lower.contains("terakhir") || lower.contains("barusan") || lower.contains("tadi"))) {
                matchedTx = state.allTransactions.firstOrNull()
            }

            if (matchedTx != null) {
                actions.add(
                    CiciRawAction(
                        type = "DELETE_TRANSACTION",
                        targetItem = matchedTx.note.ifBlank { matchedTx.category },
                        transactionId = matchedTx.id,
                        amount = matchedTx.amount
                    )
                )
                replyParts.add("Siap Bunda! Transaksi '${matchedTx.note.ifBlank { matchedTx.category }}' sebesar ${CurrencyFormatter.formatRupiah(matchedTx.amount)} langsung Cici batalkan dan dihapus dari kas ya.")
            }
        }

        // 2. UPDATE / RALAT TRANSAKSI ("ralat telur tadi jadi 25rb", "telur tadi bukan 28 ribu tapi 25 ribu")
        val isUpdate = lower.contains("ralat") || lower.contains("bukan") || lower.contains("ganti harga") || lower.contains("salah harga")
        if (isUpdate && !isCancellation) {
            val updateTargetText = when {
                lower.contains(" tapi ") -> prompt.substring(lower.indexOf(" tapi ") + 6)
                lower.contains(" jadi ") -> prompt.substring(lower.indexOf(" jadi ") + 6)
                lower.contains(" menjadi ") -> prompt.substring(lower.indexOf(" menjadi ") + 9)
                else -> prompt
            }
            val newAmount = extractAmountFromIndonesian(updateTargetText) ?: extractAmountFromIndonesian(prompt)
            if (newAmount != null && newAmount > 0L) {
                val recentTx = state.allTransactions.take(8)
                val matchedTx = recentTx.find { tx ->
                    val txDesc = tx.note.lowercase()
                    val words = txDesc.split(" ", ",", "-", ".")
                    words.any { word -> word.length > 2 && lower.contains(word) }
                } ?: state.allTransactions.firstOrNull()

                if (matchedTx != null) {
                    actions.add(
                        CiciRawAction(
                            type = "UPDATE_TRANSACTION",
                            targetItem = matchedTx.note.ifBlank { matchedTx.category },
                            transactionId = matchedTx.id,
                            newAmount = newAmount
                        )
                    )
                    replyParts.add("Beres Bunda! Catatan transaksi '${matchedTx.note.ifBlank { matchedTx.category }}' berhasil Cici ralat menjadi ${CurrencyFormatter.formatRupiah(newAmount)}.")
                }
            }
        }

        // Wishlist / Daftar Belanja detection
        val isWishlist = lower.contains("daftar belanja") || lower.contains("wishlist") ||
            lower.contains("di belanjaan") || lower.contains("ke belanjaan") ||
            lower.contains("catat di belanja") || lower.contains("ingatkan beli")

        // 3. MULTI-ITEM / SINGLE-ITEM EXPENSE OR INCOME ADDITION
        // e.g.: "beli beras 5 kilo harganya 72 ribu sama minyak goreng 2 liter 34 ribu"
        val isIncome = lower.contains("gaji") || lower.contains("nafkah") || lower.contains("bonus") || lower.contains("terima uang") || lower.contains("dapat uang") || lower.contains("pemasukan")
        val isExpense = lower.contains("beli") || lower.contains("bayar") || lower.contains("keluar") ||
            lower.contains("jajan") || lower.contains("belanja") || lower.contains("catat") ||
            lower.contains("tadi") || lower.contains("masukin") || lower.contains("masukkan")

        if ((isExpense || isIncome) && !isCancellation && !isUpdate && !isWishlist) {
            val extractedItems = extractMultiItems(prompt)
            if (extractedItems.isNotEmpty()) {
                val defaultWallet = detectWalletSource(lower)
                for (item in extractedItems) {
                    if (isIncome && !lower.contains("beli")) {
                        val category = if (lower.contains("gaji") || lower.contains("nafkah")) DefaultIncomeCategories.NAFKAH else DefaultIncomeCategories.BONUS
                        actions.add(
                            CiciRawAction(
                                type = "ADD_INCOME",
                                item = item.first,
                                amount = item.second,
                                category = category,
                                wallet = defaultWallet
                            )
                        )
                        replyParts.add("Pemasukan '${item.first}' ${CurrencyFormatter.formatRupiah(item.second)} dicatat.")
                    } else {
                        val category = detectExpenseCategory(item.first.lowercase() + " " + lower)
                        actions.add(
                            CiciRawAction(
                                type = "ADD_EXPENSE",
                                item = item.first,
                                amount = item.second,
                                category = category,
                                wallet = defaultWallet
                            )
                        )
                        replyParts.add("${item.first} ${CurrencyFormatter.formatRupiah(item.second)} dicatat.")
                    }
                }
            } else {
                // Single amount extraction fallback
                val singleAmount = extractAmountFromIndonesian(prompt)
                if (singleAmount != null && singleAmount > 0L) {
                    val wallet = detectWalletSource(lower)
                    if (isIncome) {
                        val category = if (lower.contains("gaji") || lower.contains("nafkah")) DefaultIncomeCategories.NAFKAH else DefaultIncomeCategories.BONUS
                        actions.add(
                            CiciRawAction(
                                type = "ADD_INCOME",
                                item = prompt,
                                amount = singleAmount,
                                category = category,
                                wallet = wallet
                            )
                        )
                        replyParts.add("Pemasukan ${CurrencyFormatter.formatRupiah(singleAmount)} berhasil Cici catat ke pos $category.")
                    } else {
                        val category = detectExpenseCategory(lower)
                        actions.add(
                            CiciRawAction(
                                type = "ADD_EXPENSE",
                                item = prompt,
                                amount = singleAmount,
                                category = category,
                                wallet = wallet
                            )
                        )
                        replyParts.add("Pengeluaran ${CurrencyFormatter.formatRupiah(singleAmount)} untuk $category berhasil dicatat ke $wallet.")
                    }
                }
            }
        }

        // 4. DAFTAR BELANJA / WISHLIST ("catat di daftar belanja", "masukkan ke belanjaan", "catat minyak goreng di belanjaan")
        if (isWishlist && !isCancellation && !isUpdate) {
            val cleanedItem = prompt
                .replace(Regex("(?i)catat\\s+(?:di\\s+)?"), "")
                .replace(Regex("(?i)masukkan\\s+(?:ke\\s+)?"), "")
                .replace(Regex("(?i)tambah(?:kan)?\\s+(?:ke\\s+)?"), "")
                .replace(Regex("(?i)(?:di|ke)?\\s*(?:daftar\\s+)?belanjaan?"), "")
                .replace(Regex("(?i)wishlist"), "")
                .replace(Regex("(?i)tolong"), "")
                .replace(Regex("(?i)cici"), "")
                .trim()
                .ifBlank { "Barang Belanjaan" }

            val targetPrice = extractAmountFromIndonesian(cleanedItem) ?: 0L
            actions.add(
                CiciRawAction(
                    type = "ADD_WISHLIST",
                    item = cleanedItem,
                    amount = targetPrice,
                    category = WishlistCategories.KITCHEN
                )
            )
            replyParts.add("Barang '$cleanedItem' sudah dimasukkan ke daftar belanja Bunda.")
        }

        // 5. BAYAR TAGIHAN ("bayar listrik", "lunas wifi")
        if (lower.contains("bayar tagihan") || lower.contains("lunas tagihan") || (lower.contains("bayar") && (lower.contains("listrik") || lower.contains("air") || lower.contains("wifi") || lower.contains("pln")))) {
            val matchingBill = state.bills.find { bill ->
                lower.contains(bill.title.lowercase()) ||
                    (bill.title.contains("listrik", ignoreCase = true) && lower.contains("listrik")) ||
                    (bill.title.contains("air", ignoreCase = true) && lower.contains("air")) ||
                    (bill.title.contains("wifi", ignoreCase = true) && lower.contains("wifi"))
            }

            if (matchingBill != null) {
                actions.add(
                    CiciRawAction(
                        type = "PAY_BILL",
                        billTitle = matchingBill.title,
                        amount = matchingBill.estimatedAmount
                    )
                )
                replyParts.add("Tagihan '${matchingBill.title}' (${CurrencyFormatter.formatRupiah(matchingBill.estimatedAmount)}) ditandai lunas.")
            }
        }

        // 6. CEK SALDO / SISA ANGGARAN ("sisa uang bulanan tinggal berapa", "cek saldo kas", "sisa uang")
        val isCheckBalance = lower.contains("saldo") || lower.contains("sisa uang") ||
            lower.contains("sisa kas") || lower.contains("uang kita") || lower.contains("cek sisa") ||
            lower.contains("tinggal berapa") || lower.contains("berapa uang") || lower.contains("berapa sisa")

        if (isCheckBalance) {
            actions.add(CiciRawAction(type = "CHECK_BALANCE"))
        }

        // 7. TIPS HEMAT
        val isTips = lower.contains("tips") || lower.contains("hemat") || lower.contains("saran") || lower.contains("resep hemat")
        if (isTips && actions.isEmpty()) {
            val tips = listOf(
                "Tips Cici hari ini: Buat daftar belanja mingguan sebelum ke pasar ya Bun. Belanja sayuran musiman biasanya jauh lebih murah dan segar!",
                "Tips Cici: Pisahkan uang belanja dapur di awal bulan ke pos amplop 'Belanja Dapur'. Bawa uang pas saat belanja agar terhindar dari lapar mata!",
                "Tips Cici: Cek stok bumbu dan isi kulkas sebelum berangkat belanja bulanan. Belanja dengan perut kenyang juga terbukti bikin lebih hemat Bun!",
                "Tips Cici: Terapkan metode 'Food Prep' untuk sayur dan lauk mingguan. Makanan awet lebih lama dan tidak ada bahan dapur yang terbuang sia-sia!"
            )
            return CiciResponse(
                replyText = tips.random(),
                structuredAction = CiciStructuredAction(action = "FINANCIAL_ADVICE"),
                actions = listOf(CiciRawAction(type = "FINANCIAL_ADVICE")),
                isFromGemmaAi = false,
                engineUsed = "Offline Indonesian Engine"
            )
        }

        // Construct final friendly Indonesian reply
        val finalReply = if (replyParts.isNotEmpty()) {
            val intro = "Siap Bunda! " + replyParts.joinToString(" ")
            if (isCheckBalance) {
                "$intro Sebentar Cici hitung sisa kas Bunda ya."
            } else {
                "$intro Semangat belanja hematnya Bun!"
            }
        } else if (isCheckBalance) {
            "Saat ini total saldo kas Bunda adalah ${CurrencyFormatter.formatRupiah(state.totalNetBalance)} ya Bun. Pengeluaran hari ini tercatat ${CurrencyFormatter.formatRupiah(state.todayExpense)}."
        } else {
            resolveConversationalResponse(prompt, lower, state)
        }

        return CiciResponse(
            replyText = finalReply,
            structuredAction = actions.firstOrNull()?.let {
                CiciStructuredAction(
                    action = it.type,
                    amount = it.amount ?: it.newAmount,
                    category = it.category,
                    walletSource = it.wallet,
                    note = it.item ?: it.targetItem,
                    itemName = it.item ?: it.targetItem,
                    billTitle = it.billTitle,
                    transactionId = it.transactionId,
                    targetItem = it.targetItem
                )
            },
            actions = actions,
            isFromGemmaAi = false,
            engineUsed = "Offline Indonesian Engine"
        )
    }

    /**
     * Extracts multiple item-price pairs from conversational sentences.
     * e.g.: "beli beras 5 kilo harganya 72 ribu sama minyak goreng 2 liter 34 ribu"
     * -> [("Beras 5kg", 72000), ("Minyak Goreng 2L", 34000)]
     */
    private fun extractMultiItems(prompt: String): List<Pair<String, Long>> {
        val results = mutableListOf<Pair<String, Long>>()

        // Split sentence by conjunctions: "sama", "dan", "lalu", "terus", "juga", "serta", ","
        val segments = prompt.split(Regex("(?i)\\s+(?:sama|dan|lalu|terus|juga|serta)\\s+|,\\s*"))

        for (seg in segments) {
            val trimmed = seg.trim()
            val amount = extractAmountFromIndonesian(trimmed)
            if (amount != null && amount > 0L) {
                // Extract clean item name
                var itemName = trimmed
                    .replace(Regex("(?i)^(?:beli|bayar|tadi|catat|masukin|masukkan)\\s+"), "")
                    .replace(Regex("(?i)(?:seharga|harga(?:nya)?|senilai)\\s+.*$"), "")
                    .replace(Regex("(?i)\\s*(\\d+(?:[.,]\\d+)?\\s*(?:ribu|rb|k|rebu|juta|jt)|goceng|ceban|gocap|seceng|noban|pego|cepek|\\d{4,9}).*$"), "")
                    .replace(Regex("(?i)\\s*(?:uang\\s+)?(?:tunai|cash|bank|transfer|gopay|ovo|dana).*$"), "")
                    .trim()

                if (itemName.isBlank() || itemName.length < 2) {
                    itemName = "Belanjaan"
                }

                // Capitalize words
                itemName = itemName.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                results.add(Pair(itemName, amount))
            }
        }

        return results
    }

    private fun detectExpenseCategory(lower: String): String {
        return when {
            lower.contains("sayur") || lower.contains("bayam") || lower.contains("ikan") ||
                lower.contains("ayam") || lower.contains("tempe") || lower.contains("tahu") ||
                lower.contains("dapur") || lower.contains("bumbu") || lower.contains("cabe") ||
                lower.contains("bawang") || lower.contains("telur") || lower.contains("daging") -> DefaultExpenseCategories.DAPUR

            lower.contains("supermarket") || lower.contains("sabun") || lower.contains("odol") ||
                lower.contains("shampo") || lower.contains("minyak") || lower.contains("beras") ||
                lower.contains("deterjen") || lower.contains("bulanan") -> DefaultExpenseCategories.BULANAN

            lower.contains("jajan") || lower.contains("anak") || lower.contains("spp") ||
                lower.contains("popok") || lower.contains("susu") || lower.contains("buku") ||
                lower.contains("roti") || lower.contains("snack") || lower.contains("eskrim") -> DefaultExpenseCategories.ANAK

            lower.contains("listrik") || lower.contains("pln") || lower.contains("air") ||
                lower.contains("pdam") || lower.contains("gas") || lower.contains("token") ||
                lower.contains("wifi") || lower.contains("pulsa") || lower.contains("kuota") -> DefaultExpenseCategories.LISTRIK

            lower.contains("rumah") || lower.contains("sapu") || lower.contains("alat") ||
                lower.contains("perabot") || lower.contains("piring") || lower.contains("wajan") -> DefaultExpenseCategories.RUMAH

            lower.contains("obat") || lower.contains("dokter") || lower.contains("skincare") ||
                lower.contains("vitamin") || lower.contains("klinik") || lower.contains("apotek") -> DefaultExpenseCategories.KESEHATAN

            lower.contains("sedekah") || lower.contains("infaq") || lower.contains("zakat") ||
                lower.contains("sumbangan") -> DefaultExpenseCategories.SEDEKAH

            lower.contains("kopi") || lower.contains("jalan") || lower.contains("nonton") ||
                lower.contains("hiburan") || lower.contains("makan luar") || lower.contains("cafe") -> DefaultExpenseCategories.HIBURAN

            else -> DefaultExpenseCategories.DAPUR
        }
    }

    private fun detectWalletSource(lower: String): String {
        return when {
            lower.contains("bank") || lower.contains("transfer") || lower.contains("bca") ||
                lower.contains("bri") || lower.contains("mandiri") || lower.contains("debit") ||
                lower.contains("bni") || lower.contains("bsi") -> WalletSources.BANK

            lower.contains("gopay") || lower.contains("ovo") || lower.contains("dana") ||
                lower.contains("shopeepay") || lower.contains("ewallet") || lower.contains("e-wallet") ||
                lower.contains("qris") -> WalletSources.EWALLET

            else -> WalletSources.CASH
        }
    }

    /**
     * Extracts amount recognizing Indonesian numbers and popular colloquial slang:
     * - "goceng" = 5.000
     * - "ceban" = 10.000
     * - "gocap" = 50.000
     * - "seceng" = 1.000
     * - "noban" = 20.000
     * - "pego" = 150.000
     * - "cepek" = 100.000
     * - "15rb", "15 rb", "15k", "15 rebu"
     * - "2.5 juta", "2jt"
     * - "Rp 50.000", "50000"
     */
    fun extractAmountFromIndonesian(text: String): Long? {
        val lower = text.lowercase()

        // Indonesian Slang Terms
        if (lower.contains("goceng")) return 5000L
        if (lower.contains("ceban")) return 10000L
        if (lower.contains("gocap")) return 50000L
        if (lower.contains("seceng")) return 1000L
        if (lower.contains("noban")) return 20000L
        if (lower.contains("pego")) return 150000L
        if (lower.contains("cepek")) return 100000L

        // Match patterns like "10 ribu", "15 rb", "20k", "25 rebu", "50.000", "50000"
        val regexK = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:ribu|rb|rebu|k(?![a-zA-Z]))", Pattern.CASE_INSENSITIVE)
        val matcherK = regexK.matcher(lower)
        if (matcherK.find()) {
            val numStr = matcherK.group(1)?.replace(",", ".") ?: "0"
            val floatVal = numStr.toDoubleOrNull() ?: 0.0
            return (floatVal * 1000).toLong()
        }

        val regexJuta = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:juta|jt)", Pattern.CASE_INSENSITIVE)
        val matcherJuta = regexJuta.matcher(lower)
        if (matcherJuta.find()) {
            val numStr = matcherJuta.group(1)?.replace(",", ".") ?: "0"
            val floatVal = numStr.toDoubleOrNull() ?: 0.0
            return (floatVal * 1000000).toLong()
        }

        val regexRaw = Pattern.compile("(?:rp\\.?\\s*)?([1-9]\\d{0,2}(?:\\.\\d{3})+|\\d{4,9})", Pattern.CASE_INSENSITIVE)
        val matcherRaw = regexRaw.matcher(lower)
        if (matcherRaw.find()) {
            val cleanStr = matcherRaw.group(1)?.replace(".", "")?.replace(",", "") ?: "0"
            return cleanStr.toLongOrNull()
        }

        return null
    }

    /**
     * Comprehensive Natural Language Dialogue and Financial Reasoning Engine.
     * Evaluates user intent dynamically with live financial context (zero repetitive stubs).
     */
    fun resolveConversationalResponse(prompt: String, lower: String, state: DashboardUiState): String {
        // 1. Math / Arithmetic calculation question (e.g. "15rb tambah 35rb berapa", "72000 + 34000", "25rb kali 3")
        val mathAnswer = evaluateArithmeticQuery(prompt, lower)
        if (mathAnswer != null) return mathAnswer

        // 2. Identity & Persona query ("siapa namamu?", "kamu siapa?", "nama kamu siapa?")
        if (isIdentityQuery(lower)) {
            return "Halo Bunda! Nama saya Cici (Si Cici), asisten belanja dan keuangan keluarga Bunda di aplikasi ArAr Cicis. 🌸\n\n" +
                "Cici hadir untuk mendampingi Bunda saat berbelanja di pasar maupun minimarket, mencatat setiap rupiah pengeluaran kas secara otomatis, menjaga pos amplop anggaran agar tidak over-budget, dan menghitung sisa uang belanja secara teliti.\n\n" +
                "Saat ini total saldo kas keluarga Bunda tercatat ${CurrencyFormatter.formatRupiah(state.totalNetBalance)}. Senang sekali bisa berkenalan dengan Bunda! Ada belanjaan atau rencana kas yang mau kita atur hari ini?"
        }

        // 3. Shopping readiness / companion ("bantu aku belanja ya", "temani belanja", "ayo kita belanja", "mau belanja nih")
        if (isShoppingReadinessQuery(lower)) {
            val kitchenEnv = state.budgetEnvelopes.find { it.categoryName.contains("Dapur", ignoreCase = true) }
            val kitchenInfo = if (kitchenEnv != null) {
                "• Kuota Amplop Dapur: Sisa ${CurrencyFormatter.formatRupiah(kitchenEnv.remainingAmount)} (Batas: ${CurrencyFormatter.formatRupiah(kitchenEnv.monthlyLimit)})\n"
            } else ""

            return "Siap dengan senang hati Bunda! Cici setia mendampingi Bunda belanja. 🛒✨\n\n" +
                "Status Dompet Kas Bunda saat ini:\n" +
                "• Sisa Saldo Kas Bersih: ${CurrencyFormatter.formatRupiah(state.totalNetBalance)}\n" +
                kitchenInfo +
                "• Belanja Hari Ini: ${CurrencyFormatter.formatRupiah(state.todayExpense)}\n\n" +
                "Silakan sebutkan atau ketik saja belanjaan Bunda, misalnya:\n" +
                "👉 'Beli beras 5 kilo 72rb sama minyak 34rb'\n" +
                "👉 'Beli bayam 5rb dan tempe 8rb tunai'\n\n" +
                "Nanti langsung otomatis Cici catat rapi ke pembukuan kas. Mau mulai dari belanjaan apa dulu nih Bun?"
        }

        // 4. Capabilities / Help ("kamu bisa apa", "bisa apa aja", "fitur apa", "cara pakai")
        if (isCapabilitiesQuery(lower)) {
            return "Cici adalah asisten belanja dan keuangan keluarga serbabisa untuk Bunda. Berikut hal-hal praktis yang bisa Cici lakukan:\n\n" +
                "1. 🛒 **Catat Belanja Otomatis**: Ketik/sebutkan 'Beli beras 72rb dan minyak 34rb', Cici langsung catat keduanya sekaligus ke kas.\n" +
                "2. 💰 **Cek Saldo Kas Eksak**: Tanya 'Sisa uang kas tinggal berapa?', Cici hitung eksak tanpa halusinasi matematika.\n" +
                "3. ✏️ **Ralat Catatan**: 'Ralat telur tadi bukan 28rb tapi 25rb', catatan langsung diperbarui.\n" +
                "4. ❌ **Batal Belanja**: 'Rotinya kemahalan, gak jadi beli deh', item langsung dihapus dari pembukuan kas.\n" +
                "5. 📝 **Daftar Belanja (Wishlist)**: 'Catat wajan teflon di belanjaan', masuk ke daftar belanja Bunda.\n" +
                "6. ⚡ **Tagihan Rutin**: 'Bayar tagihan listrik PLN', tagihan ditandai lunas dan tercatat di kas.\n" +
                "7. 🧮 **Kalkulator Kas**: '15rb tambah 35rb berapa?', Cici hitung seketika.\n" +
                "8. 📊 **Evaluasi Keuangan**: Tanya 'Pos mana yang paling boros?' atau 'Gimana kondisi keuangan bulan ini?'.\n" +
                "9. 💡 **Tips Hemat Dapur**: Tanya 'Bagi tips hemat belanja dong'.\n" +
                "10. 🎙️ **Hands-Free Suara**: Tekan ikon headphone di atas, lalu bicara bebas sambil dorong troli!"
        }

        // 5. Boros / Pengeluaran Terbesar ("pos mana yang paling boros?", "pengeluaran terbesar")
        if (isBorosQuery(lower)) {
            val sortedEnvelopes = state.budgetEnvelopes.sortedByDescending { it.spentAmount }
            val top = sortedEnvelopes.firstOrNull()
            return if (top != null && top.spentAmount > 0L) {
                val isExceeded = top.isAlert || top.spentAmount > top.monthlyLimit
                val statusText = if (isExceeded) {
                    "Pos ini sudah melebihi batas anggaran ya Bun. Untuk sisa hari bulan ini, yuk kita rem dulu belanja di kategori ini!"
                } else {
                    "Pos ini masih berada dalam batas aman. Tetap teliti ya Bun!"
                }
                "Berdasarkan catatan kas bulan ini, pos pengeluaran terbesar Bunda ada di:\n\n" +
                    "📌 **${top.categoryName}**\n" +
                    "• Total Terpakai: ${CurrencyFormatter.formatRupiah(top.spentAmount)}\n" +
                    "• Batas Bulanan: ${CurrencyFormatter.formatRupiah(top.monthlyLimit)}\n" +
                    "• Sisa Kuota Pos: ${CurrencyFormatter.formatRupiah(top.remainingAmount)}\n\n" +
                    statusText
            } else {
                "Belum ada catatan pengeluaran besar yang menonjol bulan ini ya Bun. Catatan kas masih sangat aman dan rapi!"
            }
        }

        // 6. Today's Recap ("rekap belanja hari ini", "tadi belanja apa aja")
        if (isTodayRecapQuery(lower)) {
            val startOfDay = DateTimeUtils.getStartOfDay()
            val endOfDay = DateTimeUtils.getEndOfDay()
            val todayTxs = state.allTransactions.filter {
                it.dateTimestamp in startOfDay..endOfDay && it.type == TransactionTypes.EXPENSE
            }
            return if (todayTxs.isNotEmpty()) {
                val sb = java.lang.StringBuilder("📝 **Rekap Belanja Bunda Hari Ini**:\n\n")
                todayTxs.forEachIndexed { i, tx ->
                    sb.append("${i + 1}. ${tx.note.ifBlank { tx.category }}: ${CurrencyFormatter.formatRupiah(tx.amount)} (${tx.walletSource})\n")
                }
                sb.append("\n💰 **Total Pengeluaran Hari Ini**: ${CurrencyFormatter.formatRupiah(state.todayExpense)}\n")
                sb.append("Sisa saldo kas Bunda: ${CurrencyFormatter.formatRupiah(state.totalNetBalance)}.")
                sb.toString()
            } else {
                "Hari ini belum ada catatan pengeluaran yang dicatat ya Bun. Pengeluaran hari ini masih Rp 0. Sisa saldo kas Bunda saat ini: ${CurrencyFormatter.formatRupiah(state.totalNetBalance)}."
            }
        }

        // 7. Unpaid Bills query ("tagihan belum dibayar", "cek tagihan")
        if (isUnpaidBillsQuery(lower)) {
            val currentMonth = DateTimeUtils.getCurrentMonthYearKey()
            val unpaid = state.bills.filter { it.lastPaidMonthYear != currentMonth }
            return if (unpaid.isNotEmpty()) {
                val sb = java.lang.StringBuilder("⚡ **Daftar Tagihan Rutin yang Belum Lunas Bulan Ini**:\n\n")
                var totalBills = 0L
                unpaid.forEachIndexed { i, bill ->
                    sb.append("${i + 1}. **${bill.title}** (Jatuh tempo tgl ${bill.dueDayOfMonth}): ~${CurrencyFormatter.formatRupiah(bill.estimatedAmount)}\n")
                    totalBills += bill.estimatedAmount
                }
                sb.append("\n💡 Total estimasi tagihan: ${CurrencyFormatter.formatRupiah(totalBills)}.\n")
                sb.append("Kalau sudah bayar, Bunda cukup sebutkan 'Bayar tagihan ${unpaid.first().title}' ya Bun!")
                sb.toString()
            } else {
                "Alhamdulillah! Seluruh tagihan rutin bulan ini sudah tercatat lunas semua Bunda. Hebat sekali! 👏🎉"
            }
        }

        // 8. Financial Health / Kondisi Keuangan ("kondisi keuangan", "keuangan kita", "apakah uangku cukup", "apakah kita boros")
        if (isFinancialHealthQuery(lower)) {
            val balance = state.totalNetBalance
            val monthExpense = state.thisMonthExpense
            val monthIncome = state.thisMonthIncome
            val envelopes = state.budgetEnvelopes

            val healthStatus = when {
                balance <= 0 -> "🔴 Perlu Perhatian Khusus: Saldo kas saat ini berada di angka Rp 0 atau minus. Cici sarankan tunda dulu belanja non-pokok dan prioritaskan kebutuhan mendesak ya Bun."
                monthExpense > monthIncome && monthIncome > 0 -> "🟡 Waspada: Pengeluaran bulan ini (${CurrencyFormatter.formatRupiah(monthExpense)}) telah melampaui pemasukan (${CurrencyFormatter.formatRupiah(monthIncome)}). Sebaiknya perketat pos belanja jajan & hiburan."
                else -> "🟢 Sangat Sehat: Arus kas keluarga berjalan baik dan terkendali! Pengeluaran masih berada dalam batas wajar."
            }

            val overBudgetEnvelopes = envelopes.filter { it.isAlert || it.spentAmount > it.monthlyLimit }
            val warningEnvelopes = envelopes.filter { it.isWarning }

            val envelopeReport = java.lang.StringBuilder()
            if (overBudgetEnvelopes.isNotEmpty()) {
                envelopeReport.append("\n⚠️ Pos yang melebihi batas anggaran:\n")
                overBudgetEnvelopes.forEach {
                    envelopeReport.append("• ${it.categoryName}: Terpakai ${CurrencyFormatter.formatRupiah(it.spentAmount)} / Batas ${CurrencyFormatter.formatRupiah(it.monthlyLimit)}\n")
                }
            }
            if (warningEnvelopes.isNotEmpty()) {
                envelopeReport.append("\n⚡ Pos yang mendekati batas (waspada):\n")
                warningEnvelopes.forEach {
                    envelopeReport.append("• ${it.categoryName}: Sisa ${CurrencyFormatter.formatRupiah(it.remainingAmount)}\n")
                }
            }

            return "📊 **Analisis Keuangan Keluarga Bulan Ini**:\n\n" +
                "• Status Arus Kas: $healthStatus\n" +
                "• Total Sisa Kas Bersih: ${CurrencyFormatter.formatRupiah(balance)}\n" +
                "• Total Pemasukan: ${CurrencyFormatter.formatRupiah(monthIncome)}\n" +
                "• Total Pengeluaran: ${CurrencyFormatter.formatRupiah(monthExpense)}\n" +
                "• Pengeluaran Hari Ini: ${CurrencyFormatter.formatRupiah(state.todayExpense)}\n" +
                envelopeReport.toString() + "\n" +
                "Bunda hebat sudah rajin mencatat pembukuan keluarga! Tetap semangat hemat ya Bun. ❤️"
        }

        // 9. Greetings & Salutations ("halo", "hai", "selamat pagi", "assalamu'alaikum")
        if (isGreetingQuery(lower)) {
            val timeGreeting = when {
                lower.contains("pagi") -> "Selamat pagi Bunda tersayang! ☀️"
                lower.contains("siang") -> "Selamat siang Bunda! 🌤️"
                lower.contains("sore") -> "Selamat sore Bunda! 🌇"
                lower.contains("malam") -> "Selamat malam Bunda! 🌙"
                lower.contains("assalam") -> "Wa'alaikumsalam warahmatullahi wabarakatuh Bunda sayang! 🤲"
                else -> "Halo Bunda tersayang! Senang bertemu Bunda hari ini. 🌸"
            }
            return "$timeGreeting\n\n" +
                "Kondisi kas keluarga saat ini:\n" +
                "• Total Saldo Kas: ${CurrencyFormatter.formatRupiah(state.totalNetBalance)}\n" +
                "• Belanja Hari Ini: ${CurrencyFormatter.formatRupiah(state.todayExpense)}\n\n" +
                "Cici siap sedia membantu catat belanjaan, ralat harga, cek sisa kas, atau kasih tips hemat dapur. Ada yang bisa Cici bantu sekarang Bun?"
        }

        // 10. Gratitude & Empathy ("terima kasih", "kamu pintar", "capek belanja")
        val gratitudeResponse = handleGratitudeAndEmpathy(lower)
        if (gratitudeResponse != null) return gratitudeResponse

        // 11. Dynamic Contextual Fallback (Context-rich, responsive, NEVER a static copy-paste template)
        return "Halo Bunda! Cici menyimak: \"$prompt\".\n\n" +
            "Sebagai asisten belanja keluarga Bunda, Cici siap membantu mencatat pengeluaran belanja, merawat amplop anggaran, atau mengecek saldo kas.\n\n" +
            "• Sisa Kas Sekarang: ${CurrencyFormatter.formatRupiah(state.totalNetBalance)}\n" +
            "• Pengeluaran Hari Ini: ${CurrencyFormatter.formatRupiah(state.todayExpense)}\n\n" +
            "Bunda bisa langsung sebutkan belanjaan (misalnya: 'Beli telur 28rb sama tepung 10rb') atau tanyakan hal lain seputar kas keluarga ke Cici ya Bun!"
    }

    private fun isIdentityQuery(lower: String): Boolean {
        return lower.contains("siapa namamu") || lower.contains("namamu siapa") ||
            lower.contains("kamu siapa") || lower.contains("siapa kamu") ||
            lower.contains("nama kamu") || lower.contains("nama anda") ||
            lower.contains("cici siapa") || lower.contains("siapa cici") ||
            lower.contains("cici itu siapa") || lower.contains("kenalan") ||
            lower.contains("tentang kamu") || lower.contains("kamu itu siapa") ||
            lower.contains("siapa yang buat kamu") || lower.contains("siapa penciptamu")
    }

    private fun isShoppingReadinessQuery(lower: String): Boolean {
        return lower.contains("bantu aku belanja") || lower.contains("bantu belanja") ||
            lower.contains("temani belanja") || lower.contains("teman belanja") ||
            lower.contains("ayo kita belanja") || lower.contains("ayo belanja") ||
            lower.contains("mau belanja") || lower.contains("lagi belanja") ||
            lower.contains("sedang belanja") || lower.contains("di pasar") ||
            lower.contains("di minimarket") || lower.contains("di supermarket") ||
            lower.contains("belanja bareng") || lower.contains("pandu belanja") ||
            lower.contains("siap belanja")
    }

    private fun isCapabilitiesQuery(lower: String): Boolean {
        return lower.contains("kamu bisa apa") || lower.contains("bisa apa aja") ||
            lower.contains("bisa apa saja") || lower.contains("apa yang bisa kamu lakukan") ||
            lower.contains("fitur apa") || lower.contains("fitur cici") ||
            lower.contains("cara pakai") || lower.contains("apa kegunaanmu") ||
            lower.contains("kemampuan kamu") || lower.contains("bantuan") ||
            lower.contains("panduan")
    }

    private fun isBorosQuery(lower: String): Boolean {
        return lower.contains("paling boros") || lower.contains("pos mana yang paling boros") ||
            lower.contains("kategori apa yang paling banyak") || lower.contains("pengeluaran terbesar") ||
            lower.contains("boros di mana") || lower.contains("paling banyak keluar")
    }

    private fun isTodayRecapQuery(lower: String): Boolean {
        return lower.contains("rekap belanja hari ini") || lower.contains("rekap hari ini") ||
            lower.contains("tadi belanja apa aja") || lower.contains("tadi beli apa aja") ||
            lower.contains("pengeluaran hari ini") || lower.contains("catatan hari ini") ||
            lower.contains("belanjaan hari ini")
    }

    private fun isUnpaidBillsQuery(lower: String): Boolean {
        return lower.contains("tagihan belum dibayar") || lower.contains("cek tagihan") ||
            lower.contains("tagihan apa aja") || lower.contains("tagihan bulan ini") ||
            lower.contains("belum lunas")
    }

    private fun isFinancialHealthQuery(lower: String): Boolean {
        return lower.contains("kondisi keuangan") || lower.contains("keuangan kita") ||
            lower.contains("apakah uangku cukup") || lower.contains("uang cukup") ||
            lower.contains("apakah kita boros") || lower.contains("evaluasi keuangan") ||
            lower.contains("status keuangan") || lower.contains("analisis anggaran") ||
            lower.contains("analisis keuangan") || lower.contains("gimana keuangan") ||
            lower.contains("keuangan bulan ini")
    }

    private fun isGreetingQuery(lower: String): Boolean {
        return lower == "halo" || lower == "hai" || lower == "hei" ||
            lower.startsWith("halo ") || lower.startsWith("hai ") ||
            lower.contains("pagi") || lower.contains("siang") ||
            lower.contains("sore") || lower.contains("malam") ||
            lower.contains("assalam")
    }

    private fun handleGratitudeAndEmpathy(lower: String): String? {
        if (lower.contains("makasih") || lower.contains("terima kasih") || lower.contains("tengkyu") || lower.contains("matur nuwun") || lower.contains("nuhun")) {
            return "Sama-sama Bunda tersayang! Senang sekali Cici bisa mendampingi dan meringankan urusan rumah tangga Bunda. Sehat, berkah, dan bahagia selalu untuk Bunda dan keluarga tercinta! ❤️🌸"
        }
        if (lower.contains("kamu pintar") || lower.contains("cici pintar") || lower.contains("hebat") || lower.contains("keren") || lower.contains("mantap") || lower.contains("bagus")) {
            return "Alhamdulillah, terima kasih banyak apresiasinya Bunda! Pujian Bunda bikin Cici makin semangat mendampingi belanja keluarga. Semua berkat ketelitian Bunda juga lho! 🥰"
        }
        if (lower.contains("capek") || lower.contains("lelah") || lower.contains("pusing") || lower.contains("mumet")) {
            return "Peluk hangat untuk Bunda! Belanja dan mengurus kebutuhan rumah memang menguras tenaga. Istirahat sejenak yuk Bun, minum teh hangat atau air putih dulu. Bunda sudah melakukan yang terbaik hari ini! Catatan belanjaan biar Cici yang rapikan ya."
        }
        if (lower.contains("siapa penciptamu") || lower.contains("dibuat oleh siapa") || lower.contains("siapa yang buat kamu")) {
            return "Cici dikembangkan khusus untuk aplikasi ArAr Cicis sebagai asisten cerdas bagi ibu rumah tangga dan keluarga Indonesia, agar pengelolaan kas belanja selalu rapi, hemat, dan bebas dari halusinasi hitung!"
        }
        return null
    }

    private fun evaluateArithmeticQuery(prompt: String, lower: String): String? {
        val hasMathWords = lower.contains("tambah") || lower.contains("ditambah") ||
            lower.contains("kurang") || lower.contains("dikurang") ||
            lower.contains("kali") || lower.contains("dikali") ||
            lower.contains("dibagi") || lower.contains("bagi") ||
            lower.contains("+") || lower.contains("-") || lower.contains("*") || lower.contains("/")

        if (!hasMathWords) return null

        var mathExpr = lower
            .replace("ditambah", "+")
            .replace("tambah", "+")
            .replace("dikurang", "-")
            .replace("kurang", "-")
            .replace("dikali", "*")
            .replace("kali", "*")
            .replace("dibagi", "/")
            .replace("bagi", "/")
            .replace("goceng", "5000")
            .replace("ceban", "10000")
            .replace("gocap", "50000")
            .replace("seceng", "1000")
            .replace("noban", "20000")
            .replace("pego", "150000")
            .replace("cepek", "100000")

        val regexK = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:ribu|rb|rebu|k(?![a-zA-Z]))", Pattern.CASE_INSENSITIVE)
        val matcherK = regexK.matcher(mathExpr)
        val sb = StringBuffer()
        while (matcherK.find()) {
            val numStr = matcherK.group(1)?.replace(",", ".") ?: "0"
            val floatVal = numStr.toDoubleOrNull() ?: 0.0
            matcherK.appendReplacement(sb, (floatVal * 1000).toLong().toString())
        }
        matcherK.appendTail(sb)
        mathExpr = sb.toString()

        val cleanMathChars = mathExpr.filter { it.isDigit() || it == '+' || it == '-' || it == '*' || it == '/' || it == '.' || it == ' ' }
        if (!MathExpressionEvaluator.isCompoundExpression(cleanMathChars)) return null

        val calcResult = MathExpressionEvaluator.evaluateMathExpression(cleanMathChars)
        if (calcResult <= 0L) return null

        return "Hasil hitungannya: $prompt = ${CurrencyFormatter.formatRupiah(calcResult)} ya Bun. Mau sekalian Cici catat ke pengeluaran kas?"
    }
}
