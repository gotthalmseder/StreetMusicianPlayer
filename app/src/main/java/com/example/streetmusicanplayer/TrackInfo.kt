package com.example.streetmusicanplayer

import android.net.Uri

data class TrackInfo(
    val uriString: String,
    val displayName: String,
    val modifiedTime: Long
) {
    val uri: Uri
        get() = uriString?.let { Uri.parse(it) } ?: Uri.EMPTY
}