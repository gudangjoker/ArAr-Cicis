package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ai.model.ChatMessage
import com.example.ai.model.DownloadStatus
import com.example.ai.voice.CiciVoiceService
import com.example.ai.voice.VoiceAssistantManager
import com.example.ui.dialog.ModelDownloadDialog
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MintLight
import com.example.ui.viewmodel.DashboardUiState
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.CurrencyFormatter
import kotlinx.coroutines.launch

@Composable
fun SiCiciChatScreen(
    viewModel: FinanceViewModel,
    uiState: DashboardUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isThinking by viewModel.isCiciThinking.collectAsStateWithLifecycle()
    val isTtsEnabled by viewModel.isTtsVoiceEnabled.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.modelDownloadProgress.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()

    var showModelDownloadDialog by remember { mutableStateOf(false) }
    var inputPrompt by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Voice assistant manager instance
    val voiceManager = remember {
        VoiceAssistantManager(context) { recognizedText ->
            if (recognizedText.isNotBlank()) {
                inputPrompt = recognizedText
                viewModel.askSiCici(recognizedText) { responseSpeech ->
                    if (viewModel.isTtsVoiceEnabled.value) {
                        CiciVoiceService.speak(context.applicationContext, responseSpeech)
                    }
                }
            }
        }
    }

    val isListening by voiceManager.isListening.collectAsStateWithLifecycle()
    val isSpeaking by voiceManager.isSpeaking.collectAsStateWithLifecycle()
    val isStandbyActive by voiceManager.isStandbyActive.collectAsStateWithLifecycle()

    // Clean up voice manager when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            voiceManager.destroy()
        }
    }

    // Audio & Notification permissions launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val audioGranted = perms[Manifest.permission.RECORD_AUDIO] == true
        if (audioGranted) {
            try {
                voiceManager.startSingleListen()
            } catch (t: Throwable) {
                Toast.makeText(context, "Asisten suara siap digunakan di aplikasi", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Izin mikrofon diperlukan untuk perintah suara Si Cici", Toast.LENGTH_SHORT).show()
        }
    }

    val hasAudioPermission = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    val requestPermissionsIfNeeded = { onGranted: () -> Unit ->
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val ungranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (ungranted.isEmpty()) {
            onGranted()
        } else {
            permissionsLauncher.launch(ungranted.toTypedArray())
        }
    }

    val startVoiceInput = {
        requestPermissionsIfNeeded {
            try {
                if (isListening) {
                    voiceManager.stopStandby()
                } else {
                    voiceManager.startSingleListen()
                }
            } catch (t: Throwable) {
                Toast.makeText(context, "Gagal memulai mikrofon: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val toggleStandbyMode = {
        requestPermissionsIfNeeded {
            try {
                voiceManager.toggleStandby()
            } catch (t: Throwable) {
                Log.e("SiCiciChatScreen", "Error toggling standby mode: ${t.message}", t)
                Toast.makeText(context, "Mode suara dapat digunakan langsung di aplikasi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Auto-scroll to latest message
    LaunchedEffect(chatMessages.size, isThinking) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    val quickSuggestions = listOf(
        "💰 Sisa saldo kas?",
        "🛒 Beli beras 72rb & minyak 34rb",
        "🥬 Beli bayam 10rb tunai",
        "❌ Rotinya kemahalan batal beli",
        "✏️ Ralat telur tadi jadi 25rb",
        "📝 Catat wajan di belanjaan",
        "⚡ Tagihan listrik bulan ini",
        "💡 Tips hemat dapur"
    )

    if (showModelDownloadDialog) {
        ModelDownloadDialog(
            viewModel = viewModel,
            onDismiss = { showModelDownloadDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .testTag("sicici_chat_screen")
    ) {
        // 1. Top Header: Si Cici Avatar, Title, Status & Actions
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_arar_cicis_logo),
                                contentDescription = "Avatar Si Cici",
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, EmeraldPrimary, CircleShape)
                            )
                            // AI Active green pulse dot
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        if (downloadProgress.status == DownloadStatus.DOWNLOADED) Color(0xFF00C853) else Color(0xFFFFB300),
                                        CircleShape
                                    )
                                    .border(1.5.dp, Color.White, CircleShape)
                            )
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Si Cici",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EmeraldPrimary
                                )
                                // Interactive Model Download / Engine Badge
                                Surface(
                                    color = when (downloadProgress.status) {
                                        DownloadStatus.DOWNLOADED -> Color(0xFF2E7D32).copy(alpha = 0.12f)
                                        DownloadStatus.DOWNLOADING -> Color(0xFF1976D2).copy(alpha = 0.12f)
                                        else -> EmeraldPrimary.copy(alpha = 0.12f)
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.clickable { showModelDownloadDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = when (downloadProgress.status) {
                                                DownloadStatus.DOWNLOADED -> Icons.Default.CheckCircle
                                                DownloadStatus.DOWNLOADING -> Icons.Default.CloudDownload
                                                else -> Icons.Default.AutoAwesome
                                            },
                                            contentDescription = null,
                                            tint = when (downloadProgress.status) {
                                                DownloadStatus.DOWNLOADED -> Color(0xFF2E7D32)
                                                DownloadStatus.DOWNLOADING -> Color(0xFF1976D2)
                                                else -> EmeraldPrimary
                                            },
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = when (downloadProgress.status) {
                                                DownloadStatus.DOWNLOADED -> "${selectedModel.family.name} Offline"
                                                DownloadStatus.DOWNLOADING -> "Unduh ${downloadProgress.progressPercent}%"
                                                else -> "Unduh Model AI"
                                            },
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (downloadProgress.status) {
                                                DownloadStatus.DOWNLOADED -> Color(0xFF2E7D32)
                                                DownloadStatus.DOWNLOADING -> Color(0xFF1976D2)
                                                else -> EmeraldPrimary
                                            }
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (downloadProgress.status == DownloadStatus.DOWNLOADED) "Mode 100% Offline Aktif" else "Klik badge untuk unduh SLM",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Header Action Buttons: Standby Mode, TTS Voice Toggle & Clear Chat
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Hands-free shopping standby mode
                        IconButton(
                            onClick = { toggleStandbyMode() },
                            modifier = Modifier.size(36.dp).testTag("btn_toggle_standby")
                        ) {
                            Icon(
                                imageVector = if (isStandbyActive) Icons.Default.Hearing else Icons.Default.HeadsetMic,
                                contentDescription = if (isStandbyActive) "Matikan Mode Siaga Belanja" else "Mode Siaga Belanja Hands-Free",
                                tint = if (isStandbyActive) Color(0xFF00C853) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.toggleTtsVoice() },
                            modifier = Modifier.size(36.dp).testTag("btn_toggle_tts")
                        ) {
                            Icon(
                                imageVector = if (isTtsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = "Toggle Suara TTS",
                                tint = if (isTtsEnabled) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearChatHistory() },
                            modifier = Modifier.size(36.dp).testTag("btn_clear_chat")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Bersihkan Chat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Balance Live Ticker Bar
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EmeraldPrimary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💰 Kas Sekarang:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.formatRupiah(uiState.totalNetBalance),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = EmeraldPrimary
                    )
                }

                // Hands-free Mode Standby Active Notification Banner
                AnimatedVisibility(
                    visible = isStandbyActive,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF81C784)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Hearing,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "🎙️ Mode Siaga Belanja Aktif (Hands-Free)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1B5E20)
                                    )
                                    Text(
                                        text = "Si Cici tetap mendengar di latar belakang walau app di-minimize",
                                        fontSize = 10.sp,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                            TextButton(
                                onClick = { voiceManager.stopStandby() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = "Matikan",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD32F2F)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Chat Conversation List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(chatMessages, key = { it.id }) { msg ->
                ChatBubbleItem(message = msg)
            }

            if (isThinking) {
                item {
                    CiciThinkingIndicator()
                }
            }
        }

        // 3. Quick Suggestions Chips (Horizontal Carousel)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickSuggestions.forEach { suggestion ->
                FilterChip(
                    selected = false,
                    onClick = {
                        val promptText = suggestion.substringAfter(" ").trim()
                        viewModel.askSiCici(promptText) { replySpeech ->
                            if (isTtsEnabled) voiceManager.speak(replySpeech)
                        }
                    },
                    label = { Text(suggestion, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        // 4. Voice Listening Wave Indicator (visible while listening)
        AnimatedVisibility(
            visible = isListening,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EmeraldPrimary.copy(alpha = 0.12f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Mendengarkan suara Bunda... Sebutkan instruksi atau belanjaan",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary
                )
            }
        }

        // 5. Input Bar: Mic Button, TextField, Send Button
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mic Button
                val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
                val micScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = if (isListening) 1.25f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "mic_scale"
                )

                IconButton(
                    onClick = startVoiceInput,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isListening) Color(0xFFD32F2F) else EmeraldPrimary.copy(alpha = 0.12f),
                        contentColor = if (isListening) Color.White else EmeraldPrimary
                    ),
                    modifier = Modifier
                        .size(46.dp)
                        .scale(micScale)
                        .testTag("btn_mic_speech")
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Bicara ke Si Cici",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Text Input
                OutlinedTextField(
                    value = inputPrompt,
                    onValueChange = { inputPrompt = it },
                    placeholder = { Text("Tanya Cici atau catat kas...", fontSize = 13.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_cici_prompt"),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    singleLine = true
                )

                // Send Button
                IconButton(
                    onClick = {
                        val promptToSend = inputPrompt.trim()
                        if (promptToSend.isNotBlank()) {
                            inputPrompt = ""
                            viewModel.askSiCici(promptToSend) { replySpeech ->
                                if (isTtsEnabled) voiceManager.speak(replySpeech)
                            }
                        }
                    },
                    enabled = inputPrompt.isNotBlank() && !isThinking,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = Color.White,
                        disabledContainerColor = EmeraldPrimary.copy(alpha = 0.3f),
                        disabledContentColor = Color.White.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("btn_send_cici_prompt")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Kirim",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubbleItem(message: ChatMessage) {
    val isUser = message.isUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Image(
                painter = painterResource(id = R.drawable.ic_arar_cicis_logo),
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 8.dp, top = 2.dp)
                    .size(28.dp)
                    .clip(CircleShape)
            )
        }

        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = if (isUser) 2.dp else 1.dp,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )

                    // Engine Badge (e.g. 100% Offline Qwen / Gemma)
                    if (!isUser && message.engineBadge != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = message.engineBadge,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary
                        )
                    }
                }
            }

            // Real Executed Actions (Multiple Room / SQLite CRUD executions)
            val actionResults = message.allActionResults
            if (actionResults.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    actionResults.forEach { actionResult ->
                        ActionExecutionCard(result = actionResult)
                    }
                }
            }
        }
    }
}

@Composable
fun ActionExecutionCard(result: com.example.ai.model.CiciActionResult) {
    val (cardColor, borderColor, iconColor, icon) = when {
        result.isDeleted -> Quadruple(
            Color(0xFFFFEBEE),
            Color(0xFFE57373),
            Color(0xFFC62828),
            Icons.Default.Delete
        )
        result.isUpdated -> Quadruple(
            Color(0xFFE3F2FD),
            Color(0xFF90CAF9),
            Color(0xFF1565C0),
            Icons.Default.Edit
        )
        result.actionType == com.example.ai.model.CiciActionType.CHECK_BALANCE -> Quadruple(
            EmeraldPrimary.copy(alpha = 0.08f),
            EmeraldPrimary.copy(alpha = 0.3f),
            EmeraldPrimary,
            Icons.Default.AccountBalanceWallet
        )
        result.actionType == com.example.ai.model.CiciActionType.ADD_WISHLIST -> Quadruple(
            Color(0xFFFFF3E0),
            Color(0xFFFFB74D),
            Color(0xFFE65100),
            Icons.Default.CheckCircle
        )
        else -> Quadruple(
            EmeraldPrimary.copy(alpha = 0.08f),
            EmeraldPrimary.copy(alpha = 0.25f),
            EmeraldPrimary,
            Icons.Default.CheckCircle
        )
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.padding(start = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = result.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = iconColor
                )
                Text(
                    text = result.details,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun CiciThinkingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_arar_cicis_logo),
            contentDescription = null,
            modifier = Modifier
                .padding(end = 8.dp)
                .size(28.dp)
                .clip(CircleShape)
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = EmeraldPrimary
                )
                Text(
                    text = "Si Cici sedang berpikir...",
                    fontSize = 12.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
