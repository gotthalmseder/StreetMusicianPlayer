
// Strg Shift Minus  drücken um Methoden einzuklappen

package com.example.streetmusicanplayer

import android.annotation.SuppressLint
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import androidx.core.content.ContextCompat
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import kotlin.math.min
import androidx.appcompat.app.AlertDialog
import com.example.streetmusicanplayer.ui.theme.EqualizerDialog
import java.util.Timer
import java.util.TimerTask
import android.content.Context
import android.graphics.Color
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.delay
import android.view.LayoutInflater
import android.view.Gravity
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

class MainActivity : AppCompatActivity() {

    companion object {
        var globalEqualizer: Equalizer? = null
        var isTrackInfoLoaded: Boolean = false
        var globalTrackInfos: List<TrackInfo>? = null
        val cachedTrackInfosByFolder: MutableMap<String, List<TrackInfo>> = mutableMapOf()
    }
    private var equalizer: Equalizer? = null
    private lateinit var folderButton: Button
    private lateinit var playNextButton: Button
    private lateinit var volumeButton: Button
    private lateinit var transferButton: Button
    private lateinit var arrangeButton: Button
    private lateinit var progressBarRed: View
    private lateinit var equalizerButton: Button
    private lateinit var loopButton: Button
    private lateinit var pitchButton: Button
    private lateinit var arrangeLauncher: ActivityResultLauncher<Intent>
    private var currentFolder = "tracks_1"
    private var currentTrackIndex = -1
    private var volume = 0.5f
    private var isPlaying = false
    private var mediaPlayer: MediaPlayer? = null
    private var fadeOutHandler: Handler? = null
    private var fadeOutRunnable: Runnable? = null
    var tracks: MutableList<Uri> = mutableListOf()
    //private var tracks: List<Uri> = listOf()
    private var baseUri: Uri? = null
    private var backScrollTimer: Timer? = null
    private var isBackScrolling = false
    private var backScrollIndex = 0
    private var longPressTriggered = false
    private lateinit var trackTitleTextView: TextView
    private var isGreen = false
    private var isYellow = false
    private var isOrange = false
    private var yellowToOrangeHandler: Handler? = null
    private var yellowToOrangeRunnable: Runnable? = null
    private var autoNext = false
    private var endCountdownHandler: Handler? = null
    private var endCountdownRunnable: Runnable? = null
    private var progressHandler: Handler? = null
    private var progressRunnable: Runnable? = null
    private var isReplayMode = false
    private lateinit var replayModeButton: Button
    private var rewindHandler: Handler? = null
    private var rewindRunnable: Runnable? = null
    private var isRewinding = false
    private var isAllowingAnpieksPause = false
    private var isFadingOut = false
    private var justSwitchedFolder = true
    private var folderCreationChosen = false
    private var currentLoopCount = 1
    private var loopRemaining = 0
    private var isLoopingInternally = false
    var currentPitchValue = 0     // Pitch in Halbtonschritten (z. B. -3, +2)
    var currentSpeedValue = 100   // Tempo in Prozent (z. B. 80 = 80%)
    private var loadingDialog: AlertDialog? = null
    var loopCountCompleted = 0
    private var loadTracksJob: Job? = null



    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        //Überprüfung der exampleTracks zurücksetzen bei deinstallation
//        getSharedPreferences("prefs", Context.MODE_PRIVATE)
//            .edit().remove("exampleTracksCopied").apply()

        setupArrangeLauncher()

        // 1. Initialize Views
        trackTitleTextView = findViewById(R.id.trackTitleTextView)
        arrangeButton = findViewById(R.id.arrangeButton)
        folderButton = findViewById(R.id.folderButton)
        transferButton = findViewById(R.id.transferButton)
        equalizerButton = findViewById(R.id.equalizerButton)
        replayModeButton = findViewById(R.id.replayModeButton)
        playNextButton = findViewById(R.id.playNextButton)
        volumeButton = findViewById(R.id.volumeButton)
        loopButton = findViewById(R.id.loopButton)
        pitchButton = findViewById(R.id.pitchButton)
        progressBarRed = findViewById(R.id.progressBarRed)

        // 2. UI flags
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.insetsController?.let { controller ->
            controller.hide(WindowInsets.Type.statusBars())
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )

        Log.d("MAIN", "App started")

        // 3. Enable buttons
        setupAllButtons()

// 4. Check SAF access
        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val savedUriString = prefs.getString("saf_uri", null)

        if (savedUriString != null) {
            try {
                val parsedUri = Uri.parse(savedUriString)

                // 🧠 Zugriff wiederherstellen
                contentResolver.takePersistableUriPermission(
                    parsedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                baseUri = parsedUri
                FolderManager.initialize(this, parsedUri)

                Log.d("SAF", "✅ SAF-Zugriff bereits vorhanden")
                continueAppStartup()
            } catch (e: SecurityException) {
                Log.w("SAF", "⚠️ Zugriff konnte nicht wiederhergestellt werden: ${e.message}")
                showStorageChoiceDialog()
                return
            }
        } else {
            Log.w("SAF", "📂 Kein SAF-Zugriff gefunden – Dialog wird angezeigt")
            showStorageChoiceDialog()
            return
        }

    }

