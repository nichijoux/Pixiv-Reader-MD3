package com.pixiv.reader.feature.user

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixivapi.model.User
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.network.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 我的页 ViewModel：当前登录用户信息 + 应用版本 + 屏蔽标签管理（本地偏好）。
 */
@HiltViewModel
class MeViewModel @Inject constructor(
    @ApplicationContext context: Context,
    sessionRepository: SessionRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(sessionRepository.currentUser?.user)
    val user: StateFlow<User?> = _user.asStateFlow()

    /** 当前登录用户 UID（进个人主页用）。 */
    val ownUid: Long? get() = _user.value?.id

    val versionName: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    /** 屏蔽标签（本地偏好，用于推荐/搜索过滤）。 */
    val mutedTags: StateFlow<List<String>> =
        userPreferences.mutedTags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 主题模式：0 跟随系统 / 1 浅色 / 2 深色。 */
    val themeMode: StateFlow<Int> =
        userPreferences.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 动态取色。 */
    val dynamicColor: StateFlow<Boolean> =
        userPreferences.dynamicColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _message = Channel<String>(Channel.BUFFERED)
    val message = _message.receiveAsFlow()

    fun setThemeMode(value: Int) {
        viewModelScope.launch { userPreferences.setThemeMode(value) }
    }

    fun setDynamicColor(value: Boolean) {
        viewModelScope.launch { userPreferences.setDynamicColor(value) }
    }

    fun addMutedTag(tag: String) {
        val t = tag.trim()
        if (t.isBlank()) return
        viewModelScope.launch {
            runCatching {
                if (t !in mutedTags.value) userPreferences.setMutedTags(mutedTags.value + t)
            }
        }
    }

    fun removeMutedTag(tag: String) {
        viewModelScope.launch {
            runCatching { userPreferences.setMutedTags(mutedTags.value - tag) }
        }
    }
}
