package com.pixiv.reader.feature.user.state

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.network.download.DownloadQueue
import com.pixiv.reader.core.novel.parser.EpubNovelParser
import com.pixiv.reader.core.novel.parser.MarkdownNovelParser
import com.pixiv.reader.core.novel.parser.TxtNovelParser
import com.pixiv.reader.core.novel.store.LocalReaderStore
import com.pixiv.reader.feature.user.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** 作品段涵盖插画/漫画/动图，小说段含 txt/epub/md/pdf/docx。 */
enum class DownloadFilter(@param:StringRes val labelRes: Int) {
    ILLUST(R.string.downloads_filter_illust),
    NOVEL(R.string.downloads_filter_novel),
}

/**
 * 下载管理实现在本模块（历史 feature/download 已删除）：txt/epub/md 解析后进 LocalReaderStore 本地阅读，
 * pdf/docx 交系统应用打开（见 DownloadsRoute）。
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloadEntryDao: DownloadEntryDao,
) : ViewModel() {

    val entries: StateFlow<List<DownloadEntryEntity>> =
        downloadEntryDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 选中态由 UI 侧 Pager 状态持有，此处仅用于分流，不对外暴露。 */
    private val filter = MutableStateFlow(DownloadFilter.ILLUST)

    fun selectFilter(f: DownloadFilter) {
        if (filter.value != f) filter.value = f
    }

    /** 未完成条目需同时取消后台任务，否则删除后联网 Worker 会复活重建条目。 */
    fun delete(entry: DownloadEntryEntity) {
        viewModelScope.launch {
            if (entry.status != DownloadEntryEntity.STATUS_DONE) {
                runCatching {
                    WorkManager.getInstance(context).cancelAllWorkByTag(
                        DownloadQueue.workTag(entry.targetType, entry.targetId, entry.format, entry.scopeKey),
                    )
                }
            }
            withContext(Dispatchers.IO) {
                entry.localPath?.let { path ->
                    runCatching {
                        if (path.startsWith("content://")) {
                            context.contentResolver.delete(path.toUri(), null, null)
                        } else {
                            val file = File(path)
                            if (file.exists() && file.canonicalPath.startsWith(context.filesDir.canonicalPath)) {
                                // 多页插画下载存的是目录（Downloads/pixiv_{id}/）
                                if (file.isDirectory) file.deleteRecursively() else file.delete()
                            }
                        }
                    }
                }
            }
            downloadEntryDao.delete(entry)
        }
    }

    /** 解析结果写入 LocalReaderStore（内存单槽，consume 即清空），由调用方接着导航。 */
    fun openLocal(
        entry: DownloadEntryEntity,
        onReady: () -> Unit,
        onFailed: () -> Unit = {},
    ) {
        viewModelScope.launch {
            val doc = withContext(Dispatchers.IO) {
                val path = entry.localPath ?: return@withContext null
                // content://media/... 不含文件名，类型只能靠索引 format 字段判断
                val ext = when (entry.format) {
                    DownloadEntryEntity.FORMAT_MARKDOWN -> "md"
                    DownloadEntryEntity.FORMAT_EPUB -> "epub"
                    else -> entry.format.lowercase() // TXT → txt
                }
                try {
                    if (path.startsWith("content://")) {
                        val bytes = context.contentResolver.openInputStream(path.toUri())
                            ?.use { it.readBytes() } ?: return@withContext null
                        when (ext) {
                            "txt" -> TxtNovelParser.parse(bytes.toString(Charsets.UTF_8))
                            "md" -> MarkdownNovelParser.parse(bytes.toString(Charsets.UTF_8))
                            "epub" -> {
                                // epub 解析需要 File：写临时文件，解析结束后立即清理
                                val tmp = File(context.cacheDir, "open_local_${entry.targetId}.epub")
                                try {
                                    tmp.writeBytes(bytes)
                                    EpubNovelParser.parse(tmp)
                                } finally {
                                    tmp.delete()
                                }
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
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
            if (doc != null) {
                LocalReaderStore.set(
                    doc,
                    entry.title ?: context.getString(R.string.downloads_local_novel)
                )
                onReady()
            } else {
                onFailed()
            }
        }
    }
}
