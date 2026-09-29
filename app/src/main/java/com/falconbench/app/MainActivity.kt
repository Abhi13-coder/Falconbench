package com.falconbench.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.falconbench.app.ui.BenchScreen
import com.falconbench.app.ui.FalconBenchTheme

class MainActivity : ComponentActivity() {
    private val vm: BenchViewModel by viewModels()
    private val pickGguf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) { }
        vm.importGguf(uri)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FalconBenchTheme {
                Surface(Modifier.fillMaxSize()) {
                    BenchScreen(vm = vm, onPickModel = {
                        pickGguf.launch(arrayOf("*/*", "application/octet-stream"))
                    })
                }
            }
        }
    }
}
