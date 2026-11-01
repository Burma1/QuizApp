package com.example.quizapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.quizapp.data.locale.LocalStrings
import com.example.quizapp.data.locale.QuizStrings
import com.example.quizapp.data.model.AiProfile
import com.example.quizapp.data.model.AiProvider
import com.example.quizapp.data.model.AppColorTheme
import com.example.quizapp.data.model.AppLanguage
import com.example.quizapp.data.model.AppThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<AiProfile?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = QuizStrings.settingsTitle(activeLanguage),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Language Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = QuizStrings.languageLabel(activeLanguage),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppLanguage.entries.forEach { lang ->
                            val isSelected = lang == activeLanguage
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onLanguageChange(lang) }
                                    .testTag("lang_option_${lang.code}"),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = lang.displayName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. AI Profiles Card (Профили ИИ)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_profiles_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = Color(0xFF7C3AED),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = QuizStrings.aiProfilesTitle(activeLanguage),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Каждому профилю присваивается порядковый номер",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // List of AI Profiles
                    aiProfiles.forEach { profile ->
                        val isSelected = profile.id == activeProfile.id
                        val providerColor = providerAccentColor(profile.provider)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) providerColor.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) providerColor else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { onSelectProfile(profile.id) }
                                .padding(12.dp)
                                .testTag("ai_profile_item_${profile.orderNumber}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Circle with sequential profile number (Порядковый номер профиля)
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) providerColor else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.5.dp,
                                        if (isSelected) providerColor else MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${profile.orderNumber}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = profile.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = providerColor,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "Активен",
                                                    fontSize = 10.sp,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${profile.provider.displayName} • ${profile.modelId}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (profile.apiKey.isNotBlank()) "API-ключ: ••••${profile.apiKey.takeLast(4)}" else "API-ключ не указан",
                                        fontSize = 11.sp,
                                        color = if (profile.apiKey.isNotBlank()) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                    )
                                }

                                // Edit Profile Button
                                IconButton(
                                    onClick = { editingProfile = profile },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("edit_profile_${profile.orderNumber}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Редактировать профиль",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                // Delete Profile Button
                                IconButton(
                                    onClick = { onDeleteProfile(profile.id) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("delete_profile_${profile.orderNumber}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Удалить профиль",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // "Добавить профиль ИИ" Button
                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("add_ai_profile_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = QuizStrings.addAiProfileButton(activeLanguage),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
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
            title = "Редактировать профиль #${profileToEdit.orderNumber}",
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

    // When provider changes, reset to default catalog
    LaunchedEffect(selectedProvider) {
        availableModels = selectedProvider.availableModels
        if (selectedModel !in availableModels) {
            selectedModel = selectedProvider.defaultModel
        }
        loadStatusText = "Базовых моделей: ${availableModels.size}"
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
                // Header with sequential number circle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = providerAccentColor(selectedProvider),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$orderNumber",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
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

                // 1. Profile Name Input
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input"),
                    label = { Text("Название профиля") },
                    placeholder = { Text("Например: Рабочий Gemini / Мой GPT-4o") },
                    shape = RoundedCornerShape(12.dp),
                    colors = textFieldColors,
                    singleLine = true
                )

                // 2. AI Provider Selection
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

                // 3. API Key Input
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

                // 4. Load Models & Model Selection
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
                                color = if (isLoadError) MaterialTheme.colorScheme.error else Color(0xFF22C55E),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isLoadingModels = true
                                loadStatusText = "Подключение к серверу..."
                                isLoadError = false
                                val result = onFetchModels(selectedProvider, apiKey)
                                when (result) {
                                    is com.example.quizapp.data.ai.FetchModelsResult.Success -> {
                                        availableModels = result.models
                                        if (selectedModel !in result.models) {
                                            selectedModel = result.models.firstOrNull() ?: selectedProvider.defaultModel
                                        }
                                        loadStatusText = "✅ Найдено моделей: ${result.count}"
                                        isLoadError = false
                                    }
                                    is com.example.quizapp.data.ai.FetchModelsResult.Error -> {
                                        loadStatusText = "❌ ${result.message}"
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

                // Model Selector Dropdown Box
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
                                fontWeight = FontWeight.Bold,
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

                // Action Buttons
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
                        Text("Сохранить профиль", fontWeight = FontWeight.Bold, color = Color.White)
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
