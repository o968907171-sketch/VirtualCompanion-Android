package com.example.virtualcompanion.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.example.virtualcompanion.service.OverlayService

/**
 * 很薄的圖片選擇橋接 Activity：從 Overlay 呼叫系統檔案選擇器，
 * 選完後把 URI 交回 OverlayService 顯示與回應。
 */
class PhotoReactionActivity : Activity() {
    private var characterId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        characterId = intent.getStringExtra(OverlayService.EXTRA_CHARACTER_ID).orEmpty()
        if (savedInstanceState == null) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, REQ_PICK)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                val service = Intent(this, OverlayService::class.java)
                    .setAction(OverlayService.ACTION_REACT_IMAGE)
                    .putExtra(OverlayService.EXTRA_CHARACTER_ID, characterId)
                    .putExtra(OverlayService.EXTRA_IMAGE_URI, uri.toString())
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
            }
        }
        finish()
    }

    companion object { private const val REQ_PICK = 6120 }
}