    private val safLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                Log.e("SAF", "Could not persist permission: ${e.message}")
            }

            getSharedPreferences("prefs", Context.MODE_PRIVATE).edit()
                .putString("saf_uri", uri.toString())
                .putBoolean("folderChosen", true) // optional, falls noch gebraucht
                .apply()

            baseUri = uri
            FolderManager.initialize(this, baseUri!!)
            Toast.makeText(this, "✅ Access granted to StreetMusician folder", Toast.LENGTH_SHORT).show()

            val baseDoc = DocumentFile.fromTreeUri(this, uri)
            if (baseDoc != null) {
                setupStreetMusicianFoldersSAF(baseDoc)
            } else {
                Log.e("SAF", "❌ Could not convert URI to DocumentFile")
            }

            continueAppStartup()
        } else {
            Toast.makeText(this, "❌ Access denied", Toast.LENGTH_SHORT).show()
            Log.e("SAF", "User did not select any folder")
        }
    }

    private fun requestFolderAccess() {
        val hasSDCard = isExternalStorageAvailable()

        val baseMessage = """
        Please select the folder:

        Music/StreetMusician

        and grant access.
    """.trimIndent()

        val sdCardHint = """
        ⚠️ There are two Music folders: one on internal storage, and one on the SD card. If 'StreetMusician' is not visible, you're probably in the wrong location.
    """.trimIndent()

        val finalMessage = if (hasSDCard) "$baseMessage\n\n$sdCardHint" else baseMessage

        AlertDialog.Builder(this)
            .setTitle("Storage Access Required")
            .setMessage(finalMessage)
            .setCancelable(false)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val externalDirs = ContextCompat.getExternalFilesDirs(this, null)
                    if (externalDirs.size > 1 && externalDirs[1] != null) {
                        val sdPath = externalDirs[1].absolutePath.substringBefore("/Android")
                        val sdMusic = File(sdPath, "Music")
                        val uri = Uri.fromFile(sdMusic)
                        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                        Log.d("SAF", "Trying to open SAF in: $uri")
                    }
                }

                safLauncher.launch(intent)
            }
            .show()
    }
    private fun prepareFoldersAndRequestAccess(useSdCard: Boolean) {
        if (useSdCard) {
            writeBaseFolderToSdCard()
        } else {
            val baseDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "StreetMusician")
            if (!baseDir.exists()) {
                baseDir.mkdirs()
                Log.d("SETUP", "📁 Internal music folder created: ${baseDir.absolutePath}")
            }
        }
        Handler(Looper.getMainLooper()).postDelayed({
            requestFolderAccess()
        }, 300)
    }
    private fun writeBaseFolderToSdCard() {
        val externalDirs = ContextCompat.getExternalFilesDirs(this, null)
        if (externalDirs.size < 2 || externalDirs[1] == null) {
            Log.e("SDTEST", "❌ No SD card detected")
            return
        }

        val sdCardRoot = externalDirs[1].absolutePath.substringBefore("/Android")
        val musicDirOnSd = File("$sdCardRoot/Music/StreetMusician")

        if (!musicDirOnSd.exists()) {
            val created = musicDirOnSd.mkdirs()
            Log.d("SDTEST", "📁 Music folder created on SD: $created → ${musicDirOnSd.absolutePath}")
        } else {
            Log.d("SDTEST", "📁 Folder already exists: ${musicDirOnSd.absolutePath}")
        }
    }
    fun setupStreetMusicianFoldersSAF(baseDoc: DocumentFile) {
        for (i in 1..4) {
            if (baseDoc.findFile("tracks_$i") == null) {
                baseDoc.createDirectory("tracks_$i")
                Log.d("SAF", "📁 Created: tracks_$i")
            }
        }

        if (baseDoc.findFile("basket") == null) {
            baseDoc.createDirectory("basket")
            Log.d("SAF", "📁 Created: basket")
        }
    }
    private fun showStorageChoiceDialog() {
        val hasSDCard = isExternalStorageAvailable()
        if (!hasSDCard) {
            folderCreationChosen = true
            prepareFoldersAndRequestAccess(useSdCard = false)
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Choose Storage Location")
            .setMessage("Where should the music folders be created?")
            .setCancelable(false)
            .setPositiveButton("Internal Storage") { _, _ ->
                folderCreationChosen = true
                prepareFoldersAndRequestAccess(useSdCard = false)
            }
            .setNegativeButton("SD Card") { _, _ ->
                folderCreationChosen = true
                prepareFoldersAndRequestAccess(useSdCard = true)
            }
            .show()
    }
    private fun isExternalStorageAvailable(): Boolean {
        val externalVolumes = ContextCompat.getExternalFilesDirs(this, null)
        return externalVolumes.size > 1 && externalVolumes[1] != null
    }


    @SuppressLint("ClickableViewAccessibility")
    private fun setupAllButtons() {

        setVolumeButtonText(volumeButton, 50)
        setPlayNextButtonText(playNextButton)
        setButtonToRed()

        arrangeButton.setOnClickListener {
            arrangeButton.setBackgroundResource(R.drawable.button_gradient_active)

            if (!cachedTrackInfosByFolder.containsKey(currentFolder)) {
                Toast.makeText(this, "❌ Track list not available. Please restart the app.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

// 📄 1. Eigene TextView vorbereiten
            val infoText = TextView(this).apply {
                text = "🎵 Loading track list...     Please wait."
                setPadding(50, 40, 50, 40)
                textSize = 20f
            }

// 📦 2. Dialog erstellen und TextView als Inhalt setzen
            val loadingDialog = AlertDialog.Builder(this)
                .setView(infoText)
                .setCancelable(false)
                .create()

// ▶️ 3. Dialog anzeigen
            loadingDialog.show()

            // 🔁 Automatisch schließen nach 2 Sekunden
            Handler(Looper.getMainLooper()).postDelayed({
                loadingDialog.dismiss()
            }, 2000)

            val currentInfos = cachedTrackInfosByFolder[currentFolder]
            val cachedInfos = currentInfos?.let {
                JSONArray().apply {
                    it.forEach { info ->
                        put(JSONObject().apply {
                            put("uri", info.uri.toString())
                            put("name", info.displayName)
                            put("time", info.modifiedTime)
                        })
                    }
                }.toString()
            }

            val intent = Intent(this, ArrangeTracks::class.java).apply {
                putExtra("folder", currentFolder)
                putExtra("baseUri", baseUri)
                putExtra("selectedIndex", currentTrackIndex)
                if (cachedInfos != null) {
                    putExtra("cachedTrackInfos", cachedInfos)
                }
            }
            Handler(Looper.getMainLooper()).post {
                arrangeLauncher.launch(intent)
            }
        }


        val equalizerButton = findViewById<Button>(R.id.equalizerButton)
        equalizerButton.setOnClickListener {
            val player = mediaPlayer
            if (player == null || player.duration <= 0) {
                Toast.makeText(this, "Please start a track before opening the equalizer.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val dialog = EqualizerDialog()
            dialog.show(supportFragmentManager, "EqualizerDialog")
        }

        var folderDownTimestamp = 0L  // 🕒 Zeitpunkt speichern

        folderButton.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // ⛔ Laufenden Ladevorgang abbrechen – sofort!
                    loadTracksJob?.cancel()

                    isBackScrolling = false
                    longPressTriggered = false
                    backScrollIndex = FolderManager.getAvailableFolders().indexOf(currentFolder)

                    folderDownTimestamp = System.currentTimeMillis()
                    folderButton.setBackgroundResource(R.drawable.button_gradient_pressed)

                    backScrollTimer = Timer()
                    backScrollTimer?.schedule(object : TimerTask() {
                        override fun run() {
                            isBackScrolling = true
                            longPressTriggered = true
                            val folders = FolderManager.getAvailableFolders()
                            backScrollIndex =
                                if (backScrollIndex - 1 >= 0) backScrollIndex - 1 else folders.size - 1
                            val newFolder = folders[backScrollIndex]

                            runOnUiThread {
                                folderButton.text = newFolder
                            }
                        }
                    }, 200, 700)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    backScrollTimer?.cancel()
                    backScrollTimer = null

                    val now = System.currentTimeMillis()
                    val pressDuration = now - folderDownTimestamp
                    val minHoldTime = 150L

                    val runAfterHoldTime = {
                        val folders = FolderManager.getAvailableFolders()
                        currentFolder = if (isBackScrolling) {
                            folders[backScrollIndex]
                        } else {
                            FolderManager.getNextFolder(currentFolder)
                        }

                        justSwitchedFolder = true
                        folderButton.text = currentFolder
                        folderButton.setBackgroundResource(R.drawable.button_gradient_selector)

                        resetPlaybackStateAndProgressBar()

                        // ✅ Zeige sofort CACHED Trackliste
                        val stored = TrackInfoStorage.load(this@MainActivity, currentFolder)
                        if (stored != null && stored.isNotEmpty()) {
                            cachedTrackInfosByFolder[currentFolder] = stored
                            isTrackInfoLoaded = true
                            currentTrackIndex = 0
                            updateTracksFromTrackInfos()
                        } else {
                            cachedTrackInfosByFolder.remove(currentFolder)
                            isTrackInfoLoaded = false
                            tracks = mutableListOf()
                            currentTrackIndex = -1
                            trackTitleTextView.text = "[No tracks]"
                        }

                        // 🚀 Dann im Hintergrund checken, ob es Änderungen gibt
                        loadTracksJob?.cancel()
                        loadTracksJob = lifecycleScope.launch {
                            val hasChanged = checkForExternalFolderChanges()
                            if (hasChanged) {
                                Log.d("FOLDER", "🔁 Track list changed – reloading $currentFolder")
                                loadTracks(fullReload = true)
                            } else {
                                Log.d("FOLDER", "✅ Track list unchanged – using cached list")
                            }
                        }
                    }


                    if (pressDuration < minHoldTime) {
                        val delay = minHoldTime - pressDuration
                        folderButton.postDelayed({ runAfterHoldTime() }, delay)
                    } else {
                        runAfterHoldTime()
                    }

                    true
                }

                else -> false
            }
        }



        playNextButton.setOnTouchListener { view, event ->
            // 🚫 Schutz vor leerer Trackliste
            if (event.action == MotionEvent.ACTION_DOWN &&
                (tracks.isEmpty() || currentTrackIndex < 0)
            ) {
                Toast.makeText(this, "⚠️ No track loaded", Toast.LENGTH_SHORT).show()
                return@setOnTouchListener true
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isBackScrolling = false
                    longPressTriggered = false
                    backScrollIndex = currentTrackIndex
                    playNextButton.setBackgroundColor(Color.rgb(80, 130, 0)) // dunkleres Grün

                    backScrollTimer = Timer()
                    backScrollTimer?.schedule(object : TimerTask() {
                        override fun run() {
                            isBackScrolling = true
                            longPressTriggered = true
                            backScrollIndex =
                                if (backScrollIndex - 1 >= 0) backScrollIndex - 1 else tracks.size - 1
                            runOnUiThread {
                                val trackNumber = backScrollIndex + 1
                                val formattedNumber = String.format("%02d", trackNumber)
//                                val trackTitle =
//                                    "$formattedNumber " + FolderManager.getFileNameFromUri(tracks[backScrollIndex])
//                                trackTitleTextView.text = trackTitle
                                val info = cachedTrackInfosByFolder[currentFolder]?.getOrNull(backScrollIndex)
                                val displayName = info?.displayName ?: "[Unknown Track]"
                                trackTitleTextView.text = "$formattedNumber $displayName"

                            }
                        }
                    }, 500, 700)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    backScrollTimer?.cancel()
                    backScrollTimer = null
                    playNextButton.setBackgroundColor(Color.rgb(100, 180, 0)) // normales Grün

                    when {
                        isBackScrolling -> {
                            currentTrackIndex = backScrollIndex
                            playTrack()
                        }

                        isReplayMode -> {
                            currentTrackIndex = (currentTrackIndex + 1) % tracks.size
                            playTrack()
                        }

                        justSwitchedFolder && tracks.isNotEmpty() -> {
                            justSwitchedFolder = false
                            playTrack()
                        }

                        tracks.isNotEmpty() -> {
                            when {
                                isGreen -> {
                                    autoNext = true
                                    startFadeOut()
                                    setButtonToYellow()
                                }

                                isYellow -> {
                                    stopFadeOut()
                                    autoNext = true
                                    setButtonToGreen()
                                    playNextTrack()
                                }

                                isOrange -> {
                                    autoNext = false
                                    setButtonToRed()
                                }

                                else -> {
                                    stopFadeOut()
                                    autoNext = true
                                    playNextTrack()
                                }
                            }
                        }
                    }

                    return@setOnTouchListener true
                }

                else -> false
            }
        }

        transferButton.setOnClickListener {
            showTransferDialog()
        }

        var downTime: Long = 0L
        var isTouchHeld = false
        val holdThreshold = 300L
        val handler = Handler(Looper.getMainLooper())

        volumeButton.setOnTouchListener { _, event ->
            if (isReplayMode) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downTime = System.currentTimeMillis()
                        isTouchHeld = true
                        volumeButton.setBackgroundColor(Color.rgb(50, 100, 200))

                        handler.postDelayed({
                            if (isTouchHeld && !isRewinding) {
                                startRewind()
                            }
                        }, holdThreshold)
                    }

                    MotionEvent.ACTION_UP -> {
                        isTouchHeld = false
                        val duration = System.currentTimeMillis() - downTime
                        volumeButton.setBackgroundColor(Color.rgb(70, 150, 255))

                        if (duration < holdThreshold) {
                            val player = mediaPlayer ?: return@setOnTouchListener true
                            if (player.isPlaying) {
                                player.pause()
                                volumeButton.setBackgroundColor(Color.rgb(230, 70, 70)) // Rot
                            } else {
                                player.start()
                                volumeButton.setBackgroundColor(
                                    Color.rgb(
                                        70,
                                        150,
                                        255
                                    )
                                ) // Replay-Blau
                            }
                            setReplayButtonText(volumeButton)
                        } else {
                            // Hold beendet → Resume
                            stopRewindAndPlay()
                        }
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        isTouchHeld = false
                        stopRewindAndPlay()
                    }
                }
                true
            } else {
                // Live-Modus (Volume)
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downTime = System.currentTimeMillis()
                        isTouchHeld = true
                        volumeButton.setBackgroundColor(Color.rgb(100, 100, 255))
                        startVolumeDown()
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isTouchHeld = false
                        stopVolumeDown()
                        volumeButton.setBackgroundColor(
                            ContextCompat.getColor(
                                this,
                                android.R.color.darker_gray
                            )
                        )

                        if (System.currentTimeMillis() - downTime < holdThreshold) {
                            increaseVolume()
                            volumeButton.setBackgroundColor(Color.rgb(100, 100, 255))
                            Handler(Looper.getMainLooper()).postDelayed({
                                volumeButton.setBackgroundColor(
                                    ContextCompat.getColor(
                                        this,
                                        android.R.color.darker_gray
                                    )
                                )
                            }, 150)
                        }
                    }
                }
                true
            }
        }

        replayModeButton.setOnClickListener {
            isReplayMode = !isReplayMode

            if (isReplayMode) {
                // 🎵 Replay-Modus aktiviert
                replayModeButton.setBackgroundResource(R.drawable.button_gradient_active)
                volumeButton.setBackgroundResource(R.drawable.button_gradient_active)
                setReplayButtonText(volumeButton)
                setReplayPlayNextButtonText(playNextButton)
            } else {
                // 🎵 Zurück zu Live-Modus
                replayModeButton.setBackgroundResource(R.drawable.button_gradient)
                volumeButton.setBackgroundResource(R.drawable.button_gradient)
                updateVolumeDisplay()
                setPlayNextButtonText(playNextButton)
            }

            if (!isReplayMode) {
                // Falls noch pausiert → weiterspielen
                if (mediaPlayer?.isPlaying == false) {
                    mediaPlayer?.start()
                }
                // UI zurücksetzen
                updateVolumeDisplay()
                volumeButton.setBackgroundColor(Color.LTGRAY)
                replayModeButton.setBackgroundColor(Color.LTGRAY)
            }
        }

        loopButton.setOnClickListener {
            showLoopDialog()
        }
        pitchButton.setOnClickListener {
            if (isPlaying) {
                stopPlayer() // oder fadeOutAndStop(), je nach Logik
            }
            showPitchDialog()
        }

        trackTitleTextView.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN && mediaPlayer?.isPlaying == true) {
                val width = view.width.toFloat()
                val touchX = event.x.coerceIn(0f, width)
                val positionRatio = touchX / width

                val newPositionMs = (mediaPlayer!!.duration * positionRatio).toInt()
                mediaPlayer!!.seekTo(newPositionMs)

                // 🟥 Fortschrittsbalken aktualisieren
                val redBar = findViewById<View>(R.id.progressBarRed)
                val container = findViewById<View>(R.id.progressBarContainer)
                val containerWidth = container.width

                val newBarWidth = (containerWidth * positionRatio).toInt()
                val layoutParams = redBar.layoutParams
                layoutParams.width = newBarWidth
                redBar.layoutParams = layoutParams

                true
            } else {
                false
            }
        }



        findViewById<Button>(R.id.impressumButton).setOnClickListener {
            showImpressumDialog()
        }

        findViewById<Button>(R.id.licensesButton).setOnClickListener {
            showAddInsDialog()
        }
    }


    private fun resetPlaybackStateAndProgressBar() {
        stopPlayer(startNext = false)

        isPlaying = false
        isGreen = false
        isYellow = false
        isOrange = false
        autoNext = false

        endCountdownRunnable?.let { endCountdownHandler?.removeCallbacks(it) }
        endCountdownHandler = null

        setButtonToRed()

        val redBar = findViewById<View>(R.id.progressBarRed)
        val layoutParams = redBar.layoutParams
        layoutParams.width = 0
        redBar.layoutParams = layoutParams
    }

    private fun setupArrangeLauncher() {
        arrangeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data

                if (data?.getBooleanExtra("resetArrangeStyle", false) == true) {
                    resetArrangeButtonStyle()
                }

                if (data?.getBooleanExtra("resetPlayback", false) == true) {
                    resetPlaybackStateAndProgressBar()
                }

                val jsonString = data?.getStringExtra("updatedTrackInfos")
                if (jsonString != null) {
                    try {
                        val jsonArray = JSONArray(jsonString)
                        val restoredList = (0 until jsonArray.length()).mapNotNull { i ->
                            val obj = jsonArray.getJSONObject(i)
                            val uri = Uri.parse(obj.getString("uri"))
                            val name = obj.getString("name")
                            val time = obj.getLong("time")
                            TrackInfo(uri.toString(), name, time)
                        }

                        cachedTrackInfosByFolder[currentFolder] = restoredList
                        Log.d("ARRANGE", "✅ Neue Reihenfolge übernommen")

                        updateTracksFromTrackInfos()
                        stopPlayer()
                        setButtonToRed()

                    } catch (e: Exception) {
                        Log.e("ARRANGE", "❌ Fehler beim Verarbeiten der Liste: ${e.message}")
                        loadTracksJob = lifecycleScope.launch {
                            loadTracks()
                        }
                    }
                } else {
                    loadTracksJob = lifecycleScope.launch {
                        loadTracks()
                    }
                }
            }
        }
    }

    private fun updateTracksFromTrackInfos() {
        val infos = cachedTrackInfosByFolder[currentFolder] ?: return
        tracks = infos.map { Uri.parse(it.uri.toString()) }.toMutableList()

        // Prüfe, ob der aktuelle Index noch gültig ist
        if (currentTrackIndex >= tracks.size) {
            currentTrackIndex = tracks.lastIndex  // auf den letzten gültigen Track setzen
        }

        updateTrackTitleDisplay()
        Log.d("TRACKCACHE", "🎵 Tracks aus cachedTrackInfosByFolder übernommen: ${tracks.size}")
    }
    private fun showLoadingDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("⏳ Updating3 track list…")
            .setMessage("Please wait a moment")
            .setCancelable(false)
            .create()

        dialog.show()

