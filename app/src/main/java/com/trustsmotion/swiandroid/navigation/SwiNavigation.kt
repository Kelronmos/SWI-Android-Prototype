package com.trustsmotion.swiandroid.navigation

import android.app.Activity
import androidx.activity.ComponentActivity
import android.content.Intent
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import com.trustsmotion.swiandroid.MainActivity

object SwiNavigation {

    fun addNavigation(
        activity: Activity,
        layout: LinearLayout,
        showHome: Boolean = true
    ) {
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(8, 16, 8, 16)
        }

        bar.addView(Button(activity).apply {
            text = "← BACK"
            setOnClickListener {
                if (!activity.isFinishing) {
                    if (activity is ComponentActivity) {
                        activity.onBackPressedDispatcher.onBackPressed()
                    } else {
                        @Suppress("DEPRECATION")
                        activity.onBackPressed()
                    }
                }
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))

        if (showHome && activity !is MainActivity) {
            bar.addView(Button(activity).apply {
                text = "⌂ HOME"
                setOnClickListener {
                    val intent = Intent(activity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    activity.startActivity(intent)
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }

        layout.addView(bar)
    }
}
