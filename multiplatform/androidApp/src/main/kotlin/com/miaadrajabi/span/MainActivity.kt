package com.miaadrajabi.span

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val directory = (getExternalFilesDir(null) ?: filesDir).resolve("Span").absolutePath
        setContent {
            val downloader = remember { OkHttpSpanDownloader() }
            SpanScreen(directory, downloader)
        }
    }
}
