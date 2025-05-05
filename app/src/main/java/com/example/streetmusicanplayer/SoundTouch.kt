package com.example.streetmusicanplayer

class SoundTouch {
    init {
        System.loadLibrary("soundtouch")
    }

    external fun setTempo(tempo: Float)
    external fun setPitchSemiTones(pitch: Float)
    external fun processFile(inputFile: String, outputFile: String): Int
}