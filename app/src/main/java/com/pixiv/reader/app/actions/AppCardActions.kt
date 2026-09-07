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
 * 两组集合缓存为进程级 StateFlow 供卡片同步读取（模糊判断无异步等待）；
 * 写操作乐观更新 StateFlow（UI 即时反馈），落库结果经数据流回灌纠正。
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
     * 加入 / 移出稍后再看（按目标当前状态取反；乐观更新 + 落库）。
     * 加入时先删旧再插（target 唯一），payloadJson 快照随行落库供离线还原卡片。
     */
    override fun toggleReadLater(target: CardActionTarget) {
        val key = keyOf(target)
        val removing = key in _readLaterIds.value
        _readLaterIds.value = if (removing) _readLaterIds.value - key else _readLaterIds.value + key
        appScope.launch {
            if (removing) {
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

    /** 屏蔽 / 取消屏蔽（按目标当前状态取反；乐观更新 + 落 DataStore）。 */
    override fun toggleBlock(targetType: String, targetId: Long) {
        val key = "$targetType:$targetId"
        val removing = key in _blockedIds.value
        _blockedIds.value = if (removing) _blockedIds.value - key else _blockedIds.value + key
        appScope.launch {
            if (removing) userPreferences.removeBlockedTarget(targetType, targetId)
            else userPreferences.addBlockedTarget(targetType, targetId)
        }
    }

    /** 目标键（`"illust:1"` 形式，与集合成员一致）。 */
    private fun keyOf(target: CardActionTarget): String = "${target.targetType}:${target.targetId}"
}
