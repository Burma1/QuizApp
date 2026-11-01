package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.quizapp.data.locale.LocalStrings
import com.example.quizapp.data.locale.QuizStrings
import com.example.quizapp.data.model.AppThemeMode
import com.example.quizapp.data.model.Flashcard
import com.example.quizapp.data.model.StudyModule
import com.example.quizapp.data.repository.QuizRepository
import com.example.quizapp.ui.screens.AiChatScreen
import com.example.quizapp.ui.screens.CreateModuleScreen
import com.example.quizapp.ui.screens.FlashcardsScreen
import com.example.quizapp.ui.screens.HomeScreen
import com.example.quizapp.ui.screens.ImportExportScreen
import com.example.quizapp.ui.screens.LearnScreen
import com.example.quizapp.ui.screens.LibraryScreen
import com.example.quizapp.ui.screens.ModuleDetailScreen
import com.example.quizapp.ui.screens.PhotoToCardsScreen
import com.example.quizapp.ui.screens.SettingsScreen
import com.example.quizapp.ui.screens.TestScreen
import com.example.ui.theme.MyApplicationTheme

sealed class Screen {
    data object Home : Screen()
    data class Library(val initialTab: Int = 0) : Screen()
    data class ModuleDetail(val moduleId: String) : Screen()
    data class Flashcards(val moduleId: String, val customCards: List<Flashcard>? = null) : Screen()
    data class Learn(val moduleId: String, val customCards: List<Flashcard>? = null) : Screen()
    data class Test(val moduleId: String) : Screen()
    data object AiChat : Screen()
    data object Settings : Screen()
    data object CreateModule : Screen()
    data object PhotoScan : Screen()
    data object ImportExport : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = QuizRepository(applicationContext)
        setContent {
            val themeMode by repository.themeMode.collectAsState()
            val colorTheme by repository.colorTheme.collectAsState()
            val activeLanguage by repository.activeLanguage.collectAsState()

            val isDark = when (themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            CompositionLocalProvider(LocalStrings provides QuizStrings.get(activeLanguage)) {
                MyApplicationTheme(
                    darkTheme = isDark,
                    colorTheme = colorTheme
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        QuizAppNavHost(repository = repository)
                    }
                }
            }
        }
    }
}

