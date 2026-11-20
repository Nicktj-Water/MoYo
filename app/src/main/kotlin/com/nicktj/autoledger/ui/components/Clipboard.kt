package com.nicktj.autoledger.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

/** 复制一段文本到系统剪贴板（用系统 API，避开 Compose 剪贴板 API 的版本差异）。 */
fun copyToClipboard(context: Context, label: String, text: String, toast: String = "已复制") {
    runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        manager?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
    }
}
