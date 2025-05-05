package com.example.streetmusicianplayer

import android.util.Log
import java.io.File

object AudioConverter {

    init {
        System.loadLibrary("soundtouch")  // lädt libsoundtouch.so aus jniLibs
    }

    // Native Funktionen aus der .so-Datei
    external fun setPitchSemiTones(pitch: Float)
    external fun setTempo(tempo: Float)
    external fun processFile(inputPath: String, outputPath: String): Int

    /**
     * Führt die Umwandlung durch (MP3 → WAV mit Pitch/Tempo)
     */
    fun convertFile(
        inputPath: String,
        outputPath: String,
        pitchSemiTones: Int,
        speedPercent: Int
    ): Boolean {
        try {
            val soundTouch = net.surina.soundtouch.SoundTouch()

            val tempo = speedPercent / 100f
            Log.d("AudioConverter", "→ PITCH: $pitchSemiTones | TEMPO: $tempo")

            soundTouch.setPitchSemiTones(pitchSemiTones.toFloat())
            soundTouch.setTempo(tempo)

            val result = soundTouch.processFile(inputPath, outputPath)
            soundTouch.close()

            return result == 0
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
