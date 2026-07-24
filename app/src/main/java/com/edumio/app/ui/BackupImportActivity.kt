package com.edumio.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.edumio.app.R
import com.edumio.app.core.BackupManager

class BackupImportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup_import)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val input = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.backupJsonInput)
        findViewById<android.widget.Button>(R.id.btnValidate).setOnClickListener {
            val json = input.text?.toString()?.trim() ?: ""
            val summary = BackupManager.validateImport(this, json)
            if (summary == null) {
                android.widget.Toast.makeText(this, "Geçersiz yedek formatı", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val msg = getString(R.string.backup_import_summary, summary.profileCount, summary.blockedAppCount)
            MaterialAlertDialogBuilder(this)
                .setTitle("Yedek Özeti")
                .setMessage("$msg\n\nUygula?")
                .setPositiveButton("Evet") { _, _ ->
                    if (BackupManager.importBackup(this, json)) {
                        android.widget.Toast.makeText(this, "Yedek geri yüklendi", android.widget.Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        android.widget.Toast.makeText(this, "Geri yükleme hatası", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("İptal", null)
                .show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
