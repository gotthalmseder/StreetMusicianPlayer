package com.example.streetmusicanplayer

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.appcompat.app.AlertDialog

class ArrangeTracks : AppCompatActivity() {

    private lateinit var trackListLayout: LinearLayout
    private lateinit var upButton: Button
    private lateinit var downButton: Button
    private lateinit var buttonSortAlphabetic: Button
    private lateinit var buttonSortByDate: Button
    private lateinit var buttonSortChaotic: Button
    private lateinit var buttonCustom: Button
    private lateinit var buttonClose: Button
    private var isCustomActive = true
//    private lateinit var loadingText: TextView

    private var trackInfos: MutableList<TrackInfo> = mutableListOf()
    private lateinit var folderManager: FolderManager
    private var selectedIndex = 0
    private lateinit var currentFolder: String
    private lateinit var baseUri: Uri
    private var trackNameViews = mutableListOf<TextView>()
    private lateinit var emptyListHint: TextView

    fun updateList() {
        trackListLayout.removeAllViews()

        if (trackInfos.isEmpty()) {
            emptyListHint.visibility = View.VISIBLE
            return
        } else {
            emptyListHint.visibility = View.GONE
        }
        val start = System.currentTimeMillis()

        trackListLayout.removeAllViews()
        trackNameViews.clear()

        val names = trackInfos.mapIndexed { index, info ->
            val displayName = info.displayName.replace(Regex("\\.mp3$", RegexOption.IGNORE_CASE), "")
            "${(index + 1).toString().padStart(2, '0')} $displayName"
        }

        names.forEachIndexed { index, name ->
            val textView = TextView(this).apply {
                text = name
                setPadding(10)
                setTextColor(Color.WHITE)
                textSize = 16f
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    selectedIndex = index
                    updateList()
                }
                setBackgroundColor(
                    if (index == selectedIndex) Color.rgb(70, 150, 255)
                    else Color.TRANSPARENT
                )
            }
            trackNameViews.add(textView)
            trackListLayout.addView(textView)
        }

