package com.brokoli5191.androidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

class ResultsViewModel(private val dao: ResultDao) : ViewModel() {

    val items = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(name: String, jumpMeters: Double, sprintSeconds: Double) {
        viewModelScope.launch {
            dao.insert(ResultEntity(name = name, jumpMeters = jumpMeters, sprintSeconds = sprintSeconds))
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { dao.deleteById(id) }
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
    val items by vm.items.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var jump by remember { mutableStateOf("") }
    var sprint by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Athletik") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
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
                label = { Text("Sprungweite (m)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = sprint,
                onValueChange = { sprint = it },
                label = { Text("Sprintzeit (s)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    val n = name.trim()
                    val jm = jump.replace(',', '.').toDoubleOrNull()
                    val ss = sprint.replace(',', '.').toDoubleOrNull()
                    if (n.isEmpty() || jm == null || ss == null) return@Button
                    vm.add(n, jm, ss)
                    name = ""; jump = ""; sprint = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Speichern")
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                Text("Sprung: ${item.jumpMeters} m | Sprint: ${item.sprintSeconds} s")
                            }
                            TextButton(onClick = { vm.delete(item.id) }) {
                                Text("Löschen")
                            }
                        }
                    }
                }
            }
        }
    }
}
