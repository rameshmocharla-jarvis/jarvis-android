import android.content.IntentFilter
package com.jarvis.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var status: TextView
    private lateinit var tts: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null
    private var flashOn = false

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) startListening()
            else speak("Microphone permission denied")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(35, 60, 35, 35)
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        status = TextView(this).apply {
            text = "J.A.R.V.I.S.\nSYSTEM ONLINE"
            textSize = 27f
            setTextColor(android.graphics.Color.CYAN)
            gravity = android.view.Gravity.CENTER
        }

        val voiceButton = Button(this).apply {
            text = "ACTIVATE VOICE"
            setOnClickListener { checkMicrophone() }
        }

        val flashlightButton = Button(this).apply {
            text = "FLASHLIGHT"
            setOnClickListener { toggleFlashlight() }
        }

        val volumeButton = Button(this).apply {
            text = "VOLUME SETTINGS"
            setOnClickListener {
                startActivity(
                    Intent("android.settings.SOUND_SETTINGS")
                )
            }
        }

        layout.addView(status)
        layout.addView(voiceButton)
        layout.addView(flashlightButton)
        layout.addView(volumeButton)

        setContentView(layout)
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            speak("JARVIS system online")
        }
    }

    private fun speak(message: String) {
        if (::tts.isInitialized) {
            tts.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "JARVIS"
            )
        }
        status.text = "J.A.R.V.I.S.\n$message"
    }

    private fun checkMicrophone() {
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
            speak("Speech recognition is unavailable")
            return
        }

        speechRecognizer?.destroy()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(
            object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    status.text = "LISTENING..."
                }

                override fun onResults(results: Bundle?) {
                    val words = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )
                    val command = words?.firstOrNull()?.lowercase() ?: ""
                    handleCommand(command)
                }

                override fun onError(error: Int) {
                    status.text = "VOICE ERROR: $error"
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            }
        )

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        speechRecognizer?.startListening(intent)
    }

    private fun handleCommand(command: String) {
        when {
            command.contains("flashlight on") ||
            command.contains("torch on") -> {
                setFlashlight(true)
                speak("Flashlight turned on")
            }

            command.contains("flashlight off") ||
            command.contains("torch off") -> {
                setFlashlight(false)
                speak("Flashlight turned off")
            }

            command.contains("volume") -> {
                startActivity(Intent("android.settings.SOUND_SETTINGS"))
                speak("Opening volume settings")
            }

            command.contains("wifi") -> {
                startActivity(Intent("android.settings.WIFI_SETTINGS"))
                speak("Opening Wi-Fi settings")
            }

            command.contains("bluetooth") -> {
                startActivity(Intent("android.settings.BLUETOOTH_SETTINGS"))
                speak("Opening Bluetooth settings")
            }

            command.contains("battery") -> {
                val battery = registerReceiver(
                    null,
                    IntentFilter("android.intent.action.BATTERY_CHANGED")
                )
                val level = battery?.getIntExtra("level", -1) ?: -1
                speak("Battery level is $level percent")
            }

            command.contains("youtube") -> {
                val intent = packageManager.getLaunchIntentForPackage(
                    "com.google.android.youtube"
                )
                if (intent != null) startActivity(intent)
                else speak("YouTube is not installed")
            }

            else -> speak("Command not recognized: $command")
        }
    }

    private fun toggleFlashlight() {
        setFlashlight(!flashOn)
        speak(if (flashOn) "Flashlight on" else "Flashlight off")
    }

    private fun setFlashlight(enabled: Boolean) {
        try {
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }

            if (cameraId == null) {
                speak("Flashlight unavailable")
                return
            }

            manager.setTorchMode(cameraId, enabled)
            flashOn = enabled
        } catch (e: Exception) {
            speak("Unable to control flashlight")
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}

