package com.example.streetmusicanplayer.ui.theme

import android.app.Dialog
import android.content.Context
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import android.widget.SeekBar
import android.widget.Space
import android.view.View
import android.widget.FrameLayout
import com.example.streetmusicanplayer.MainActivity

class EqualizerDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val eq = MainActivity.globalEqualizer ?: return super.onCreateDialog(savedInstanceState)

        val root = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(32, 32, 32, 32)
        }

        val linearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER
        }

        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        val numberOfBands = eq.numberOfBands.toInt()
        val minLevel = -1600
        val maxLevel = 1600

        for (i in 0 until numberOfBands * 2 + 1) {
            if (i % 2 == 0) {
                // Spacer
                val spacer = Space(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                }
                linearLayout.addView(spacer)
            } else {
                val bandIndex = i / 2
                val freq = eq.getCenterFreq(bandIndex.toShort()) / 1000

                val bandLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                }

                val seekBar = SeekBar(context).apply {
                    max = maxLevel - minLevel
                    progress = prefs.getInt("eq_band_$bandIndex", 0) - minLevel
                    rotation = -90f
                    layoutParams = LinearLayout.LayoutParams(1000, 150) // extra lang
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                            val value = progress + minLevel
                            editor.putInt("eq_band_$bandIndex", value).apply()
                            eq.setBandLevel(bandIndex.toShort(), value.toShort())
                        }
                        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                    })
                }

                val label = TextView(context).apply {
                    text = "${freq} Hz"
                    gravity = Gravity.CENTER
                }

                bandLayout.addView(seekBar)
                bandLayout.addView(label)
                linearLayout.addView(bandLayout)
            }
        }

        root.addView(linearLayout)

        // ❌ Schließen-Button oben rechts
        val closeButton = Button(context).apply {
            text = "X"
            setOnClickListener { dismiss() }
        }
        val closeParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.END or Gravity.TOP
            setMargins(0, 16, 16, 0)
        }
        root.addView(closeButton, closeParams)

        // 🔁 Reset-Button unten links
        val resetButton = Button(context).apply {
            text = "RESET"
            setOnClickListener {
                for (i in 0 until numberOfBands) {
                    val default = 0
                    eq.setBandLevel(i.toShort(), default.toShort())
                    editor.putInt("eq_band_$i", default).apply()
                }

                // ❗ Jetzt alle SeekBars im Layout zurücksetzen
                for (j in 0 until linearLayout.childCount) {
                    val view = linearLayout.getChildAt(j)
                    if (view is LinearLayout) {
                        val seekBar = view.getChildAt(0)
                        if (seekBar is SeekBar) {
                            seekBar.progress = 0 - minLevel
                        }
                    }
                }
            }
        }
        val resetParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.START or Gravity.BOTTOM
            setMargins(32, 32, 32, 32)
        }
        root.addView(resetButton, resetParams)

        return Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
            setContentView(root)
        }
    }
}
