package com.vigicamia.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        updateSystemBars(resources.configuration.orientation)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    VigiCamScreen()
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateSystemBars(newConfig.orientation)
    }

    private fun updateSystemBars(orientation: Int) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VigiCamScreen() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val clipboardManager = LocalClipboardManager.current
    val prefs = remember { PrefsManager(context) }
    val alertManager = remember { AlertManager(context) }
    val detectorHelper = remember { ObjectDetectorHelper(context) }

    var ipInput by remember { mutableStateOf(prefs.cameraIp) }
    var recentIps by remember { mutableStateOf(prefs.getRecentIps()) }
    var userInput by remember { mutableStateOf(prefs.cameraUser) }
    var passInput by remember { mutableStateOf(prefs.cameraPass) }
    var portInput by remember { mutableStateOf(prefs.streamPort) }
    var pathInput by remember { mutableStateOf(prefs.streamPath) }
    var showConfigDialog by remember { mutableStateOf(false) }

    // En horizontal permite tocar la pantalla para ocultar/mostrar botones y dejar el video 100% limpio
    var showLandscapeHud by remember { mutableStateOf(true) }

    var isConnected by remember { mutableStateOf(false) }
    var aiStatus by remember { mutableStateOf("Iniciando IA...") }
    var aiReady by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Listo. Escribe la IP completa y pulsa Conectar.") }
    var detections by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var intruderActive by remember { mutableStateOf(false) }
    var frameWidth by remember { mutableIntStateOf(1) }
    var frameHeight by remember { mutableIntStateOf(1) }

    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }

    val libVLC = remember {
        LibVLC(context, arrayListOf("--no-drop-late-frames", "--no-skip-frames", "--rtsp-tcp"))
    }
    val mediaPlayer = remember { MediaPlayer(libVLC) }

    // Ajusta el video al 100% de la pantalla sin franjas negras al girar
    LaunchedEffect(isLandscape, configuration.screenWidthDp, configuration.screenHeightDp, isConnected) {
        if (isLandscape && configuration.screenWidthDp > 0 && configuration.screenHeightDp > 0) {
            mediaPlayer.aspectRatio = "${configuration.screenWidthDp}:${configuration.screenHeightDp}"
            mediaPlayer.scale = 0f
        } else {
            mediaPlayer.aspectRatio = null
            mediaPlayer.scale = 0f
            showLandscapeHud = true
        }
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val numberUri = Uri.parse("tel:+56987296485")
        val intent = if (granted) Intent(Intent.ACTION_CALL, numberUri) else Intent(Intent.ACTION_DIAL, numberUri)
        context.startActivity(intent)
    }

    fun callCarabineros() {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:+56987296485")))
        } else {
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    fun toggleConnection() {
        if (!isConnected) {
            if (ipInput.isBlank()) {
                statusText = "Ingresa una dirección IP válida primero."
                return
            }
            prefs.cameraIp = ipInput
            recentIps = prefs.getRecentIps()
            val url = prefs.buildStreamUrl(ipInput)
            statusText = "Conectando a $url"
            val media = Media(libVLC, Uri.parse(url)).apply {
                setHWDecoderEnabled(false, false)
                addOption(":network-caching=300")
            }
            mediaPlayer.media = media
            media.release()
            mediaPlayer.play()
            isConnected = true
            statusText = "En vivo ($ipInput) • $aiStatus"
        } else {
            mediaPlayer.stop()
            isConnected = false
            statusText = "Desconectado. Puedes cambiar la IP completa."
        }
    }

    LaunchedEffect(Unit) {
        val res = detectorHelper.initialize()
        if (res == "OK") {
            aiReady = true
            aiStatus = "IA Lista (ML Kit Offline)"
        } else {
            aiReady = false
            aiStatus = "Error IA: $res"
        }
    }

    LaunchedEffect(isConnected, aiReady) {
        while (isActive && isConnected && aiReady) {
            val tv = textureViewRef
            if (tv != null && tv.isAvailable && tv.width > 0 && tv.height > 0) {
                val bmp: Bitmap? = try { tv.bitmap } catch (_: Exception) { null }
                if (bmp != null) {
                    frameWidth = bmp.width
                    frameHeight = bmp.height
                    val found = detectorHelper.detect(bmp)
                    detections = found
                    val personDetected = found.any { it.isPerson }
                    intruderActive = personDetected
                    if (personDetected) {
                        alertManager.triggerIntruderAlert()
                    }
                }
            }
            delay(400L)
        }
        if (!isConnected) {
            detections = emptyList()
            intruderActive = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer.stop()
                mediaPlayer.vlcVout.detachViews()
                mediaPlayer.release()
                libVLC.release()
                alertManager.shutdown()
            } catch (_: Exception) {}
        }
    }

    // Estructura única: el video NUNCA se destruye al girar, evitando colapsos
    Column(
        modifier = Modifier
            .fillMaxSize()
.background(if (isLandscape) Color.Black else Color(0xFF101318))
.padding(if (isLandscape) 0.dp else 12.dp)
) {
if (!isLandscape) {
Row(
modifier = Modifier.fillMaxWidth(),
horizontalArrangement = Arrangement.SpaceBetween,
verticalAlignment = Alignment.CenterVertically
) {
Column {
Text("VigiCam IA • V380", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
Text(aiStatus, fontSize = 12.sp, color = if (aiReady) Color(0xFF81C784) else Color(0xFFFF8A65))
}
IconButton(onClick = { showConfigDialog = true }) {
Icon(Icons.Default.Settings, contentDescription = "Ajustes RTSP", tint = Color(0xFF64B5F6))
}
}
Row(
modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
verticalAlignment = Alignment.CenterVertically
) {
OutlinedTextField(
value = ipInput,
onValueChange = { ipInput = it },
enabled = !isConnected,
label = { Text("IP completa o URL") },
placeholder = { Text("Ej: 192.168.1.50") },
singleLine = true,
keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
trailingIcon = {
if (!isConnected) {
if (ipInput.isNotEmpty()) {
IconButton(onClick = { ipInput = "" }) {
Icon(Icons.Default.Clear, contentDescription = "Borrar IP completa")
}
} else {
IconButton(onClick = {
clipboardManager.getText()?.text?.let { pasted ->
ipInput = pasted.trim()
}
}) {
Icon(Icons.Default.ContentPaste, contentDescription = "Pegar IP")
}
}
}
},
modifier = Modifier.weight(1f)
)
Spacer(modifier = Modifier.width(8.dp))
Button(
onClick = { toggleConnection() },
colors = ButtonDefaults.buttonColors(
containerColor = if (isConnected) Color(0xFFD32F2F) else Color(0xFF2E7D32)
),
modifier = Modifier.height(56.dp)
) {
Icon(if (isConnected) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
Spacer(Modifier.width(4.dp))
Text(if (isConnected) "Parar" else "Conectar")
}
}
if (recentIps.isNotEmpty() && !isConnected) {
Row(
modifier = Modifier
.fillMaxWidth()
.horizontalScroll(rememberScrollState())
.padding(vertical = 4.dp),
horizontalArrangement = Arrangement.spacedBy(6.dp)
) {
recentIps.forEach { savedIp ->
FilterChip(
selected = ipInput == savedIp,
onClick = { ipInput = savedIp },
label = { Text(savedIp, fontSize = 12.sp) }
)
}
}
}
if (intruderActive) {
Card(
colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C)),
modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
) {
Row(
modifier = Modifier.padding(12.dp),
verticalAlignment = Alignment.CenterVertically
) {
Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(32.dp))
Spacer(Modifier.width(10.dp))
Text(
"¡ALERTA INTRUSO DETECTADO!",
color = Color.White,
fontWeight = FontWeight.ExtraBold,
fontSize = 17.sp
)
}
}
}
}
// Visor de video único (ocupa 100% en horizontal; tócalo para ocultar/mostrar botones)
Box(
modifier = Modifier
.fillMaxWidth()
.weight(1f)
.padding(top = if (isLandscape) 0.dp else 4.dp)
.background(Color.Black)
.border(
width = if (intruderActive) 4.dp else 0.dp,
color = if (intruderActive) Color.Red else Color.Transparent
)
.clickable(
interactionSource = remember { MutableInteractionSource() },
indication = null
) {
if (isLandscape) showLandscapeHud = !showLandscapeHud
}
) {
AndroidView(
factory = { ctx ->
TextureView(ctx).also { tv ->
textureViewRef = tv
mediaPlayer.vlcVout.setVideoView(tv)
mediaPlayer.vlcVout.attachViews()
}
},
modifier = Modifier.fillMaxSize()
)
Canvas(modifier = Modifier.fillMaxSize()) {
val scaleX = size.width / frameWidth.toFloat()
val scaleY = size.height / frameHeight.toFloat()
detections.forEach { det ->
val boxColor = if (det.isPerson) Color.Red else Color.Green
drawRect(
color = boxColor,
topLeft = Offset(det.boundingBox.left * scaleX, det.boundingBox.top * scaleY),
size = Size(det.boundingBox.width() * scaleX, det.boundingBox.height() * scaleY),
style = Stroke(width = 7f)
)
}
}
if (!isLandscape || showLandscapeHud) {
Text(
text = if (isLandscape) "$statusText (Toca pantalla para ocultar botones)" else statusText,
color = Color.White,
fontSize = 11.sp,
modifier = Modifier
.align(Alignment.TopStart)
.background(Color(0x88000000))
.padding(6.dp)
)
}
if (isLandscape) {
if (intruderActive) {
Card(
colors = CardDefaults.cardColors(containerColor = Color(0xDDB71C1C)),
modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
) {
Row(
modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
verticalAlignment = Alignment.CenterVertically
) {
Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Yellow)
Spacer(Modifier.width(8.dp))
Text(
"¡ALERTA INTRUSO DETECTADO!",
color = Color.White,
fontWeight = FontWeight.ExtraBold,
fontSize = 16.sp
)
}
}
}
if (showLandscapeHud) {
Row(
modifier = Modifier
.align(Alignment.BottomCenter)
.fillMaxWidth()
.background(Color(0x88000000))
.padding(horizontal = 16.dp, vertical = 6.dp),
horizontalArrangement = Arrangement.SpaceBetween,
verticalAlignment = Alignment.CenterVertically
) {
Button(
onClick = { toggleConnection() },
colors = ButtonDefaults.buttonColors(
containerColor = if (isConnected) Color(0xFFD32F2F) else Color(0xFF2E7D32)
)
) {
Icon(if (isConnected) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
Spacer(Modifier.width(4.dp))
Text(if (isConnected) "Parar" else "Conectar")
}
Text(
text = if (detections.isEmpty()) "Sin detecciones"
else detections.joinToString { "${it.categoryEs} (${(it.score * 100).toInt()}%)" },
color = Color.White,
fontSize = 12.sp
)
Button(
onClick = { callCarabineros() },
colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
) {
Icon(Icons.Default.Call, contentDescription = null, tint = Color.White)
Spacer(Modifier.width(6.dp))
Text("CARABINEROS", color = Color.White, fontWeight = FontWeight.Bold)
}
}
}
}
}
if (!isLandscape) {
Text(
text = if (detections.isEmpty()) "Detecciones: Ninguna"
else "Detectado: " + detections.joinToString { "${it.categoryEs} (${(it.score * 100).toInt()}%)" },
color = Color(0xFFB0BEC5),
fontSize = 13.sp,
modifier = Modifier.padding(vertical = 8.dp)
)
Button(
onClick = { callCarabineros() },
colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
shape = RoundedCornerShape(10.dp),
modifier = Modifier.fillMaxWidth().height(58.dp)
) {
Icon(Icons.Default.Call, contentDescription = null, tint = Color.White)
Spacer(Modifier.width(10.dp))
Text(
"LLAMAR A CARABINEROS (+56 9 8729 6485)",
color = Color.White,
fontWeight = FontWeight.Bold,
fontSize = 14.sp
)
}
}
}
if (showConfigDialog) {
AlertDialog(
onDismissRequest = { showConfigDialog = false },
title = { Text("Parámetros de conexión VLC") },
text = {
Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
OutlinedTextField(value = userInput, onValueChange = { userInput = it }, label = { Text("Usuario (opcional)") })
OutlinedTextField(value = passInput, onValueChange = { passInput = it }, label = { Text("Contraseña (opcional)") })
OutlinedTextField(value = portInput, onValueChange = { portInput = it }, label = { Text("Puerto (ej. 554)") })
OutlinedTextField(value = pathInput, onValueChange = { pathInput = it }, label = { Text("Ruta (ej. /live/ch00_1)") })
}
},
confirmButton = {
TextButton(onClick = {
prefs.cameraUser = userInput
prefs.cameraPass = passInput
prefs.streamPort = portInput
prefs.streamPath = pathInput
showConfigDialog = false
}) { Text("Guardar") }
},
dismissButton = {
TextButton(onClick = { showConfigDialog = false }) { Text("Cancelar") }
}
)
}
}
