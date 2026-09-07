package com.pixiv.api.model

import com.google.gson.annotations.SerializedName

/**
 * 私信（talk）模型（app-api.pixiv.net，Bearer 鉴权；只读展示）。
 * 字段以官方 app 接口常见结构为准，全部可空兜底。
 */

/** 私信会话列表响应（v1/talk/rooms） */
data class TalkRoomsResponse(
    @SerializedName("talk_rooms") val talkRooms: List<TalkRoom>? = null,
)

/** 私信会话 */
data class TalkRoom(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("partner_user") val partnerUser: User? = null,
    @SerializedName("last_message") val lastMessage: TalkLastMessage? = null,
    @SerializedName("unread") val unread: Boolean = false,
)

/** 会话最后一条消息摘要 */
data class TalkLastMessage(
    @SerializedName("content") val content: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

/** 某会话消息历史响应（v1/talk/room/{room_id}/messages） */
data class TalkMessagesResponse(
    @SerializedName("talk_room_messages") val talkRoomMessages: List<TalkMessage>? = null,
)

/** 单条私信消息 */
data class TalkMessage(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("user") val user: User? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)
