package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.CommandLog
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.util.DeviceController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Give warning about stored API key to remain completely secure and professional
        Toast.makeText(
            this,
            "Note: NVIDIA MiniMax M3 is configured. Ensure microphone access is allowed.",
            Toast.LENGTH_LONG
        ).show()

        setContent {
            MyApplicationTheme {
                val isListening by viewModel.isListening.collectAsStateWithLifecycle()
                val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
                val isAlwaysListening by viewModel.alwaysListening.collectAsStateWithLifecycle()
                val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()
                val inputText by viewModel.inputText.collectAsStateWithLifecycle()
                val overrideKey by viewModel.overrideApiKey.collectAsStateWithLifecycle()
                val logs by viewModel.commandLogs.collectAsStateWithLifecycle()

                // Permission launcher
                val micPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        toggleSpeechListening()
                    } else {
                        Toast.makeText(
                            this,
                            "Microphone permission is essential for vocal AI control.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                // Handle system execution triggers
                val executionTrigger by viewModel.executionTrigger.collectAsStateWithLifecycle()
                LaunchedEffect(executionTrigger) {
                    executionTrigger?.let { (action, parameter) ->
                        DeviceController.executeAction(this@MainActivity, action, parameter)
                        viewModel.clearExecutionTrigger()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background
                    ) { innerPadding ->
                        AssistantDashboard(
                            modifier = Modifier.padding(innerPadding),
                            logs = logs,
                            inputText = inputText,
                            isListening = isListening,
                            assistantState = assistantState,
                            isAlwaysListening = isAlwaysListening,
                            feedbackMessage = feedbackMessage,
                            overrideKey = overrideKey,
                            onMicTap = {
                                if (ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    toggleSpeechListening()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            onSendText = { text ->
                                viewModel.onCommandReceived(text)
                                viewModel.updateInputText("")
                            },
                            onInputTextChange = { viewModel.updateInputText(it) },
                            onToggleAlwaysListening = { viewModel.toggleAlwaysListening() },
                            onKeyChange = { viewModel.updateApiKey(it) },
                            onClearHistory = { viewModel.clearHistory() },
                            onDeleteLog = { id -> viewModel.deleteLog(id) }
                        )
                    }
                }
            }
        }
    }

    private fun toggleSpeechListening() {
        if (viewModel.isListening.value) {
            viewModel.stopRecording()
        } else {
            viewModel.startRecording()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}

@Composable
fun AssistantDashboard(
    modifier: Modifier = Modifier,
    logs: List<CommandLog>,
    inputText: String,
    isListening: Boolean,
    assistantState: String,
    isAlwaysListening: Boolean,
    feedbackMessage: String,
    overrideKey: String,
    onMicTap: () -> Unit,
    onSendText: (String) -> Unit,
    onInputTextChange: (String) -> Unit,
    onToggleAlwaysListening: () -> Unit,
    onKeyChange: (String) -> Unit,
    onClearHistory: () -> Unit,
    onDeleteLog: (Int) -> Unit
) {
    var showConfig by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App top header
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AI Assistant",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Application Controller Node",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { showConfig = !showConfig },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (showConfig) Icons.Filled.Close else Icons.Filled.Settings,
                    contentDescription = "Expand configuration settings",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Expanded Configuration Panel
        AnimatedVisibility(
            visible = showConfig,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = borderStroke()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "NVIDIA Model Configuration",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Configures requests targeted to NVIDIA meta/llama-3.1-70b-instruct",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = overrideKey,
                        onValueChange = onKeyChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_override_field"),
                        label = { Text("NVIDIA NIM Override API Key") },
                        placeholder = { Text("nvapi-...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        supportingText = {
                            Text(
                                "Leave empty to use built-in, preinstalled prompt key securely.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Hearing,
                                contentDescription = null,
                                tint = if (isAlwaysListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                        Text(
                            "Continuous wake word",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                                Text(
                                    "Looks for 'Hey assistant'",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isAlwaysListening,
                            onCheckedChange = { onToggleAlwaysListening() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large Glowing Pulse Ring Visualizer & Status Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.1f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Interactive Sine Canvas Soundwave
                AnimatedWaves(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(80.dp),
                    isRunning = isListening || assistantState == "PROCESSING"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action / Command Status Description
                Text(
                    text = assistantState,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    ),
                    color = when (assistantState) {
                        "LISTENING" -> MaterialTheme.colorScheme.primary
                        "PROCESSING" -> MaterialTheme.colorScheme.tertiary
                        "EXECUTING" -> Color(0xFF005AC1)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = feedbackMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Glowing Tactile Mic Trigger Button (Sleek Style)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(160.dp)
                ) {
                    // Outer glow rings
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD3E4FF).copy(alpha = 0.5f))
                    )
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFC7D2FE).copy(alpha = 0.4f)) // indigo-200/40 equivalent
                    )

                    // Core AI Sphere
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = CircleShape,
                                ambientColor = Color.Black,
                                spotColor = Color.Black.copy(alpha = 0.06f)
                            )
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFFD3E4FF), Color(0xFFF1F4FF))
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                            .clickable { onMicTap() }
                            .testTag("tap_to_talk_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        // Center inner knob
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .shadow(elevation = 6.dp, shape = CircleShape)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Voice assistant listen button",
                                modifier = Modifier.size(28.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Built-in Horizontal Preset pills
        Text(
            text = "PRESET SYSTEM INTENT SHORTCUTS",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = listOf(
                Pair("Timer", "Set a 5 minute timer"),
                Pair("Music", "Open YouTube and play some music"),
                Pair("Navigate", "Take me to Starbucks"),
                Pair("Call Mom", "Call mom on phone"),
                Pair("Camera", "Open camera")
            )

            presets.forEach { (label, fullQuery) ->
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clickable {
                            onSendText(fullQuery)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("preset_" + label.lowercase().replace(" ", "_"))
                ) {
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // History Log Timeline
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "COMMAND RUNTIME LOGS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = Color.DarkGray
            )

            if (logs.isNotEmpty()) {
                Text(
                    text = "Clear History",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .clickable { onClearHistory() }
                        .testTag("clear_history_label")
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.3f)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (logs.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = Color.DarkGray,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "No controller instructions logged yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(logs, key = { it.id }) { log ->
                    LogItemCard(log = log, onDelete = { onDeleteLog(log.id) })
                }
            }
        }

        // Bottom manual text control input dock (Single-View Backup UI)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChange,
                modifier = Modifier
                    .weight(1f)
                    .testTag("command_input_field"),
                placeholder = { Text("Type an app control command...", color = Color.Gray) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (inputText.isNotBlank()) {
                        onSendText(inputText)
                        keyboardController?.hide()
                    }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(24.dp)
            )

            FloatingActionButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onSendText(inputText)
                        keyboardController?.hide()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("submit_command_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send action button",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun LogItemCard(log: CommandLog, onDelete: () -> Unit) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(log.timestamp) { formatter.format(Date(log.timestamp)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("log_card_${log.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        border = borderStroke()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Icon + Category + Time + Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (log.actionDetected) {
                            "PLAY_YOUTUBE", "OPEN_YOUTUBE" -> Icons.Default.PlayArrow
                            "OPEN_GALLERY" -> Icons.Default.Image
                            "OPEN_CAMERA" -> Icons.Default.PhotoCamera
                            "WEB_SEARCH" -> Icons.Default.Language
                            else -> Icons.Default.SmartToy
                        },
                        contentDescription = null,
                        tint = when (log.status) {
                            "SUCCESS" -> MaterialTheme.colorScheme.primary
                            "FAILED" -> Color(0xFFFF5252)
                            else -> Color(0xFFFFD700)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (log.actionDetected == "PROCESSING") "PROCESSING" else log.actionDetected,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• $timeStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete item",
                        tint = Color.DarkGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // User audio request
            Text(
                text = "\"${log.inputText}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // AI Action execution description
            Text(
                text = log.responseReply,
                style = MaterialTheme.typography.bodySmall,
                color = if (log.status == "SUCCESS") MaterialTheme.colorScheme.primary.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AnimatedWaves(modifier: Modifier = Modifier, isRunning: Boolean) {
    val transition = rememberInfiniteTransition(label = "canvas_wave_anim")

    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val amplitudeScale by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "amp"
    )

    val idleLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        if (isRunning) {
            val colorList = listOf(
                Color(0xFF005AC1).copy(alpha = 0.8f),
                Color(0xFF001D47).copy(alpha = 0.5f),
                Color(0xFFD3E4FF).copy(alpha = 0.8f)
            )

            colorList.forEachIndexed { index, color ->
                val path = Path()
                path.moveTo(0f, centerY)

                val frequency = 0.015f + (index * 0.005f)
                val amplitude = (25f + (index * 10f)) * amplitudeScale
                val shift = phase + (index * 1.5f)

                for (x in 0..width.toInt() step 5) {
                    val y = centerY + amplitude * sin((x * frequency) + shift)
                    path.lineTo(x.toFloat(), y)
                }

                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = 3f + (index * 1.5f))
                )
            }
        } else {
            drawLine(
                color = idleLineColor,
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 2f
            )
        }
    }
}

@Composable
fun borderStroke() = androidx.compose.foundation.BorderStroke(
    width = 1.dp,
    brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
        )
    )
)
