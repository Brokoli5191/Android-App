package com.brokoli5191.androidapp

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.brokoli5191.androidapp.data.AppDb
import com.brokoli5191.androidapp.data.ResultDao
import com.brokoli5191.androidapp.data.ResultEntity
import com.brokoli5191.androidapp.ui.theme.AndroidAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dao = AppDb.get(this).resultDao()

        setContent {
            AndroidAppTheme {
                val vm: ResultsViewModel = viewModel(factory = ResultsViewModelFactory(dao))
                ResultsScreen(vm)
            }
        }
    }
}

enum class SortMode(val label: String) {
    CLASS_THEN_NAME("Klasse → Name"),
    CLASS_THEN_SPRINT("Klasse → Sprintzeit"),
    CLASS_THEN_JUMP("Klasse → Sprungweite")
}

class ResultsViewModel(private val dao: ResultDao) : ViewModel() {

    val items = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addOrUpdate(id: Long?, className: String?, name: String, jumpMeters: Double?, sprintSeconds: Double?) {
        viewModelScope.launch {
            val entity = ResultEntity(
                id = id ?: 0,
                className = className?.takeIf { it.isNotBlank() },
                name = name,
                jumpMeters = jumpMeters,
                sprintSeconds = sprintSeconds
            )
            if (id == null) dao.insert(entity) else dao.update(entity)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { dao.deleteById(id) }
    }

    suspend fun exportCsv(context: Context, uri: Uri) {
        val all = dao.getAllOnce()
        val header = "klasse;name;sprung_m;sprint_s\n"
        val lines = buildString {
            append(header)
            for (r in all) {
                val c = (r.className ?: "").replace(";", ",")
                val n = r.name.replace(";", ",")
                val j = r.jumpMeters?.toString() ?: ""
                val s = r.sprintSeconds?.toString() ?: ""
                append(c).append(';').append(n).append(';').append(j).append(';').append(s).append('\n')
            }
        }

        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(lines.toByteArray(Charsets.UTF_8))
            }
        }
    }
}

class ResultsViewModelFactory(private val dao: ResultDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ResultsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ResultsViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(vm: ResultsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val items by vm.items.collectAsStateWithLifecycle()

    var editingId by remember { mutableStateOf<Long?>(null) }

    var className by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var jump by remember { mutableStateOf("") }
    var sprint by remember { mutableStateOf("") }

    var sortMode by remember { mutableStateOf(SortMode.CLASS_THEN_NAME) }
    var classFilter by remember { mutableStateOf<String?>(null) } // null = alle

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri: Uri? ->
            if (uri != null) {
                scope.launch { vm.exportCsv(context, uri) }
            }
        }
    )

    val knownClasses = remember(items) {
        items.mapNotNull { it.className?.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    val filtered = remember(items, classFilter) {
        if (classFilter == null) items
        else items.filter { (it.className ?: "").trim() == classFilter }
    }

    val sortedGrouped = remember(filtered, sortMode) {
        val keyClass: (ResultEntity) -> String = { (it.className ?: "(keine Klasse)").trim().ifBlank { "(keine Klasse)" } }

        val withinComparator: Comparator<ResultEntity> = when (sortMode) {
            SortMode.CLASS_THEN_NAME -> compareBy<ResultEntity> { it.name.lowercase() }
            SortMode.CLASS_THEN_SPRINT -> compareBy(nullsLast()) { it.sprintSeconds }
            SortMode.CLASS_THEN_JUMP -> compareByDescending<ResultEntity> { it.jumpMeters ?: Double.NEGATIVE_INFINITY }
        }

        filtered
            .groupBy(keyClass)
            .toSortedMap(compareBy<String> { it.lowercase() })
            .mapValues { (_, list) -> list.sortedWith(withinComparator) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Athletik") },
                actions = {
                    IconButton(onClick = { exportLauncher.launch("athletik.csv") }) {
                        Icon(Icons.Outlined.Download, contentDescription = "Export")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = className,
                onValueChange = { className = it },
                label = { Text("Klasse") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = jump,
                onValueChange = { jump = it },
                label = { Text("Sprungweite (m) – optional") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = sprint,
                onValueChange = { sprint = it },
                label = { Text("Sprintzeit (s) – optional") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    val n = name.trim()
                    if (n.isEmpty()) return@Button

                    val jm = jump.trim().takeIf { it.isNotEmpty() }?.replace(',', '.')?.toDoubleOrNull()
                    val ss = sprint.trim().takeIf { it.isNotEmpty() }?.replace(',', '.')?.toDoubleOrNull()
                    val c = className.trim().takeIf { it.isNotEmpty() }

                    vm.addOrUpdate(editingId, c, n, jm, ss)

                    editingId = null
                    className = ""; name = ""; jump = ""; sprint = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Speichern")
            }

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                var sortExpanded by remember { mutableStateOf(false) }
                FilledTonalButton(onClick = { sortExpanded = true }) {
                    Icon(Icons.Outlined.Sort, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(sortMode.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                    SortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            onClick = {
                                sortMode = mode
                                sortExpanded = false
                            }
                        )
                    }
                }

                var filterExpanded by remember { mutableStateOf(false) }
                FilledTonalButton(onClick = { filterExpanded = true }) {
                    Icon(Icons.Outlined.FilterList, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(classFilter ?: "Alle Klassen", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Alle Klassen") },
                        onClick = {
                            classFilter = null
                            filterExpanded = false
                        }
                    )
                    knownClasses.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c) },
                            onClick = {
                                classFilter = c
                                filterExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(Modifier.fillMaxSize()) {
                sortedGrouped.forEach { (clazz, list) ->
                    item(key = "h_$clazz") {
                        Text(
                            text = clazz,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(list, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                                    val j = item.jumpMeters?.let { "${it} m" } ?: "–"
                                    val s = item.sprintSeconds?.let { "${it} s" } ?: "–"
                                    Text("Sprung: $j | Sprint: $s")
                                }

                                IconButton(
                                    onClick = {
                                        editingId = item.id
                                        className = item.className ?: ""
                                        name = item.name
                                        jump = item.jumpMeters?.toString() ?: ""
                                        sprint = item.sprintSeconds?.toString() ?: ""
                                    }
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                                }

                                IconButton(onClick = { vm.delete(item.id) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
