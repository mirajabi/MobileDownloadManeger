package com.miaadrajabi.span

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    val directory = remember { documentsSpanFolder() }
    val downloader = remember { IosSpanDownloader() }
    SpanScreen(directory, downloader)
}

private fun documentsSpanFolder(): String {
    val roots = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
    val base = roots.firstOrNull() as? String ?: NSHomeDirectory()
    return "$base/Span"
}
