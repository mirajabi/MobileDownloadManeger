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
}
