package com.nossam.smahobutton

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class PermitActivity : ComponentActivity() {
    private val camera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val message = if (granted) "ライトを使えます" else "ライトの許可がありません"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action != ACTION_CAMERA) {
            finish()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            finish()
            return
        }
        if (savedInstanceState == null) camera.launch(Manifest.permission.CAMERA)
    }

    companion object {
        const val ACTION_CAMERA = "com.nossam.smahobutton.action.CAMERA"
    }
}
