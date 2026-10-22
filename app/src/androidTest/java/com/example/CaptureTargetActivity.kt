package com.example

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView

/** Lives in the separately installed test APK, independently of Peek's activities. */
class CaptureTargetActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(245, 247, 250))
            for ((index, label) in listOf("Other app", "A page to select", "Screen sharing stays active here").withIndex()) {
                addView(TextView(this@CaptureTargetActivity).apply {
                    text = label
                    textSize = 26f
                    setTextColor(Color.rgb(20, 35, 55))
                    setPadding(32, 60, 32, 60)
                    if (index == 1) setBackgroundColor(Color.rgb(135, 220, 210))
                })
            }
        })
    }
}
