package pt.mybets.presentation

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.mybets.presentation.util.normalizeKey
import pt.mybets.presentation.viewmodel.BetViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageDataScreen(
    viewModel: BetViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Equipas", "Competições", "Mercados", "Sinónimos")

    val teams by viewModel.teamSuggestions.collectAsState()
    val competitions by viewModel.competitionSuggestions.collectAsState()
    val modalities by viewModel.modalities.collectAsState()
    val aliases by viewModel.nameAliases.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    // Estados para diálogos de renomear e combinar
    var itemToRename by remember { mutableStateOf<Pair<String, String>?>(null) } // category to oldName
    var renameNewValue by remember { mutableStateOf("") }

    var itemToMerge by remember { mutableStateOf<Pair<String, String>?>(null) } // category to sourceName
    var mergeTargetValue by remember { mutableStateOf("") }
    var mergeDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gerir Equipas e Mercados") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            searchQuery = ""
                        },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Pesquisar nesta lista...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true
            )

            val queryKey = searchQuery.normalizeKey()

            when (selectedTab) {
                0 -> {
                    // EQUIPAS
                    val filteredTeams = remember(teams, queryKey) {
                        if (queryKey.isEmpty()) teams else teams.filter { it.normalizeKey().contains(queryKey) }
                    }
                    if (filteredTeams.isEmpty()) {
                        EmptyListNotice("Nenhuma equipa encontrada.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredTeams) { team ->
                                ManageItemCard(
                                    name = team,
                                    onRename = {
                                        itemToRename = Pair("TEAM", team)
                                        renameNewValue = team
                                    },
                                    onMerge = {
                                        itemToMerge = Pair("TEAM", team)
                                        mergeTargetValue = ""
                                    }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // COMPETIÇÕES
                    val filteredCompetitions = remember(competitions, queryKey) {
                        if (queryKey.isEmpty()) competitions else competitions.filter { it.normalizeKey().contains(queryKey) }
                    }
                    if (filteredCompetitions.isEmpty()) {
                        EmptyListNotice("Nenhuma competição encontrada.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredCompetitions) { comp ->
                                ManageItemCard(
                                    name = comp,
                                    onRename = {
                                        itemToRename = Pair("COMPETITION", comp)
                                        renameNewValue = comp
                                    },
                                    onMerge = {
                                        itemToMerge = Pair("COMPETITION", comp)
                                        mergeTargetValue = ""
                                    }
                                )
                            }
                        }
                    }
                }
                2 -> {
                    // MERCADOS (MODALIDADES)
                    val filteredModalities = remember(modalities, queryKey) {
                        if (queryKey.isEmpty()) modalities else modalities.filter { it.name.normalizeKey().contains(queryKey) }
                    }
                    if (filteredModalities.isEmpty()) {
                        EmptyListNotice("Nenhum mercado encontrado.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredModalities) { mod ->
                                ManageItemCard(
                                    name = mod.name,
                                    onRename = {
                                        itemToRename = Pair("MODALITY", mod.name)
                                        renameNewValue = mod.name
                                    },
                                    onMerge = {
                                        itemToMerge = Pair("MODALITY", mod.name)
                                        mergeTargetValue = ""
                                    }
                                )
                            }
                        }
                    }
                }
                3 -> {
                    // SINÓNIMOS / ALIASES
                    val filteredAliases = remember(aliases, queryKey) {
                        if (queryKey.isEmpty()) aliases else aliases.filter {
                            it.alias.normalizeKey().contains(queryKey) || it.canonicalName.normalizeKey().contains(queryKey)
                        }
                    }
                    if (filteredAliases.isEmpty()) {
                        EmptyListNotice("Ainda não existem sinónimos ou combinações guardadas.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredAliases) { alias ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = "${alias.alias} ➔ ${alias.canonicalName}",
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Categoria: ${alias.category}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Gray
                                            )
                                        }
                                        IconButton(onClick = {
                                            viewModel.deleteAlias(alias.id)
                                            Toast.makeText(context, "Sinónimo removido", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.Delete, "Remover", tint = MaterialTheme.colorScheme.error)
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

    // DIÁLOGO PARA RENOMEAR
    if (itemToRename != null) {
        val (category, oldName) = itemToRename!!
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("Renomear $category") },
            text = {
                Column {
                    Text("Altera o nome de '$oldName'. Todos os registos passados serão atualizados com o novo nome.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = renameNewValue,
                        onValueChange = { renameNewValue = it },
                        label = { Text("Novo Nome") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newName = renameNewValue.trim()
                        if (newName.isNotBlank() && !newName.equals(oldName, ignoreCase = true)) {
                            when (category) {
                                "TEAM" -> viewModel.renameTeam(oldName, newName)
                                "COMPETITION" -> viewModel.renameCompetition(oldName, newName)
                                "MODALITY" -> {
                                    val modEntity = modalities.find { it.name.equals(oldName, ignoreCase = true) }
                                    if (modEntity != null) {
                                        viewModel.renameModality(modEntity.id, newName)
                                    }
                                }
                            }
                            Toast.makeText(context, "Nome atualizado para '$newName'!", Toast.LENGTH_SHORT).show()
                        }
                        itemToRename = null
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) { Text("Cancelar") }
            }
        )
    }

    // DIÁLOGO PARA COMBINAR / FUNDIR
    if (itemToMerge != null) {
        val (category, sourceName) = itemToMerge!!
        val availableTargets = when (category) {
            "TEAM" -> teams.filter { !it.equals(sourceName, ignoreCase = true) }
            "COMPETITION" -> competitions.filter { !it.equals(sourceName, ignoreCase = true) }
            "MODALITY" -> modalities.map { it.name }.filter { !it.equals(sourceName, ignoreCase = true) }
            else -> emptyList()
        }

        AlertDialog(
            onDismissRequest = { itemToMerge = null },
            title = { Text("Combinar '$sourceName'") },
            text = {
                Column {
                    Text("Todos os registos associados a '$sourceName' passarão a ficar associados ao nome selecionado. No futuro, ambos os nomes serão reconhecidos como o mesmo.")
                    Spacer(Modifier.height(16.dp))

                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { mergeDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (mergeTargetValue.isNotBlank()) mergeTargetValue else "Selecionar nome de destino...")
                        }

                        DropdownMenu(
                            expanded = mergeDropdownExpanded,
                            onDismissRequest = { mergeDropdownExpanded = false },
                            modifier = Modifier.heightIn(max = 240.dp)
                        ) {
                            availableTargets.forEach { target ->
                                DropdownMenuItem(
                                    text = { Text(target) },
                                    onClick = {
                                        mergeTargetValue = target
                                        mergeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = mergeTargetValue.trim()
                        if (target.isNotBlank()) {
                            when (category) {
                                "TEAM" -> viewModel.mergeTeams(sourceName, target)
                                "COMPETITION" -> viewModel.mergeCompetitions(sourceName, target)
                                "MODALITY" -> {
                                    val sourceMod = modalities.find { it.name.equals(sourceName, ignoreCase = true) }
                                    val targetMod = modalities.find { it.name.equals(target, ignoreCase = true) }
                                    if (sourceMod != null && targetMod != null) {
                                        viewModel.mergeModalities(sourceMod.id, targetMod.id, sourceName, target)
                                    }
                                }
                            }
                            Toast.makeText(context, "'$sourceName' combinado com '$target'!", Toast.LENGTH_SHORT).show()
                        }
                        itemToMerge = null
                    },
                    enabled = mergeTargetValue.isNotBlank()
                ) {
                    Text("Combinar")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToMerge = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun ManageItemCard(
    name: String,
    onRename: () -> Unit,
    onMerge: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRename, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("Renomear", style = MaterialTheme.typography.bodySmall)
                }
                FilledTonalButton(onClick = onMerge, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("Combinar", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun EmptyListNotice(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
    }
}
