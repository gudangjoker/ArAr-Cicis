package com.example.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CoralAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.util.CurrencyFormatter
import com.example.util.MathExpressionEvaluator

private val EmojiPresets = listOf(
    "🥬", "🛒", "🎒", "⚡", "🏠", "💊", "🤲", "☕",
    "🚗", "👶", "💄", "🎮", "🥩", "📱", "💰", "⛽", "🏥", "🎁", "📝"
)

@Composable
fun EditBudgetDialog(
    initialCategoryName: String = "",
    currentLimit: Long = 0L,
    initialIconEmoji: String = "💰",
    isNewCategory: Boolean = false,
    onDismissRequest: () -> Unit,
    onSaveCategory: (categoryName: String, limitExp: String, iconEmoji: String) -> Unit,
    onDeleteCategory: (() -> Unit)? = null
) {
    var categoryName by remember { mutableStateOf(initialCategoryName) }
    var iconEmoji by remember { mutableStateOf(initialIconEmoji) }
    var limitExp by remember { mutableStateOf(if (currentLimit > 0) currentLimit.toString() else "") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val calculatedLimit by remember(limitExp) {
        derivedStateOf {
            if (limitExp.isBlank()) 0L else MathExpressionEvaluator.evaluateMathExpression(limitExp)
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Hapus Pos Anggaran?") },
            text = { Text("Pos '$categoryName' akan dihapus dari daftar amplop. Transaksi riwayat belanja Bunda tetap aman.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteCategory?.invoke()
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralAccent)
                ) {
                    Text("Ya, Hapus Pos")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Batal")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("edit_budget_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = iconEmoji,
                    fontSize = 24.sp
                )
                Text(
                    text = if (isNewCategory) "Buat Pos Anggaran Baru" else "Atur Pos Anggaran",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Nama Pos Anggaran (Bebas diisi user!)
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text("Nama Pos Anggaran") },
                    placeholder = { Text("Contoh: Belanja Dapur, Uang Sekolah, dll") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("budget_category_name_input")
                )

                // 2. Pilih Ikon Emoji
                Text(
                    text = "Pilih Ikon Emoji:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EmojiPresets.forEach { emoji ->
                        val isSelected = iconEmoji == emoji
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) EmeraldPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { iconEmoji = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 18.sp)
                        }
                    }
                }

                // 3. Quick Nominal Presets
                Text(
                    text = "Batas Bulanan (Opsional):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "Rp 300 rb" to 300_000L,
                        "Rp 500 rb" to 500_000L,
                        "Rp 1 jt" to 1_000_000L,
                        "Rp 1,5 jt" to 1_500_000L,
                        "Rp 2 jt" to 2_000_000L,
                        "Rp 3 jt" to 3_000_000L
                    ).forEach { (label, value) ->
                        Surface(
                            onClick = { limitExp = value.toString() },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // 4. Batas Bulanan Field (Bisa Kalkulator)
                OutlinedTextField(
                    value = limitExp,
                    onValueChange = { limitExp = it },
                    label = { Text("Nominal Kuota (Bisa Kalkulator)") },
                    placeholder = { Text("Contoh: 1500000 atau 1200000+300000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("budget_limit_input")
                )

                if (calculatedLimit > 0) {
                    Text(
                        text = "= ${CurrencyFormatter.formatRupiah(calculatedLimit)} per bulan",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary
                    )
                }

                // 5. Tombol Hapus Pos (Jika Bukan Kategori Baru)
                if (onDeleteCategory != null && !isNewCategory) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = CoralAccent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_category_button")
                    ) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Hapus Pos Anggaran Ini", color = CoralAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (categoryName.isNotBlank()) {
                        onSaveCategory(categoryName.trim(), limitExp, iconEmoji)
                        onDismissRequest()
                    }
                },
                enabled = categoryName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier.testTag("confirm_budget_limit_btn")
            ) {
                Text(if (isNewCategory) "Buat Pos" else "Simpan Perubahan")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismissRequest) {
                Text("Batal")
            }
        }
    )
}
