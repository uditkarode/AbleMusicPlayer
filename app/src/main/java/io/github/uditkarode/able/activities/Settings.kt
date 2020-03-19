package io.github.uditkarode.able.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.afollestad.materialdialogs.MaterialDialog
import io.github.inflationx.calligraphy3.CalligraphyConfig
import io.github.inflationx.calligraphy3.CalligraphyInterceptor
import io.github.inflationx.viewpump.ViewPump
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import io.github.uditkarode.able.R
import kotlinx.android.synthetic.main.settings.*

class Settings: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        ViewPump.init(
            ViewPump.builder()
                .addInterceptor(
                    CalligraphyInterceptor(
                        CalligraphyConfig.Builder()
                            .setDefaultFontPath("fonts/inter.otf")
                            .setFontAttrId(R.attr.fontPath)
                            .build()
                    )
                )
                .build()
        )
        setContentView(R.layout.settings)

        support.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/AbleApp")))
        }

        donate.setOnClickListener {
            MaterialDialog(this@Settings).show {
                title(text = "Donate")
                message(text = "Hey everyone! It took a lot of hard work " +
                        "to turn this project into reality, and I hope you're " +
                        "enjoying it! If you like the project" +
                        " and want it to continue, please consider donating. It motivates me" +
                        " to keep working on it." +
                        "\n\n\nYou can donate via " +
                        "<a href=\"https://paypal.me/uditkarode\">PayPal</a>" +
                        "\nor via the UPI address udit.karode@okaxis"){
                    html()
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase!!))
    }
}