        // Reihenfolge speichern
        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val order = trackInfos.joinToString("|") { it.displayName }
        prefs.edit().putString("order_$currentFolder", order).apply()

    }


    private var trackUpdateDialog: AlertDialog? = null

    private fun showTrackUpdateDialog() {
        if (trackUpdateDialog?.isShowing == true) return

        trackUpdateDialog = AlertDialog.Builder(this)
            .setTitle("⏳ Updating2 track list…")
            .setMessage("Please wait a moment")
            .setCancelable(false)
            .create()
        trackUpdateDialog?.show()
    }

    private fun dismissTrackUpdateDialog() {
        trackUpdateDialog?.dismiss()
        trackUpdateDialog = null
    }
    private fun highlightActiveSortButton(active: Button) {
        val allSortButtons = listOf(buttonCustom, buttonSortAlphabetic, buttonSortByDate, buttonSortChaotic)
        allSortButtons.forEach {
            it.setBackgroundResource(R.drawable.button_gradient)
        }
        active.setBackgroundResource(R.drawable.button_gradient_active)
    }
    private fun updateReorderVisibility() {
        val visibility = if (isCustomActive) View.VISIBLE else View.GONE
        upButton.visibility = visibility
        downButton.visibility = visibility
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_arrange_tracks)

        // 🛠️ 1. Alle Views sofort initialisieren
        trackListLayout = findViewById(R.id.trackListLayout)
        upButton = findViewById(R.id.buttonMoveUp)
        downButton = findViewById(R.id.buttonMoveDown)
        buttonSortAlphabetic = findViewById(R.id.buttonSortAlphabetic)
        buttonSortByDate = findViewById(R.id.buttonSortByDate)
        buttonSortChaotic = findViewById(R.id.buttonSortChaotic)
        buttonCustom = findViewById(R.id.buttonCustom)
        buttonClose = findViewById(R.id.buttonClose)
        emptyListHint = findViewById(R.id.emptyListHint)

        // 🎯 2. Intent-Daten lesen
        selectedIndex = intent?.getIntExtra("selectedIndex", 0) ?: 0
        currentFolder = intent.getStringExtra("folder") ?: return
        baseUri = intent.getParcelableExtra("baseUri") ?: return

        // 🚀 3. Versuchen, cachedTrackInfos zu verwenden (schnell!)
        val cached = MainActivity.cachedTrackInfosByFolder[currentFolder]
        if (cached != null) {
            trackInfos = cached.toMutableList()
            Log.d("ARRANGE", "✅ TrackInfos direkt aus globalem Cache geladen: ${trackInfos.size} Tracks")
            updateList()
        } else {
            // ❗ Sollte kaum noch eintreten – SAF-Fallback
            Log.d("ARRANGE", "⚠️ Kein Cache gefunden – lade aus SAF.")

            showTrackUpdateDialog()

            lifecycleScope.launch {
                val urisInFolder = withContext(Dispatchers.IO) {
                    FolderManager.getMp3FilesFromSAF(currentFolder)
                }

                val loaded = withContext(Dispatchers.IO) {
                    urisInFolder.map { uri ->
                        val name = FolderManager.getFileNameFromUri(uri)
                        val time = FolderManager.getFileModifiedTime(uri) ?: 0L
                        TrackInfo(uri.toString(), name, time)
                    }
                }

                trackInfos = loaded.toMutableList()

                MainActivity.cachedTrackInfosByFolder[currentFolder] = trackInfos
                TrackInfoStorage.save(this@ArrangeTracks, currentFolder, trackInfos)

                updateList()
                dismissTrackUpdateDialog()
            }
        }


        // 🛠️ 4. UI vorbereiten
        updateReorderVisibility()
        highlightActiveSortButton(buttonCustom)
        setupAllButtons()

        // 🎛️ 5. Fenster- und Statusleisten-Handling (optional verschieben oder ganz rausnehmen)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    )
        }
    }




    override fun onBackPressed() {
        closeAndReturnToMain()
    }

    private fun setupAllButtons() {
        upButton.setOnClickListener {
            if (selectedIndex > 0) {
                trackInfos[selectedIndex] = trackInfos.set(selectedIndex - 1, trackInfos[selectedIndex])
                selectedIndex--
                updateList()
            }
        }

        downButton.setOnClickListener {
            if (selectedIndex < trackInfos.size - 1) {
                trackInfos[selectedIndex] = trackInfos.set(selectedIndex + 1, trackInfos[selectedIndex])
                selectedIndex++
                updateList()
            }
        }

        buttonSortAlphabetic.setOnClickListener {
            // 🛡️ Speichern, falls wir gerade aus dem Custom-Modus kommen
            if (isCustomActive) {
                TrackInfoStorage.save(this, currentFolder, trackInfos)
                Log.d("ARRANGE", "💾 Auto-Save (before ABC) → ${trackInfos.map { it.displayName }}")
            }
            trackInfos.sortBy { it.displayName }
            selectedIndex = 0
            updateList()
            isCustomActive = false
            updateReorderVisibility()
            highlightActiveSortButton(buttonSortAlphabetic)
        }

        buttonSortByDate.setOnClickListener {
            // 🛡️ Speichern, falls wir gerade aus dem Custom-Modus kommen
            if (isCustomActive) {
                TrackInfoStorage.save(this, currentFolder, trackInfos)
                Log.d("ARRANGE", "💾 Auto-Save (before ABC) → ${trackInfos.map { it.displayName }}")
            }
            trackInfos.sortByDescending { it.modifiedTime }
            selectedIndex = 0
            updateList()
            isCustomActive = false
            updateReorderVisibility()
            highlightActiveSortButton(buttonSortByDate)
            Toast.makeText(this, "Sorted by date (newest first)", Toast.LENGTH_SHORT).show()
        }

        buttonSortChaotic.setOnClickListener {
            // 🛡️ Speichern, falls wir gerade aus dem Custom-Modus kommen
            if (isCustomActive) {
                TrackInfoStorage.save(this, currentFolder, trackInfos)
                Log.d("ARRANGE", "💾 Auto-Save (before ABC) → ${trackInfos.map { it.displayName }}")
            }
            trackInfos.shuffle()
            selectedIndex = 0
            updateList()
            isCustomActive = false
            updateReorderVisibility()
            highlightActiveSortButton(buttonSortChaotic)
        }
        buttonCustom.setOnClickListener {

            val saved = TrackInfoStorage.load(this, currentFolder)
            if (saved != null) {
                val existingNames = FolderManager.getMp3FilesFromSAF(currentFolder)
                    .map { FolderManager.getFileNameFromUri(it) }
                    .toSet()

                val filtered = saved.filter { it.displayName in existingNames }

                trackInfos = filtered.toMutableList()
                selectedIndex = 0
                updateList()
                isCustomActive = true
                updateReorderVisibility()
                highlightActiveSortButton(buttonCustom)
            } else {
                Toast.makeText(this, "No saved order found", Toast.LENGTH_SHORT).show()
            }
        }
        buttonClose.setOnClickListener {
            closeAndReturnToMain()
        }
    }
    private fun closeAndReturnToMain() {
        if (isCustomActive) {
            TrackInfoStorage.save(this, currentFolder, trackInfos)
            Log.d("ARRANGE", "💾 TrackInfos gespeichert: ${trackInfos.map { it.displayName }}")
        } else {
            Log.d("ARRANGE", "🔁 Temporäre Sortierung – nichts gespeichert.")
        }
        // 🔙 Style-Reset des Arrange-Buttons in MainActivity
//        runOnUiThread {
//            (applicationContext as? MainActivity)?.resetArrangeButtonStyle()
//        }

        MainActivity.globalTrackInfos = trackInfos
        MainActivity.isTrackInfoLoaded = true

        val resultIntent = Intent().apply {
            putExtra("updatedTrackInfos", JSONArray().apply {
                trackInfos.forEach {
                    put(JSONObject().apply {
                        put("uri", it.uri.toString())
                        put("name", it.displayName)
                        put("time", it.modifiedTime)
                    })
                }
            }.toString())
            putExtra("resetArrangeStyle", true) // ✨ wichtig!
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }


}

