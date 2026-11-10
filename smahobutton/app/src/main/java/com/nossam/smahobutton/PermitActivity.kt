package com.nossam.smahobutton

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.nossam.smahobutton.service.OverlayService

class PermitActivity : ComponentActivity() {
    private val camera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val message = if (granted) "ライトを使えます" else "ライトの許可がありません"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private val microphone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            OverlayService.start(this)
            armAndFinish(0)
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.action) {
            ACTION_CAMERA -> openCamera(savedInstanceState)
            ACTION_MIC -> openMicrophone(savedInstanceState)
            else -> finish()
        }
    }

    private fun openCamera(savedInstanceState: Bundle?) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            finish()
            return
        }
        if (savedInstanceState == null) camera.launch(Manifest.permission.CAMERA)
    }

    private fun armAndFinish(attempt: Int) {
        if (isDestroyed) return
        if (OverlayService.armMicrophone() || attempt >= 8) {
            finish()
            return
        }
        window.decorView.postDelayed({ armAndFinish(attempt + 1) }, 50)
    }

    private fun openMicrophone(savedInstanceState: Bundle?) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            OverlayService.start(this)
            armAndFinish(0)
            return
        }
        if (savedInstanceState == null) microphone.launch(Manifest.permission.RECORD_AUDIO)
    }

    companion object {
        const val ACTION_CAMERA = "com.nossam.smahobutton.action.CAMERA"
        const val ACTION_MIC = "com.nossam.smahobutton.action.MIC"
    }
}
