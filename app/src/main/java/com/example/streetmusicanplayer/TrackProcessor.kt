package com.example.streetmusicanplayer

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.streetmusicanplayer.TrackProcessor.convertMp3UriToWavFile
import com.example.streetmusicianplayer.AudioConverter
import com.naman14.androidlame.AndroidLame
import com.naman14.androidlame.LameBuilder
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

object TrackProcessor {

    private const val DELETION_THRESHOLD = 5

    fun getProcessedTrackUri(
        context: Context,
        originalUri: Uri,
        pitch: Int,
        speed: Int
    ): Uri {
        if (pitch == 0 && speed == 100) {
            return originalUri
        }

        val fileName = DocumentFile.fromSingleUri(context, originalUri)?.name ?: "track"
        val baseName = fileName.substringBeforeLast(".")
        val pitchedFolder = FolderManager.ensureFolderExists("basket/pitched") ?: return originalUri
        val sharedPrefs = context.getSharedPreferences("pitch_usage_prefs", Context.MODE_PRIVATE)

        val targetName = "${baseName}_p${pitch}_s${speed}.mp3"
        val targetFile = pitchedFolder?.findFile(targetName)
        if (targetFile != null) {
            incrementPitchUsage(sharedPrefs, targetName)
            maybeCleanupPitches(context, sharedPrefs, pitchedFolder, baseName, targetName)
            return targetFile.uri
        }

        val wavInputFile = convertMp3UriToWavFile(context, originalUri)
        if (wavInputFile == null || !wavInputFile.exists()) {
            Log.e("TrackProcessor", "MP3→WAV-Konvertierung fehlgeschlagen")
            return originalUri
        }

        val tempWavFile = File(context.cacheDir, "${baseName}_temp.wav")
        val success = AudioConverter.convertFile(
            inputPath = wavInputFile.absolutePath,
            outputPath = tempWavFile.absolutePath,
            pitchSemiTones = pitch,
            speedPercent = speed
        )

        if (!success || !tempWavFile.exists()) {
            Log.e("TrackProcessor", "SoundTouch-Pitching fehlgeschlagen")
            return originalUri
        }

        val tempMp3File = File(context.getExternalFilesDir(null), "temp_${System.currentTimeMillis()}.mp3")
        val mp3Success = convertWavToMp3(tempWavFile, tempMp3File)

        tempWavFile.delete()
        wavInputFile.delete()

        val resultUri: Uri = if (mp3Success && tempMp3File.exists()) {
            val pitchedFolder = FolderManager.ensureFolderExists("basket/pitched") ?: return originalUri
            val outUri = pitchedFolder.createFile("audio/mpeg", targetName)?.uri ?: return originalUri

            context.contentResolver.openOutputStream(outUri)?.use { output ->
                tempMp3File.inputStream().use { input -> input.copyTo(output) }
            }

            outUri
        } else {
            originalUri
        }

// Aufräumen
        tempMp3File.delete()
        incrementPitchUsage(sharedPrefs, targetName)
        maybeCleanupPitches(context, sharedPrefs, pitchedFolder, baseName, targetName)

        // 🟢 Loop vom Original übernehmen
        val originalFileName = fileName.substringBeforeLast(".")
        val loopPrefs = context.getSharedPreferences("loop_prefs", Context.MODE_PRIVATE)
        val originalLoopValue = loopPrefs.getInt("loops_$originalFileName", 1)
        loopPrefs.edit().putInt("loops_${targetName.substringBeforeLast(".")}", originalLoopValue).apply()
        return resultUri
    }

    private fun incrementPitchUsage(prefs: SharedPreferences, name: String) {
        val current = prefs.getInt(name, 0)
        prefs.edit().putInt(name, current + 1).apply()
    }

    private fun maybeCleanupPitches(
        context: Context,
        prefs: SharedPreferences,
        folder: DocumentFile,
        baseName: String,
        currentName: String
    ) {
        val currentCount = prefs.getInt(currentName, 0)
        if (currentCount < DELETION_THRESHOLD) return

        folder.listFiles().forEach { file ->
            if (file.name != null &&
                file.name!!.startsWith(baseName) &&
                file.name != currentName
            ) {
                file.delete()
                prefs.edit().remove(file.name).apply()
            }
        }
    }




    fun convertMp3UriToWavFile(context: Context, mp3Uri: Uri): File? {
        val TAG = "Mp3ToWav"
        val inputFile = File.createTempFile("input_", ".mp3", context.cacheDir)
        context.contentResolver.openInputStream(mp3Uri)?.use { input ->
            inputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val extractor = MediaExtractor()
        extractor.setDataSource(inputFile.absolutePath)
        var trackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                trackIndex = i
                break
            }
        }
        if (trackIndex == -1) {
            Log.e(TAG, "No audio track found")
            return null
        }

        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val outputFile = File.createTempFile("converted_", ".wav", context.cacheDir)
        val outputStream = BufferedOutputStream(FileOutputStream(outputFile))
        val wavData = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inputBufIndex = codec.dequeueInputBuffer(10000)
                if (inputBufIndex >= 0) {
                    val dstBuf = codec.getInputBuffer(inputBufIndex)!!
                    val sampleSize = extractor.readSampleData(dstBuf, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEOS = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        codec.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }
            }

