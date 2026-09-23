package com.fazzdev.offlineedgeportal.pkg.model

enum class PkgLogLevel {
    INFO,
    SUCCESS,
    WARN,
    ERROR
}

data class PkgLogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: PkgLogLevel = PkgLogLevel.INFO,
    val message: String,
    val details: String? = null
)
