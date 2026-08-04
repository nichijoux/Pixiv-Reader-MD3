package com.pixiv.reader.feature.user

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.network.offline.OfflineNovelRepository
import com.pixiv.reader.core.novel.EpubNovelParser
import com.pixiv.reader.core.novel.LocalReaderStore
import com.pixiv.reader.core.novel.TxtNovelParser
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

/** 下载管理分类（插画 / 小说 / 离线）。 */
enum class DownloadFilter { ILLUST, NOVEL, OFFLINE }

/**
 * 下载管理 ViewModel：观察下载索引（Room），支持按类型分类与删除。
 * - illust / novel：文件导出，删除时删本地文件 + 索引
 * - novel_offline：离线缓存，删除时清离线缓存 + 索引
 * - novel（txt/epub）：解析本地文件 → LocalReaderStore 供本地阅读
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadEntryDao: DownloadEntryDao,
    private val offlineNovelRepository: OfflineNovelRepository,
) : ViewModel() {

    val entries: StateFlow<List<DownloadEntryEntity>> =
        downloadEntryDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val filter = MutableStateFlow(DownloadFilter.ILLUST)
    val filterFlow: StateFlow<DownloadFilter> = filter

    fun selectFilter(f: DownloadFilter) {
        if (filter.value != f) filter.value = f
    }

    /** 删除索引，并清理对应本地资源（文件导出删文件；离线缓存清缓存）。 */
    fun delete(entry: DownloadEntryEntity) {
        viewModelScope.launch {
            if (entry.targetType == "novel_offline") {
                runCatching { offlineNovelRepository.delete(entry.targetId) }
            } else {
                entry.localPath?.let { path ->
                    runCatching {
                        val file = File(path)
                        if (file.exists() && file.canonicalPath.startsWith(context.filesDir.canonicalPath)) {
                            file.delete()
                        }
                    }
                }
            }
            downloadEntryDao.delete(entry)
        }
    }

    /** 解析 txt/epub 本地文件 → LocalReaderStore，成功后回调 onReady。 */
    fun openLocal(entry: DownloadEntryEntity, onReady: () -> Unit) {
        viewModelScope.launch {
            val doc = withContext(Dispatchers.IO) {
                val path = entry.localPath ?: return@withContext null
                val file = File(path)
                if (!file.exists()) return@withContext null
                when (file.extension.lowercase()) {
                    "txt" -> TxtNovelParser.parse(file.readText(Charsets.UTF_8))
                    "epub" -> EpubNovelParser.parse(file)
                    else -> null
                }
            }
            if (doc != null) {
                LocalReaderStore.set(doc, entry.title ?: "本地小说")
                onReady()
            }
        }
    }
}
