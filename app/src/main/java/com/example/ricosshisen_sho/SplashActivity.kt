package com.example.ricosshisen_sho

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Must be called before super.onCreate
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // 2. Set content so the window is initialized
        setContentView(R.layout.activity_splash)

        // 3. Setup Edge-to-Edge and Hide System Bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemUI()

        val logo = findViewById<ImageView>(R.id.splash_logo)

        lifecycleScope.launch {
            delay(800)

            logo?.animate()
                ?.alpha(0f)
                ?.setDuration(400)
                ?.start()

            delay(350)

            val intent = Intent(this@SplashActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(intent)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.fade_in, 0)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.fade_in, 0)
            }

            delay(50)
            finish()
        }
    }

    private fun hideSystemUI() {
        // Using WindowInsetsControllerCompat is the correct way for 2026/Jetpack apps
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        // Hide both the status bar and the navigation bar
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        // Make them reappear only briefly when the user swipes
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}