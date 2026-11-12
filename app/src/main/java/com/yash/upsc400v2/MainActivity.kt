package com.yash.upsc400v2

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import com.yash.upsc400v2.data.exportBackup
import com.yash.upsc400v2.data.importBackup
import com.yash.upsc400v2.timer.Notifier
import com.yash.upsc400v2.ui.AppRoot

class MainActivity : ComponentActivity() {

    private val model: AppModel by viewModels()
    private var pickRes: String? = null

    private val pickPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val res = pickRes
        pickRes = null
        if (uri != null && res != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // some providers do not allow persistable grants; the URI may stop working later
            }
            model.attachPdf(res, uri)
        } else {
            model.cancelPending()
        }
    }

    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            Thread {
                try {
                    val json = model.store.exportBackup()
                    contentResolver.openOutputStream(uri)?.use { out -> out.write(json.toByteArray(Charsets.UTF_8)) }
                    runOnUiThread { model.message = "Backup saved" }
                } catch (e: Exception) {
                    runOnUiThread { model.message = "Backup failed: " + (e.message ?: "unknown error") }
                }
            }.start()
        }
    }

    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            Thread {
                try {
                    val text = contentResolver.openInputStream(uri)?.use { s -> s.readBytes().toString(Charsets.UTF_8) }
                        ?: throw IllegalArgumentException("Could not read the file")
                    model.timer.cancelAll()
                    model.store.importBackup(text)
                    runOnUiThread {
                        model.openSession = null
                        model.reader = null
                        model.bump()
                        model.message = "Backup restored"
                    }
                } catch (e: Exception) {
                    runOnUiThread { model.message = "Restore failed: " + (e.message ?: "unknown error") }
                }
            }.start()
        }
    }

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannels(this)
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(this)) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        model.pickPdf = { res ->
            pickRes = res
            pickPdf.launch(arrayOf("application/pdf"))
        }
        model.exportBackupFile = { createBackup.launch("upsc400-v2-backup.json") }
        model.importBackupFile = { openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        setContent {
            AppRoot(model, isSystemInDarkTheme())
        }
    }
}
