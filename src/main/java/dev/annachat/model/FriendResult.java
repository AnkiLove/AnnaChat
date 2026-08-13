package dev.annachat.model;

/**
 * 好友关系操作结果，命令层据此选择对应的本地化消息。
 */
public enum FriendResult {
    REQUESTED,
    ACCEPTED,
    ALREADY_FRIENDS,
    ALREADY_REQUESTED,
    SELF,
    NO_REQUEST,
    REMOVED,
    NOT_FRIEND
}
