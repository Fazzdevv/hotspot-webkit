package com.fazzdev.offlineedgeportal.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.fazzdev.offlineedgeportal.ui.screens.DashboardScreen
import com.fazzdev.offlineedgeportal.ui.theme.OfflineEdgePortalTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // SAF Document Picker for .zip files
    private val pickZipLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = queryFileName(uri)
            viewModel.importZip(this, uri, fileName)
        }
    }

    // Permission request launcher for notifications on Android 13+
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Proceed regardless
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkNotificationPermission()
        viewModel.loadInitialSiteInfo(this)

        setContent {
            OfflineEdgePortalTheme {
                DashboardScreen(
                    viewModel = viewModel,
                    onPickZipClick = {
                        pickZipLauncher.launch(
                            arrayOf(
                                "application/zip",
                                "application/x-zip-compressed",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshNetworkInterfaces()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun queryFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }
}
