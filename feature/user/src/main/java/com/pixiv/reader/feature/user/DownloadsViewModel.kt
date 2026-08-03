package com.pixiv.reader.feature.user

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 下载管理分类。 */
enum class DownloadFilter { ALL, ILLUST, NOVEL }

/**
 * 下载管理 ViewModel：观察下载索引（Room），支持按类型分类与删除（含本地文件）。
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadEntryDao: DownloadEntryDao,
) : ViewModel() {

    val entries: StateFlow<List<DownloadEntryEntity>> =
        downloadEntryDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val filter = MutableStateFlow(DownloadFilter.ALL)
    val filterFlow: StateFlow<DownloadFilter> = filter

    fun selectFilter(f: DownloadFilter) {
        if (filter.value != f) filter.value = f
    }

    /** 删除索引，并尝试删除本地文件（仅应用私有目录内文件）。 */
    fun delete(entry: DownloadEntryEntity) {
        viewModelScope.launch {
            entry.localPath?.let { path ->
                runCatching {
                    val file = File(path)
                    if (file.exists() && file.canonicalPath.startsWith(context.filesDir.canonicalPath)) {
                        file.delete()
                    }
                }
            }
            downloadEntryDao.delete(entry)
        }
    }
}
