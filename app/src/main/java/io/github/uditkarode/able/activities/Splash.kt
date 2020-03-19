package io.github.uditkarode.able.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import io.github.uditkarode.able.R
import io.github.uditkarode.able.utils.Constants
import kotlinx.android.synthetic.main.splash.*

class Splash: AppCompatActivity() {

    var seen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash)

        Handler().postDelayed({
            if(checkCallingOrSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED){
                splash_logo.visibility = View.GONE
                startActivity(Intent(this@Splash, Welcome::class.java))
            } else {
                finish()
            }
        }, 1000)
    }

    override fun onResume() {
        super.onResume()
        if(!seen) seen = true
        else finish()
    }
}