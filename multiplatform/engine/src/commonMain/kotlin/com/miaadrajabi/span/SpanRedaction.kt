package com.miaadrajabi.span

object SpanRedaction {
    private val authorization = Regex("(?i)authorization\\s*[:=]\\s*\\S+(?:\\s+\\S+)?")
    private val bearer = Regex("(?i)bearer\\s+\\S+")
    private val querySecret = Regex("(?i)([?&](?:access_token|token|password|passwd|secret|signature|sig)=)[^&\\s]+")
    private val userInfo = Regex("(?i)(https?://)[^/\\s:@]+:[^/\\s@]+@")

    fun redact(line: String): String {
        val hiddenHeader = authorization.replace(line, "Authorization: [redacted]")
        val hiddenBearer = bearer.replace(hiddenHeader, "Bearer [redacted]")
        val hiddenQuery = querySecret.replace(hiddenBearer) { match -> match.groupValues[1] + "[redacted]" }
        return userInfo.replace(hiddenQuery) { match -> match.groupValues[1] + "[redacted]@" }
    }
}
