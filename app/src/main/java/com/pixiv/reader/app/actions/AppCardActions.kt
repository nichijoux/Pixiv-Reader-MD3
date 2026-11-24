package com.pixiv.reader.app.actions

import com.pixiv.reader.core.database.dao.ReadLaterDao
import com.pixiv.reader.core.database.entity.ReadLaterEntity
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.ui.component.actions.CardActionTarget
import com.pixiv.reader.core.ui.component.actions.CardActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** 集合状态完全由 Room/DataStore 回灌驱动，写操作不做内存乐观更新以避免双源互踩。 */
@Singleton
class AppCardActions @Inject constructor(
    private val readLaterDao: ReadLaterDao,
    private val userPreferences: UserPreferences,
) : CardActions {

    private lateinit var appScope: CoroutineScope

    private val _readLaterIds = MutableStateFlow<Set<String>>(emptySet())
    override val readLaterIds: StateFlow<Set<String>> = _readLaterIds.asStateFlow()

    private val _blockedIds = MutableStateFlow<Set<String>>(emptySet())
    override val blockedIds: StateFlow<Set<String>> = _blockedIds.asStateFlow()

    private val _mutedTags = MutableStateFlow<Set<String>>(emptySet())
    override val mutedTags: StateFlow<Set<String>> = _mutedTags.asStateFlow()

    fun start(scope: CoroutineScope) {
        appScope = scope
        // 仅订阅目标键投影，避开 payloadJson 大列
        scope.launch {
            readLaterDao.observeAllTargets().collect { list ->
                _readLaterIds.value = list.map { "${it.targetType}:${it.targetId}" }.toSet()
            }
        }
        scope.launch {
            userPreferences.blockedTargets.collect { _blockedIds.value = it }
        }
        // 预先规范化为小写集合，避免卡片高频渲染期重复处理
        scope.launch {
            userPreferences.mutedTags.collect { tags ->
                _mutedTags.value = tags
                    .asSequence()
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .toSet()
            }
        }
    }

    override fun toggleReadLater(target: CardActionTarget) {
        val key = keyOf(target)
        appScope.launch {
            if (key in _readLaterIds.value) {
                readLaterDao.deleteByTarget(target.targetType, target.targetId)
            } else {
                readLaterDao.replaceTop(
                    ReadLaterEntity(
                        targetType = target.targetType,
                        targetId = target.targetId,
                        title = target.title,
                        payloadJson = target.payloadJson,
                    ),
                )
            }
        }
    }

    override fun toggleBlock(targetType: String, targetId: Long) {
        val key = "$targetType:$targetId"
        appScope.launch {
            if (key in _blockedIds.value) {
                userPreferences.removeBlockedTarget(targetType, targetId)
            } else {
                userPreferences.addBlockedTarget(targetType, targetId)
            }
        }
    }

    private fun keyOf(target: CardActionTarget): String = "${target.targetType}:${target.targetId}"
}
