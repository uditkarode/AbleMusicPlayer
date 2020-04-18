package io.github.uditkarode.able.activities

import android.os.Bundle
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import com.takisoft.preferencex.PreferenceFragmentCompat
import io.github.uditkarode.able.R
import io.github.uditkarode.able.utils.Shared

class Settings : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_view)
        title = "Settings"

        actionBar?.setDisplayHomeAsUpEnabled(true)
        if (Shared.isFirstRun) Shared.isFirstRun = false

        supportFragmentManager.beginTransaction()
            .replace(
                R.id.content,
                SettingsFragment()
            )
            .commit()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        return true
    }
}

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, null)
    }
}