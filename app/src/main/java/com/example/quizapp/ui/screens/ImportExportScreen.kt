package com.example.quizapp.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.data.model.AppLanguage
import com.example.quizapp.data.model.StudyModule
import com.example.quizapp.data.repository.QuizRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(
    modules: List<StudyModule>,
    activeLanguage: AppLanguage,
    repository: QuizRepository,
    initialTab: Int = 0,
    initialModuleId: String? = null,
    onBack: () -> Unit,
    onModuleImported: (StudyModule) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }

    // Import State
    var rawInputText by remember { mutableStateOf("") }
    var importTitle by remember { mutableStateOf("") }
    var importCategory by remember { mutableStateOf("Импорт") }
    var importStatusMessage by remember { mutableStateOf<String?>(null) }
    var parsedCardsCount by remember { mutableIntStateOf(0) }

    // Export State — user/imported modules ALWAYS listed first
    val orderedModules = remember(modules) {
        val custom = modules.filter { !it.id.startsWith("seed_") }
        val seed = modules.filter { it.id.startsWith("seed_") }
        custom + seed
    }

    var selectedModuleId by remember(initialModuleId, orderedModules) {
        mutableStateOf(
            if (initialModuleId != null && orderedModules.any { it.id == initialModuleId }) {
                initialModuleId
            } else {
                orderedModules.firstOrNull()?.id
            }
        )
    }
    var exportFormat by remember { mutableStateOf("JSON") } // "JSON" or "CSV / Текст"
    var isModulePickerOpen by remember { mutableStateOf(false) }
    var moduleSearchQuery by remember { mutableStateOf("") }

    val currentExportModule = orderedModules.find { it.id == selectedModuleId } ?: orderedModules.firstOrNull()
    val exportedText = remember(currentExportModule, exportFormat) {
        if (currentExportModule == null) ""
        else if (exportFormat == "JSON") {
            repository.exportModuleToJson(currentExportModule)
        } else {
            repository.exportModuleToCsv(currentExportModule)
        }
    }

    // File picker launcher for import
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                var pickedName = ""
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) pickedName = cursor.getString(idx).orEmpty()
                    }
                }
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).readText()
                }.orEmpty().removePrefix("\uFEFF")

                rawInputText = content
                if (importTitle.isBlank() && pickedName.isNotBlank()) {
                    importTitle = pickedName.substringBeforeLast(".").trim()
                }
                val lines = content.lines()
                val detected = lines.count { line ->
                    val t = line.trim()
                    t.contains("\t") || t.contains(" - ") || t.contains(" — ") || t.contains(" – ") || t.contains(";") || t.contains(": ") || t.contains("\"term\"")
                }
                parsedCardsCount = maxOf(detected, 1)
                importStatusMessage = "Файл загружен: найдено примерно $detected карточек"
            } catch (e: Exception) {
                importStatusMessage = "Ошибка чтения файла: ${e.message}"
            }
        }
    }

    // File saver launcher for export
    val saveExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            if (exportFormat == "JSON") "application/json" else "text/plain"
        )
    ) { uri: Uri? ->
        if (uri != null && exportedText.isNotBlank()) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(exportedText.toByteArray(StandardCharsets.UTF_8))
                }
                Toast.makeText(context, "Файл успешно сохранён!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка сохранения: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (selectedTab == 0) Icons.Default.FileUpload else Icons.Default.FileDownload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (selectedTab == 0) "Импорт карточек" else "Экспорт модулей",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("import_export_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Импорт из файла", fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.testTag("tab_import")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Экспорт модуля", fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.testTag("tab_export")
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedTab == 0) {
                    // --- IMPORT VIEW ---
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Загрузка из файла или вставка текста",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Поддерживаются JSON-файлы QuizApp, CSV, TSV (таблицы) или формат: 'Термин - Определение'",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedButton(
                                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("pick_file_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Выбрать файл (.json / .csv / .txt)", fontWeight = FontWeight.Bold)
                            }

                            importStatusMessage?.let { status ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = status,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Или вставьте текст карточек напрямую:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = rawInputText,
                                onValueChange = {
                                    rawInputText = it
                                    val lines = it.lines()
                                    val detected = lines.count { line ->
                                        val t = line.trim()
                                        t.contains("\t") || t.contains(" - ") || t.contains(" — ") || t.contains(" – ") || t.contains(";") || t.contains(": ") || t.contains("\"term\"")
                                    }
                                    parsedCardsCount = detected
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .testTag("import_raw_text_field"),
                                placeholder = {
                                    Text(
                                        "Пример:\nApple\tЯблоко\nBook\tКнига\nCat - Кот\nИли вставьте полный JSON...",
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Параметры нового модуля",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = importTitle,
                                onValueChange = { importTitle = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("import_title_input"),
                                label = { Text("Название модуля") },
                                placeholder = { Text("Импортированный набор слов") },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = importCategory,
                                onValueChange = { importCategory = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Категория") },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (rawInputText.isNotBlank()) {
                                        try {
                                            val trimmed = rawInputText.removePrefix("\uFEFF").trim()
                                            val importedMod = if (trimmed.startsWith("{") && trimmed.contains("\"cards\"")) {
                                                repository.importModuleFromJson(trimmed)
                                            } else {
                                                repository.importModuleFromTextOrCsv(
                                                    rawText = trimmed,
                                                    defaultTitle = importTitle.trim().ifBlank { "Импортированный модуль" }
                                                )
                                            }
                                            if (importedMod.cards.isEmpty()) {
                                                importStatusMessage = "Не удалось распознать карточки. Проверьте разделитель (табуляция, ' - ', ' — ', ';', ': ') или JSON."
                                                return@Button
                                            }
                                            val finalMod = importedMod.copy(
                                                title = importTitle.trim().ifBlank { importedMod.title },
                                                category = importCategory.trim().ifBlank { importedMod.category }
                                            )
                                            repository.saveModule(finalMod)
                                            onModuleImported(finalMod)
                                            Toast.makeText(context, "Модуль '${finalMod.title}' (${finalMod.cards.size} карт.) сохранён!", Toast.LENGTH_LONG).show()
                                        } catch (e: Exception) {
                                            importStatusMessage = "Ошибка парсинга: ${e.message}"
                                        }
                                    }
                                },
                                enabled = rawInputText.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("execute_import_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Импортировать в библиотеку", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                } else {
                    // --- EXPORT VIEW ---
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Выберите модуль для экспорта",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Module Selector Button opening searchable ModalBottomSheet
                            Surface(
                                onClick = {
                                    moduleSearchQuery = ""
                                    isModulePickerOpen = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                    .testTag("export_module_selector")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        if (currentExportModule != null) {
                                            val isCustom = !currentExportModule.id.startsWith("seed_")
                                            Text(
                                                text = if (isCustom) "Мой / импортированный модуль" else "Стандартный модуль",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isCustom) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = currentExportModule?.title ?: "Нет модулей",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${currentExportModule?.cards?.size ?: 0} карт.",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Выбрать модуль",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Формат экспорта:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                listOf("JSON", "CSV / Текст").forEach { format ->
                                    val isSelected = exportFormat == format
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { exportFormat = format }
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .padding(vertical = 10.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            text = format,
                                            textAlign = TextAlign.Center,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Предпросмотр данных:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                            ) {
                                Text(
                                    text = exportedText.take(1200) + if (exportedText.length > 1200) "\n..." else "",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .verticalScroll(rememberScrollState())
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("QuizApp Module", exportedText)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Скопировано в буфер обмена!", Toast.LENGTH_SHORT).show()
                                    },
                                    enabled = exportedText.isNotBlank(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("export_copy_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Копировать", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                Button(
                                    onClick = {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, exportedText)
                                            type = "text/plain"
                                        }
                                        val shareIntent = Intent.createChooser(sendIntent, "Поделиться модулем '${currentExportModule?.title}'")
                                        context.startActivity(shareIntent)
                                    },
                                    enabled = exportedText.isNotBlank(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("export_share_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Поделиться", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    val safeName = (currentExportModule?.title ?: "module")
                                        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                        .take(40)
                                        .ifBlank { "module" }
                                    val ext = if (exportFormat == "JSON") "json" else "txt"
                                    saveExportLauncher.launch("$safeName.$ext")
                                },
                                enabled = exportedText.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("export_save_file_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (exportFormat == "JSON") "Сохранить как файл .json" else "Сохранить как файл .txt",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Searchable ModalBottomSheet for picking any module (Custom/Imported first, then Standard)
    if (isModulePickerOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val filteredCustom = remember(orderedModules, moduleSearchQuery) {
            orderedModules.filter {
                !it.id.startsWith("seed_") &&
                    (moduleSearchQuery.isBlank() || it.title.contains(moduleSearchQuery, ignoreCase = true) ||
                        it.category.contains(moduleSearchQuery, ignoreCase = true))
            }
        }
        val filteredSeed = remember(orderedModules, moduleSearchQuery) {
            orderedModules.filter {
                it.id.startsWith("seed_") &&
                    (moduleSearchQuery.isBlank() || it.title.contains(moduleSearchQuery, ignoreCase = true) ||
                        it.category.contains(moduleSearchQuery, ignoreCase = true))
            }
        }

        ModalBottomSheet(
            onDismissRequest = { isModulePickerOpen = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Выберите модуль для экспорта",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = moduleSearchQuery,
                    onValueChange = { moduleSearchQuery = it },
                    placeholder = { Text("Поиск модуля...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (filteredCustom.isNotEmpty()) {
                        item {
                            Text(
                                text = "Мои и импортированные модули (${filteredCustom.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A),
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }
                        items(filteredCustom, key = { it.id }) { mod ->
                            ExportModulePickerRow(
                                module = mod,
                                isSelected = mod.id == currentExportModule?.id,
                                isCustom = true,
                                onClick = {
                                    selectedModuleId = mod.id
                                    isModulePickerOpen = false
                                }
                            )
                        }
                    }

                    if (filteredSeed.isNotEmpty()) {
                        if (filteredCustom.isNotEmpty()) {
                            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                        }
                        item {
                            Text(
                                text = "Стандартные модули (${filteredSeed.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }
                        items(filteredSeed, key = { it.id }) { mod ->
                            ExportModulePickerRow(
                                module = mod,
                                isSelected = mod.id == currentExportModule?.id,
                                isCustom = false,
                                onClick = {
                                    selectedModuleId = mod.id
                                    isModulePickerOpen = false
                                }
                            )
                        }
                    }

                    if (filteredCustom.isEmpty() && filteredSeed.isEmpty()) {
                        item {
                            Text(
                                text = "Модули не найдены",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportModulePickerRow(
    module: StudyModule,
    isSelected: Boolean,
    isCustom: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("export_picker_item_${module.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.title,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${module.category} • ${module.cards.size} карт." + if (isCustom) " • Свой" else "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Выбрано",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
