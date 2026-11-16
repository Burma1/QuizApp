package com.example.quizapp.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.quizapp.data.locale.QuizStrings
import com.example.quizapp.data.model.AiProfile
import com.example.quizapp.data.model.AiProvider
import com.example.quizapp.data.model.AppColorTheme
import com.example.quizapp.data.model.AppLanguage
import com.example.quizapp.data.model.AppThemeMode
import com.example.quizapp.data.model.DatabaseRestoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SettingsSubPage {
    ROOT,
    AI_MODE,
    DATABASE_BACKUP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    activeLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    colorTheme: AppColorTheme = AppColorTheme.SYSTEM,
    onColorThemeChange: (AppColorTheme) -> Unit = {},
    aiProfiles: List<AiProfile>,
    activeProfile: AiProfile,
    onSelectProfile: (String) -> Unit,
    onAddProfile: (name: String, provider: AiProvider, apiKey: String, modelId: String) -> Unit,
    onUpdateProfile: (AiProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onFetchModels: suspend (AiProvider, String) -> com.example.quizapp.data.ai.FetchModelsResult,
    databaseSummary: DatabaseRestoreResult = DatabaseRestoreResult(0, 0, 0, 0, 0, 0, 0, 0),
    onExportFullDatabase: () -> String = { "{}" },
    onImportFullDatabase: (String, Boolean) -> Result<DatabaseRestoreResult> = { _, _ ->
        Result.failure(IllegalStateException("Not wired"))
    },
    onOpenImportExport: () -> Unit = {},
    onBack: () -> Unit
) {
    var subPage by remember { mutableStateOf(SettingsSubPage.ROOT) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<AiProfile?>(null) }

    BackHandler(enabled = subPage != SettingsSubPage.ROOT) {
        subPage = SettingsSubPage.ROOT
    }

    AnimatedContent(
        targetState = subPage,
        transitionSpec = {
            if (targetState != SettingsSubPage.ROOT) {
                (slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(180)))
                    .togetherWith(slideOutHorizontally(tween(180)) { -it / 4 } + fadeOut(tween(150)))
            } else {
                (slideInHorizontally(tween(220)) { -it / 3 } + fadeIn(tween(180)))
                    .togetherWith(slideOutHorizontally(tween(180)) { it / 4 } + fadeOut(tween(150)))
            }
        },
        label = "settings_subscreen"
    ) { page ->
        when (page) {
            SettingsSubPage.ROOT -> {
                SettingsRootScreen(
                    activeLanguage = activeLanguage,
                    activeProfile = activeProfile,
                    databaseSummary = databaseSummary,
                    onOpenAiMode = { subPage = SettingsSubPage.AI_MODE },
                    onOpenLanguageDialog = { showLanguageDialog = true },
                    onOpenDatabaseBackup = { subPage = SettingsSubPage.DATABASE_BACKUP },
                    onBack = onBack
                )
            }
            SettingsSubPage.AI_MODE -> {
                AiModeSettingsSubScreen(
                    activeLanguage = activeLanguage,
                    aiProfiles = aiProfiles,
                    activeProfile = activeProfile,
                    onSelectProfile = onSelectProfile,
                    onAddClick = { showCreateDialog = true },
                    onEditClick = { editingProfile = it },
                    onDeleteClick = onDeleteProfile,
                    onBack = { subPage = SettingsSubPage.ROOT }
                )
            }
            SettingsSubPage.DATABASE_BACKUP -> {
                DatabaseBackupSubScreen(
                    activeLanguage = activeLanguage,
                    databaseSummary = databaseSummary,
                    onExportFullDatabase = onExportFullDatabase,
                    onImportFullDatabase = onImportFullDatabase,
                    onBack = { subPage = SettingsSubPage.ROOT }
                )
            }
        }
    }

    if (showLanguageDialog) {
        LanguagePickerDialog(
            activeLanguage = activeLanguage,
            onSelect = { lang ->
                onLanguageChange(lang)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showCreateDialog) {
        val nextNumber = (aiProfiles.maxOfOrNull { it.orderNumber } ?: 0) + 1
        AiProfileModalDialog(
            title = "Новый профиль ИИ #$nextNumber",
            orderNumber = nextNumber,
            initialName = "",
            initialProvider = AiProvider.GOOGLE_GEMINI,
            initialApiKey = "",
            initialModel = AiProvider.GOOGLE_GEMINI.defaultModel,
            onFetchModels = onFetchModels,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, provider, apiKey, modelId ->
                onAddProfile(name, provider, apiKey, modelId)
                showCreateDialog = false
            }
        )
    }

    editingProfile?.let { profileToEdit ->
        AiProfileModalDialog(
            title = "Профиль #${profileToEdit.orderNumber}",
            orderNumber = profileToEdit.orderNumber,
            initialName = profileToEdit.name,
            initialProvider = profileToEdit.provider,
            initialApiKey = profileToEdit.apiKey,
            initialModel = profileToEdit.modelId,
            onFetchModels = onFetchModels,
            onDismiss = { editingProfile = null },
            onConfirm = { name, provider, apiKey, modelId ->
                onUpdateProfile(
                    profileToEdit.copy(
                        name = name,
                        provider = provider,
                        apiKey = apiKey,
                        modelId = modelId
                    )
                )
                editingProfile = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsRootScreen(
    activeLanguage: AppLanguage,
    activeProfile: AiProfile,
    databaseSummary: DatabaseRestoreResult,
    onOpenAiMode: () -> Unit,
    onOpenLanguageDialog: () -> Unit,
    onOpenDatabaseBackup: () -> Unit,
    onBack: () -> Unit
) {
    val aiModeTitle = when (activeLanguage) {
        AppLanguage.RU -> "Режим ИИ"
        AppLanguage.EN -> "AI Mode"
        AppLanguage.ES -> "Modo IA"
    }
    val languageTitle = when (activeLanguage) {
        AppLanguage.RU -> "Язык"
        AppLanguage.EN -> "Language"
        AppLanguage.ES -> "Idioma"
    }
    val generalSectionTitle = when (activeLanguage) {
        AppLanguage.RU -> "Основные"
        AppLanguage.EN -> "General"
        AppLanguage.ES -> "General"
    }
    val appSectionTitle = when (activeLanguage) {
        AppLanguage.RU -> "База данных и приложение"
        AppLanguage.EN -> "Database & App"
        AppLanguage.ES -> "Base de datos y app"
    }
    val backupTitle = when (activeLanguage) {
        AppLanguage.RU -> "Полный импорт и экспорт базы"
        AppLanguage.EN -> "Full Database Backup & Restore"
        AppLanguage.ES -> "Copia completa de la base de datos"
    }
    val backupSubtitle = when (activeLanguage) {
        AppLanguage.RU -> "Карточки, прогресс изучения, папки и избранное (${databaseSummary.totalCards} карт.)"
        AppLanguage.EN -> "Cards, study progress, folders & starred (${databaseSummary.totalCards} cards)"
        AppLanguage.ES -> "Tarjetas, progreso, carpetas y favoritos (${databaseSummary.totalCards} tarj.)"
    }
    val aboutTitle = when (activeLanguage) {
        AppLanguage.RU -> "О приложении"
        AppLanguage.EN -> "About"
        AppLanguage.ES -> "Acerca de"
    }
    val aiSubtitle = buildString {
        append(activeProfile.name)
        append(" • ")
        append(activeProfile.modelId)
        if (activeProfile.apiKey.isBlank()) {
            append(
                when (activeLanguage) {
                    AppLanguage.RU -> " (ключ не задан)"
                    AppLanguage.EN -> " (no API key)"
                    AppLanguage.ES -> " (sin clave API)"
                }
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = QuizStrings.settingsTitle(activeLanguage),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: General (1st button = AI Mode, 2nd button = Language)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = generalSectionTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // 1. Режим ИИ (First button)
                        SettingsListRow(
                            icon = Icons.Default.Psychology,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = aiModeTitle,
                            subtitle = aiSubtitle,
                            onClick = onOpenAiMode,
                            showChevron = true,
                            testTag = "settings_ai_mode_row"
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 60.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // 2. Язык (Second button)
                        SettingsListRow(
                            icon = Icons.Default.Language,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = languageTitle,
                            subtitle = activeLanguage.displayName,
                            onClick = onOpenLanguageDialog,
                            showChevron = true,
                            testTag = "settings_language_row"
                        )
                    }
                }
            }

            // Section 2: Full Database Backup & App Info
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = appSectionTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsListRow(
                            icon = Icons.Default.Backup,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = backupTitle,
                            subtitle = backupSubtitle,
                            onClick = onOpenDatabaseBackup,
                            showChevron = true,
                            testTag = "settings_import_export_row"
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 60.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        SettingsListRow(
                            icon = Icons.Default.Info,
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            title = aboutTitle,
                            subtitle = "QuizApp • v${BuildConfig.VERSION_NAME}",
                            onClick = null,
                            showChevron = false,
                            testTag = "settings_about_row"
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DatabaseBackupSubScreen(
    activeLanguage: AppLanguage,
    databaseSummary: DatabaseRestoreResult,
    onExportFullDatabase: () -> String,
    onImportFullDatabase: (String, Boolean) -> Result<DatabaseRestoreResult>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var replaceExisting by remember { mutableStateOf(true) }
    var showPasteBox by remember { mutableStateOf(false) }
    var pastedJson by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorStatus by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

    val saveBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isBusy = true
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val json = onExportFullDatabase()
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        } ?: error("Не удалось открыть поток для записи файла")
                    }
                }
                isBusy = false
                result.onSuccess {
                    isErrorStatus = false
                    statusMessage = "Полная резервная копия базы успешно сохранена в файл: " +
                        "${databaseSummary.totalModules} мод., ${databaseSummary.totalCards} карт. " +
                        "(усвоено: ${databaseSummary.masteredCards}, изучено: ${databaseSummary.learningCards}, " +
                        "повторить: ${databaseSummary.needsReviewCards}, ⭐: ${databaseSummary.starredCards}, папок: ${databaseSummary.foldersCount})."
                }.onFailure { e ->
                    isErrorStatus = true
                    statusMessage = "Ошибка сохранения файла: ${e.localizedMessage ?: "неизвестная ошибка"}"
                }
            }
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isBusy = true
                val readResult = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                            it.readText()
                        } ?: error("Не удалось прочитать выбранный файл")
                    }
                }
                readResult.onSuccess { rawJson ->
                    val importRes = onImportFullDatabase(rawJson, replaceExisting)
                    isBusy = false
                    importRes.onSuccess { summary ->
                        isErrorStatus = false
                        statusMessage = "База успешно восстановлена! Модулей: ${summary.totalModules} " +
                            "(своих: ${summary.customModulesCount}), карточек: ${summary.totalCards} " +
                            "(усвоено: ${summary.masteredCards}, изучено: ${summary.learningCards}, " +
                            "требуют повторения: ${summary.needsReviewCards}, ⭐ избранных: ${summary.starredCards}, " +
                            "папок в библиотеке: ${summary.foldersCount})."
                    }.onFailure { err ->
                        isErrorStatus = true
                        statusMessage = err.message ?: "Ошибка восстановления базы"
                    }
                }.onFailure { err ->
                    isBusy = false
                    isErrorStatus = true
                    statusMessage = "Ошибка чтения файла: ${err.localizedMessage ?: "неизвестная ошибка"}"
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (activeLanguage) {
                            AppLanguage.RU -> "Резервная копия базы"
                            AppLanguage.EN -> "Database Backup"
                            AppLanguage.ES -> "Copia de base de datos"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("db_backup_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current database overview card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("db_summary_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Текущее состояние базы QuizApp",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "Полная резервная копия сохраняет и восстанавливает все созданные вами модули и карточки, прогресс изучения по всем карточкам (требуют повторения, изучено, усвоено + интервалы повторений), избранные карточки (⭐) и структуру/порядок папок в библиотеке.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DbStatBadge("Модулей: ${databaseSummary.totalModules} (своих: ${databaseSummary.customModulesCount})")
                        DbStatBadge("Всего карточек: ${databaseSummary.totalCards}")
                        DbStatBadge("Усвоено: ${databaseSummary.masteredCards}")
                        DbStatBadge("Изучено: ${databaseSummary.learningCards}")
                        DbStatBadge("Повторить: ${databaseSummary.needsReviewCards}")
                        DbStatBadge("⭐ Избранные: ${databaseSummary.starredCards}")
                        DbStatBadge("Папок: ${databaseSummary.foldersCount}")
                    }
                }
            }

            // Status message banner
            AnimatedVisibility(visible = statusMessage != null) {
                statusMessage?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isErrorStatus) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("db_backup_status_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isErrorStatus) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isErrorStatus) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (isErrorStatus) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Section 1: Export full DB
            Text(
                text = "Экспорт всей базы данных",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            saveBackupLauncher.launch("quizapp_full_backup.json")
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("export_full_db_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Сохранить всю базу в файл (.json)",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val json = onExportFullDatabase()
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_SUBJECT, "Резервная копия базы QuizApp")
                                    putExtra(Intent.EXTRA_TEXT, json)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Поделиться резервной копией"))
                            },
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("share_full_db_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Поделиться", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }

                        OutlinedButton(
                            onClick = {
                                val json = onExportFullDatabase()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("QuizApp Full Backup", json))
                                isErrorStatus = false
                                statusMessage = "JSON полной резервной копии скопирован в буфер обмена (${databaseSummary.totalCards} карточек)."
                            },
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("copy_full_db_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Скопировать", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // Section 2: Import / Restore full DB
            Text(
                text = "Импорт и восстановление базы",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Режим импорта",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Option 1: Replace
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { replaceExisting = true }
                            .padding(vertical = 4.dp)
                            .testTag("import_mode_replace"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = replaceExisting,
                            onClick = { replaceExisting = true }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Полное восстановление (заменить текущую базу)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Точно восстанавливает все модули, прогресс, избранное и порядок папок из копии",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Option 2: Merge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { replaceExisting = false }
                            .padding(vertical = 4.dp)
                            .testTag("import_mode_merge"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !replaceExisting,
                            onClick = { replaceExisting = false }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Объединить с текущей базой",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Сохраняет текущие модули и добавляет новые модули, папки и прогресс из файла",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = {
                            openBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("import_full_db_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Восстановить базу из файла (.json)",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = { showPasteBox = !showPasteBox },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("toggle_paste_db_json"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (showPasteBox) "Скрыть поле вставки JSON" else "Вставить JSON резервной копии из текста",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    AnimatedVisibility(visible = showPasteBox) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = pastedJson,
                                onValueChange = { pastedJson = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 220.dp)
                                    .testTag("import_full_db_text_input"),
                                label = { Text("JSON резервной копии базы QuizApp") },
                                placeholder = { Text("{\"backupFormat\":\"quizapp_full_db_v2\", ...}") },
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                shape = RoundedCornerShape(12.dp)
                            )

                            FilledTonalButton(
                                onClick = {
                                    val res = onImportFullDatabase(pastedJson, replaceExisting)
                                    res.onSuccess { summary ->
                                        isErrorStatus = false
                                        statusMessage = "База успешно восстановлена! Модулей: ${summary.totalModules} " +
                                            "(своих: ${summary.customModulesCount}), карточек: ${summary.totalCards} " +
                                            "(усвоено: ${summary.masteredCards}, изучено: ${summary.learningCards}, " +
                                            "повторить: ${summary.needsReviewCards}, ⭐: ${summary.starredCards}, папок: ${summary.foldersCount})."
                                        pastedJson = ""
                                        showPasteBox = false
                                    }.onFailure { err ->
                                        isErrorStatus = true
                                        statusMessage = err.message ?: "Ошибка импорта JSON базы"
                                    }
                                },
                                enabled = pastedJson.isNotBlank() && !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("import_full_db_text_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Restore,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Применить резервную копию",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DbStatBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun SettingsListRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    showChevron: Boolean,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showChevron) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun LanguagePickerDialog(
    activeLanguage: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    val dialogTitle = when (activeLanguage) {
        AppLanguage.RU -> "Язык приложения"
        AppLanguage.EN -> "App Language"
        AppLanguage.ES -> "Idioma de la app"
    }
    val cancelLabel = when (activeLanguage) {
        AppLanguage.RU -> "Отмена"
        AppLanguage.EN -> "Cancel"
        AppLanguage.ES -> "Cancelar"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = dialogTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                AppLanguage.entries.forEach { lang ->
                    val selected = lang == activeLanguage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(lang) }
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                            .testTag("lang_option_${lang.code}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onSelect(lang) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${lang.flagEmoji}  ${lang.displayName}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelLabel)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiModeSettingsSubScreen(
    activeLanguage: AppLanguage,
    aiProfiles: List<AiProfile>,
    activeProfile: AiProfile,
    onSelectProfile: (String) -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (AiProfile) -> Unit,
    onDeleteClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val titleText = when (activeLanguage) {
        AppLanguage.RU -> "Режим ИИ"
        AppLanguage.EN -> "AI Mode"
        AppLanguage.ES -> "Modo IA"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("ai_mode_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onAddClick,
                        modifier = Modifier.testTag("topbar_add_ai_profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = QuizStrings.addAiProfileButton(activeLanguage),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = QuizStrings.aiProfilesTitle(activeLanguage),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_profiles_card")
            ) {
                Column {
                    aiProfiles.forEachIndexed { idx, profile ->
                        val isSelected = profile.id == activeProfile.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectProfile(profile.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("ai_profile_item_${profile.orderNumber}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectProfile(profile.id) }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${profile.provider.displayName} • ${profile.modelId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (profile.apiKey.isNotBlank()) "API-ключ: ••••${profile.apiKey.takeLast(4)}" else "API-ключ не указан",
                                    fontSize = 11.sp,
                                    color = if (profile.apiKey.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            IconButton(
                                onClick = { onEditClick(profile) },
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("edit_profile_${profile.orderNumber}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Редактировать профиль",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { onDeleteClick(profile.id) },
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("delete_profile_${profile.orderNumber}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Удалить профиль",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (idx < aiProfiles.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 52.dp, end = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            FilledTonalButton(
                onClick = onAddClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("add_ai_profile_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = QuizStrings.addAiProfileButton(activeLanguage),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiProfileModalDialog(
    title: String,
    orderNumber: Int,
    initialName: String,
    initialProvider: AiProvider,
    initialApiKey: String,
    initialModel: String,
    onFetchModels: suspend (AiProvider, String) -> com.example.quizapp.data.ai.FetchModelsResult,
    onDismiss: () -> Unit,
    onConfirm: (name: String, provider: AiProvider, apiKey: String, modelId: String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var profileName by remember { mutableStateOf(initialName) }
    var selectedProvider by remember { mutableStateOf(initialProvider) }
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(initialModel) }
    var availableModels by remember { mutableStateOf(initialProvider.availableModels) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var loadStatusText by remember { mutableStateOf<String?>(null) }
    var isLoadError by remember { mutableStateOf(false) }
    var isModelDropdownOpen by remember { mutableStateOf(false) }

    LaunchedEffect(selectedProvider) {
        availableModels = selectedProvider.availableModels
        if (selectedModel !in availableModels) {
            selectedModel = selectedProvider.defaultModel
        }
        loadStatusText = "Доступно моделей: ${availableModels.size}"
        isLoadError = false
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 640.dp)
                .testTag("ai_profile_dialog"),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = providerAccentColor(selectedProvider),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$orderNumber",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input"),
                    label = { Text("Название профиля") },
                    placeholder = { Text("Например: Gemini Flash / Мой GPT") },
                    shape = RoundedCornerShape(12.dp),
                    colors = textFieldColors,
                    singleLine = true
                )

                Text(
                    text = "Провайдер ИИ",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiProvider.entries.forEach { provider ->
                        val isSelected = provider == selectedProvider
                        val pColor = providerAccentColor(provider)
                        Surface(
                            modifier = Modifier
                                .clickable {
                                    selectedProvider = provider
                                    selectedModel = provider.defaultModel
                                }
                                .testTag("dialog_provider_${provider.name}"),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) pColor else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) pColor else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = provider.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_api_key_input"),
                    label = { Text("API Ключ (${selectedProvider.displayName})") },
                    placeholder = { Text("Введите ваш API ключ...") },
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Показать/скрыть ключ",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = textFieldColors,
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Модель ИИ",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (loadStatusText != null) {
                            Text(
                                text = loadStatusText!!,
                                fontSize = 11.sp,
                                color = if (isLoadError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isLoadingModels = true
                                loadStatusText = "Загрузка списка моделей..."
                                isLoadError = false
                                val result = onFetchModels(selectedProvider, apiKey)
                                when (result) {
                                    is com.example.quizapp.data.ai.FetchModelsResult.Success -> {
                                        availableModels = result.models
                                        if (selectedModel !in result.models) {
                                            selectedModel = result.models.firstOrNull() ?: selectedProvider.defaultModel
                                        }
                                        loadStatusText = "Найдено моделей: ${result.count}"
                                        isLoadError = false
                                    }
                                    is com.example.quizapp.data.ai.FetchModelsResult.Error -> {
                                        loadStatusText = result.message
                                        isLoadError = true
                                    }
                                }
                                isLoadingModels = false
                            }
                        },
                        enabled = !isLoadingModels,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("load_models_button")
                    ) {
                        if (isLoadingModels) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Загрузка...", fontSize = 12.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Загрузить модели", fontSize = 12.sp)
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isModelDropdownOpen = true }
                            .testTag("select_model_dropdown"),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedModel.ifBlank { selectedProvider.defaultModel },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Выбрать (${availableModels.size}) ▼",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = isModelDropdownOpen,
                        onDismissRequest = { isModelDropdownOpen = false },
                        modifier = Modifier.heightIn(max = 260.dp)
                    ) {
                        availableModels.forEach { modelOption ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = modelOption,
                                        fontWeight = if (modelOption == selectedModel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (modelOption == selectedModel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    selectedModel = modelOption
                                    isModelDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("cancel_profile_dialog_button")
                    ) {
                        Text("Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val finalName = profileName.trim().ifBlank {
                                "${selectedProvider.displayName} #$orderNumber"
                            }
                            onConfirm(
                                finalName,
                                selectedProvider,
                                apiKey.trim(),
                                selectedModel.trim().ifBlank { selectedProvider.defaultModel }
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("save_profile_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Сохранить", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

fun providerAccentColor(provider: AiProvider): Color = when (provider) {
    AiProvider.GOOGLE_GEMINI -> Color(0xFF1A73E8)
    AiProvider.OPENAI -> Color(0xFF10A37F)
    AiProvider.ANTHROPIC -> Color(0xFFD97706)
    AiProvider.DEEPSEEK -> Color(0xFF4F46E5)
    AiProvider.OPENROUTER -> Color(0xFF9333EA)
}
