package com.pixiv.reader.feature.user.state

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.novel.parser.EpubNovelParser
import com.pixiv.reader.core.novel.parser.MarkdownNovelParser
import com.pixiv.reader.core.novel.parser.TxtNovelParser
import com.pixiv.reader.core.novel.store.LocalReaderStore
import com.pixiv.reader.feature.user.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** 下载管理分类（插画 / 小说）。 */
enum class DownloadFilter(@param:StringRes val labelRes: Int) {
    ILLUST(R.string.downloads_filter_illust),
    NOVEL(R.string.downloads_filter_novel),
}

/**
 * 下载管理 ViewModel：观察下载索引（Room），支持按类型分类与删除。
 * - illust：图片文件导出，删除时删本地文件 + 索引
 * - novel（txt/epub/md）：解析本地文件 → LocalReaderStore 供本地阅读
 * - novel（pdf/docx）：系统应用打开（见 DownloadsRoute）
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloadEntryDao: DownloadEntryDao,
) : ViewModel() {

    val entries: StateFlow<List<DownloadEntryEntity>> =
        downloadEntryDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val filter = MutableStateFlow(DownloadFilter.ILLUST)
    val filterFlow: StateFlow<DownloadFilter> = filter

    fun selectFilter(f: DownloadFilter) {
        if (filter.value != f) filter.value = f
    }

    /** 删除索引，并清理对应本地文件（私有文件路径 / SAF / MediaStore content uri）。 */
    fun delete(entry: DownloadEntryEntity) {
        viewModelScope.launch {
            entry.localPath?.let { path ->
                runCatching {
                    if (path.startsWith("content://")) {
                        // SAF 与 MediaStore uri 统一经 ContentResolver 删除
                        context.contentResolver.delete(path.toUri(), null, null)
                    } else {
                        val file = File(path)
                        if (file.exists() && file.canonicalPath.startsWith(context.filesDir.canonicalPath)) {
                            // 多页插画下载存目录（Downloads/pixiv_{id}/），递归删除
                            if (file.isDirectory) file.deleteRecursively() else file.delete()
                        }
                    }
                }
            }
            downloadEntryDao.delete(entry)
        }
    }

    /** 解析 txt/epub/md 本地文件 → LocalReaderStore，成功后回调 onReady。支持私有路径与 content uri（SAF/MediaStore）。 */
    fun openLocal(entry: DownloadEntryEntity, onReady: () -> Unit) {
        viewModelScope.launch {
            val doc = withContext(Dispatchers.IO) {
                val path = entry.localPath ?: return@withContext null
                // MediaStore uri（content://media/...）不含文件名，类型用索引 format 字段判断
                val ext = when (entry.format) {
                    "MARKDOWN" -> "md"
                    "EPUB" -> "epub"
                    else -> entry.format.lowercase() // TXT → txt
                }
                if (path.startsWith("content://")) {
                    // SAF/MediaStore 目录导出：经 ContentResolver 读取
                    val bytes = runCatching {
                        context.contentResolver.openInputStream(path.toUri())
                            ?.use { it.readBytes() }
                    }.getOrNull() ?: return@withContext null
                    when (ext) {
                        "txt" -> TxtNovelParser.parse(bytes.toString(Charsets.UTF_8))
                        "md" -> MarkdownNovelParser.parse(bytes.toString(Charsets.UTF_8))
                        "epub" -> {
                            // epub 解析需要 File：复制到私有缓存临时文件
                            val tmp = File(context.cacheDir, "open_local_${entry.targetId}.epub")
                            runCatching { tmp.writeBytes(bytes) }
                            EpubNovelParser.parse(tmp)
                        }

                        else -> null
                    }
                } else {
                    val file = File(path)
                    if (!file.exists()) return@withContext null
                    when (ext) {
                        "txt" -> TxtNovelParser.parse(file.readText(Charsets.UTF_8))
                        "md" -> MarkdownNovelParser.parse(file.readText(Charsets.UTF_8))
                        "epub" -> EpubNovelParser.parse(file)
                        else -> null
                    }
                }
            }
            if (doc != null) {
                LocalReaderStore.set(
                    doc,
                    entry.title ?: context.getString(R.string.downloads_local_novel)
                )
                onReady()
            }
        }
    }
}
