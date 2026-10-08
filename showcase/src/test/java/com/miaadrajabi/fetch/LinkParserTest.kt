package com.miaadrajabi.fetch

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkParserTest {

    @Test
    fun parseKeepsHttpLinksAndDropsTheRest() {
        val raw = """
            notes before
            https://downloads.example.com/app.apk
            http://cdn.example.com/pack.zip, https://downloads.example.com/app.apk
            not a link
        """.trimIndent()

        assertEquals(
            listOf(
                "https://downloads.example.com/app.apk",
                "http://cdn.example.com/pack.zip"
            ),
            LinkParser.parse(raw)
        )
    }

    @Test
    fun fileNameComesFromTheLastPathSegment() {
        assertEquals(
            "app-release.apk",
            LinkParser.fileNameFrom("https://downloads.example.com/tms/app-release.apk?token=1")
        )
    }

    @Test
    fun aSinglePreferredNameReplacesTheGuess() {
        assertEquals(
            listOf("nightly.apk"),
            LinkParser.uniqueNames(
                listOf("https://downloads.example.com/a.apk"),
                "nightly.apk"
            )
        )
    }

    @Test
    fun severalLinksKeepDistinctNames() {
        assertEquals(
            listOf("a.apk", "a-2.apk"),
            LinkParser.uniqueNames(
                listOf(
                    "https://downloads.example.com/a.apk",
                    "https://other.example.com/a.apk"
                ),
                ""
            )
        )
    }

    @Test
    fun aLongEncodedNameKeepsTheExtensionAndStaysShort() {
        val name = LinkParser.fileNameFrom(
            "https://downloads.example.com/files/com.google.android.gms_26.33.32_%28040400-974685114%29" +
                "-263332004_minAPI24%28arm64-v8a%2Carmeabi-v7a%29%28nodpi%29_apkmirror.com.apk" +
                "?X-Amz-Signature=ignored"
        )
        assertEquals(true, name.endsWith(".apk"))
        assertEquals(true, name.length <= 80)
        assertEquals(false, name.contains("%"))
        assertEquals(false, name.contains("?"))
    }
}
