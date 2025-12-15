package com.example.irisaid

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PointF
import android.graphics.Rect
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Layout
import android.text.StaticLayout
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.view.Surface
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.lang.IllegalStateException
import kotlin.math.abs
import kotlin.math.sqrt


class ModeActivity : ComponentActivity(), HandLandmarkerHelper.LandmarkerListener {
    // Initialize text size instances
    private var minTextSize = 10f                                               // minimum text size
    private var maxTextSize = 60f                                               // maximum text size
    private var currentTextSize = 20f                                           // default text size
    private val smoothingFactor = 0.1                                           // lower = more stable, less sensitive
    private val maxDeltaPerFrame = 3f                                           // max text size change per frame
    private val pinchThreshold = 0.5f                                           // fingers must be closer than this to trigger zoom
    private var previousDistance = 0.0                                          // last measured face distance for reference
    private var smoothedDistance = 0.0                                          // smoothed distance to reduce jitter

    // Initialize voice command instances
    private var speechRecognizer: SpeechRecognizer? = null                      // SpeechRecognizer object
    private var recognizerIntent: Intent? = null                                // SpeechRecognizer intent
    private var isRecording = false                                             // is mic recording
    private var previousVolume = 0                                              // system sound volume
    private var startSound = 0                                                  // start sound media
    private var stopSound = 0                                                   // stop sound media

    // Initialize camera instances
    private var camera: Camera? = null                                          // camera object
    private var preview: Preview? = null                                        // camera preview
    private lateinit var previewView: PreviewView                               // camera preview view
    private var cameraProvider: ProcessCameraProvider? = null                   // camera provider
    private var imageAnalyzer: ImageAnalysis? = null                            // image analyzer

    // Initialize hand instances
    private var handVisible = false                                             // is hand currently detected
    private var swipeStartX: Float? = null                                      // starting X position before swipe
    private var swipeActive = false                                             // is swipe active
    private var lastSwipeTime = 0L                                              // last time swipe gesture was read
    private var pinchStartTime = 0L                                             // pinch gesture start time
    private val pinchActivationDelay = 1000L                                    // delay on reading pinch gesture
    private val swipeThreshold = 0.15f                                          // percentage of screen swipe takes up
    private val fingerProximityThreshold = 0.08f                                // finge proximity threshold

    // Initialize face instances
    private var lastFaceArea = 0f                                               // area of last face box
    private var zoomActive = false                                              // is zoom active
    private var zoomStartTime = 0L                                              // zoom feature start time
    private var zoomCooldownUntil = 0L                                          // time left before reading next zoom change
    private val swipeCooldown = 1000L                                           // cooldown

    // Instantiate dropdown menu and TextView instances
    private lateinit var spinner: Spinner                                       // dropdown menu object
    private lateinit var adapter: ArrayAdapter<CharSequence>                    // dropdown menu text adapter
    private lateinit var excerptText: TextView                                  // book excerpt text view
    private lateinit var pageIndicator: TextView                                // page indicator text view

    // Instantiate paginate instances
    private var fullText: String = ""                                           // single full page text
    private var pageTexts: MutableList<String> = mutableListOf()                // list of pages
    private var currentPageIndex = 0                                            // current page

    // Instantiate music, voice command, hand landmarker, face detection instances
    private lateinit var soundPool: SoundPool                                   // mobile sound pool
    private lateinit var mediaPlayer: MediaPlayer                               // media player object
    private lateinit var playButton: Button                                     // play button for media
    private lateinit var voiceButton: Button                                    // voice button for mic recording
    private lateinit var prevButton: Button                                     // previous page button
    private lateinit var nextButton: Button                                     // next page button
    private lateinit var handLandmarkerHelper: HandLandmarkerHelper             // hand landmarker object
    private lateinit var faceDetector: FaceDetector                             // face detector object
    private lateinit var handOverlay: HandOverlayView                           // hand overlay for camera preview
    private lateinit var faceOverlay: FaceOverlayView                           // face overlay for camera preview
    private lateinit var zoomText: TextView                                     // zoom percentage text view


