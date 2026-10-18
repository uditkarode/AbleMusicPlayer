package io.github.uditkarode.able.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.appbar.MaterialToolbar
import com.takisoft.preferencex.PreferenceFragmentCompat
import io.github.uditkarode.able.R
import io.github.uditkarode.able.utils.DevLog
import io.github.uditkarode.able.utils.Shared
/**
 * The settings page.
 */
class Settings: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        setContentView(R.layout.settings_view)

        val toolbar = findViewById<MaterialToolbar>(R.id.settings_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)

        if (Shared.isFirstOpen) Shared.isFirstOpen = false
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.content,
                SettingsFragment()
            )
            .commit()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        return true
    }
}

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, null)

        // The preference framework persists the switch; here we only start/stop
        // the capture in step with it (auto-start on boot lives in DevLog.init).
        findPreference<SwitchPreferenceCompat>("developer_logs")
            ?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue == true) DevLog.start(requireContext().applicationContext)
                else DevLog.stop()
                true
            }

        findPreference<Preference>("export_logs")
            ?.setOnPreferenceClickListener {
                exportLogs()
                true
            }
    }

    /** Zips every retained log file and hands it to a share sheet. */
    private fun exportLogs() {
        val ctx = requireContext()
        val zip = DevLog.exportZip(ctx)
        if (zip == null) {
            Toast.makeText(ctx, R.string.devlog_export_empty, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                ctx, "${ctx.packageName}.fileprovider", zip
            )
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, getString(R.string.pref_export_logs)))
        } catch (e: Exception) {
            Toast.makeText(ctx, R.string.devlog_export_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
