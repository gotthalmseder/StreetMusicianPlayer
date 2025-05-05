package com.example.streetmusicanplayer

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object TrackInfoStorage {

    fun save(context: Context, folderName: String, list: List<TrackInfo>) {
        val json = Gson().toJson(list)
        val file = File(context.filesDir, "trackInfos_$folderName.json")
        file.writeText(json)
    }

    fun load(context: Context, folderName: String): List<TrackInfo>? {
        val file = File(context.filesDir, "trackInfos_$folderName.json")
        if (!file.exists()) return null
        val json = file.readText()
        return Gson().fromJson(json, object : TypeToken<List<TrackInfo>>() {}.type)
    }
    fun clear(context: Context, folderName: String) {
        val file = File(context.filesDir, "trackinfos_$folderName.json")
        if (file.exists()) {
            file.delete()
        }
    }
}