    // Represents activity launcher that checks for camera permissions
    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value) permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(
                    baseContext,
                    "Camera or microphone permission request denied",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                setupVoiceButton()
                setUpCamera()
            }
        }

    // Represents array of required permission objects
    companion object {
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
            ).toTypedArray()
    }

    // ---------------------- OVERRIDE FUNCTIONS ---------------------- //

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = previewView.display.rotation
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_mode)

        // Finds view instances by ID
        spinner = findViewById(R.id.modeSpinner)
        excerptText = findViewById(R.id.excerptText)
        zoomText = findViewById(R.id.zoomText)
        pageIndicator = findViewById(R.id.pageIndicator)
        playButton = findViewById(R.id.playButton)
        voiceButton = findViewById(R.id.voiceButton)
        prevButton = findViewById(R.id.prevButton)
        nextButton = findViewById(R.id.nextButton)
        previewView = findViewById(R.id.previewView)
        handOverlay = findViewById(R.id.handOverlay)
        faceOverlay = findViewById(R.id.faceOverlay)

        // Initialize spinner (dropdown menu for modes)
        val prefs = getSharedPreferences("zoom_prefs", Context.MODE_PRIVATE)
        val savedMode = prefs.getString("current_mode", "🔴 Button + Swipe")
        adapter = ArrayAdapter.createFromResource(
            this,
            R.array.zoom_modes,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(adapter.getPosition(savedMode))

        // Update spinner across all activity pages where spinner is used to synchronize correctly
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedMode = parent.getItemAtPosition(position).toString()
                prefs.edit { putString("current_mode", selectedMode) }
                Toast.makeText(this@ModeActivity, "Selected: $selectedMode", Toast.LENGTH_SHORT).show()
                updateMode(selectedMode)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Change excerpt text in TextView based on selected book cover
        excerptText.text = when (intent.getStringExtra("BOOK_ID") ?: "book1") {
            "book1" -> getString(R.string.book1_excerpt)
            "book2" -> getString(R.string.book2_excerpt)
            "book3" -> getString(R.string.book3_excerpt)
            else -> "No excerpt found."
        }
        fullText = excerptText.text.toString()

        // Prepare pagination and text zoom percentage
        preparePages()
        showPage(0)
        updateZoomPercentage(currentTextSize)

        // Set on-click listener for previous page button
        prevButton.setOnClickListener {
            showPrevPage()
        }
        // Set on-click listener for next page button
        nextButton.setOnClickListener {
            showNextPage()
        }

        // Initialize media player for testing volume zoom button feature
        mediaPlayer = MediaPlayer.create(this, R.raw.demo)
        soundPool = SoundPool.Builder().setMaxStreams(2).build()
        startSound = soundPool.load(this, R.raw.start, 1)
        stopSound = soundPool.load(this, R.raw.stop, 1)

        // Initialize hand landmarker for Pinch Gesture
        handLandmarkerHelper = HandLandmarkerHelper(
            context = this,
            runningMode = RunningMode.LIVE_STREAM,
            minHandDetectionConfidence = HandLandmarkerHelper.DEFAULT_HAND_DETECTION_CONFIDENCE,
            minHandPresenceConfidence = HandLandmarkerHelper.DEFAULT_HAND_PRESENCE_CONFIDENCE,
            minHandTrackingConfidence = HandLandmarkerHelper.DEFAULT_HAND_TRACKING_CONFIDENCE,
            maxNumHands = HandLandmarkerHelper.DEFAULT_NUM_HANDS,
            currentDelegate = HandLandmarkerHelper.DELEGATE_CPU,
            handLandmarkerHelperListener = this
        )

        // Initialize face detector for Face Distance
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .enableTracking()
            .build()
        faceDetector = FaceDetection.getClient(options)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
        soundPool.release()
    }

    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Activates only if current mode is set to button controls
        if (getCurrentMode() == "🔴 Button + Swipe") {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val isPlaying = audioManager.isMusicActive

            // If media is not playing, then volume buttons are overwritten
            if (!isPlaying) {
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        zoomTextIn()
                        return true
                    }

                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        zoomTextOut()
                        return true
                    }
                }
            } else {
                Toast.makeText(this,
                    "Volume button zoom disabled while media is playing",
                    Toast.LENGTH_SHORT).show()
                return super.onKeyDown(keyCode, event)
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("zoom_prefs", Context.MODE_PRIVATE)
        val savedMode = prefs.getString("current_mode", "🔴 Button + Swipe")
        spinner.setSelection(adapter.getPosition(savedMode))
        updateMode(savedMode ?: "🔴 Button + Swipe")
    }

    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
        runOnUiThread {
            // Process results for hand detection if it doesn't exist
            val handResult = resultBundle.results.firstOrNull()
            if (handResult == null || handResult.landmarks().isEmpty()) {
                handOverlay.setResults(null, 0, 0, RunningMode.LIVE_STREAM)
                handOverlay.invalidate()
                excerptText.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSize)
                handVisible = false
                return@runOnUiThread
            }

            // Otherwise, normal processing
            handOverlay.setResults(
                handResult,
                resultBundle.inputImageHeight,
                resultBundle.inputImageWidth,
                RunningMode.LIVE_STREAM
            )
            handOverlay.invalidate()
            handleHandGestures(handResult)
        }
    }

    // ---------------------- CUSTOM FUNCTIONS ---------------------- //

    // Requests required permissions
    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    // Checks if all required permissions are granted
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    // ---------------------- CAMERA SETUP FUNCTIONS ---------------------- //

    // Sets up camera preview
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            previewView.post { bindCameraUseCases() }
        }, ContextCompat.getMainExecutor(this)
        )
    }

    // Binds camera use cases depending on modes that require the camera
    private fun bindCameraUseCases() {
        val aspectRatioStrategy = AspectRatioStrategy(
            AspectRatio.RATIO_16_9,
            AspectRatioStrategy.FALLBACK_RULE_NONE)
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(aspectRatioStrategy)
            .build()
        val cameraProvider = cameraProvider?: throw IllegalStateException("Camera initialization failed.")
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
            .build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

        imageAnalyzer = when (spinner.selectedItem.toString()) {
            "🟢 Hand Gesture" -> createHandAnalyzer(resolutionSelector)
            "🔵 Face Distance" -> createFaceAnalyzer(resolutionSelector)
            else -> null
        }

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
        } catch (exc: Exception) {
            Log.e("TAG", "Use case binding failed", exc)
        }
    }

    // Stops and removes camera preview and any (hand or face) detections
    private fun stopCameraAndDetection() {
        try {
            cameraProvider?.unbindAll()
            handOverlay.setResults(null, 0, 0, RunningMode.LIVE_STREAM)
            handOverlay.invalidate()
            faceOverlay.setFaceBounds(null)
            faceOverlay.invalidate()
            Log.d("ModeActivity", "Camera and detection stopped")
        } catch (e: Exception) {
            Log.e("ModeActivity", "Error stopping camera/detection", e)
        }
    }

    // ---------------------- MODE SELECTION FUNCTIONS ---------------------- //

    // Returns current selected mode
    private fun getCurrentMode(): String {
        val prefs = getSharedPreferences("zoom_prefs", MODE_PRIVATE)
        return prefs.getString("current_mode", "🔴 Button Button") ?: "🔴 Button Button"
    }

    // Starts and stops background processes depending on what mode is selected
    private fun updateMode(mode: String) {
        val cameraFrame = findViewById<FrameLayout>(R.id.cameraFrame)

        when (mode) {
            "🔴 Button + Swipe" -> {
                stopVoiceRecording()
                stopCameraAndDetection()
                cameraFrame.visibility = View.GONE
                playButton.visibility = View.VISIBLE
                voiceButton.visibility = View.GONE
                handOverlay.visibility = View.GONE
                faceOverlay.visibility = View.GONE
                setupSwipeForVolumeMode()
                startMediaPlayer()
            }
            "🟡 Voice Command" -> {
                stopMediaPlayer()
                stopCameraAndDetection()
                if (allPermissionsGranted()) {
                    cameraFrame.visibility = View.GONE
                    playButton.visibility = View.GONE
                    voiceButton.visibility = View.VISIBLE
                    handOverlay.visibility = View.GONE
                    faceOverlay.visibility = View.GONE
                    setupVoiceButton()
                } else {
                    requestPermissions()
                }
            }
            "🟢 Hand Gesture" -> {
                stopMediaPlayer()
                stopVoiceRecording()
                if (allPermissionsGranted()) {
                    setUpCamera()
                    cameraFrame.visibility = View.VISIBLE
                    playButton.visibility = View.GONE
                    voiceButton.visibility = View.GONE
                    handOverlay.visibility = View.VISIBLE
                    faceOverlay.visibility = View.GONE
                } else {
                    requestPermissions()
                }
            }
            "🔵 Face Distance" -> {
                stopMediaPlayer()
                stopVoiceRecording()
                if (allPermissionsGranted()) {
                    setUpCamera()
                    cameraFrame.visibility = View.VISIBLE
                    playButton.visibility = View.GONE
                    voiceButton.visibility = View.GONE
                    handOverlay.visibility = View.GONE
                    faceOverlay.visibility = View.VISIBLE
                } else {
                    requestPermissions()
                }
            }
            else -> {
                stopMediaPlayer()
                stopVoiceRecording()
                stopCameraAndDetection()
                cameraFrame.visibility = View.GONE
                playButton.visibility = View.GONE
                voiceButton.visibility = View.GONE
                handOverlay.visibility = View.GONE
                faceOverlay.visibility = View.GONE
            }
        }
    }

    // ---------------------- PAGINATION FUNCTIONS ---------------------- //

    // Prepares pages (based on fixed reading view and text size) and stores them
    private fun preparePages() {
        pageTexts.clear()

        val cardView = findViewById<CardView>(R.id.pageCard)
        val visibleHeight = cardView.height
        val visibleWidth = cardView.width

        if (visibleHeight == 0 || visibleWidth == 0) {
            cardView.post { preparePages() }
            return
        }

        val textPaint = excerptText.paint
        textPaint.textSize = excerptText.textSize

        val words = fullText.split(" ")
        var pageText = ""

        for (word in words) {
            val tempText = if (pageText.isEmpty()) word else "$pageText $word"

            val staticLayout = StaticLayout.Builder
                .obtain(tempText, 0, tempText.length, textPaint,
                    visibleWidth - excerptText.paddingLeft - excerptText.paddingRight)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(excerptText.lineSpacingExtra, 1f)
                .build()

            if (staticLayout.height > visibleHeight - excerptText.paddingTop - excerptText.paddingBottom) {
                pageTexts.add(pageText)
                pageText = word
            } else {
                pageText = tempText
            }
        }

        if (pageText.isNotEmpty()) pageTexts.add(pageText)
        currentPageIndex = 0
        showPage(currentPageIndex)
    }

    // Displays current showing page and updates indicator
    private fun showPage(index: Int) {
        if (index in pageTexts.indices) {
            excerptText.text = pageTexts[index]
            currentPageIndex = index
            prevButton.isEnabled = currentPageIndex > 0
            nextButton.isEnabled = currentPageIndex < pageTexts.size - 1

            // Update page indicator
            pageIndicator.text = "${currentPageIndex + 1}/${pageTexts.size}"
        }
    }

    // Shows previous page from current page index
    private fun showPrevPage() {
        if (currentPageIndex > 0) showPage(currentPageIndex - 1)
    }

    // Shows next page from current page index
    private fun showNextPage() {
        if (currentPageIndex < pageTexts.size - 1) showPage(currentPageIndex + 1)
    }

    // Updates pages by preparing pages again after text size changes
    private fun updatePagesForCurrentTextSize() {
        val oldIndex = currentPageIndex
        preparePages()
        // Keep current page as close as possible
        showPage(oldIndex.coerceIn(0, pageTexts.size - 1))
        pageIndicator.text = "${currentPageIndex + 1}/${pageTexts.size}"
    }

    // ---------------------- BUTTON + SWIPE FUNCTIONS ---------------------- //

    // Increases text size for zoom in
    private fun zoomTextIn() {
        currentTextSize += 2f
        if (currentTextSize > maxTextSize) currentTextSize = maxTextSize
        excerptText.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSize)
        updateZoomPercentage(currentTextSize)
        updatePagesForCurrentTextSize()
        Toast.makeText(this@ModeActivity, "Zoom In detected", Toast.LENGTH_SHORT).show()
    }

    // Decreases text size for zoom out
    private fun zoomTextOut() {
        currentTextSize -= 2f
        if (currentTextSize < minTextSize) currentTextSize = minTextSize
        excerptText.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSize)
        updateZoomPercentage(currentTextSize)
        updatePagesForCurrentTextSize()
        Toast.makeText(this@ModeActivity, "Zoom Out detected", Toast.LENGTH_SHORT).show()
    }

    // Updates zoom percentage when text size changes, limited by minimum and maximum text size
    private fun updateZoomPercentage(currentSize: Float, minSize: Float = minTextSize, maxSize: Float = maxTextSize): Unit {
        val minPercent = 50
        val maxPercent = 300
        val percentage = ((currentSize - minSize) / (maxSize - minSize) * (maxPercent - minPercent) + minPercent).toInt()
        zoomText.text = "$percentage%"
    }

    // Creates media player when testing button + swipe mode
    private fun startMediaPlayer() {
        mediaPlayer = MediaPlayer.create(this, R.raw.demo)
        playButton.setOnClickListener {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                playButton.text = "Play Music"
            } else {
                mediaPlayer.start()
                playButton.text = "Pause Music"
            }
        }
    }

    // Releases media player when not in use or paused
    private fun stopMediaPlayer() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
            mediaPlayer.release()
            playButton.text = "Play Music"
        }
    }

    // Implements touch swipe gestures in volume mode
    private fun setupSwipeForVolumeMode() {
        val cardView = findViewById<CardView>(R.id.pageCard)

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val swipeThreshold = 50  // lower for testing
            private val swipeVelocityThreshold = 50

            // override fling gesture to implement swiping for page changes
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false

                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > swipeThreshold && abs(velocityX) > swipeVelocityThreshold) {
                        if (diffX > 0) {
                            Toast.makeText(this@ModeActivity, "Swipe Right detected", Toast.LENGTH_SHORT).show()
                            if (currentPageIndex > 0) showPage(currentPageIndex - 1)
                        } else {
                            Toast.makeText(this@ModeActivity, "Swipe Left detected", Toast.LENGTH_SHORT).show()
                            if (currentPageIndex < pageTexts.size - 1) showPage(currentPageIndex + 1)
                        }
                        return true
                    }
                }
                return false
            }
        })

        cardView.post {  // ensure CardView is laid out
            cardView.setOnTouchListener { _, event ->
                if (getCurrentMode() == "🔴 Button + Swipe") {
                    gestureDetector.onTouchEvent(event)
                }
                true
            }
        }
    }

    // ---------------------- VOICE COMMAND FUNCTIONS ---------------------- //

    // Sets up voice button which begins/stops voice recording
    private fun setupVoiceButton() {
        voiceButton.setOnClickListener {
            isRecording = !isRecording
            if (isRecording) {
                soundPool.play(startSound, 1f, 1f, 1, 0, 1f)
                muteSystemSounds()
                startSpeechRecognizer()
                voiceButton.text = "🎤 Stop"
                Toast.makeText(this@ModeActivity, "Listening", Toast.LENGTH_SHORT).show()
            } else {
                soundPool.play(stopSound, 1f, 1f, 1, 0, 1f)
                restoreSystemSounds()
                stopSpeechRecognizer()
                voiceButton.text = "🎤 Start"
                Toast.makeText(this@ModeActivity, "Stopped listening", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Creates SpeechRecognizer to use and handle voice commands
    private fun startSpeechRecognizer() {
        if (speechRecognizer == null) {
            // creates SpeechRecognizer object
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            // create intent for SpeechRecognizer
            recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }

            // override SpeechRecognizer functions to provides custom results and error cases
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    if (isRecording) startSpeechRecognizer()
                }

                override fun onResults(results: Bundle?) {
                    results?.let {
                        val matches = it.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.forEach { command ->
                            handleVoiceCommand(command.lowercase())
                        }
                    }
                    // Restart listening if still recording
                    if (isRecording) startSpeechRecognizer()
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        speechRecognizer?.startListening(recognizerIntent)
    }

    // Stop SpeechRecognizer
    private fun stopSpeechRecognizer() {
        speechRecognizer?.stopListening()
    }

    // Mute system sounds when audio recording is active
    private fun muteSystemSounds() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
    }

    // Restore system sounds when audio recording is inactive
    private fun restoreSystemSounds() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, previousVolume, 0)
    }

    // Stop SpeechRecognizer when swapping modes
    private fun stopVoiceRecording() {
        if (isRecording) {
            isRecording = false
            soundPool.play(stopSound, 1f, 1f, 1, 0, 1f)
            speechRecognizer?.stopListening()
            voiceButton.text = "🎤 Start"
            Toast.makeText(this, "Voice recording stopped", Toast.LENGTH_SHORT).show()
        }
    }

    // Interprets and executes text / swipe interactions based on extracted commands
    private fun handleVoiceCommand(command: String) {
        val lowerCommand = command.lowercase().trim()

        when {
            "zoom in max" in lowerCommand -> repeat(20) { zoomTextIn() }
            "zoom out max" in lowerCommand -> repeat(20) { zoomTextOut() }
            "zoom in" in lowerCommand -> zoomTextIn()
            "zoom out" in lowerCommand -> zoomTextOut()
            "previous" in lowerCommand -> showPrevPage()
            "next" in lowerCommand -> showNextPage()
            "first page" in lowerCommand -> if (currentPageIndex != 0) showPage(0)
            "last page" in lowerCommand -> if (currentPageIndex != pageTexts.size - 1) showPage(pageTexts.size - 1)
        }

        // implemented regex to allow more flexibility with commands
        Regex("""zoom in(?: (\d+))?""").find(lowerCommand)?.let {
            val times = it.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            repeat(times) { zoomTextIn() }
            return
        }
        Regex("""zoom out(?: (\d+))?""").find(lowerCommand)?.let {
            val times = it.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            repeat(times) { zoomTextOut() }
            return
        }
        Regex("""go to page (\d+)""").find(lowerCommand)?.let {
            val pageNum = it.groupValues[1].toIntOrNull() ?: 1
            val pageIndex = when {
                pageNum < 1 -> 0
                pageNum > pageTexts.size -> pageTexts.size - 1
                else -> pageNum - 1 // because page index is 0-based
            }
            showPage(pageIndex)
            return
        }
    }

    // ---------------------- HAND LANDMARKER FUNCTIONS ---------------------- //

    // Creates hand analyzer when hand gesture mode is selected
    private fun createHandAnalyzer(resolutionSelector: ResolutionSelector) = ImageAnalysis.Builder()
        .setResolutionSelector(resolutionSelector)
        .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .build().also {
            it.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                val image = imageProxy.image
                if (image != null) detectHand(imageProxy)
            }
        }

    // Outputs live hand detection using hand landmarker
    private fun detectHand(imageProxy: ImageProxy) {
        handLandmarkerHelper.detectLiveStream(imageProxy, isFrontCamera = true)
    }

    // Handles all hand gesture related functions and is called by hand analyzer
    private fun handleHandGestures(handResult: HandLandmarkerResult) {
        zoomGesture(handResult)
        swipeGesture(handResult)
    }

    // Implements zoom pinch gesture
    private fun zoomGesture(handResult: HandLandmarkerResult) {
        runOnUiThread {
            // checks if there is any hands and executes accordingly
            if (handResult.landmarks().isEmpty()) {
                excerptText.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSize)
                handVisible = false
                pinchStartTime = 0L
                return@runOnUiThread
            }

            // extract hand landmarks from a single hand
            val landmarks = handResult.landmarks().first()

            // defines individual landmarks / joints coordinates (x, y separately)
            val thumbTipX = 1f - landmarks[4].x()
            val thumbTipY = landmarks[4].y()
            val indexTipX = 1f - landmarks[8].x()
            val indexTipY = landmarks[8].y()
            val middleTipY = landmarks[12].y()
            val ringTipY = landmarks[16].y()
            val pinkyTipY = landmarks[20].y()
            val middleMcpY = landmarks[9].y()
            val ringMcpY = landmarks[13].y()
            val pinkyMcpY = landmarks[17].y()

            // index must be above thumb
            if (indexTipY >= thumbTipY) {
                pinchStartTime = 0L
                handVisible = false
                return@runOnUiThread
            }

            // other fingers must be curled: tip Y < MCP Y
            val otherFingersNotCurled = middleTipY > middleMcpY &&
                    ringTipY > ringMcpY &&
                    pinkyTipY > pinkyMcpY

            if (!otherFingersNotCurled) {
                pinchStartTime = 0L
                handVisible = false
                return@runOnUiThread
            }

            // delays gesture to decreases sensitivity to movement or changes
            val now = System.currentTimeMillis()
            if (pinchStartTime == 0L) {
                pinchStartTime = now
                return@runOnUiThread
            }

            if (now - pinchStartTime < pinchActivationDelay) {
                // still waiting → don't zoom yet
                return@runOnUiThread
            }

            // calculates distance from index tip to thumb tip and checks if pinch gesture is valid
            val dx = thumbTipX - indexTipX
            val dy = thumbTipY - indexTipY
            val rawDistance = sqrt((dx * dx + dy * dy).toDouble())

            if (rawDistance > pinchThreshold) {
                handVisible = true
                return@runOnUiThread
            }

            // smooths distance for text size changes
            smoothedDistance = smoothingFactor * rawDistance + (1 - smoothingFactor) * smoothedDistance

            if (!handVisible) {
                previousDistance = smoothedDistance
                handVisible = true
                return@runOnUiThread
            }

            previousDistance = smoothedDistance
            val newSize = mapDistanceToTextSize(smoothedDistance)
            val clampedSize = excerptText.textSize.coerceIn(
                newSize - maxDeltaPerFrame,
                newSize + maxDeltaPerFrame
            )

            // Updates current text size after change and updates zoom percentage + readjusts pagination
            currentTextSize = clampedSize
            excerptText.textSize = currentTextSize
            updateZoomPercentage(currentTextSize)
            updatePagesForCurrentTextSize()
        }
    }

    // Implements swipe finger gesture
    private fun swipeGesture(handResult: HandLandmarkerResult) {
        runOnUiThread {
            if (handResult.landmarks().isEmpty()) {
                swipeStartX = null
                swipeActive = false
                return@runOnUiThread
            }

            val now = System.currentTimeMillis()
            if (now - lastSwipeTime < swipeCooldown) {
                // Still in cooldown → ignore swipe
                return@runOnUiThread
            }

            val landmarks = handResult.landmarks().first()
            val indexTipX = 1f - landmarks[8].x()
            val indexTipY = landmarks[8].y()
            val middleTipX = 1f - landmarks[12].x()
            val middleTipY = landmarks[12].y()

            // Fingers must be close together to start swipe
            val fingersClose = abs(indexTipX - middleTipX) < fingerProximityThreshold &&
                    abs(indexTipY - middleTipY) < fingerProximityThreshold

            if (!fingersClose) {
                swipeStartX = null
                swipeActive = false
                return@runOnUiThread
            }

            if (!swipeActive) {
                swipeStartX = indexTipX
                swipeActive = true
                return@runOnUiThread
            }

            val deltaX = indexTipX - (swipeStartX ?: indexTipX)

            if (abs(deltaX) > swipeThreshold) {
                if (deltaX > 0) {
                    // Swipe Right → next page
                    if (currentPageIndex < pageTexts.size - 1) showPage(currentPageIndex + 1)
                    Toast.makeText(this, "Swipe Right detected", Toast.LENGTH_SHORT).show()
                } else {
                    // Swipe Left → previous page
                    if (currentPageIndex > 0) showPage(currentPageIndex - 1)
                    Toast.makeText(this, "Swipe Left detected", Toast.LENGTH_SHORT).show()
                }

                // Reset swipe & start cooldown
                swipeStartX = null
                swipeActive = false
                lastSwipeTime = now
            }
        }
    }

    // ---------------------- FACE DETECTION FUNCTIONS ---------------------- //

    // Creates face analyzer when face distance mode is selected
    private fun createFaceAnalyzer(resolutionSelector: ResolutionSelector) = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setResolutionSelector(resolutionSelector)
        .build().also {
            it.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                val image = imageProxy.image
                if (image != null) {
                    val mediaImage = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
                    faceOverlay.previewWidth = mediaImage.width
                    faceOverlay.previewHeight = mediaImage.height
                    detectFace(mediaImage, imageProxy)
                }
            }
        }

    // Checks if all facial landmarks are visible on preview
    private fun areAllLandmarksVisible(face: Face): Boolean {
        val requiredLandmarks = listOf(
            FaceContour.FACE,
            FaceContour.LEFT_EYE,
            FaceContour.RIGHT_EYE,
            FaceContour.UPPER_LIP_TOP,
            FaceContour.LOWER_LIP_BOTTOM,
            FaceContour.LEFT_CHEEK,
            FaceContour.RIGHT_CHEEK,
            FaceContour.NOSE_BRIDGE
        )

        return requiredLandmarks.all { face.getContour(it) != null }
    }

    // Outputs live face & facial landmark detection using face detector
    private fun detectFace(image: InputImage, imageProxy: ImageProxy) {
        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces.first()
                    val faceBox = faces[0].boundingBox
                    val landmarks = mutableListOf<PointF>()

                    // get all contours
                    val contours = listOf(
                        FaceContour.FACE,
                        FaceContour.LEFT_EYE,
                        FaceContour.RIGHT_EYE,
                        FaceContour.UPPER_LIP_TOP,
                        FaceContour.LOWER_LIP_BOTTOM,
                        FaceContour.LEFT_CHEEK,
                        FaceContour.RIGHT_CHEEK,
                        FaceContour.NOSE_BRIDGE
                    )

                    // adds contours to landmarks
                    for (type in contours) {
                        face.getContour(type)?.points?.let { points ->
                            landmarks.addAll(points)
                        }
                    }

                    faceOverlay.setFaceBounds(faceBox)
                    faceOverlay.setLandmarks(landmarks)
                    handleFaceDetection(faceBox, face)
                } else {
                    faceOverlay.setFaceBounds(null)
                }
            }.addOnCompleteListener { imageProxy.close() }
    }

    // Handles all face detection related functions and is called by face analyzer
    private fun handleFaceDetection(faceBox: Rect, face: Face) {
        faceZoom(faceBox, face)
        faceSwipe(face)
    }

    // Linearly maps distance to text size for zoom adjustment
    private fun mapDistanceToTextSize(distance: Double): Float {
        val minDistance = 0.02
        val maxDistance = 0.3

        val clampedDistance = distance.coerceIn(minDistance, maxDistance)
        val ratio = (clampedDistance - minDistance) / (maxDistance - minDistance)
        return (minTextSize + ratio * (maxTextSize - minTextSize)).toFloat()
    }

    // Zooms in or out based on facial distance from camera
    private fun faceZoom(faceBox: Rect, face: Face) {
        val faceHeight = faceBox.height().toFloat()
        val faceWidth = faceBox.width().toFloat()
        val screenHeight = previewView.height.toFloat()

        val minRatio = 0.01f
        val maxRatio = 1f

        if (screenHeight == 0f) return

        // head pose stability check
        val headStable = abs(face.headEulerAngleY) < 10f &&   // yaw (left/right)
                    abs(face.headEulerAngleX) < 7f &&   // pitch (up/down)
                    abs(face.headEulerAngleZ) < 7f      // roll (tilt)
        // reset timer if head is unstable
        if (!headStable) {
            zoomStartTime = 0L  // reset timer if head moves
            return
        }

        // ignore micro jitter in face size
        val faceArea = faceHeight * faceWidth
        val areaDelta = abs(faceArea - lastFaceArea)
        lastFaceArea = faceArea
        if (areaDelta < 1000f) return  // tweak threshold as needed

        // compute inverted face ratio for zoom
        val faceRatio = ((faceHeight + faceWidth) / 2f) / screenHeight
        val clampedRatio = faceRatio.coerceIn(minRatio, maxRatio)
        val invertedRatio = 1f - (clampedRatio - minRatio) / (maxRatio - minRatio)

        // map to target text size
        val targetSize = (minTextSize + (maxTextSize - minTextSize) * invertedRatio).coerceIn(minTextSize, maxTextSize)
        val ratioDelta = targetSize - currentTextSize

        // dynamic leap calculation
        val dynamicLeapFactor = if (ratioDelta > 0) {
            // face moving away → zoom in (text grows) smoothly
            (0.2f + 0.5f * abs(ratioDelta) / (maxTextSize - minTextSize)).coerceIn(0.1f, 0.5f)
        } else {
            // face moving closer → zoom out slowly
            (0.02f + 0.1f * abs(ratioDelta) / (maxTextSize - minTextSize)).coerceIn(0.01f, 0.15f)
        }

        val delta = ratioDelta * dynamicLeapFactor
        currentTextSize += delta

        runOnUiThread {
            excerptText.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSize)
            updateZoomPercentage(currentTextSize)
            updatePagesForCurrentTextSize()
        }
    }

    // Swipes pages based on face orientation (facing left -> swipe left, facing right, swipe right)
    private fun faceSwipe(face: Face) {
        val now = System.currentTimeMillis()

        // cooldown to prevent multiple swipes
        if (!areAllLandmarksVisible(face)) {
            zoomActive = false
            zoomStartTime = 0L
            zoomCooldownUntil = now + 800
            return
        }

        if (now - lastSwipeTime < swipeCooldown) return

        val yaw = face.headEulerAngleY // negative = left, positive = right
        val swipeThreshold = 25f // adjust sensitivity

        // mirrored camera preview so left is positive and right is negative
        when {
            yaw > swipeThreshold -> { // turned left -> previous page
                if (currentPageIndex > 0) {
                    currentPageIndex--
                    showPage(currentPageIndex)
                    lastSwipeTime = now
                    Toast.makeText(this, "Swipe Left detected", Toast.LENGTH_SHORT).show()
                }
            }
            yaw < -swipeThreshold -> { // turned right -> next page
                if (currentPageIndex < pageTexts.size - 1) {
                    currentPageIndex++
                    showPage(currentPageIndex)
                    lastSwipeTime = now
                    Toast.makeText(this, "Swipe Right detected", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                // head roughly centered -> do nothing
            }
        }
    }
}