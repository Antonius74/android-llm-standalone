package com.example.androidllm

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.androidllm.ui.theme.AndroidLLMTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndroidLLMTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatScreen()
                }
            }
        }
    }
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val llm = LlmInference()
    private val modelFile: File = File(application.filesDir, "gemma-4b-it.gguf")

    var messages by mutableStateOf(listOf<Pair<String, Boolean>>())
        private set
    var isGenerating by mutableStateOf(false)
        private set
    var modelReady by mutableStateOf(false)
        private set
    var statusText by mutableStateOf("Model not loaded")
        private set

    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (modelFile.exists() && modelFile.length() > 0) {
                loadModel()
            } else {
                statusText = "Model missing. Place ${modelFile.name} in files dir or use download."
            }
        }
    }

    fun loadModel() {
        viewModelScope.launch(Dispatchers.IO) {
            statusText = "Loading model..."
            val ok = llm.loadModel(modelFile.absolutePath)
            modelReady = ok
            statusText = if (ok) "Model loaded" else "Failed to load model"
        }
    }

    fun sendMessage(prompt: String) {
        if (prompt.isBlank() || isGenerating || !modelReady) return
        messages = messages + (prompt to true)
        isGenerating = true
        viewModelScope.launch(Dispatchers.IO) {
            val reply = llm.generate(prompt)
            withContext(Dispatchers.Main) {
                messages = messages + (reply to false)
                isGenerating = false
            }
        }
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val context = LocalContext.current
    var input by remember { mutableStateOf(TextFieldValue("")) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = viewModel.statusText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            reverseLayout = true
        ) {
            items(viewModel.messages.reversed()) { (text, isUser) ->
                Bubble(text = text, isUser = isUser)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a prompt...") },
                enabled = viewModel.modelReady && !viewModel.isGenerating
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    viewModel.sendMessage(input.text)
                    input = TextFieldValue("")
                },
                enabled = viewModel.modelReady && !viewModel.isGenerating && input.text.isNotBlank()
            ) {
                Text("Send")
            }
        }
        if (!viewModel.modelReady) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.loadModel() },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Retry load model")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { downloadModel(context) },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Download demo model (see docs)")
            }
        }
    }
}

@Composable
fun Bubble(text: String, isUser: Boolean) {
    val bg = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = bg,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

private fun downloadModel(context: android.content.Context) {
    // The model is large. Provide a placeholder action; users should download via browser/ADB.
    // For a real implementation use DownloadManager / WorkManager.
    android.widget.Toast.makeText(
        context,
        "Please download a Gemma-4B GGUF and push it to ${context.filesDir}/gemma-4b-it.gguf",
        android.widget.Toast.LENGTH_LONG
    ).show()
}
