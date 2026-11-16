package com.example.quizapp.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.quizapp.data.locale.LocalStrings
import com.example.quizapp.data.model.AppLanguage
import com.example.quizapp.data.model.StudyModule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    modules: List<StudyModule>,
    activeLanguage: AppLanguage,
    customFolders: Set<String> = emptySet(),
    folderOrder: List<String> = emptyList(),
    initialTab: Int = 0,
    onSelectModule: (StudyModule) -> Unit,
    onCreateModule: () -> Unit,
    onDeleteModule: (String) -> Unit,
    onUpdateModuleCategory: (String, String) -> Unit = { _, _ -> },
    onCreateFolder: (String) -> Unit = {},
    onRenameFolder: (String, String) -> Unit = { _, _ -> },
    onMoveFolderOrder: (String, Int) -> Unit = { _, _ -> },
    onDeleteFolder: (String) -> Unit = {},
    onAddModulesToFolder: (String, Set<String>) -> Unit = { _, _ -> },
    onRemoveModulesFromFolder: (String, Set<String>) -> Unit = { _, _ -> },
    onNavigateHome: () -> Unit = {}
) {
    val s = LocalStrings.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    val filterTabs = remember(s) {
        listOf(s.allTermsTab, "Мои карточки", s.learningLabel, s.needsReviewTitle, s.masteredLabel, "⭐ Избранные")
    }

    // Collapse/Expand state for folders (default is expanded)
    val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }

    // Dialog state for managing folder contents
    var folderManaging by remember { mutableStateOf<String?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderNameInput by remember { mutableStateOf("") }
    var moduleToMove by remember { mutableStateOf<StudyModule?>(null) }

    val filteredModules = remember(modules, searchQuery, selectedTab) {
        val query = searchQuery.trim()
        modules.filter { mod ->
            val matchesQuery = query.isBlank() ||
                mod.title.contains(query, ignoreCase = true) ||
                mod.author.contains(query, ignoreCase = true) ||
                mod.category.contains(query, ignoreCase = true)
            val matchesTab = when (selectedTab) {
                1 -> !mod.id.startsWith("seed_")
                2 -> mod.learningCount > 0 || (mod.masteredCount > 0 && mod.masteredCount < mod.cards.size)
                3 -> mod.needsReviewCount > 0
                4 -> mod.masteredCount > 0
                5 -> mod.starredCount > 0
                else -> true
            }
            matchesQuery && matchesTab
        }
    }

    // Canonical ordered list of all known folders (for Move/Manage dialogs and ordering)
    val allFolderNames = remember(modules, customFolders, folderOrder) {
        val fromModules = modules.map { it.category.trim().ifBlank { "Общее" } }
        val customClean = customFolders.map { it.trim() }.filter { it.isNotBlank() }
        (folderOrder + fromModules + customClean + listOf("Общее"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    // Fast O(M + F) grouping that ONLY shows folders with matching modules in filtered tabs/searches,
    // and places empty custom folders only in the "Все" tab after populated folders unless explicitly ordered.
    val groupedByCategory = remember(filteredModules, customFolders, allFolderNames, selectedTab, searchQuery) {
        val byLowerCat = filteredModules.groupBy { it.category.trim().ifBlank { "Общее" }.lowercase() }
        val customLowerSet = customFolders.map { it.trim().lowercase() }.toSet()
        val showEmptyCustomFolders = selectedTab == 0 && searchQuery.isBlank()

        val populatedEntries = mutableListOf<Pair<String, List<StudyModule>>>()
        val emptyCustomEntries = mutableListOf<Pair<String, List<StudyModule>>>()

        for (folderName in allFolderNames) {
            val key = folderName.lowercase()
            val list = byLowerCat[key].orEmpty()
            if (list.isNotEmpty()) {
                populatedEntries.add(folderName to list)
            } else if (showEmptyCustomFolders && key in customLowerSet) {
                emptyCustomEntries.add(folderName to emptyList())
            }
        }

        val map = linkedMapOf<String, List<StudyModule>>()
        for ((name, list) in populatedEntries) {
            map[name] = list
        }
        for ((name, list) in emptyCustomEntries) {
            map[name] = list
        }
        map
    }

    val visibleFolderKeys = remember(groupedByCategory) {
        groupedByCategory.keys.toList()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = s.libraryTab,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            newFolderNameInput = ""
                            showCreateFolderDialog = true
                        },
                        modifier = Modifier.testTag("library_add_folder_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "Создать папку",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onCreateModule,
                        modifier = Modifier.testTag("library_add_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = s.createModule,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp
            ) {
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateHome,
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Home,
                            contentDescription = s.homeTab
                        )
                    },
                    label = {
                        Text(text = s.homeTab)
                    },
                    modifier = Modifier.testTag("library_bottom_nav_home")
                )

                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = s.libraryTab
                        )
                    },
                    label = {
                        Text(
                            text = s.libraryTab,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    modifier = Modifier.testTag("library_bottom_nav_library")
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search Bar
            item(key = "search_bar") {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("library_search_input"),
                        placeholder = {
                            Text(
                                text = s.searchHint,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear"
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                }
            }

            // MD3 Filter Chips
            item(key = "filter_chips") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filterTabs.size) { index ->
                        FilterChip(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            label = {
                                Text(
                                    text = filterTabs[index],
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            modifier = Modifier.testTag("library_filter_tab_$index")
                        )
                    }
                }
            }

            if (filteredModules.isEmpty() && groupedByCategory.isEmpty()) {
                item(key = "empty_state") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp, start = 24.dp, end = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when (selectedTab) {
                                1 -> "У вас пока нет созданных модулей"
                                2 -> "Нет изучаемых модулей"
                                3 -> "Нет модулей, требующих повторения"
                                4 -> "Пока нет полностью усвоенных модулей"
                                5 -> "Нет отмеченных карточек"
                                else -> "Модули не найдены"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when (selectedTab) {
                                1 -> "Создайте свой первый учебный модуль или попросите Репетитора"
                                5 -> "Отмечайте сложные термины звёздочкой ⭐ в модуле или карточках, чтобы быстро повторять их"
                                else -> "Попробуйте изменить поисковый запрос или фильтр"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (selectedTab == 1) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = onCreateModule,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Создать модуль")
                            }
                        }
                    }
                }
            }

            groupedByCategory.forEach { (categoryName, categoryModules) ->
                val isExpanded = expandedFolders[categoryName] ?: true
                val folderIndex = visibleFolderKeys.indexOf(categoryName)

                // Folder Header
                item(key = "folder_header_$categoryName") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedFolders[categoryName] = !isExpanded
                            }
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("folder_header_$categoryName"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = categoryName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = "${categoryModules.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Up/Down folder reorder buttons when viewing all folders
                        if (selectedTab == 0 && searchQuery.isBlank() && visibleFolderKeys.size > 1) {
                            IconButton(
                                onClick = { onMoveFolderOrder(categoryName, -1) },
                                enabled = folderIndex > 0,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("folder_move_up_$categoryName")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Переместить папку выше",
                                    tint = if (folderIndex > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onMoveFolderOrder(categoryName, 1) },
                                enabled = folderIndex < visibleFolderKeys.lastIndex,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("folder_move_down_$categoryName")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Переместить папку ниже",
                                    tint = if (folderIndex < visibleFolderKeys.lastIndex) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Manage folder contents button (add / remove modules, rename)
                        IconButton(
                            onClick = { folderManaging = categoryName },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("manage_folder_$categoryName")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Изменить состав папки",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(2.dp))

                        // Arrow to the right of the folder name to collapse/expand
                        IconButton(
                            onClick = {
                                expandedFolders[categoryName] = !isExpanded
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("toggle_folder_$categoryName")
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Render modules in folder only when expanded
                if (isExpanded) {
                    if (categoryModules.isEmpty()) {
                        item(key = "empty_folder_$categoryName") {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Папка пуста. Нажмите на иконку карандаша, чтобы добавить модули.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { folderManaging = categoryName }) {
                                        Text("Добавить", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        items(categoryModules, key = { "mod_${categoryName}_${it.id}" }) { mod ->
                            LibraryModuleMd3Row(
                                module = mod,
                                onClick = { onSelectModule(mod) },
                                onMoveToFolder = { moduleToMove = mod },
                                onRemoveFromFolder = if (!mod.category.equals("Общее", ignoreCase = true)) {
                                    { onUpdateModuleCategory(mod.id, "Общее") }
                                } else null,
                                onDelete = if (!mod.id.startsWith("seed_")) {
                                    { onDeleteModule(mod.id) }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog: Manage Folder Contents (Rename, Add / remove modules with checkboxes, Delete)
    folderManaging?.let { folderName ->
        ManageFolderDialog(
            folderName = folderName,
            allModules = modules,
            isCustomFolder = customFolders.any { it.equals(folderName, ignoreCase = true) } || !folderName.equals("Общее", ignoreCase = true),
            onDismiss = { folderManaging = null },
            onApply = { newName, checkedIds ->
                val cleanNewName = newName.trim().ifBlank { folderName }
                val targetFolder = if (!cleanNewName.equals(folderName, ignoreCase = true)) {
                    onRenameFolder(folderName, cleanNewName)
                    cleanNewName
                } else {
                    folderName
                }
                val currentInFolder = modules.filter {
                    it.category.equals(folderName, ignoreCase = true) || it.category.equals(targetFolder, ignoreCase = true)
                }.map { it.id }.toSet()
                val newlyAdded = checkedIds - currentInFolder
                val newlyRemoved = currentInFolder - checkedIds
                if (newlyAdded.isNotEmpty()) {
                    onAddModulesToFolder(targetFolder, newlyAdded)
                }
                if (newlyRemoved.isNotEmpty()) {
                    onRemoveModulesFromFolder(targetFolder, newlyRemoved)
                }
                folderManaging = null
            },
            onDeleteFolder = {
                onDeleteFolder(folderName)
                folderManaging = null
            }
        )
    }

    // Dialog: Create New Folder
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Новая папка", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Введите название для новой папки:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newFolderNameInput,
                        onValueChange = { newFolderNameInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Например: Английский, Экзамен 2026") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newFolderNameInput.trim()
                        if (clean.isNotBlank()) {
                            onCreateFolder(clean)
                            showCreateFolderDialog = false
                            folderManaging = clean
                        }
                    },
                    enabled = newFolderNameInput.isNotBlank()
                ) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Dialog: Move Single Module to Folder
    moduleToMove?.let { mod ->
        MoveModuleDialog(
            module = mod,
            availableFolders = allFolderNames,
            onDismiss = { moduleToMove = null },
            onSelectFolder = { chosenFolder ->
                onUpdateModuleCategory(mod.id, chosenFolder)
                moduleToMove = null
            },
            onCreateNewFolder = { newName ->
                onCreateFolder(newName)
                onUpdateModuleCategory(mod.id, newName)
                moduleToMove = null
            }
        )
    }
}

@Composable
private fun ManageFolderDialog(
    folderName: String,
    allModules: List<StudyModule>,
    isCustomFolder: Boolean,
    onDismiss: () -> Unit,
    onApply: (String, Set<String>) -> Unit,
    onDeleteFolder: () -> Unit
) {
    var editedName by remember(folderName) { mutableStateOf(folderName) }
    var searchQuery by remember { mutableStateOf("") }
    val initialChecked = remember(folderName, allModules) {
        allModules.filter { it.category.equals(folderName, ignoreCase = true) }.map { it.id }.toSet()
    }
    var checkedIds by remember { mutableStateOf(initialChecked) }

    val filtered = remember(allModules, searchQuery) {
        if (searchQuery.isBlank()) allModules
        else allModules.filter { it.title.contains(searchQuery.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Настройка папки",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Выбрано модулей: ${checkedIds.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("Название папки") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск модулей...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered, key = { it.id }) { mod ->
                        val isChecked = mod.id in checkedIds
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    checkedIds = if (isChecked) checkedIds - mod.id else checkedIds + mod.id
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        checkedIds = if (checked) checkedIds + mod.id else checkedIds - mod.id
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mod.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${mod.cards.size} карточек • папка: ${mod.category}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (isCustomFolder) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onDeleteFolder,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Удалить папку (перенести модули в «Общее»)")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(editedName, checkedIds) },
                enabled = editedName.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun MoveModuleDialog(
    module: StudyModule,
    availableFolders: List<String>,
    onDismiss: () -> Unit,
    onSelectFolder: (String) -> Unit,
    onCreateNewFolder: (String) -> Unit
) {
    var showCreateSubDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    if (showCreateSubDialog) {
        AlertDialog(
            onDismissRequest = { showCreateSubDialog = false },
            title = { Text("Новая папка", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = { Text("Название папки") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newFolderName.trim()
                        if (clean.isNotBlank()) {
                            onCreateNewFolder(clean)
                            showCreateSubDialog = false
                        }
                    },
                    enabled = newFolderName.isNotBlank()
                ) {
                    Text("Создать и переместить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateSubDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "Переместить «${module.title}» в папку",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Текущая папка: ${module.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(availableFolders) { folder ->
                            val isCurrent = folder.equals(module.category, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectFolder(folder) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = folder,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showCreateSubDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Создать другую папку")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun LibraryModuleMd3Row(
    module: StudyModule,
    onClick: () -> Unit,
    onMoveToFolder: () -> Unit,
    onRemoveFromFolder: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    val s = LocalStrings.current
    var showMenu by remember { mutableStateOf(false) }

    OutlinedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("library_row_${module.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Style,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val starredSuffix = if (module.starredCount > 0) " • ⭐ ${module.starredCount}" else ""
                Text(
                    text = "${module.cards.size} ${s.cardsCount} • ${s.masteredLabel}: ${module.masteredCount} • ${s.learningLabel}: ${module.learningCount}$starredSuffix",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Переместить в папку...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = {
                            showMenu = false
                            onMoveToFolder()
                        }
                    )
                    if (onRemoveFromFolder != null) {
                        DropdownMenuItem(
                            text = { Text("Убрать из этой папки") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                showMenu = false
                                onRemoveFromFolder()
                            }
                        )
                    }
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text("Удалить модуль", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}