            val outputBufIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputBufIndex >= 0) {
                val outputBuf = codec.getOutputBuffer(outputBufIndex)!!
                val chunk = ByteArray(bufferInfo.size)
                outputBuf.get(chunk)
                outputBuf.clear()
                wavData.write(chunk)
                codec.releaseOutputBuffer(outputBufIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    sawOutputEOS = true
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        val totalAudioLen = wavData.size().toLong()
        val totalDataLen = totalAudioLen + 36
        val byteRate = 16 * sampleRate * channels / 8
        val header = ByteArray(44)
        writeWavHeader(header, totalAudioLen, totalDataLen, sampleRate, channels, byteRate)
        outputStream.write(header)
        wavData.writeTo(outputStream)
        outputStream.flush()
        outputStream.close()
        Log.d(TAG, "WAV conversion successful: ${outputFile.absolutePath}")
        return outputFile
    }

    private fun writeWavHeader(
        header: ByteArray,
        totalAudioLen: Long,
        totalDataLen: Long,
        sampleRate: Int,
        channels: Int,
        byteRate: Int
    ) {
        val littleEndian = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        littleEndian.put("RIFF".toByteArray())
        littleEndian.putInt(totalDataLen.toInt())
        littleEndian.put("WAVE".toByteArray())
        littleEndian.put("fmt ".toByteArray())
        littleEndian.putInt(16)
        littleEndian.putShort(1)
        littleEndian.putShort(channels.toShort())
        littleEndian.putInt(sampleRate)
        littleEndian.putInt(byteRate)
        littleEndian.putShort((channels * 16 / 8).toShort())
        littleEndian.putShort(16)
        littleEndian.put("data".toByteArray())
        littleEndian.putInt(totalAudioLen.toInt())
    }

    fun convertWavToMp3(inputWav: File, outputMp3: File): Boolean {
        return try {
            val wavBytes = inputWav.readBytes()
            val isStereo = detectStereo(wavBytes)
            val channels = if (isStereo) 2 else 1

            val builder = LameBuilder()
                .setInSampleRate(44100)
                .setOutChannels(channels)
                .setOutBitrate(192)
                .setOutSampleRate(44100)
                .setMode(if (channels == 2) LameBuilder.Mode.STEREO else LameBuilder.Mode.MONO)
                .setQuality(5)

            val lame = AndroidLame(builder)

            val bufferSize = 8192
            val pcmBufferL = ShortArray(bufferSize)
            val pcmBufferR = ShortArray(bufferSize)
            val monoBuffer = ShortArray(bufferSize)
            val mp3Buffer = ByteArray((bufferSize * 1.25).toInt() + 7200)

            val outputStream = outputMp3.outputStream()
            var bytesEncoded: Int
            var i = 44

            while (i + 1 < wavBytes.size) {
                if (channels == 2) {
                    var j = 0
                    while (j < bufferSize && i + 3 < wavBytes.size) {
                        val left = (wavBytes[i + 1].toInt() shl 8) or (wavBytes[i].toInt() and 0xff)
                        val right = (wavBytes[i + 3].toInt() shl 8) or (wavBytes[i + 2].toInt() and 0xff)
                        pcmBufferL[j] = left.toShort()
                        pcmBufferR[j] = right.toShort()
                        j++
                        i += 4
                    }
                    bytesEncoded = lame.encode(pcmBufferL, pcmBufferR, bufferSize, mp3Buffer)
                } else {
                    var j = 0
                    while (j < bufferSize && i + 1 < wavBytes.size) {
                        val sample = (wavBytes[i + 1].toInt() shl 8) or (wavBytes[i].toInt() and 0xff)
                        monoBuffer[j] = sample.toShort()
                        j++
                        i += 2
                    }
                    bytesEncoded = lame.encode(monoBuffer, monoBuffer, bufferSize, mp3Buffer)
                }

                if (bytesEncoded > 0) {
                    outputStream.write(mp3Buffer, 0, bytesEncoded)
                }
            }

            bytesEncoded = lame.flush(mp3Buffer)
            if (bytesEncoded > 0) {
                outputStream.write(mp3Buffer, 0, bytesEncoded)
            }

            outputStream.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun detectStereo(wavBytes: ByteArray): Boolean {
        if (wavBytes.size < 22) return false
        val channels = ((wavBytes[23].toInt() shl 8) or (wavBytes[22].toInt() and 0xff))
        return channels == 2
    }
}