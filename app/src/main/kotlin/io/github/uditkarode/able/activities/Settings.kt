package io.github.uditkarode.able.activities

import android.os.Bundle
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.takisoft.preferencex.PreferenceFragmentCompat
import io.github.uditkarode.able.R
import io.github.uditkarode.able.utils.Shared
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * The settings page.
 */
@ExperimentalCoroutinesApi
class Settings: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        setContentView(R.layout.settings_view)
        title = getString(R.string.settings)

        actionBar?.setDisplayHomeAsUpEnabled(true)
        if (Shared.isFirstOpen) Shared.isFirstOpen = false
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