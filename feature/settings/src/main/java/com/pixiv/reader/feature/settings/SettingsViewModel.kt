package com.pixiv.reader.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.reader.core.datastore.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置 ViewModel：主题模式 / 动态取色 / 自动更新（读写 DataStore）+ 关于信息。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    val themeMode: StateFlow<Int> =
        userPreferences.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val dynamicColor: StateFlow<Boolean> =
        userPreferences.dynamicColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val autoUpdate: StateFlow<Boolean> =
        userPreferences.autoUpdate.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val versionName: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    fun setThemeMode(value: Int) {
        viewModelScope.launch { userPreferences.setThemeMode(value) }
    }

    fun setDynamicColor(value: Boolean) {
        viewModelScope.launch { userPreferences.setDynamicColor(value) }
    }

    fun setAutoUpdate(value: Boolean) {
        viewModelScope.launch { userPreferences.setAutoUpdate(value) }
    }
}
