package com.example.quizapp.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.data.locale.QuizStrings
import com.example.quizapp.data.model.AiProfile
import com.example.quizapp.data.model.AppLanguage
import com.example.quizapp.data.model.ChatMessage
import com.example.quizapp.data.model.Flashcard
import com.example.quizapp.data.model.StudyModule
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    messages: List<ChatMessage>,
    activeLanguage: AppLanguage,
    aiProfiles: List<AiProfile>,
    activeProfile: AiProfile,
    onSelectProfile: (String) -> Unit,
    onBack: () -> Unit,
    onSendMessage: suspend (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSaveGeneratedModule: (StudyModule) -> Unit,
    onClearHistory: () -> Unit = {},
    onOpenModule: (String) -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var savedCardsId by remember { mutableStateOf<String?>(null) }
    var isProfileMenuOpen by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val quickPrompts = when (activeLanguage) {
        AppLanguage.RU -> listOf(
            "Создай карточки: Фрукты (Apple - Яблоко, Banana - Банан)",
            "Создай карточки по теме Неправильные глаголы",
            "Создай карточки по теме Present Perfect",
            "Добавь в модуль Фрукты: Grape - Виноград, Peach - Персик",
            "Удали модуль Фрукты"
        )
        AppLanguage.EN -> listOf(
            "Create cards: Fruits (Apple - Red fruit, Banana - Yellow fruit)",
            "Create cards about Irregular verbs",
            "Create cards about Present Perfect",
            "Add to module Fruits: Grape - Small fruit",
            "Delete module Fruits"
        )
        AppLanguage.ES -> listOf(
            "Crea tarjetas: Frutas (Apple - Manzana, Banana - Plátano)",
            "Crea tarjetas sobre Verbos irregulares",
            "Crea tarjetas sobre Present Perfect",
            "Añade al módulo Frutas: Grape - Uva",
            "Elimina módulo Frutas"
        )
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val activeAccent = providerAccentColor(activeProfile.provider)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = Color(0xFF7C3AED),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = QuizStrings.aiTutor(activeLanguage),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onClearHistory,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("chat_reset_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Сбросить историю",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // Small circle with sequential profile number in the top-right corner
                    Box(modifier = Modifier.padding(end = 4.dp)) {
                        Surface(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable { isProfileMenuOpen = true }
                                .testTag("chat_profile_selector_button"),
                            shape = CircleShape,
                            color = activeAccent,
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${activeProfile.orderNumber}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isProfileMenuOpen,
                            onDismissRequest = { isProfileMenuOpen = false }
                        ) {
                            Text(
                                text = "Выбор профиля ИИ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                            HorizontalDivider()
                            aiProfiles.forEach { profile ->
                                val isCurrent = profile.id == activeProfile.id
                                val pColor = providerAccentColor(profile.provider)
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isCurrent) pColor else MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "${profile.orderNumber}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = profile.name,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${profile.provider.displayName} • ${profile.modelId}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (isCurrent) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = pColor,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        onSelectProfile(profile.id)
                                        isProfileMenuOpen = false
                                    },
                                    modifier = Modifier.testTag("chat_switch_profile_${profile.orderNumber}")
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Управление профилями ИИ...",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                onClick = {
                                    isProfileMenuOpen = false
                                    onOpenSettings()
                                }
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("chat_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
            // Quick Prompt Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickPrompts) { prompt ->
                    Surface(
                        modifier = Modifier.clickable {
                            inputText = prompt
                        },
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = prompt,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    if (msg.isUser) {
                        // User message (Right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 18.dp,
                                            topEnd = 18.dp,
                                            bottomStart = 18.dp,
                                            bottomEnd = 4.dp
                                        )
                                    )
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(14.dp)
                            ) {
                                MarkdownMessageContent(
                                    markdown = msg.text,
                                    textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    codeBackgroundColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f),
                                    codeTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    accentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else {
                        // AI message (Left) with Markdown design
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF7C3AED),
                                modifier = Modifier
                                    .size(32.dp)
                                    .padding(top = 4.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(0.92f),
                                shape = RoundedCornerShape(
                                    topStart = 4.dp,
                                    topEnd = 18.dp,
                                    bottomStart = 18.dp,
                                    bottomEnd = 18.dp
                                ),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    if (msg.isGenerating) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = Color(0xFF7C3AED),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "ИИ печатает ответ...",
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else if (msg.text.startsWith("⚠️")) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Ошибка подключения к ИИ",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            MarkdownMessageContent(
                                                markdown = msg.text,
                                                textColor = MaterialTheme.colorScheme.onSurface,
                                                codeBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                                codeTextColor = MaterialTheme.colorScheme.primary,
                                                accentColor = MaterialTheme.colorScheme.error
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Button(
                                                onClick = onOpenSettings,
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("chat_fix_settings_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Settings,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = Color.White
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Открыть Настройки ИИ", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    } else {
                                        MarkdownMessageContent(
                                            markdown = msg.text,
                                            textColor = MaterialTheme.colorScheme.onSurface,
                                            codeBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                            codeTextColor = MaterialTheme.colorScheme.primary,
                                            accentColor = Color(0xFF7C3AED)
                                        )

                                        // Interactive Flashcard Module Card
                                        if (msg.createdModuleId != null || msg.suggestedCards.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            val createdModId = msg.createdModuleId
                                            val isAlreadySaved = createdModId != null || savedCardsId == msg.id

                                            Card(
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                ),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isAlreadySaved) Color(0xFF16A34A).copy(alpha = 0.45f) else Color(0xFF7C3AED).copy(alpha = 0.35f)
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("chat_cards_block_${msg.id}")
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.weight(1f),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Style,
                                                                contentDescription = null,
                                                                tint = if (isAlreadySaved) Color(0xFF16A34A) else Color(0xFF7C3AED),
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = if (createdModId != null) "Модуль: ${msg.createdModuleTitle ?: "Карточки"}" else "Карточки к теме (${msg.suggestedCards.size})",
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 13.sp,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        if (isAlreadySaved) {
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Surface(
                                                                color = Color(0xFF16A34A).copy(alpha = 0.15f),
                                                                shape = RoundedCornerShape(6.dp)
                                                            ) {
                                                                Text(
                                                                    text = "✓ В библиотеке",
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color(0xFF16A34A),
                                                                    maxLines = 1,
                                                                    softWrap = false,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    // Preview of cards (front & back)
                                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        msg.suggestedCards.take(4).forEach { pair ->
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clip(RoundedCornerShape(6.dp))
                                                                    .background(MaterialTheme.colorScheme.surface)
                                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(
                                                                    text = pair.first,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 12.sp,
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.weight(0.42f),
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Text(
                                                                    text = "—",
                                                                    fontSize = 12.sp,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                                )
                                                                Text(
                                                                    text = pair.second,
                                                                    fontSize = 12.sp,
                                                                    color = MaterialTheme.colorScheme.onSurface,
                                                                    modifier = Modifier.weight(0.58f),
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                        if (msg.suggestedCards.size > 4) {
                                                            Text(
                                                                text = "+ ещё ${msg.suggestedCards.size - 4} карточек",
                                                                fontSize = 11.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(10.dp))
                                                    if (createdModId != null) {
                                                        Button(
                                                            onClick = { onOpenModule(createdModId) },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .testTag("open_created_module_${createdModId}")
                                                        ) {
                                                            Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text("Открыть модуль и учить карточки", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                        }
                                                    } else {
                                                        Button(
                                                            onClick = {
                                                                val rawCards = msg.suggestedCards.map {
                                                                    Flashcard(
                                                                        id = UUID.randomUUID().toString(),
                                                                        moduleId = "ai_chat",
                                                                        term = it.first,
                                                                        definition = it.second
                                                                    )
                                                                }
                                                                val newModule = StudyModule(
                                                                    id = "chat_mod_" + System.currentTimeMillis(),
                                                                    title = "Карточки из диалога",
                                                                    description = "Создано по объяснению AI репетитора",
                                                                    category = "AI Репетитор",
                                                                    author = "Вы & Репетитор",
                                                                    isVerified = false,
                                                                    colorHex = 0xFF16A34A,
                                                                    cards = rawCards
                                                                )
                                                                onSaveGeneratedModule(newModule)
                                                                savedCardsId = msg.id
                                                            },
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = if (isAlreadySaved) Color(0xFF16A34A) else Color(0xFF7C3AED)
                                                            ),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .testTag("create_cards_from_chat_${msg.id}")
                                                        ) {
                                                            Icon(
                                                                imageVector = if (isAlreadySaved) Icons.Default.Check else Icons.Default.AutoAwesome,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(16.dp),
                                                                tint = Color.White
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = if (isAlreadySaved) "Карточки сохранены в «Ваши карточки»!" else "Создать модуль из этих карточек",
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Input Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF7C3AED).copy(alpha = 0.14f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        inputText = "Создай карточки по теме "
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Style, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("➕ Создать карточки", fontSize = 12.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onClearHistory() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("🔄 Сбросить историю", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        placeholder = {
                            Text(
                                text = QuizStrings.chatHint(activeLanguage),
                                fontSize = 14.sp
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedBorderColor = Color(0xFF7C3AED),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        ),
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isSending) {
                                val textToSend = inputText.trim()
                                inputText = ""
                                coroutineScope.launch {
                                    isSending = true
                                    onSendMessage(textToSend)
                                    isSending = false
                                }
                            }
                        },
                        enabled = inputText.isNotBlank() && !isSending,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (inputText.isNotBlank()) Color(0xFF7C3AED) else MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("chat_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        }
    }
}

/**
 * Renders structured Markdown content in chat messages:
 * - Headings (#, ##, ###)
 * - Fenced code blocks (```...```)
 * - Blockquotes (> ...)
 * - Bullet lists (-, *, •)
 * - Numbered lists (1., 2., ...)
 * - Inline bold (**text**), italic (*text*), and inline code (`code`)
 */
@Composable
fun MarkdownMessageContent(
    markdown: String,
    textColor: Color,
    codeBackgroundColor: Color,
    codeTextColor: Color,
    accentColor: Color
) {
    val lines = remember(markdown) { markdown.lines() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var i = 0
        while (i < lines.size) {
            val rawLine = lines[i]
            val trimmed = rawLine.trim()

            when {
                trimmed.isEmpty() -> {
                    Spacer(modifier = Modifier.height(2.dp))
                    i++
                }

                trimmed.startsWith("```") -> {
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    if (i < lines.size) i++ // skip closing ```
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = codeBackgroundColor,
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = codeLines.joinToString("\n"),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = codeTextColor,
                            lineHeight = 19.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                trimmed.startsWith("### ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("### ").trim(), codeBackgroundColor, codeTextColor),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        lineHeight = 22.sp
                    )
                    i++
                }

                trimmed.startsWith("## ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("## ").trim(), codeBackgroundColor, codeTextColor),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor,
                        lineHeight = 23.sp
                    )
                    i++
                }

                trimmed.startsWith("# ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("# ").trim(), codeBackgroundColor, codeTextColor),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor,
                        lineHeight = 25.sp
                    )
                    i++
                }

                trimmed == "---" || trimmed == "***" -> {
                    HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                    i++
                }

                trimmed.startsWith("> ") -> {
                    val quoteContent = trimmed.removePrefix("> ").trim()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(24.dp)
                                .background(accentColor, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseInlineMarkdown(quoteContent, codeBackgroundColor, codeTextColor),
                            fontSize = 13.sp,
                            color = textColor.copy(alpha = 0.9f),
                            lineHeight = 19.sp
                        )
                    }
                    i++
                }

                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                    val itemText = trimmed.drop(2).trim()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = accentColor,
                            modifier = Modifier.padding(end = 8.dp, top = 1.dp)
                        )
                        Text(
                            text = parseInlineMarkdown(itemText, codeBackgroundColor, codeTextColor),
                            fontSize = 14.sp,
                            color = textColor,
                            lineHeight = 21.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    i++
                }

                trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val numberPrefix = trimmed.substringBefore(".") + "."
                    val itemText = trimmed.substringAfter(".").trim()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = numberPrefix,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = accentColor,
                            modifier = Modifier.padding(end = 8.dp, top = 1.dp)
                        )
                        Text(
                            text = parseInlineMarkdown(itemText, codeBackgroundColor, codeTextColor),
                            fontSize = 14.sp,
                            color = textColor,
                            lineHeight = 21.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    i++
                }

                else -> {
                    Text(
                        text = parseInlineMarkdown(trimmed, codeBackgroundColor, codeTextColor),
                        fontSize = 15.sp,
                        color = textColor,
                        lineHeight = 22.sp
                    )
                    i++
                }
            }
        }
    }
}

private fun parseInlineMarkdown(
    text: String,
    codeBg: Color,
    codeColor: Color
): AnnotatedString = buildAnnotatedString {
    var idx = 0
    val len = text.length
    while (idx < len) {
        when {
            // Inline code `...`
            text.startsWith("`", idx) -> {
                val endIdx = text.indexOf('`', idx + 1)
                if (endIdx != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBg,
                            color = codeColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    ) {
                        append(" " + text.substring(idx + 1, endIdx) + " ")
                    }
                    idx = endIdx + 1
                } else {
                    append(text[idx])
                    idx++
                }
            }

            // Bold **...**
            text.startsWith("**", idx) -> {
                val endIdx = text.indexOf("**", idx + 2)
                if (endIdx != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(idx + 2, endIdx))
                    }
                    idx = endIdx + 2
                } else {
                    append(text[idx])
                    idx++
                }
            }

            // Italic *...*
            text.startsWith("*", idx) -> {
                val endIdx = text.indexOf('*', idx + 1)
                if (endIdx != -1 && endIdx > idx + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(idx + 1, endIdx))
                    }
                    idx = endIdx + 1
                } else {
                    append(text[idx])
                    idx++
                }
            }

            else -> {
                append(text[idx])
                idx++
            }
        }
    }
}
