package com.example.sleepwisepoc

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sleepwisepoc.alarm.AlarmViewModel
import com.example.sleepwisepoc.alarm.SmartAlarmScreen
import com.example.sleepwisepoc.report.SleepReportScreen
import com.example.sleepwisepoc.ui.theme.SleepWisePOCTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var samsungHealthManager: SamsungHealthManager? = null
    private var tfLitePredictor: TFLiteSleepPredictor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        samsungHealthManager = SamsungHealthManager(this)
        val sdkInitialized = samsungHealthManager?.initialize() ?: false

        tfLitePredictor = TFLiteSleepPredictor(this)
        val tfliteInitialized = tfLitePredictor?.initialize() ?: false

        Log.d("MainActivity", "Samsung Health SDK: $sdkInitialized, TFLite: $tfliteInitialized")

        // POST_NOTIFICATIONS is required on Android 13+ for alarm notifications to appear.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    /* requestCode= */ 100,
                )
            }
        }

        setContent {
            SleepWisePOCTheme {
                // Navigation state: null = SmartAlarm, "poc" = POC debug, "demo" = full demo
                var screen by remember { mutableStateOf("alarm") }
                val alarmViewModel: AlarmViewModel = viewModel()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (screen) {
                        "demo" -> DemoScreen(
                            context   = this,
                            predictor = if (tfliteInitialized) tfLitePredictor else null,
                            onBack    = { screen = "poc" },
                        )

                        "poc" -> SleepWisePOCApp(
                            modifier              = Modifier.padding(innerPadding),
                            activity              = this,
                            samsungHealthManager  = if (sdkInitialized) samsungHealthManager else null,
                            tfLitePredictor       = if (tfliteInitialized) tfLitePredictor else null,
                            onStartDemo           = { screen = "demo" },
                            onBack                = { screen = "alarm" },
                        )

                        "report" -> SleepReportScreen(
                            modifier = Modifier.padding(innerPadding),
                            onBack   = { screen = "alarm" },
                        )

                        else -> SmartAlarmScreen(
                            modifier  = Modifier.padding(innerPadding),
                            viewModel = alarmViewModel,
                            onDevMode = { screen = "poc" },
                            onHistory = { screen = "report" },
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tfLitePredictor?.close()
    }
}

// ─── POC / debug screen ───────────────────────────────────────────────────────

@Composable
fun SleepWisePOCApp(
    modifier: Modifier = Modifier,
    activity: Activity,
    samsungHealthManager: SamsungHealthManager?,
    tfLitePredictor: TFLiteSleepPredictor?,
    onStartDemo: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    var predictionResult by remember { mutableStateOf<TFLiteSleepPredictor.SleepPrediction?>(null) }
    var statusMessage    by remember { mutableStateOf("TFLite LSTM Model Ready") }
    var isLoading        by remember { mutableStateOf(false) }
    var bufferStatus     by remember { mutableStateOf("Buffer: 0/10 epochs") }
    var realHealthData   by remember { mutableStateOf("") }
    var realDataPrediction by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Back to alarm screen
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) {
                Text("← Smart Alarm", color = Color(0xFF6875C4))
            }
        }

        Text(text = "Developer Mode", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(text = "ML / Sensor debug", fontSize = 14.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onStartDemo,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) {
            Text("Start Night Demo", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (tfLitePredictor != null) "TFLite Model: Loaded" else "TFLite Model: Failed",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (tfLitePredictor != null) Color(0xFF4CAF50) else Color.Red
                )
                Text(text = "Input: 5 epochs (5 min) × 46 features", fontSize = 12.sp, color = Color.Gray)
                Text(text = "Binary: Deep (N3+REM) vs Light (Wake+N1+N2)", fontSize = 11.sp, color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Demo Scenarios (Mock Data)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(text = "Binary: Deep (bad to wake) vs Light (OK to wake)", fontSize = 14.sp, color = Color.Gray)

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScenarioButton(text = "Deep (N3)", color = Color(0xFF3F51B5), modifier = Modifier.weight(1f)) {
                        scope.launch {
                            isLoading = true
                            statusMessage = "Simulating Deep sleep → expect 'Deep'"
                            predictionResult = runPrediction(tfLitePredictor, "deep")
                            bufferStatus = "Buffer: ${tfLitePredictor?.getBufferSize() ?: 0}/5 epochs"
                            isLoading = false
                        }
                    }
                    ScenarioButton(text = "REM", color = Color(0xFF9C27B0), modifier = Modifier.weight(1f)) {
                        scope.launch {
                            isLoading = true
                            statusMessage = "Simulating REM sleep → expect 'Deep'"
                            predictionResult = runPrediction(tfLitePredictor, "rem")
                            bufferStatus = "Buffer: ${tfLitePredictor?.getBufferSize() ?: 0}/5 epochs"
                            isLoading = false
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScenarioButton(text = "Light", color = Color(0xFF2196F3), modifier = Modifier.weight(1f)) {
                        scope.launch {
                            isLoading = true
                            statusMessage = "Simulating Light sleep → expect 'Light'"
                            predictionResult = runPrediction(tfLitePredictor, "light")
                            bufferStatus = "Buffer: ${tfLitePredictor?.getBufferSize() ?: 0}/5 epochs"
                            isLoading = false
                        }
                    }
                    ScenarioButton(text = "Wake", color = Color(0xFFFF9800), modifier = Modifier.weight(1f)) {
                        scope.launch {
                            isLoading = true
                            statusMessage = "Simulating Wake → expect 'Light'"
                            predictionResult = runPrediction(tfLitePredictor, "wake")
                            bufferStatus = "Buffer: ${tfLitePredictor?.getBufferSize() ?: 0}/5 epochs"
                            isLoading = false
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            statusMessage = "Simulating Deep → Light transition..."
                            predictionResult = runTransitionPrediction(tfLitePredictor)
                            bufferStatus = "Buffer: ${tfLitePredictor?.getBufferSize() ?: 0}/5 epochs"
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("Transition (Deep → Light)", fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(text = bufferStatus, fontSize = 12.sp, color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(text = statusMessage, fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        predictionResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.shouldWake) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("LSTM Prediction", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .background(getSleepStageColor(result.sleepStage), RoundedCornerShape(12.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(text = result.sleepStage, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Confidence: ${(result.confidence * 100).toInt()}%", fontSize = 16.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Probabilities:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            result.probabilities.forEach { (stage, prob) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = stage, fontSize = 12.sp, modifier = Modifier.width(50.dp))
                                    LinearProgressIndicator(
                                        progress = { prob },
                                        modifier = Modifier.weight(1f).height(8.dp),
                                        color = getSleepStageColor(stage),
                                    )
                                    Text(
                                        text = "${(prob * 100).toInt()}%",
                                        fontSize = 12.sp,
                                        modifier = Modifier.width(40.dp),
                                        textAlign = TextAlign.End
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (result.shouldWake) Color(0xFF4CAF50) else Color(0xFFE57373)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (result.shouldWake) "WAKE UP NOW" else "DON'T WAKE",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = result.message,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (samsungHealthManager != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE4EC))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Real Data (Samsung Health)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(text = "SDK Status: Available", fontSize = 12.sp, color = Color(0xFF4CAF50))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { scope.launch { samsungHealthManager.checkAndRequestPermissions(activity) } },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                        ) { Text("Permissions", fontSize = 12.sp) }

                        Button(
                            onClick = { scope.launch { realHealthData = samsungHealthManager.getFormattedHealthData() } },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                        ) { Text("Load Data", fontSize = 12.sp) }

                        Button(
                            onClick = { scope.launch { realHealthData = samsungHealthManager.exportDataToCSV(hoursBack = 24) } },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
                        ) { Text("Export CSV", fontSize = 12.sp) }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                realDataPrediction = "Loading real data..."
                                try {
                                    val epochs = samsungHealthManager.processDataIntoEpochs(hoursBack = 1)
                                    if (epochs.isEmpty()) {
                                        realDataPrediction = "No epochs available. Need more HR data."
                                    } else if (epochs.size < 5) {
                                        realDataPrediction = "Need at least 5 epochs, got ${epochs.size}."
                                    } else if (tfLitePredictor == null) {
                                        realDataPrediction = "Model not loaded"
                                    } else {
                                        tfLitePredictor.clearBuffer()
                                        val lastEpochs = epochs.takeLast(5)
                                        val results = StringBuilder()
                                        results.append("=== REAL DATA PREDICTION ===\n\n")
                                        results.append("Using ${lastEpochs.size} epochs from your watch:\n\n")
                                        lastEpochs.forEachIndexed { idx, epoch ->
                                            val features = samsungHealthManager.epochToFeatures(epoch)
                                            tfLitePredictor.addEpoch(features)
                                            results.append("${idx + 1}. ${epoch.timeString}\n")
                                            results.append("   HR: ${epoch.hrMean.toInt()} bpm (${epoch.hrSampleCount} samples)\n")
                                        }
                                        val prediction = tfLitePredictor.predict()
                                        if (prediction != null) {
                                            results.append("\n--- PREDICTION RESULT ---\n")
                                            results.append("Stage: ${prediction.sleepStage}\n")
                                            results.append("Raw Deep: ${(prediction.rawDeepProb * 100).toInt()}%\n")
                                            results.append("EMA Deep: ${(prediction.emaDeepProb * 100).toInt()}%\n")
                                            results.append("Stable: ${if (prediction.isStable) "YES" else "NO (${prediction.consecutiveCount}/3)"}\n")
                                            results.append("\nRecommendation: ")
                                            results.append(
                                                if (prediction.sleepStage == "Light" && prediction.isStable)
                                                    "Good time to wake up!"
                                                else
                                                    "Stay asleep - deep sleep detected"
                                            )
                                        } else {
                                            results.append("\nPrediction failed")
                                        }
                                        realDataPrediction = results.toString()
                                    }
                                } catch (e: Exception) {
                                    realDataPrediction = "Error: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("Predict from Real Watch Data", fontSize = 14.sp)
                    }

                    if (realHealthData.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(realHealthData, fontSize = 11.sp)
                    }
                    if (realDataPrediction.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                        ) {
                            Text(realDataPrediction, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFECEFF1))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Architecture", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    text = "Watch → Samsung Health → SDK → App\n↓\nFeature Extraction (20 base + 26 temporal)\n↓\nBinary Classifier: Deep vs Not Deep\n↓\nSmart Alarm Decision",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

private suspend fun runPrediction(predictor: TFLiteSleepPredictor?, scenario: String): TFLiteSleepPredictor.SleepPrediction? {
    if (predictor == null) return null
    return withContext(Dispatchers.Default) {
        predictor.clearBuffer()
        for (i in 0 until TFLiteSleepPredictor.SEQUENCE_LENGTH) {
            predictor.addEpoch(predictor.createMockEpoch(scenario, i, TFLiteSleepPredictor.SEQUENCE_LENGTH))
        }
        predictor.predict()
    }
}

private suspend fun runTransitionPrediction(predictor: TFLiteSleepPredictor?): TFLiteSleepPredictor.SleepPrediction? {
    if (predictor == null) return null
    return withContext(Dispatchers.Default) {
        predictor.clearBuffer()
        for (i in 0 until 2) predictor.addEpoch(predictor.createMockEpoch("deep", i, TFLiteSleepPredictor.SEQUENCE_LENGTH))
        for (i in 2 until TFLiteSleepPredictor.SEQUENCE_LENGTH) predictor.addEpoch(predictor.createMockEpoch("light", i, TFLiteSleepPredictor.SEQUENCE_LENGTH))
        predictor.predict()
    }
}

@Composable
fun ScenarioButton(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick   = onClick,
        modifier  = modifier.height(48.dp),
        colors    = ButtonDefaults.buttonColors(containerColor = color),
        shape     = RoundedCornerShape(8.dp)
    ) {
        Text(text, fontSize = 13.sp)
    }
}

fun getSleepStageColor(stage: String): Color = when (stage) {
    "Deep"  -> Color(0xFF3F51B5)
    "Light" -> Color(0xFF4CAF50)
    "Wake"  -> Color(0xFFFF9800)
    "REM"   -> Color(0xFF9C27B0)
    else    -> Color.Gray
}
