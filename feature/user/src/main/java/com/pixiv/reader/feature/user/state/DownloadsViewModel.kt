package com.pixiv.reader.feature.user.state

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.novel.EpubNovelParser
import com.pixiv.reader.core.novel.LocalReaderStore
import com.pixiv.reader.core.novel.MarkdownNovelParser
import com.pixiv.reader.core.novel.TxtNovelParser
import com.pixiv.reader.feature.user.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 下载管理分类（插画 / 小说）。 */
enum class DownloadFilter(@StringRes val labelRes: Int) {
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
    @ApplicationContext private val context: Context,
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

    /** 删除索引，并清理对应本地文件（私有文件路径 或 SAF content uri）。 */
    fun delete(entry: DownloadEntryEntity) {
        viewModelScope.launch {
            entry.localPath?.let { path ->
                runCatching {
                    if (path.startsWith("content://")) {
                        DocumentFile.fromSingleUri(context, Uri.parse(path))?.delete()
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

    /** 解析 txt/epub/md 本地文件 → LocalReaderStore，成功后回调 onReady。支持私有路径与 content uri。 */
    fun openLocal(entry: DownloadEntryEntity, onReady: () -> Unit) {
        viewModelScope.launch {
            val doc = withContext(Dispatchers.IO) {
                val path = entry.localPath ?: return@withContext null
                val ext = path.substringAfterLast('.', "").lowercase()
                if (path.startsWith("content://")) {
                    // SAF 目录导出：经 ContentResolver 读取
                    val bytes = runCatching {
                        context.contentResolver.openInputStream(Uri.parse(path))?.use { it.readBytes() }
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
                LocalReaderStore.set(doc, entry.title ?: context.getString(R.string.downloads_local_novel))
                onReady()
            }
        }
    }
}
