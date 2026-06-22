package com.example.hello_demo.constant;

/**
 * Redis 缓存 key 常量。
 */
public class RedisKeyConstants {

    private RedisKeyConstants() {
    }

    public static final String USER_DETAIL_PREFIX = "user:detail:";
    public static final String TICKET_DETAIL_PREFIX = "ticket:detail:";
    public static final String TICKET_REPLY_LIST_PREFIX = "ticket:reply:list:";

    public static String userDetailKey(Long userId) {
        return USER_DETAIL_PREFIX + userId;
    }

    public static String ticketDetailKey(Long ticketId) {
        return TICKET_DETAIL_PREFIX + ticketId;
    }

    public static String ticketReplyListKey(Long ticketId) {
        return TICKET_REPLY_LIST_PREFIX + ticketId;
    }
}
