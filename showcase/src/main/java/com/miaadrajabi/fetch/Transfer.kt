package com.miaadrajabi.fetch

data class Transfer(
    val id: String,
    val url: String,
    val fileName: String,
    val status: String,
    val bytes: Long = 0L,
    val total: Long = 0L,
    val speed: Long = 0L,
    val percent: Int = -1,
    val message: String = "",
    val whenLabel: String = "",
    val title: String = "",
    val localPath: String = "",
    val locationUri: String = ""
) {
    fun host(): String {
        val rest = url.substringAfter("://", url)
        val host = rest.substringBefore('/').substringBefore(':')
        return if (host.isBlank()) url else host
    }
}
