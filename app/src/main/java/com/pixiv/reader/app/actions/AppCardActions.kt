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

/**
 * 卡片本地动作实现（进程单例，app 层组装）：
 * 稍后再看走 Room（read_later 表）、就地屏蔽走 DataStore（blocked_targets 键）。
 *
 * 集合状态为**单一数据源**：由 Room / DataStore 的数据流回灌驱动（本地写毫秒级，无可感知延迟），
 * 写操作只负责落库，不做内存乐观更新——消除乐观值与回灌值双源互踩。
 */
@Singleton
class AppCardActions @Inject constructor(
    private val readLaterDao: ReadLaterDao,
    private val userPreferences: UserPreferences,
) : CardActions {

    /** 宿主提供的进程级协程作用域（[start] 注入；写操作用它落库）。 */
    private lateinit var appScope: CoroutineScope

    private val _readLaterIds = MutableStateFlow<Set<String>>(emptySet())
    override val readLaterIds: StateFlow<Set<String>> = _readLaterIds.asStateFlow()

    private val _blockedIds = MutableStateFlow<Set<String>>(emptySet())
    override val blockedIds: StateFlow<Set<String>> = _blockedIds.asStateFlow()

    /**
     * 启动数据流同步（应用创建时调用一次）。
     *
     * @param scope 进程级作用域（Application 生命周期，不随页面销毁）
     * @return 无返回值；订阅持续到进程结束
     */
    fun start(scope: CoroutineScope) {
        appScope = scope
        // Room 稍后再看 → StateFlow（"illust:1" 形式键）
        scope.launch {
            readLaterDao.observeAll().collect { list ->
                _readLaterIds.value = list.map { "${it.targetType}:${it.targetId}" }.toSet()
            }
        }
        // DataStore 本地屏蔽 → StateFlow
        scope.launch {
            userPreferences.blockedTargets.collect { _blockedIds.value = it }
        }
    }

    /**
     * 加入 / 移出稍后再看：按数据流回灌的当前状态取反，仅落库（幂等）。
     * 加入时先删旧再插（target 唯一），payloadJson 快照随行落库供离线还原卡片。
     */
    override fun toggleReadLater(target: CardActionTarget) {
        val key = keyOf(target)
        appScope.launch {
            if (key in _readLaterIds.value) {
                readLaterDao.deleteByTarget(target.targetType, target.targetId)
            } else {
                readLaterDao.deleteByTarget(target.targetType, target.targetId)
                readLaterDao.upsert(
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

    /** 屏蔽 / 取消屏蔽：按数据流回灌的当前状态取反，仅落 DataStore（幂等）。 */
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

    /** 目标键（`"illust:1"` 形式，与集合成员一致）。 */
    private fun keyOf(target: CardActionTarget): String = "${target.targetType}:${target.targetId}"
}