@Composable
fun QuizAppNavHost(repository: QuizRepository) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    val modules by repository.modules.collectAsState()
    val userStats by repository.userStats.collectAsState()
    val activeLanguage by repository.activeLanguage.collectAsState()
    val themeMode by repository.themeMode.collectAsState()
    val colorTheme by repository.colorTheme.collectAsState()
    val aiProfiles by repository.aiProfiles.collectAsState()
    val activeProfile by repository.activeProfile.collectAsState()
    val chatMessages by repository.chatMessages.collectAsState()

    val resolveModule: (String) -> StudyModule? = { modId ->
        modules.find { it.id == modId } ?: modules.firstOrNull()
    }

    if (currentScreen !is Screen.Home) {
        BackHandler {
            currentScreen = when (val s = currentScreen) {
                is Screen.Flashcards -> Screen.ModuleDetail(s.moduleId)
                is Screen.Learn -> Screen.ModuleDetail(s.moduleId)
                is Screen.Test -> Screen.ModuleDetail(s.moduleId)
                else -> Screen.Home
            }
        }
    }

    Crossfade(targetState = currentScreen, animationSpec = tween(120), label = "screen_nav") { screen ->
        when (screen) {
            is Screen.Home -> {
                HomeScreen(
                    modules = modules,
                    userStats = userStats,
                    activeLanguage = activeLanguage,
                    onSelectLanguage = { repository.setLanguage(it) },
                    onOpenModule = { currentScreen = Screen.ModuleDetail(it.id) },
                    onOpenCreateModule = { currentScreen = Screen.CreateModule },
                    onOpenPhotoScanner = { currentScreen = Screen.PhotoScan },
                    onOpenImportExport = { currentScreen = Screen.ImportExport },
                    onOpenAiTutor = { currentScreen = Screen.AiChat },
                    onOpenLibrary = { tab -> currentScreen = Screen.Library(tab) },
                    onOpenSettings = { currentScreen = Screen.Settings },
                    onDeleteModule = { repository.deleteModule(it) }
                )
            }
            is Screen.Library -> {
                LibraryScreen(
                    modules = modules,
                    activeLanguage = activeLanguage,
                    initialTab = screen.initialTab,
                    onSelectModule = { currentScreen = Screen.ModuleDetail(it.id) },
                    onCreateModule = { currentScreen = Screen.CreateModule },
                    onDeleteModule = { repository.deleteModule(it) },
                    onNavigateHome = { currentScreen = Screen.Home }
                )
            }
            is Screen.ModuleDetail -> {
                val mod = resolveModule(screen.moduleId)
                if (mod != null) {
                    ModuleDetailScreen(
                        module = mod,
                        onBack = { currentScreen = Screen.Home },
                        onOpenFlashcards = { customCards ->
                            currentScreen = Screen.Flashcards(mod.id, customCards)
                        },
                        onOpenLearn = { customCards ->
                            currentScreen = Screen.Learn(mod.id, customCards)
                        },
                        onOpenTest = {
                            currentScreen = Screen.Test(mod.id)
                        },
                        onOpenExport = {
                            currentScreen = Screen.ImportExport
                        },
                        onToggleStar = { cardId ->
                            repository.toggleCardStarred(mod.id, cardId)
                        },
                        onDeleteModule = {
                            repository.deleteModule(mod.id)
                            currentScreen = Screen.Home
                        }
                    )
                } else {
                    currentScreen = Screen.Home
                }
            }
            is Screen.Flashcards -> {
                val mod = resolveModule(screen.moduleId)
                if (mod != null) {
                    FlashcardsScreen(
                        module = mod,
                        customCards = screen.customCards,
                        onBack = { currentScreen = Screen.ModuleDetail(mod.id) },
                        onUpdateMastery = { cardId, isKnown ->
                            repository.updateCardMastery(mod.id, cardId, isKnown)
                        },
                        onToggleStar = { cardId ->
                            repository.toggleCardStarred(mod.id, cardId)
                        }
                    )
                } else {
                    currentScreen = Screen.Home
                }
            }
            is Screen.Learn -> {
                val mod = resolveModule(screen.moduleId)
                if (mod != null) {
                    LearnScreen(
                        module = mod,
                        customCards = screen.customCards,
                        onBack = { currentScreen = Screen.ModuleDetail(mod.id) },
                        onUpdateMastery = { cardId, isKnown ->
                            repository.updateCardMastery(mod.id, cardId, isKnown)
                        }
                    )
                } else {
                    currentScreen = Screen.Home
                }
            }
            is Screen.Test -> {
                val mod = resolveModule(screen.moduleId)
                if (mod != null) {
                    TestScreen(
                        module = mod,
                        onBack = { currentScreen = Screen.ModuleDetail(mod.id) },
                        onUpdateMastery = { cardId, isKnown ->
                            repository.updateCardMastery(mod.id, cardId, isKnown)
                        }
                    )
                } else {
                    currentScreen = Screen.Home
                }
            }
            is Screen.AiChat -> {
                AiChatScreen(
                    messages = chatMessages,
                    activeLanguage = activeLanguage,
                    aiProfiles = aiProfiles,
                    activeProfile = activeProfile,
                    onSelectProfile = { profileId -> repository.selectAiProfile(profileId) },
                    onBack = { currentScreen = Screen.Home },
                    onSendMessage = { userText -> repository.askAiTutor(userText) },
                    onOpenSettings = { currentScreen = Screen.Settings },
                    onSaveGeneratedModule = { newMod -> repository.saveModule(newMod) },
                    onClearHistory = { repository.clearChatHistory() },
                    onOpenModule = { modId -> currentScreen = Screen.ModuleDetail(modId) }
                )
            }
            is Screen.Settings -> {
                SettingsScreen(
                    activeLanguage = activeLanguage,
                    onLanguageChange = { repository.setLanguage(it) },
                    themeMode = themeMode,
                    onThemeChange = { repository.setThemeMode(it) },
                    colorTheme = colorTheme,
                    onColorThemeChange = { repository.setColorTheme(it) },
                    aiProfiles = aiProfiles,
                    activeProfile = activeProfile,
                    onSelectProfile = { profileId -> repository.selectAiProfile(profileId) },
                    onAddProfile = { name, provider, apiKey, modelId ->
                        repository.addAiProfile(name, provider, apiKey, modelId)
                    },
                    onUpdateProfile = { updated -> repository.updateAiProfile(updated) },
                    onDeleteProfile = { id -> repository.deleteAiProfile(id) },
                    onFetchModels = { provider, apiKey -> repository.fetchModelsForProviderResult(provider, apiKey) },
                    onBack = { currentScreen = Screen.Home }
                )
            }
            is Screen.CreateModule -> {
                CreateModuleScreen(
                    activeLanguage = activeLanguage,
                    onBack = { currentScreen = Screen.Home },
                    onSaveModule = { newMod ->
                        repository.saveModule(newMod)
                        currentScreen = Screen.ModuleDetail(newMod.id)
                    },
                    onGenerateWithAi = { topic -> repository.generateFlashcardsForTopic(topic) }
                )
            }
            is Screen.PhotoScan -> {
                PhotoToCardsScreen(
                    activeLanguage = activeLanguage,
                    onBack = { currentScreen = Screen.Home },
                    onGenerateFromImage = { base64, mime ->
                        repository.generateCardsFromImage(base64, mime)
                    },
                    onSaveModule = { newMod ->
                        repository.saveModule(newMod)
                        currentScreen = Screen.ModuleDetail(newMod.id)
                    }
                )
            }
            is Screen.ImportExport -> {
                ImportExportScreen(
                    modules = modules,
                    activeLanguage = activeLanguage,
                    repository = repository,
                    onBack = { currentScreen = Screen.Home },
                    onModuleImported = { importedMod ->
                        currentScreen = Screen.ModuleDetail(importedMod.id)
                    }
                )
            }
        }
    }
}
