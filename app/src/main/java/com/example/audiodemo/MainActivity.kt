package com.example.audiodemo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private val RECORD_AUDIO = 10001
    private val READ_EXTERNAL_STORAGE = 10002

    var visButton: Button? = null
    var pcmButton: Button? = null
    var root: ViewGroup? = null
    var audioVisualizerView: AudioVisualizerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        visButton = findViewById(R.id.button1)
        pcmButton = findViewById(R.id.button2)
        root = findViewById(R.id.container)

        audioVisualizerView = AudioVisualizerView(this)
        root?.addView(audioVisualizerView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        visButton?.setOnClickListener {
            PermissionUtils.requestPermission(this, Manifest.permission.RECORD_AUDIO, RECORD_AUDIO)
            PermissionUtils.requestPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE)
            audioVisualizerView?.doPlayByMediaPlayer(R.raw.music)
        }

        pcmButton?.setOnClickListener {
            val pcmPath = "hurt.pcm"
            audioVisualizerView?.doPlayByAudioTrackPlayer(pcmPath)
        }


    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        when (requestCode) {
            RECORD_AUDIO, READ_EXTERNAL_STORAGE -> if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioVisualizerView?.release()
    }
}