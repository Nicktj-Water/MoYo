package com.nicktj.autoledger.core.data.export

import com.nicktj.autoledger.core.data.dao.CategoryDao
import com.nicktj.autoledger.core.data.dao.RecognitionLogDao
import com.nicktj.autoledger.core.data.dao.TransactionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * 账单导出：先把库里的数据摊平成 [ExportRow]，再分别交给 CSV / XLSX 生成器。
 *
 * 文件落在调用方给的目录里（App 侧传 `getExternalFilesDir(null)/export/`），
 * 生成后由 App 层用 FileProvider + ACTION_SEND 分享出去。
 */
class ExportRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val recognitionLogDao: RecognitionLogDao,
) {

    /** 读取一份导出快照（不含已丢弃的记录）。 */
    suspend fun loadRows(): List<ExportRow> {
        val transactions = transactionDao.getAllOnce()
        val categoryNames = categoryDao.getAll().associate { it.id to it.name }

        val logIds = transactions.mapNotNull { it.rawLogId }.distinct()
        val rawTextById: Map<Long, String> = if (logIds.isEmpty()) {
            emptyMap()
        } else {
            recognitionLogDao.textsByIds(logIds).associate { log ->
                log.id to (log.text ?: log.bigText ?: log.title.orEmpty())
            }
        }

        return transactions.map { transaction ->
            transaction.toExportRow(
                categoryName = transaction.categoryId?.let { categoryNames[it] } ?: "未分类",
                rawText = transaction.rawLogId?.let { rawTextById[it] }.orEmpty(),
            )
        }
    }

    /** 生成 CSV 文件并返回（写盘走 IO 线程）。 */
    suspend fun exportCsv(directory: File): File = withContext(Dispatchers.IO) {
        val rows = loadRows()
        val file = newFile(directory, "csv")
        file.writeText(CsvExporter.build(rows), Charsets.UTF_8)
        file
    }

    /** 生成 XLSX 文件并返回（写盘走 IO 线程）。 */
    suspend fun exportXlsx(directory: File): File = withContext(Dispatchers.IO) {
        val rows = loadRows()
        val file = newFile(directory, "xlsx")
        file.writeBytes(XlsxExporter.build(rows))
        file
    }

    private fun newFile(directory: File, extension: String): File {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val stamp = LocalDateTime.now(EXPORT_ZONE).format(EXPORT_FILE_STAMP)
        return File(directory, "autoledger-$stamp.$extension")
    }
}
