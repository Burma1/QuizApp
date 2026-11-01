package com.example.quizapp.data.locale

import com.example.quizapp.data.model.AppLanguage

object QuizStrings {
    fun appTitle(lang: AppLanguage) = "QuizApp"

    fun tagLine(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Умные карточки и эффективное обучение"
        AppLanguage.EN -> "Smart flashcards & mastery learning"
        AppLanguage.ES -> "Tarjetas inteligentes y aprendizaje activo"
    }

    fun continueStudy(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Продолжить учебу"
        AppLanguage.EN -> "Continue studying"
        AppLanguage.ES -> "Continuar estudiando"
    }

    fun continueAction(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Продолжить"
        AppLanguage.EN -> "Continue"
        AppLanguage.ES -> "Continuar"
    }

    fun recentSection(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Недавние"
        AppLanguage.EN -> "Recent"
        AppLanguage.ES -> "Recientes"
    }

    fun upNextSection(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Рекомендуемые модули"
        AppLanguage.EN -> "Recommended modules"
        AppLanguage.ES -> "Módulos recomendados"
    }

    fun needsReviewSection(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Требуют повторения"
        AppLanguage.EN -> "Needs review"
        AppLanguage.ES -> "Necesitan repaso"
    }

    fun yourProgressTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Ваш прогресс"
        AppLanguage.EN -> "Your progress"
        AppLanguage.ES -> "Tu progreso"
    }

    fun yourProgressSubtitle(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Прогресс формируется на основе последних сеансов изучения каждого термина во всех режимах."
        AppLanguage.EN -> "Progress is calculated based on your recent study sessions for each term across all modes."
        AppLanguage.ES -> "El progreso se calcula en base a tus últimas sesiones de estudio de cada término."
    }

    fun statusNotStudied(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Не изучено"
        AppLanguage.EN -> "Not studied"
        AppLanguage.ES -> "No estudiado"
    }

    fun statusLearning(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Изучено"
        AppLanguage.EN -> "Still learning"
        AppLanguage.ES -> "Aprendiendo"
    }

    fun statusMastered(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Усвоено"
        AppLanguage.EN -> "Mastered"
        AppLanguage.ES -> "Dominado"
    }

    fun library(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Библиотека"
        AppLanguage.EN -> "Library"
        AppLanguage.ES -> "Biblioteca"
    }

    fun homeTab(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Главная"
        AppLanguage.EN -> "Home"
        AppLanguage.ES -> "Inicio"
    }

    fun searchPlaceholder(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Поиск модулей и терминов..."
        AppLanguage.EN -> "Search modules and terms..."
        AppLanguage.ES -> "Buscar módulos y términos..."
    }

    fun createModule(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Создать модуль"
        AppLanguage.EN -> "Create Module"
        AppLanguage.ES -> "Crear Módulo"
    }

    fun modeFlashcards(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Карточки"
        AppLanguage.EN -> "Flashcards"
        AppLanguage.ES -> "Tarjetas"
    }

    fun modeLearn(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Заучивание"
        AppLanguage.EN -> "Learn"
        AppLanguage.ES -> "Aprender"
    }

    fun modeTest(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Тест"
        AppLanguage.EN -> "Test"
        AppLanguage.ES -> "Examen"
    }

    fun photoScanTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Карточки из фото"
        AppLanguage.EN -> "Cards from Photo"
        AppLanguage.ES -> "Tarjetas desde Foto"
    }

    fun photoScanSub(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Сфотографируйте учебник или конспект для мгновенного создания карточек"
        AppLanguage.EN -> "Snap a textbook page or notes to create flashcards instantly"
        AppLanguage.ES -> "Toma una foto de apuntes para crear tarjetas al instante"
    }

    fun importExportTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Импорт и Экспорт"
        AppLanguage.EN -> "Import & Export"
        AppLanguage.ES -> "Importar y Exportar"
    }

    fun importExportSub(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Загружайте карточки из файлов JSON/CSV или экспортируйте для обмена"
        AppLanguage.EN -> "Import cards from JSON/CSV files or export to share"
        AppLanguage.ES -> "Importa tarjetas desde archivos JSON/CSV o exporta para compartir"
    }

    fun magicNotesTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Волшебный конспект"
        AppLanguage.EN -> "Magic Notes"
        AppLanguage.ES -> "Apuntes Mágicos"
    }

    fun magicNotesSub(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Превратите заметки в карточки, тезисы и структуру"
        AppLanguage.EN -> "Turn notes into cards, key points and structure"
        AppLanguage.ES -> "Convierte notas en tarjetas, resúmenes y más"
    }

    fun aiTutor(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Репетитор"
        AppLanguage.EN -> "AI Tutor"
        AppLanguage.ES -> "Tutor IA"
    }

    fun aiTutorSub(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Умный помощник для диалога, объяснений и тренировки"
        AppLanguage.EN -> "Smart assistant for conversation, explanations & quizzes"
        AppLanguage.ES -> "Asistente inteligente para diálogo, dudas y práctica"
    }

    fun termsCount(count: Int, lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "$count терминов"
        AppLanguage.EN -> "$count terms"
        AppLanguage.ES -> "$count términos"
    }

    fun flipCardHint(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Нажмите, чтобы перевернуть"
        AppLanguage.EN -> "Tap to flip card"
        AppLanguage.ES -> "Toca para voltear"
    }

    fun stillLearning(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Еще учу"
        AppLanguage.EN -> "Still learning"
        AppLanguage.ES -> "En proceso"
    }

    fun know(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Знаю"
        AppLanguage.EN -> "Know it"
        AppLanguage.ES -> "Ya lo sé"
    }

    fun greatFeedback(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Прекрасно!"
        AppLanguage.EN -> "Great job!"
        AppLanguage.ES -> "¡Excelente!"
    }

    fun wonderfulFeedback(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Замечательно!"
        AppLanguage.EN -> "Wonderful!"
        AppLanguage.ES -> "¡Maravilloso!"
    }

    fun tryAgainFeedback(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Требует повторения"
        AppLanguage.EN -> "Needs review"
        AppLanguage.ES -> "Necesita repaso"
    }

    fun nextQuestion(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Следующий вопрос"
        AppLanguage.EN -> "Next Question"
        AppLanguage.ES -> "Siguiente pregunta"
    }

    fun finishReview(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Завершить тренировку"
        AppLanguage.EN -> "Finish Review"
        AppLanguage.ES -> "Terminar repaso"
    }

    fun summaryTab(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Краткое содержание"
        AppLanguage.EN -> "Summary"
        AppLanguage.ES -> "Resumen"
    }

    fun keyPointsTab(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Тезисы"
        AppLanguage.EN -> "Key Takeaways"
        AppLanguage.ES -> "Puntos clave"
    }

    fun simplifyButton(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Упростить это"
        AppLanguage.EN -> "Simplify this"
        AppLanguage.ES -> "Simplificar esto"
    }

    fun saveAsModuleButton(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Сохранить как модуль"
        AppLanguage.EN -> "Save as Module"
        AppLanguage.ES -> "Guardar como módulo"
    }

    fun settingsTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Настройки"
        AppLanguage.EN -> "Settings"
        AppLanguage.ES -> "Ajustes"
    }

    fun languageLabel(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Язык приложения"
        AppLanguage.EN -> "App Language"
        AppLanguage.ES -> "Idioma de la app"
    }

    fun aiProfilesTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Профили ИИ"
        AppLanguage.EN -> "AI Profiles"
        AppLanguage.ES -> "Perfiles de IA"
    }

    fun addAiProfileButton(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Добавить профиль ИИ"
        AppLanguage.EN -> "Add AI Profile"
        AppLanguage.ES -> "Añadir perfil de IA"
    }

    fun aiProviderLabel(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Провайдер ИИ"
        AppLanguage.EN -> "AI Provider"
        AppLanguage.ES -> "Proveedor de IA"
    }

    fun profileNameLabel(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Название профиля"
        AppLanguage.EN -> "Profile Name"
        AppLanguage.ES -> "Nombre del perfil"
    }

    fun apiKeyLabel(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "API Ключ"
        AppLanguage.EN -> "API Key"
        AppLanguage.ES -> "Clave API"
    }

    fun loadModelsButton(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Загрузить модели"
        AppLanguage.EN -> "Load Models"
        AppLanguage.ES -> "Cargar modelos"
    }

    fun selectModelLabel(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Модель ИИ"
        AppLanguage.EN -> "AI Model"
        AppLanguage.ES -> "Modelo de IA"
    }

    fun chatHint(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Напишите сообщение, поздоровайтесь или задайте вопрос..."
        AppLanguage.EN -> "Type a message, say hello, or ask a question..."
        AppLanguage.ES -> "Escribe un mensaje, saluda o haz una pregunta..."
    }

    fun createCardsFromAnswer(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Создать карточки из ответа"
        AppLanguage.EN -> "Create cards from response"
        AppLanguage.ES -> "Crear tarjetas a partir de la respuesta"
    }

    fun themeTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Тема оформления (MD3)"
        AppLanguage.EN -> "Appearance & Theme (MD3)"
        AppLanguage.ES -> "Tema y Apariencia (MD3)"
    }

    fun themeSubtitle(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "По умолчанию следует за системой"
        AppLanguage.EN -> "Follows system by default"
        AppLanguage.ES -> "Sigue el sistema por defecto"
    }

    fun colorAccentTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Цветовая палитра Material 3"
        AppLanguage.EN -> "Material 3 Color Palette"
        AppLanguage.ES -> "Paleta de colores Material 3"
    }

    fun colorAccentSubtitle(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Выберите динамический системный цвет или акцентную схему"
        AppLanguage.EN -> "Choose system dynamic color or a custom accent scheme"
        AppLanguage.ES -> "Elige color dinámico del sistema o un esquema personalizado"
    }

    fun themeSystem(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Системная"
        AppLanguage.EN -> "System"
        AppLanguage.ES -> "Sistema"
    }

    fun themeLight(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Светлая"
        AppLanguage.EN -> "Light"
        AppLanguage.ES -> "Claro"
    }

    fun themeDark(lang: AppLanguage) = when (lang) {
        AppLanguage.RU -> "Тёмная"
        AppLanguage.EN -> "Dark"
        AppLanguage.ES -> "Oscuro"
    }

    fun get(lang: AppLanguage): StringResources = when (lang) {
        AppLanguage.RU -> StringResources()
        AppLanguage.EN -> StringResources(
            appName = "QuizApp",
            searchHint = "Search modules and terms...",
            jumpBackIn = "Continue studying",
            questionsCompleted = "completed",
            continueBtn = "Continue",
            recentsTitle = "Recent",
            nextUpTitle = "Recommended modules",
            needsReviewTitle = "Needs review",
            homeTab = "Home",
            createModule = "Create",
            libraryTab = "Library",
            flashcardsMode = "Flashcards",
            learnMode = "Learn",
            testMode = "Test",
            aiTutor = "AI Tutor",
            magicNotes = "Magic Notes",
            scanFromPhoto = "From Photo (AI)",
            importExportCards = "Import / Export",
            cardsCount = "terms",
            authorPrefix = "Author",
            stillLearning = "Still learning",
            knowCard = "Know",
            tapToFlip = "Tap card to flip",
            termLabel = "TERM",
            definitionLabel = "DEFINITION",
            shuffleCards = "Shuffle",
            autoPlay = "Auto-play",
            chooseCorrectAnswer = "Choose the correct term",
            typeAnswerHint = "Type the term...",
            checkAnswer = "Check",
            dontKnow = "Don't know",
            correctFeedback = "Correct!",
            incorrectFeedback = "Needs review",
            correctAnswerIs = "Correct answer",
            nextQuestion = "Continue",
            testResultsTitle = "Test Results",
            submitTest = "Submit Test",
            scoreLabel = "Score",
            trueOrFalse = "True or False",
            trueLabel = "True",
            falseLabel = "False",
            magicNotesSubtitle = "Turn lecture notes into flashcards",
            simplifyBtn = "Simplify",
            saveAsModule = "Save as module",
            aiChatTitle = "QuizApp AI Tutor",
            aiChatPlaceholder = "Ask a question or request flashcards...",
            sendBtn = "Send",
            settingsTitle = "Settings",
            languageLabel = "Interface Language",
            themeModeLabel = "Appearance (MD3)",
            themeSystem = "System",
            themeLight = "Light",
            themeDark = "Dark",
            colorThemeLabel = "Material 3 Color Palette",
            colorThemeSubtitle = "Follows system dynamic colors by default",
            colorSystem = "Dynamic (System)",
            colorIndigo = "Indigo",
            colorOcean = "Ocean",
            colorEmerald = "Emerald",
            colorViolet = "Violet",
            colorRose = "Rose",
            yourProgressTitle = "Your progress",
            yourProgressDesc = "Progress is based on your last two study sessions for each term across all modes.",
            notStudiedLabel = "Not studied",
            learningLabel = "Learning",
            masteredLabel = "Mastered",
            needsReviewLabel = "Needs review",
            studyUnlearnedBtn = "Review unlearned",
            allTermsTab = "All",
            originalOrder = "Original",
            byStatusOrder = "By status",
            aiProfilesTitle = "AI Profiles",
            addProfileBtn = "Add Profile",
            editProfileTitle = "Edit Profile",
            newProfileTitle = "New AI Profile",
            profileNameLabel = "Profile Name",
            providerLabel = "AI Provider",
            apiKeyLabel = "API Key",
            modelIdLabel = "Model",
            fetchModelsBtn = "Load Models",
            createModuleTitle = "New Study Module",
            generateWithAi = "Generate with AI",
            photoScannerSubtitle = "Take a photo of notes or textbook",
            importFromTextHint = "JSON or CSV / Tab-separated",
            exportModuleBtn = "Export",
            termsInModule = "Terms",
            greatJob = "Great job!",
            restartBtn = "Restart"
        )
        AppLanguage.ES -> StringResources(
            appName = "QuizApp",
            searchHint = "Buscar módulos y términos...",
            jumpBackIn = "Continuar estudiando",
            questionsCompleted = "completado",
            continueBtn = "Continuar",
            recentsTitle = "Recientes",
            nextUpTitle = "Módulos recomendados",
            needsReviewTitle = "Necesitan repaso",
            homeTab = "Inicio",
            createModule = "Crear",
            libraryTab = "Biblioteca",
            flashcardsMode = "Tarjetas",
            learnMode = "Aprender",
            testMode = "Prueba",
            aiTutor = "Tutor IA",
            magicNotes = "Apuntes Mágicos",
            scanFromPhoto = "Desde Foto (IA)",
            importExportCards = "Importar / Exportar",
            cardsCount = "términos",
            authorPrefix = "Autor",
            stillLearning = "Aprendiendo",
            knowCard = "Lo sé",
            tapToFlip = "Toca para voltear",
            termLabel = "TÉRMINО",
            definitionLabel = "DEFINICIÓN",
            shuffleCards = "Mezclar",
            autoPlay = "Auto-reproducir",
            chooseCorrectAnswer = "Elige el término correcto",
            typeAnswerHint = "Escribe la respuesta...",
            checkAnswer = "Comprobar",
            dontKnow = "No lo sé",
            correctFeedback = "¡Correcto!",
            incorrectFeedback = "Para repasar",
            correctAnswerIs = "Respuesta correcta",
            nextQuestion = "Continuar",
            testResultsTitle = "Resultados",
            submitTest = "Finalizar prueba",
            scoreLabel = "Puntuación",
            trueOrFalse = "Verdadero o Falso",
            trueLabel = "Verdadero",
            falseLabel = "Falso",
            magicNotesSubtitle = "Convierte apuntes en tarjetas",
            simplifyBtn = "Simplificar",
            saveAsModule = "Guardar como módulo",
            aiChatTitle = "Tutor IA QuizApp",
            aiChatPlaceholder = "Haz una pregunta...",
            sendBtn = "Enviar",
            settingsTitle = "Ajustes",
            languageLabel = "Idioma",
            themeModeLabel = "Tema (MD3)",
            themeSystem = "Sistema",
            themeLight = "Claro",
            themeDark = "Oscuro",
            colorThemeLabel = "Paleta de colores Material 3",
            colorThemeSubtitle = "Por defecto sigue los colores del sistema",
            colorSystem = "Dinámico (Sistema)",
            colorIndigo = "Índigo",
            colorOcean = "Océano",
            colorEmerald = "Esmeralda",
            colorViolet = "Violeta",
            colorRose = "Rosa",
            yourProgressTitle = "Tu progreso",
            yourProgressDesc = "El progreso se basa en las dos últimas sesiones de estudio de cada término.",
            notStudiedLabel = "No estudiado",
            learningLabel = "Aprendiendo",
            masteredLabel = "Dominado",
            needsReviewLabel = "Repasar",
            studyUnlearnedBtn = "Repasar no aprendidos",
            allTermsTab = "Todos",
            originalOrder = "Original",
            byStatusOrder = "Por estado",
            aiProfilesTitle = "Perfiles de IA",
            addProfileBtn = "Añadir perfil",
            editProfileTitle = "Editar perfil",
            newProfileTitle = "Nuevo perfil IA",
            profileNameLabel = "Nombre",
            providerLabel = "Proveedor IA",
            apiKeyLabel = "Clave API",
            modelIdLabel = "Modelo",
            fetchModelsBtn = "Cargar modelos",
            createModuleTitle = "Nuevo Módulo",
            generateWithAi = "Generar con IA",
            photoScannerSubtitle = "Sube una foto de tus apuntes",
            importFromTextHint = "JSON o CSV",
            exportModuleBtn = "Exportar",
            termsInModule = "Términos",
            greatJob = "¡Excelente trabajo!",
            restartBtn = "Reiniciar"
        )
    }
}

