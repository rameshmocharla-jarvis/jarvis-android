package com.jarvis.assistant
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var speech: TextToSpeech
    private var recognizer: SpeechRecognizer? = null
    private lateinit var status: TextView
    private var flashOn = false
    private var cameraId: String? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListening()
            else speak("Microphone permission is required.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        speech = TextToSpeech(this, this)
        setupCamera()
        createInterface()
    }

    private fun createInterface() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 30, 28, 30)
            setBackgroundColor(Color.rgb(5, 8, 15))
        }

        val title = TextView(this).apply {
            text = "J.A.R.V.I.S."
            textSize = 34f
            setTextColor(Color.CYAN)
            gravity = Gravity.CENTER
        }

        status = TextView(this).apply {
            text = "SYSTEM ONLINE"
            textSize = 16f
            setTextColor(Color.GREEN)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 30)
        }

        layout.addView(title)
        layout.addView(status)

        addButton(layout, "ACTIVATE VOICE") {
            checkMicrophonePermission()
        }

        addButton(layout, "FLASHLIGHT") {
            toggleFlashlight()
        }

        addButton(layout, "BATTERY STATUS") {
            batteryStatus()
        }

        addButton(layout, "OPEN YOUTUBE") {
            openYouTube()
        }

        addButton(layout, "WI-FI SETTINGS") {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }

        addButton(layout, "BLUETOOTH SETTINGS") {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }

        setContentView(layout)
    }

    private fun addButton(
        layout: LinearLayout,
        text: String,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            this.text = text
            setTextColor(Color.CYAN)
            setBackgroundColor(Color.rgb(18, 35, 48))
            textSize = 15f
            setOnClickListener { action() }
        }

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 8, 0, 8)
        layout.addView(button, params)
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            speech.language = Locale.US
            speak("JARVIS system online. How can I help you?")
        }
    }

    private fun speak(message: String) {
        if (::speech.isInitialized) {
            speech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "JARVIS")
        }
        if (::status.isInitialized) {
            status.text = message
        }
    }

    private fun checkMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("Speech recognition is not available.")
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                speak("Listening.")
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                status.text = "PROCESSING..."
            }

            override fun onError(error: Int) {
                speak("I could not understand. Please try again.")
            }

            override fun onResults(results: Bundle?) {
                val commands = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )
                val command = commands?.firstOrNull()?.lowercase(Locale.US)

                if (command != null) {
                    processCommand(command)
                } else {
                    speak("Please repeat your command.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JARVIS")
        }

        recognizer?.startListening(intent)
    }

    private fun processCommand(command: String) {
        when {
            "hello" in command || "hi" in command ->
                speak("Hello. I am JARVIS. How can I help?")

            "flashlight" in command || "torch" in command ->
                toggleFlashlight()

            "battery" in command ->
                batteryStatus()

            "youtube" in command ->
                openYouTube()

            "wifi" in command || "wi-fi" in command -> {
                speak("Opening Wi-Fi settings.")
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }

            "bluetooth" in command -> {
                speak("Opening Bluetooth settings.")
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }

            "time" in command -> {
                val time = java.text.SimpleDateFormat(
                    "hh:mm a",
                    Locale.getDefault()
                ).format(java.util.Date())
                speak("The time is $time")
            }

            else ->
                speak("I heard $command, but I do not know that command yet.")
        }
    }

    private fun setupCamera() {
        try {
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(
                    android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE
                ) == true
            }
        } catch (_: Exception) {
            cameraId = null
        }
    }

    private fun toggleFlashlight() {
        try {
            val id = cameraId
            if (id == null) {
                speak("Flashlight is not available.")
                return
            }

            flashOn = !flashOn
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            manager.setTorchMode(id, flashOn)

            speak(if (flashOn) "Flashlight turned on." else "Flashlight turned off.")
        } catch (_: Exception) {
            speak("Unable to control flashlight.")
        }
    }

    private fun batteryStatus() {
        val manager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        speak("Your battery level is $level percent.")
    }
private fun openYouTube() {
    try {
        val intent = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("https://www.youtube.com")
        )
        startActivity(intent)
        speak("Opening YouTube.")
    } catch (e: Exception) {
        speak("Unable to open YouTube.")
    }
}

    override fun onDestroy() {
        recognizer?.destroy()
        if (::speech.isInitialized) {
            speech.stop()
            speech.shutdown()
        }
        super.onDestroy()
    }
}