//        Handler(Looper.getMainLooper()).postDelayed({
            dialog.dismiss()

            // ⏹️ Player stoppen & UI zurücksetzen
            stopPlayer()
            setButtonToRed()
//            progressBar.progress = 0

//        }, 2000)
    }
    private var trackUpdateDialog: AlertDialog? = null
    fun showTrackUpdateDialog() {
        if (trackUpdateDialog?.isShowing == true) return

        trackUpdateDialog = AlertDialog.Builder(this)
            .setTitle("⏳ Updating track list…")
            .setMessage("Please wait a moment")
            .setCancelable(false)
            .create()
        trackUpdateDialog?.show()
    }
    fun dismissTrackUpdateDialog() {
        trackUpdateDialog?.dismiss()
        trackUpdateDialog = null
    }
    fun hideTrackUpdateDialog() {
        trackUpdateDialog?.dismiss()
        trackUpdateDialog = null
    }

    fun loadTrackInfosIfNeeded(context: Context, folderName: String) {
        val alreadyLoaded = cachedTrackInfosByFolder.containsKey(folderName)
        Log.d("TRACKCACHE", "👀 TrackInfos for $folderName already loaded: $alreadyLoaded")
        if (alreadyLoaded) return

        // 🔁 1. Versuche gespeicherte Reihenfolge zu laden
        val cached = TrackInfoStorage.load(context, folderName)
        if (cached != null) {
            cachedTrackInfosByFolder[folderName] = cached
            Log.d("TRACKCACHE", "📥 TrackInfos aus Speicher geladen (${cached.size}) für $folderName")
            return
        }

        // 🆕 2. Wenn keine gespeicherten Infos vorhanden sind → neu erzeugen und speichern
        val infos = FolderManager.getTrackInfosFromFolder(folderName)
        val sorted = infos.sortedBy { it.displayName.lowercase() }

        TrackInfoStorage.save(context, folderName, sorted)
        cachedTrackInfosByFolder[folderName] = sorted
        Log.d("TRACKCACHE", "✨ TrackInfos neu erzeugt und gespeichert (${sorted.size}) für $folderName")
    }

