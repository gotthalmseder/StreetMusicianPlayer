package com.example.streetmusicianplayer
import java.io.File
interface IConvertCallback {
    fun onSuccess(converted: File?)
    fun onFailure(error: Exception?)
}