data class StringResources(
    val appName: String = "QuizApp",
    val searchHint: String = "Поиск модулей, тем, терминов...",
    val jumpBackIn: String = "Продолжить учебу",
    val questionsCompleted: String = "пройдено",
    val continueBtn: String = "Продолжить",
    val recentsTitle: String = "Недавние",
    val nextUpTitle: String = "Рекомендуемые модули",
    val needsReviewTitle: String = "Требуют повторения",
    val homeTab: String = "Главная",
    val createModule: String = "Создать",
    val libraryTab: String = "Библиотека",
    val flashcardsMode: String = "Карточки",
    val learnMode: String = "Заучивание",
    val testMode: String = "Тест",
    val aiTutor: String = "Репетитор",
    val magicNotes: String = "Конспект с ИИ",
    val scanFromPhoto: String = "Из фото (ИИ)",
    val importExportCards: String = "Импорт / Экспорт",
    val cardsCount: String = "терминов",
    val authorPrefix: String = "Автор",
    val stillLearning: String = "Еще учу",
    val knowCard: String = "Знаю",
    val tapToFlip: String = "Нажмите на карточку, чтобы перевернуть",
    val termLabel: String = "ТЕРМИН",
    val definitionLabel: String = "ОПРЕДЕЛЕНИЕ",
    val shuffleCards: String = "Перемешать",
    val autoPlay: String = "Автовоспроизведение",
    val chooseCorrectAnswer: String = "Выберите правильный термин",
    val typeAnswerHint: String = "Введите термин...",
    val checkAnswer: String = "Проверить",
    val dontKnow: String = "Не знаю",
    val correctFeedback: String = "Верно! Отличная работа",
    val incorrectFeedback: String = "Нужно повторить",
    val correctAnswerIs: String = "Правильный ответ",
    val nextQuestion: String = "Продолжить",
    val testResultsTitle: String = "Результаты теста",
    val submitTest: String = "Завершить и проверить тест",
    val scoreLabel: String = "Ваш результат",
    val trueOrFalse: String = "Верно или Неверно",
    val trueLabel: String = "Верно",
    val falseLabel: String = "Неверно",
    val magicNotesSubtitle: String = "Создайте конспект и карточки из текста",
    val simplifyBtn: String = "Упростить текст",
    val saveAsModule: String = "Сохранить как модуль",
    val aiChatTitle: String = "ИИ-Репетитор QuizApp",
    val aiChatPlaceholder: String = "Спросите репетитора или попросите создать карточки...",
    val sendBtn: String = "Отправить",
    val settingsTitle: String = "Настройки",
    val languageLabel: String = "Язык интерфейса",
    val themeModeLabel: String = "Тема оформления (Material 3)",
    val themeSystem: String = "Системная",
    val themeLight: String = "Светлая",
    val themeDark: String = "Темная",
    val colorThemeLabel: String = "Цветовая палитра Material 3",
    val colorThemeSubtitle: String = "По умолчанию следует за системными цветами",
    val colorSystem: String = "Динамическая (Система)",
    val colorIndigo: String = "Индиго",
    val colorOcean: String = "Океан",
    val colorEmerald: String = "Изумруд",
    val colorViolet: String = "Фиолетовый",
    val colorRose: String = "Розовый",
    val yourProgressTitle: String = "Ваш прогресс",
    val yourProgressDesc: String = "Прогресс формируется на основе двух последних сеансов изучения каждого термина во всех режимах.",
    val notStudiedLabel: String = "Не изучено",
    val learningLabel: String = "Изучено",
    val masteredLabel: String = "Усвоено",
    val needsReviewLabel: String = "Повторить",
    val studyUnlearnedBtn: String = "Повторить неусвоенные",
    val allTermsTab: String = "Все",
    val originalOrder: String = "Оригинал",
    val byStatusOrder: String = "По статусу",
    val aiProfilesTitle: String = "Профили ИИ",
    val addProfileBtn: String = "Добавить профиль",
    val editProfileTitle: String = "Редактировать профиль",
    val newProfileTitle: String = "Новый профиль ИИ",
    val profileNameLabel: String = "Название профиля",
    val providerLabel: String = "Провайдер ИИ",
    val apiKeyLabel: String = "API-ключ",
    val modelIdLabel: String = "Модель",
    val fetchModelsBtn: String = "Загрузить модели",
    val createModuleTitle: String = "Новый учебный модуль",
    val generateWithAi: String = "Сгенерировать с ИИ",
    val photoScannerSubtitle: String = "Сфотографируйте страницу учебника или конспект",
    val importFromTextHint: String = "JSON или CSV / Текст с разделителями",
    val exportModuleBtn: String = "Экспортировать",
    val termsInModule: String = "Термины",
    val greatJob: String = "Отличная работа!",
    val restartBtn: String = "Пройти заново"
)

val LocalStrings = androidx.compose.runtime.staticCompositionLocalOf { StringResources() }