//    fun reloadAndSaveTrackInfos(context: Context, baseUri: Uri, folderName: String) {
//        val infos = FolderManager.getTrackInfosFromFolder(folderName)
//        TrackInfoStorage.save(context, folderName, infos)
//        currentTrackInfos = infos
//        isTrackInfoLoaded = true
//        Log.d("TRACKCACHE", "💾 TrackInfos loaded and saved (${infos.size})")
//    }
//    private fun isBaseUriValid(): Boolean {
//        if (baseUri == null) return false
//        val docFile = DocumentFile.fromTreeUri(this, baseUri!!) ?: return false
//        return docFile.exists() && docFile.isDirectory && docFile.listFiles().isNotEmpty()
//    }
    private fun updateTrackTitleDisplay() {
        if (tracks.isEmpty() || currentTrackIndex < 0 || currentTrackIndex >= tracks.size) return
        val trackNumber = currentTrackIndex + 1
        val formattedNumber = String.format("%02d", trackNumber)
//        val trackTitle = "$formattedNumber " + FolderManager.getFileNameFromUri(tracks[currentTrackIndex])
//        trackTitleTextView.text = trackTitle
        val info = cachedTrackInfosByFolder[currentFolder]?.getOrNull(currentTrackIndex)
        val displayName = info?.displayName ?: "[Unknown Track]"
        trackTitleTextView.text = "$formattedNumber $displayName"
    }
    private fun copyExampleTracksIfFirstInstall() {
        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)

        val alreadyInitialized = prefs.getBoolean("appInitialized", false)
        if (alreadyInitialized) {
            Log.d("INIT", "🛑 App already initialized – skipping example tracks")
            return
        }

        val folderUri = FolderManager.getFolderUri("tracks_1")
        if (folderUri == null) {
            Log.e("INIT", "❌ FolderUri for tracks_1 is null – SAF not ready?")
            return
        }

        try {
            val targetFolder = DocumentFile.fromTreeUri(this, folderUri)
            val fileExists1 = targetFolder?.findFile("example_1.mp3") != null
            val fileExists2 = targetFolder?.findFile("example_2.mp3") != null

            // Nur kopieren, wenn sie nicht eh schon da sind (z. B. durch manuelles Backup)
            if (!fileExists1) {
                val stream1 = assets.open("example_1.mp3")
                FolderManager.saveInputStreamToSAF(folderUri, "example_1.mp3", stream1)
            }
            if (!fileExists2) {
                val stream2 = assets.open("example_2.mp3")
                FolderManager.saveInputStreamToSAF(folderUri, "example_2.mp3", stream2)
            }

            Log.d("INIT", "✅ Example tracks copied on first install")

            prefs.edit().putBoolean("appInitialized", true).apply()
            loadTracksJob = lifecycleScope.launch {
                loadTracks()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("INIT", "❌ Error copying example tracks", e)
        }
    }


    private fun continueAppStartup() {
        lifecycleScope.launch {
            val hasChanged = checkForExternalFolderChanges()

            if (hasChanged) {
                Toast.makeText(this@MainActivity, "Updated list will appear soon", Toast.LENGTH_SHORT).show()
                loadTracks(fullReload = true)
                updateTrackTitleDisplay() // neu! Anzeige aktualisieren
            } else {
                loadTracks(fullReload = false)
            }

            setupAllButtons()
            folderButton.text = currentFolder
            copyExampleTracksIfFirstInstall()

            loadTrackInfosIfNeeded(this@MainActivity, currentFolder)
        }
    }

    fun setVolumeButtonText(volumeButton: Button, volumePercent: Int) {
        val text = "VOL: $volumePercent% \n\nCLICK:  Volume Up \nHOLD:  Volume Down\n\n\n"

        val spannable = SpannableString(text)

        spannable.setSpan(
            ForegroundColorSpan(Color.WHITE),
            10, 18, // CLICK
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            ForegroundColorSpan(Color.WHITE),
            29, 36, // HOLD
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        volumeButton.text = spannable
    }
    fun setPlayNextButtonText(playNextButton: Button) {
        val text = "CLICK:  Next Song / Fade Out\nORANGE-CLICK:  Stop\nHOLD:  Back\n\n"
        val spannable = SpannableString(text)

        // Indizes prüfen oder einfach so:
        spannable.setSpan(
            ForegroundColorSpan(Color.WHITE),
            text.indexOf("CLICK"), text.indexOf("CLICK") + 6,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            ForegroundColorSpan(Color.WHITE),
            text.indexOf("ORANGE-CLICK"), text.indexOf("ORANGE-CLICK") + 13,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            ForegroundColorSpan(Color.WHITE),
            text.indexOf("HOLD"), text.indexOf("HOLD") + 5,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        playNextButton.text = spannable
    }
    fun setReplayButtonText(button: Button) {
        val text = "CLICK:  Pause\nHOLD:  Replay\n\n"
        val spannable = SpannableString(text)

        spannable.setSpan(
            ForegroundColorSpan(Color.WHITE),
            text.indexOf("CLICK"), text.indexOf("CLICK") + 6,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        spannable.setSpan(
            ForegroundColorSpan(Color.WHITE),
            text.indexOf("HOLD"), text.indexOf("HOLD") + 5,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        button.text = spannable
    }
    fun setReplayPlayNextButtonText(button: Button) {
        val text = "CLICK:  Next Song\nHOLD:  Back\n\n"
        val spannable = SpannableString(text)

        spannable.setSpan(
            ForegroundColorSpan(Color.WHITE),
            text.indexOf("CLICK"), text.indexOf("CLICK") + 6,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        spannable.setSpan(
            ForegroundColorSpan(Color.WHITE),
            text.indexOf("HOLD"), text.indexOf("HOLD") + 5,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        button.text = spannable
    }

    private fun startProgressBar(durationMs: Int) {
        val redBar = findViewById<View>(R.id.progressBarRed)
        val container = findViewById<FrameLayout>(R.id.progressBarContainer)

        progressHandler?.removeCallbacks(progressRunnable ?: return)
        progressHandler = Handler(Looper.getMainLooper())

        progressRunnable = object : Runnable {
            override fun run() {
                val player = mediaPlayer ?: return

                val currentPosition = player.currentPosition
                val duration = player.duration

                // 🔁 Loop-Verlängerung (nur wenn Loop aktiv und nicht Replay-Modus)
                val effectiveDuration = if (!isReplayMode && currentLoopCount > 1) {
                    duration * currentLoopCount
                } else {
                    duration
                }

                // 🧮 Fortschritt über alle Loops hinweg
                val totalProgressPosition = (loopCountCompleted * duration) + currentPosition
                val progress = totalProgressPosition.toFloat() / effectiveDuration
                val newWidth = (container.width * progress).toInt()

                val layoutParams = redBar.layoutParams
                layoutParams.width = newWidth
                redBar.layoutParams = layoutParams

                // 🟠 Automatischer Orange-Zustand
                if (!isReplayMode && progress >= 0.85f && isPlaying && !isOrange && isGreen) {
                    autoNext = true
                    setButtonToOrange()
                }

                // 🟢 Zurück zu Grün, falls zurückgespult
                if (progress < 0.85f && isOrange && !isFadingOut) {
                    setButtonToGreen()
                }

                progressHandler?.postDelayed(this, 200)
            }
        }

        progressHandler?.post(progressRunnable!!)
    }



    private fun initEqualizer(sessionId: Int) {
        equalizer?.release()
        equalizer = Equalizer(0, sessionId).apply {
            enabled = true
            val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
            for (i in 0 until numberOfBands) {
                val level = prefs.getInt("eq_band_$i", 0)
                setBandLevel(i.toShort(), level.toShort())
            }
        }
        globalEqualizer = equalizer
    }

    private fun showTransferDialog() {
        val infos = cachedTrackInfosByFolder[currentFolder] ?: return
        if (tracks.isEmpty() || infos.isEmpty()) return
        if (currentTrackIndex !in tracks.indices || currentTrackIndex !in infos.indices) return

        val fileName = FolderManager.getFileNameFromUri(tracks[currentTrackIndex])
        val shortName = if (fileName.length > 30) fileName.substring(0, 30) + "..." else fileName

        val actionDialog = AlertDialog.Builder(this).setTitle(null).create()

        val outerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            gravity = Gravity.CENTER

            val titleView = TextView(context).apply {
                text = "Track: \"$shortName\""
                textSize = 18f
                setTextColor(Color.BLACK)
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setPadding(0, 0, 0, 32)
            }
            addView(titleView)

            val buttonRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                val buttonParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(20, 0, 20, 0) }

                val deleteField = Button(context).apply {
                    text = "🗑️ DELETE TRACK"
                    layoutParams = buttonParams
                    setOnClickListener {
                        actionDialog.dismiss()
                        lifecycleScope.launch {
                            showLoadingDialog()
                            delay(200)

                            val success = FolderManager.deleteTrackFromSAF(tracks[currentTrackIndex])

                            if (success) {
                                val updatedInfos = infos.toMutableList()
                                if (currentTrackIndex in updatedInfos.indices) {
                                    updatedInfos.removeAt(currentTrackIndex)
                                    TrackInfoStorage.save(this@MainActivity, currentFolder, updatedInfos)
                                    cachedTrackInfosByFolder[currentFolder] = updatedInfos
                                }
                                removeCurrentTrackFromList()
                                resetPlaybackStateAndProgressBar()
                                justSwitchedFolder = true
                            }

                            dismissTrackUpdateDialog()
                        }
                    }
                }

                val transferField = Button(context).apply {
                    text = "📥 TRANSFER TO BASKET"
                    layoutParams = buttonParams
                    setOnClickListener {
                        actionDialog.dismiss()
                        lifecycleScope.launch {
                            showLoadingDialog()
                            delay(200)

                            val moved = FolderManager.moveTrackToFolder(tracks[currentTrackIndex], "basket")

                            if (moved) {
                                val updatedInfos = infos.toMutableList()
                                if (currentTrackIndex in updatedInfos.indices) {
                                    val movedTrackInfo = updatedInfos[currentTrackIndex]
                                    updatedInfos.removeAt(currentTrackIndex)
                                    TrackInfoStorage.save(this@MainActivity, currentFolder, updatedInfos)
                                    cachedTrackInfosByFolder[currentFolder] = updatedInfos

                                    val basketInfos = TrackInfoStorage.load(this@MainActivity, "basket")?.toMutableList() ?: mutableListOf()
                                    basketInfos.add(movedTrackInfo)
                                    TrackInfoStorage.save(this@MainActivity, "basket", basketInfos)
                                    cachedTrackInfosByFolder["basket"] = basketInfos
                                }
                                removeCurrentTrackFromList()
                                resetPlaybackStateAndProgressBar()
                                justSwitchedFolder = true

                            } else {
                                Toast.makeText(this@MainActivity, "❌ Move failed", Toast.LENGTH_SHORT).show()
                            }

                            dismissTrackUpdateDialog()
                        }
                    }
                }

                val cancelField = Button(context).apply {
                    text = "Cancel"
                    layoutParams = buttonParams
                    setSingleLine(true)
                    isAllCaps = false
                    setOnClickListener { actionDialog.dismiss() }
                }

                addView(deleteField)
                addView(transferField)
                addView(cancelField)
            }

            addView(buttonRow)
        }

        actionDialog.setView(outerLayout)
        actionDialog.show()
        actionDialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.8).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun removeCurrentTrackFromList() {
        if (currentTrackIndex in tracks.indices) {
            // 1. URI sichern
            val removedUri = tracks[currentTrackIndex]

            // 2. URI aus Liste entfernen
            tracks.removeAt(currentTrackIndex)

            // 3. TrackInfo aus Cache und JSON entfernen
            val infos = cachedTrackInfosByFolder[currentFolder]?.toMutableList()
            if (infos != null) {
                infos.removeIf { it.uri == removedUri }
                cachedTrackInfosByFolder[currentFolder] = infos
                TrackInfoStorage.save(this, currentFolder, infos)
            }

            // 4. Index ggf. korrigieren
            if (tracks.isEmpty()) {
                currentTrackIndex = -1
            } else if (currentTrackIndex >= tracks.size) {
                currentTrackIndex = 0
            }

            // 5. Anzeige aktualisieren
            updateTrackTitleDisplay()

            if (tracks.isEmpty()) {
                setButtonToRed()
            } else {
                setButtonToGreen()
            }
        }
    }


    @SuppressLint("DefaultLocale")
    private fun playNextTrack() {
        progressRunnable?.let { progressHandler?.removeCallbacks(it) }// 🛑 Fortschrittsanzeige sofort stoppen
        currentTrackIndex = (currentTrackIndex + 1) % tracks.size
        playTrack()
    }

    private fun playTrack() {
        setButtonToGreen()
        mediaPlayer?.release()

        if (tracks.isEmpty()) {

            if (currentTrackIndex < 0 || currentTrackIndex >= tracks.size) {
                Toast.makeText(this, "⚠️ Invalid track index", Toast.LENGTH_SHORT).show()
                return
            }
            Toast.makeText(this, "⚠️ No tracks in folder", Toast.LENGTH_SHORT).show()
            return
        }
        // 🧼 Fortschrittszähler zurücksetzen (neuer Track!)
        loopCountCompleted = 0

        val originalUri = tracks[currentTrackIndex]

        val fileName = FolderManager.getFileNameFromUri(originalUri)
        val pitchPrefs = getSharedPreferences("pitch_prefs", Context.MODE_PRIVATE)
        val speedPrefs = getSharedPreferences("speed_prefs", Context.MODE_PRIVATE)

        currentPitchValue = pitchPrefs.getInt("pitch_$fileName", 0)
        currentSpeedValue = speedPrefs.getInt("speed_$fileName", 100)
        updatePitchButtonText()

        val trackUri = TrackProcessor.getProcessedTrackUri(
            this@MainActivity,
            originalUri,
            currentPitchValue,
            currentSpeedValue
        )

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@MainActivity, trackUri)


                // 🎚 Lautstärke vorbereiten
                val fileName = FolderManager.getFileNameFromUri(trackUri)
                val prefs = getSharedPreferences("volume_prefs", Context.MODE_PRIVATE)
                val storedVolume = prefs.getFloat("volume_$fileName", 0.5f)
                volume = storedVolume

                //  Loop-Anzahl laden
                val loopPrefs = getSharedPreferences("loop_prefs", Context.MODE_PRIVATE)
                currentLoopCount = loopPrefs.getInt("loops_$fileName", 1)
                if (!isLoopingInternally) {
                    loopRemaining = currentLoopCount - 1
                }
                updateLoopButtonText()

                setOnPreparedListener { player ->
                    loadingDialog?.dismiss()
                    loadingDialog = null
                    if (!isLoopingInternally) {
                        startProgressBar(player.duration)
                    }
                    initEqualizer(player.audioSessionId)

                    player.setVolume(volume, volume)
                    setVolumeButtonText(volumeButton, (volume * 100).toInt())

                    player.start()
                    this@MainActivity.isPlaying = true
                    setButtonToGreen()
                    updateVolumeDisplay()

                    if (isReplayMode) {
                        volumeButton.setBackgroundColor(Color.rgb(70, 150, 255))
                    }

                    // Sicherheits-Check bei Playback-Stuck
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            if (!player.isPlaying || player.currentPosition <= 0) {
                                Log.w("PLAYER", "Playback stuck – skipping track")
                                player.reset()
                                Handler(Looper.getMainLooper()).post {
                                    currentTrackIndex = (currentTrackIndex + 1) % tracks.size
                                    playNextTrack()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("PLAYER", "Playback check failed: ${e.message}")
                        }
                    }, 1500)
                }

                setOnCompletionListener {
                    if (isReplayMode) {
                        volumeButton.setBackgroundColor(Color.rgb(230, 70, 70))
                        setReplayButtonText(volumeButton)
                        return@setOnCompletionListener
                    }

                    if (currentLoopCount > 1 && loopRemaining > 0) {
                        loopRemaining--
                        loopCountCompleted++
                        isLoopingInternally = true
                        mediaPlayer?.seekTo(0)
                        mediaPlayer?.start()
                    } else {
                        isLoopingInternally = false
                        loopRemaining = currentLoopCount - 1
                        if (autoNext) {
                            playNextTrack()
                        } else {
                            setButtonToRed()
                        }
                    }
                }

                setOnSeekCompleteListener { mp ->
                    if (isRewinding && mp?.isPlaying == false) {
                        try {
                            mp.start()
                            Log.d("REWIND", "Playback resumed after seekTo.")
                        } catch (e: Exception) {
                            Log.e("PLAYER", "Start after seek failed: ${e.message}")
                        }
                    }
                }

                prepareAsync()
            }

            updateTrackTitleDisplay()
            isPlaying = true
            setButtonToGreen()
            updateVolumeDisplay()

        } catch (e: Exception) {
            Log.e("DEBUG", "Failed to play: ${e.message}")
        }
    }

    private var isMediaPlayerReleased = false

    private fun startFadeOut() {
        // 🚫 Loop abbrechen bei manuellem FadeOut
        loopRemaining = 0
        isLoopingInternally = false
        isFadingOut = true
        isMediaPlayerReleased = false // wichtig: zurücksetzen!
        fadeOutHandler = Handler(Looper.getMainLooper())

        fadeOutRunnable = object : Runnable {
            override fun run() {
                try {
                    if (mediaPlayer != null && !isMediaPlayerReleased && mediaPlayer!!.isPlaying) {
                        volume -= 0.015f
                        if (volume <= 0.015f) {
                            mediaPlayer!!.setVolume(0f, 0f)
                            updateVolumeDisplay()
                            mediaPlayer?.stop()
                            mediaPlayer?.release()
                            mediaPlayer = null
                            isMediaPlayerReleased = true
                            isPlaying = false
                            isFadingOut = false
                            if (autoNext) {
                                playNextTrack()
                            }
                            return
                        } else {
                            mediaPlayer!!.setVolume(volume, volume)
                            setVolumeButtonText(volumeButton, (volume * 100).toInt())
                            updateVolumeDisplay()
                            fadeOutHandler?.postDelayed(this, 200)
                        }
                    }
                } catch (e: IllegalStateException) {
                    Log.w("FadeOut", "mediaPlayer is in illegal state during fadeOut")
                    isFadingOut = false
                    isPlaying = false
                }
            }
        }

        fadeOutHandler?.post(fadeOutRunnable!!)
    }

    private fun stopFadeOut() {
        isFadingOut = false
        fadeOutHandler?.removeCallbacks(fadeOutRunnable ?: return)
        fadeOutHandler = null
        fadeOutRunnable = null
    }

    private fun setButtonToOrange() {
        playNextButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
        isGreen = false
        isYellow = false
        isOrange = true
        val displayIndex = (currentTrackIndex + 1) % tracks.size
        val trackNumber = displayIndex + 1
        val formattedNumber = String.format("%02d", trackNumber)
        val nextTitle = "Next: $formattedNumber " + FolderManager.getFileNameFromUri(tracks[displayIndex])
        trackTitleTextView.text = nextTitle
    }
    private fun setButtonToGreen() {
        playNextButton.setBackgroundColor(Color.rgb(100, 180, 0))
        isGreen = true
        isYellow = false
        isOrange = false
        yellowToOrangeHandler?.removeCallbacks(yellowToOrangeRunnable ?: return)

        // Anzeige zurück auf aktuellen Titel (ohne "Next: ")
        updateTrackTitleDisplay()
    }
    private fun setButtonToYellow() {
        playNextButton.setBackgroundColor(Color.rgb(220, 200, 0))
        isGreen = false
        isYellow = true
        isOrange = false

        yellowToOrangeHandler = Handler(Looper.getMainLooper())
        yellowToOrangeRunnable = Runnable {
            if (isYellow) {
                setButtonToOrange()
            }
        }
        yellowToOrangeHandler?.postDelayed(yellowToOrangeRunnable!!, 1000)
    }
    private fun setButtonToRed() {
        playNextButton.setBackgroundColor(Color.rgb(230, 70, 70))
        isGreen = false
        isYellow = false
        isOrange = false
        yellowToOrangeHandler?.removeCallbacks(yellowToOrangeRunnable ?: return)
    }


    private fun increaseVolume() {
        volume = min(1.0f, volume + 0.1f)
        updateVolumeDisplay()
        mediaPlayer?.setVolume(volume, volume)
        setVolumeButtonText(volumeButton, (volume * 100).toInt())

        // 🎚 Lautstärke speichern
        if (tracks.isNotEmpty() && currentTrackIndex >= 0) {
            val fileName = FolderManager.getFileNameFromUri(tracks[currentTrackIndex])
            getSharedPreferences("volume_prefs", Context.MODE_PRIVATE)
                .edit() {
                    putFloat("volume_$fileName", volume)
                }
        }
    }

    private fun startVolumeDown() {
        fadeOutHandler = Handler(Looper.getMainLooper())
        fadeOutRunnable = object : Runnable {
            override fun run() {
                if (volume > 0.1f) {
                    volume -= 0.02f
                    mediaPlayer?.setVolume(volume, volume)
                    setVolumeButtonText(volumeButton, (volume * 100).toInt())
                    updateVolumeDisplay()

                    // 🎚 Lautstärke speichern
                    if (tracks.isNotEmpty() && currentTrackIndex >= 0) {
                        val fileName = FolderManager.getFileNameFromUri(tracks[currentTrackIndex])
                        getSharedPreferences("volume_prefs", Context.MODE_PRIVATE)
                            .edit() {
                                putFloat("volume_$fileName", volume)
                            }
                    }

                    fadeOutHandler?.postDelayed(this, 200)
                }
            }
        }
        fadeOutHandler?.postDelayed(fadeOutRunnable!!, 150)
    }

    private fun stopVolumeDown() {
        fadeOutHandler?.removeCallbacks(fadeOutRunnable!!)
    }

    private fun updateVolumeDisplay() {
        if (!isReplayMode) {
            val volumePercentage = (volume * 100).toInt()
            setVolumeButtonText(volumeButton, volumePercentage)
        } else {
            // Im Replay-Modus: Text nicht überschreiben
            setReplayButtonText(volumeButton)
        }
    }


    @SuppressLint("DefaultLocale", "SetTextI18n")
    suspend fun loadTracks(fullReload: Boolean = false) {
        if (!coroutineContext.isActive) return  // 🛡️ ganz am Anfang

        if (baseUri == null) {
            Log.e("DEBUG", "no SAF-access")
            return
        }

        val urisInFolder = withContext(Dispatchers.IO) {
            if (!isActive) return@withContext emptyList<Uri>()

            if (fullReload) {
                FolderManager.getMp3FilesFromSAF(currentFolder)
            } else {
                if (tracks.isNotEmpty()) {
                    tracks
                } else {
                    FolderManager.getMp3FilesFromSAF(currentFolder)
                }
            }
        }

        if (!coroutineContext.isActive) return

        val fileNameToUri = urisInFolder.associateBy { FolderManager.getFileNameFromUri(it) }

        val stored = withContext(Dispatchers.IO) {
            if (!isActive) return@withContext null
            TrackInfoStorage.load(this@MainActivity, currentFolder)
        }


        if (!coroutineContext.isActive) return

        val storedNames = stored?.map { it.displayName } ?: emptyList()
        val orderedUris = mutableListOf<Uri>()

        for (name in storedNames) {
            if (!coroutineContext.isActive) return  // 🛑 auch in Schleifen sinnvoll
            fileNameToUri[name]?.let { orderedUris.add(it) }
        }

        val remainingUris = fileNameToUri.filterKeys { it !in storedNames }.values
        orderedUris.addAll(0, remainingUris)

        if (remainingUris.isNotEmpty()) {
            val updatedTrackInfos = withContext(Dispatchers.IO) {
                if (!isActive) return@withContext null
                orderedUris.mapNotNull { uri ->
                    if (!isActive) return@mapNotNull null
                    val name = FolderManager.getFileNameFromUri(uri)
                    val time = FolderManager.getFileModifiedTime(uri) ?: 0L
                    TrackInfo(uri.toString(), name, time)
                }.also {
                    if (isActive) {
                        TrackInfoStorage.save(this@MainActivity, currentFolder, it)
                        Log.d("LOADTRACKS", "✅ Neue TrackInfos gespeichert: ${it.size} Tracks")
                    }
                }
            }
        }

        if (!coroutineContext.isActive) return

        tracks = orderedUris.toMutableList()

        withContext(Dispatchers.Main) {
            if (!isActive) return@withContext  // 🔄 auch UI-Updates abbrechbar

            if (tracks.isEmpty()) {
                trackTitleTextView.text = "No Tracks in $currentFolder"
                currentTrackIndex = -1

                currentLoopCount = 1
                currentPitchValue = 0
                isPlaying = false
                isFadingOut = false
                loopCountCompleted = 0

                updateLoopButtonText()
                updatePitchButtonText()
                updateVolumeDisplay()

                val redBar = findViewById<View>(R.id.progressBarRed)
                val layoutParams = redBar.layoutParams
                layoutParams.width = 0
                redBar.layoutParams = layoutParams
            } else {
                currentTrackIndex = 0
                val trackNumber = currentTrackIndex + 1
                val formattedNumber = String.format("%02d", trackNumber)
//                val trackTitle = "$formattedNumber " + FolderManager.getFileNameFromUri(tracks[currentTrackIndex])
                val trackInfo = getCurrentTrackInfo()
                val trackTitle = if (trackInfo != null) {
                    "$formattedNumber ${trackInfo.displayName}"
                } else {
                    "$formattedNumber [Unknown Track]"
                }
                trackTitleTextView.text = trackTitle

                val fileName = FolderManager.getFileNameFromUri(tracks[0])
                val pitchPrefs = getSharedPreferences("pitch_prefs", Context.MODE_PRIVATE)
                currentPitchValue = pitchPrefs.getInt("pitch_$fileName", 0)
                val loopPrefs = getSharedPreferences("loop_prefs", Context.MODE_PRIVATE)
                currentLoopCount = loopPrefs.getInt("loops_$fileName", 1)
                updatePitchButtonText()
                updateLoopButtonText()
            }
        }
        // 🧠 Jetzt auch currentTrackInfos updaten!
        val infos = TrackInfoStorage.load(this, currentFolder)
        if (infos != null) {
            cachedTrackInfosByFolder[currentFolder] = infos
        }


//        cachedTrackInfosByFolder[currentFolder] = tracks.map { uri ->
//            val name = FolderManager.getFileNameFromUri(uri)
//            val time = FolderManager.getFileModifiedTime(uri) ?: 0L
//            TrackInfo(uri.toString(), name, time)
//        }
    }

    private suspend fun checkForExternalFolderChanges(): Boolean {
        if (baseUri == null) return false

        val safFiles = withContext(Dispatchers.IO) {
            FolderManager.getMp3FilesFromSAF(currentFolder)
        }

        return safFiles.size != tracks.size
    }

    private fun getCurrentTrackInfo(): TrackInfo? {
        return cachedTrackInfosByFolder[currentFolder]?.getOrNull(currentTrackIndex)
    }


