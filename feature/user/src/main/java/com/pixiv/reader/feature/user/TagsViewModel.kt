package com.pixiv.reader.feature.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixivapi.model.BookmarkTag
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 收藏标签类型（feature:user 本地枚举，避免跨 feature 依赖）。 */
enum class TagType { ILLUST, NOVEL }

/**
 * 收藏标签 ViewModel：当前用户收藏标签列表（插画/小说）。
 */
@HiltViewModel
class TagsViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    private val uid: Long = pixivRepository.pixivApi.session.loggedInUid

    private val _type = MutableStateFlow(TagType.ILLUST)
    val type: StateFlow<TagType> = _type.asStateFlow()

    private val _tags = MutableStateFlow<List<BookmarkTag>>(emptyList())
    val tags: StateFlow<List<BookmarkTag>> = _tags.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadTags()
    }

    fun selectType(type: TagType) {
        if (_type.value == type) return
        _type.value = type
        loadTags()
    }

    private fun loadTags() {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                when (_type.value) {
                    TagType.ILLUST -> pixivRepository.api.getIllustBookmarkTags(uid, "public").tags
                    TagType.NOVEL -> pixivRepository.api.getNovelBookmarkTags(uid, "public").tags
                }
            }.onSuccess { _tags.value = it }
                .onFailure { _tags.value = emptyList() }
            _isLoading.value = false
        }
    }
}
