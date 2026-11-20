package com.nicktj.autoledger.ui.screens.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * 把导出的文件通过系统分享面板发出去。
 *
 * FileProvider 的 authority 必须和 AndroidManifest 里注册的一致：
 * `${applicationId}.fileprovider`，这里用 `context.packageName` 拼出来。
 */
object ExportSharer {

    /** 成功返回 null，失败返回给用户看的原因。 */
    fun share(context: Context, file: File, mimeType: String): String? {
        if (!file.exists()) return "导出文件不存在：${file.name}"
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + FILE_PROVIDER_SUFFIX,
                file,
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "导出账单").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            null
        } catch (throwable: Throwable) {
            "分享失败：${throwable.message ?: throwable.javaClass.simpleName}" +
                "（请确认 AndroidManifest 已注册 FileProvider）"
        }
    }

    /** 与 manifest 中的 `android:authorities="${applicationId}.fileprovider"` 对应。 */
    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"
}