//
//    private fun deleteCurrentTrack() {
//        val uri = tracks[currentTrackIndex]
//
//        try {
//            val fileName = FolderManager.getFileNameFromUri(uri)
//            Log.d("EQ-DELETE", "Dateiname: $fileName")
//
//            val root = DocumentFile.fromTreeUri(this, baseUri!!)
//            val targetFolder = root?.findFile(currentFolder)
//            Log.d("EQ-DELETE", "current folder ($currentFolder): ${targetFolder?.uri}")
//
//            val fileToDelete = targetFolder?.listFiles()?.firstOrNull {
//                it.name.equals(fileName, ignoreCase = true)
//            }
//            Log.d("EQ-DELETE", "found file: ${fileToDelete != null}")
//
//            if (fileToDelete != null && fileToDelete.delete()) {
//                Toast.makeText(this, "🗑️ file deleted", Toast.LENGTH_SHORT).show()
//                loadTracksJob = lifecycleScope.launch {
//                    loadTracks()
//                }
//            } else {
//                Toast.makeText(this, "❌ delete failed", Toast.LENGTH_SHORT).show()
//            }
//
//        } catch (e: Exception) {
//            Toast.makeText(this, "❌ delete failed", Toast.LENGTH_SHORT).show()
//            Log.e("EQ-DELETE", "Exception: ${e.message}")
//            e.printStackTrace()
//        }
//    }

    private fun stopPlayer(startNext: Boolean = false) {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false

        if (startNext) {
            Handler(Looper.getMainLooper()).postDelayed({
                playNextTrack()
            }, 500)
        }
        endCountdownHandler?.removeCallbacks(endCountdownRunnable ?: return)
        endCountdownHandler = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayer()
        progressHandler?.removeCallbacks(progressRunnable ?: return)
    }

    private fun startRewind() {
        isRewinding = true
        isAllowingAnpieksPause = true
        val player = mediaPlayer ?: return

        //volumeButton.setBackgroundColor(Color.rgb(70, 150, 255)) // Reset falls vorher rot

        rewindHandler = Handler(Looper.getMainLooper())

        var rewindMultiplier = 2.0

        rewindRunnable = object : Runnable {
            override fun run() {
                if (!isRewinding) return

                val currentPos = player.currentPosition
                val duration = player.duration

                val rewindStep = (rewindMultiplier * 250).toInt()
                val newPosition = if (currentPos - rewindStep > 0) {
                    currentPos - rewindStep
                } else {
                    duration - ((rewindStep - currentPos) % duration)
                }

                player.seekTo(newPosition)

                rewindMultiplier = (rewindMultiplier * 1.2).coerceAtMost(64.0)

                rewindHandler?.postDelayed(this, 250)
            }
        }

        rewindHandler?.post(rewindRunnable!!)
    }

    private fun stopRewindAndPlay() {
        isRewinding = false
        isAllowingAnpieksPause = false
        if (!isRewinding) return
        isRewinding = false
        volumeButton.setBackgroundColor(Color.rgb(70, 150, 255))

        rewindHandler?.removeCallbacks(rewindRunnable!!)
        rewindRunnable = null
        rewindHandler = null

        // Kleines Delay, damit das Anpieksen sauber durch ist
        Handler(Looper.getMainLooper()).postDelayed({
            mediaPlayer?.start()
            if (isReplayMode) {
                setButtonToGreen()  // ✅ Farbe zurück auf grün, wenn wieder gespielt wird
            }
        }, 350)
    }

    fun resetArrangeButtonStyle() {
        arrangeButton.setBackgroundResource(R.drawable.button_gradient)
    }

    private fun showLoopDialog() {
        var loopValue = currentLoopCount

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_selector, null)
        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val loopCountText = dialogView.findViewById<TextView>(R.id.countText) // die eigentliche Zahl
        val plusButton = dialogView.findViewById<Button>(R.id.plusButton)
        val minusButton = dialogView.findViewById<Button>(R.id.minusButton)
        dialogTitle.text = "Choose the number of played loops"

        loopCountText.text = loopValue.toString()

        plusButton.setOnClickListener {
            loopValue++
            loopCountText.text = loopValue.toString()
        }

        minusButton.setOnClickListener {
            if (loopValue > 1) {
                loopValue--
                loopCountText.text = loopValue.toString()
            }
        }
        dialogView.findViewById<TextView>(R.id.speedTitle)?.visibility = View.GONE
        dialogView.findViewById<LinearLayout>(R.id.speedRow)?.visibility = View.GONE

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.cancelText)?.setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.okText)?.setOnClickListener {
            currentLoopCount = loopValue
            loopRemaining = loopValue - 1
            val fileName = FolderManager.getFileNameFromUri(tracks[currentTrackIndex])
            val prefs = getSharedPreferences("loop_prefs", Context.MODE_PRIVATE)
            prefs.edit().putInt("loops_$fileName", currentLoopCount).apply()
            updateLoopButtonText()
            dialog.dismiss()
        }

        dialog.show()
    }
    private fun showPitchDialog() {
        var pitchValue = currentPitchValue
        var speedValue = currentSpeedValue
        val originalUri = tracks[currentTrackIndex]
        val fileName = FolderManager.getFileNameFromUri(originalUri)

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_selector, null)

        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialogTitle)
        dialogTitle.text = "Transpose (Pitch in semitones)"

        val countText = dialogView.findViewById<TextView>(R.id.countText)
        val plusButton = dialogView.findViewById<Button>(R.id.plusButton)
        val minusButton = dialogView.findViewById<Button>(R.id.minusButton)

        countText.text = pitchValue.toString()

        plusButton.setOnClickListener {
            if (pitchValue < 6) {
                pitchValue++
                countText.text = pitchValue.toString()
            }
        }

        minusButton.setOnClickListener {
            if (pitchValue > -6) {
                pitchValue--
                countText.text = pitchValue.toString()
            }
        }

        val countText2 = dialogView.findViewById<TextView>(R.id.countText2)
        val plusButton2 = dialogView.findViewById<Button>(R.id.plusButton2)
        val minusButton2 = dialogView.findViewById<Button>(R.id.minusButton2)

        countText2.text = "$speedValue%"

        plusButton2.setOnClickListener {
            if (speedValue < 150) {
                speedValue += 10
                countText2.text = "$speedValue%"
            }
        }

        minusButton2.setOnClickListener {
            if (speedValue > 50) {
                speedValue -= 10
                countText2.text = "$speedValue%"
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.cancelText)?.setOnClickListener {
            dialog.dismiss()
            playTrack()
        }

        dialogView.findViewById<TextView>(R.id.okText)?.setOnClickListener {
            // 🔐 speichern
            getSharedPreferences("pitch_prefs", Context.MODE_PRIVATE)
                .edit().putInt("pitch_$fileName", pitchValue).apply()

            getSharedPreferences("speed_prefs", Context.MODE_PRIVATE)
                .edit().putInt("speed_$fileName", speedValue).apply()

            // 🔁 aktualisieren
            currentPitchValue = pitchValue
            currentSpeedValue = speedValue
            pitchButton.text = if (pitchValue != 0) "Pitch: $pitchValue" else "Pitch"

            dialog.dismiss()

            val infoText = TextView(this).apply {
                text = "🎵 Applying pitch...\nThis may take up to a minute."
                setPadding(50, 40, 50, 40)
                textSize = 20f
            }

            loadingDialog = AlertDialog.Builder(this)
                .setView(infoText)
                .setCancelable(false)
                .create()

            loadingDialog?.show()

            getProcessedTrackUriInBackground(
                this,
                originalUri,
                pitchValue,
                speedValue
            ) {
                playTrack()
                loadingDialog?.dismiss()
                loadingDialog = null
            }
        }

        dialog.show()
    }

    private fun updateLoopButtonText() {
        loopButton.text = if (currentLoopCount > 1) "Loops: $currentLoopCount" else "Loops"
    }

    private fun updatePitchButtonText() {
        pitchButton.text = if (currentPitchValue != 0) "Pitch: $currentPitchValue" else "Pitch"
    }

    private fun getProcessedTrackUriInBackground(
        context: Context,
        originalUri: Uri,
        pitch: Int,
        speed: Int,
        onComplete: (Uri) -> Unit
    ) {
        Thread {
            val result = TrackProcessor.getProcessedTrackUri(context, originalUri, pitch, speed)
            runOnUiThread {
                onComplete(result)
            }
        }.start()
    }
    private fun showImpressumDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_info_text, null)
        val infoText = dialogView.findViewById<TextView>(R.id.infoText)
        val closeButton = dialogView.findViewById<Button>(R.id.closeButton)

        infoText.text = HtmlCompat.fromHtml(
            """Impressum: <br>
                <br>
                StreetMusician Player<br>
                Entwickler: Manfred Gotthalmseder<br>
                Kontakt: admin@picsandpixels.at<br>
                <br>
                This app is designed for playing backing tracks.<br>
                All functions are optimized for foot-operated use.<br>
                <br>
                Find more useful musician-tools on <a href="https://www.microstudio.at">www.MicroStudio.at</a>
            """.trimMargin(),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        infoText.movementMethod = LinkMovementMethod.getInstance()


        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
    private fun showAddInsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_info_text, null)
        val infoText = dialogView.findViewById<TextView>(R.id.infoText)
        val closeButton = dialogView.findViewById<Button>(R.id.closeButton)

        infoText.text = """
    Verwendete Komponenten:

    Pitch- und Tempo-Anpassung: 🎚 SoundTouch (www.surina.net/soundtouch)
    Lizenz: LGPL v2.1 – Änderungen vorgenommen

    MP3-Encoding: 🎧 LAME MP3 Encoder (lame.sourceforge.net)
    Lizenz: GPL v2 – verwendet in Android über libandroidlame

    💾 Quellcode verfügbar unter:
    https://github.com/gotthalmseder/StreetMusicianPlayer
""".trimIndent()

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

}

