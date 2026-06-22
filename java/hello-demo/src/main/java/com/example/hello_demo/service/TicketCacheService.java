package com.example.hello_demo.service;

import com.example.hello_demo.constant.RedisKeyConstants;
import com.example.hello_demo.vo.TicketDetailVO;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 工单相关缓存服务。
 * 统一管理工单详情、回复列表等 key 的读写和清理。
 */
@Service
public class TicketCacheService {

    private static final Duration TICKET_DETAIL_TTL = Duration.ofMinutes(10);

    private final RedisCacheService redisCacheService;

    public TicketCacheService(RedisCacheService redisCacheService) {
        this.redisCacheService = redisCacheService;
    }

    public TicketDetailVO getTicketDetail(Long ticketId) {
        Object cached = redisCacheService.get(RedisKeyConstants.ticketDetailKey(ticketId));
        if (cached instanceof TicketDetailVO ticketDetailVO) {
            return ticketDetailVO;
        }
        return null;
    }

    public void setTicketDetail(Long ticketId, TicketDetailVO ticketDetailVO) {
        redisCacheService.set(
                RedisKeyConstants.ticketDetailKey(ticketId),
                ticketDetailVO,
                TICKET_DETAIL_TTL
        );
    }

    public void evictTicketRelated(Long ticketId) {
        redisCacheService.delete(RedisKeyConstants.ticketDetailKey(ticketId));
        redisCacheService.delete(RedisKeyConstants.ticketReplyListKey(ticketId));
    }
}
