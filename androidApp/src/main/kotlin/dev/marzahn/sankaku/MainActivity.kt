package dev.marzahn.sankaku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    (application as SankakuApplication).startKoinOnce(intent)

    setContent {
      App(onQuit = ::finishAndRemoveTask)
    }
  }
}
