package com.fazzdev.offlineedgeportal.pkg.model

import android.net.Uri

data class PkgFile(
    val id: String,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val titleId: String? = null,
    val contentId: String? = null,
    val packageDigest: String? = null,
    val contentType: String? = "PS4GD"
)
