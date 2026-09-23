package com.example.virtualcompanion.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.widget.TextView

object BubbleStyler {
    fun apply(view: TextView, style: String, context: Context? = null, customRef: String = "") {
        if (style == "自訂圖片" && context != null && customRef.isNotBlank()) {
            val drawable = loadDrawable(context, customRef)
            if (drawable != null) {
                view.background = drawable
                view.setTextColor(Color.WHITE)
                view.gravity = Gravity.CENTER_VERTICAL
                view.setPadding(28, 18, 28, 18)
                return
            }
        }

        val bg = GradientDrawable()
        when (style) {
            "漫畫框" -> {
                bg.setColor(Color.WHITE); bg.setStroke(4, Color.BLACK); bg.cornerRadius = 4f; view.setTextColor(Color.BLACK)
            }
            "簡約" -> {
                bg.setColor(Color.argb(230, 32, 32, 36)); bg.cornerRadius = 8f; view.setTextColor(Color.WHITE)
            }
            "透明" -> {
                bg.setColor(Color.argb(130, 0, 0, 0)); bg.cornerRadius = 28f; view.setTextColor(Color.WHITE)
            }
            else -> {
                bg.setColor(Color.WHITE); bg.setStroke(2, Color.rgb(105, 90, 140)); bg.cornerRadius = 28f; view.setTextColor(Color.rgb(35, 33, 39))
            }
        }
        view.background = bg
        view.gravity = Gravity.CENTER_VERTICAL
        view.setPadding(20, 14, 20, 14)
    }

    private fun loadDrawable(context: Context, ref: String): Drawable? = try {
        context.contentResolver.openInputStream(Uri.parse(ref))?.use { Drawable.createFromStream(it, "bubble") }
    } catch (_: Exception) { null }
}
