package com.example.diseasepredictionapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var logo: ImageView
    private lateinit var appName: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        logo = findViewById(R.id.logo)
        appName = findViewById(R.id.app_name)

        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)

        logo.startAnimation(fadeIn)
        appName.startAnimation(slideUp)

        Handler(Looper.getMainLooper()).postDelayed({
            // ✅ Navigate to LoginActivity instead of MainActivity
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }, 2500) // 2.5 second splash duration
    }
}
