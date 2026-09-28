package com.bireport.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录态存储：优先 Redis（支持踢人下线/续期），Redis 不可用时自动降级为内存存储，
 * 保证单机场景下应用始终可用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenStoreService {

    private static final String KEY_PREFIX = "bi:token:";

    private final StringRedisTemplate redisTemplate;

    private record Entry(long userId, long expireAt) {
    }

    private final Map<String, Entry> memoryStore = new ConcurrentHashMap<>();
    private volatile boolean redisAvailable = true;

    private String key(String jti) {
        return KEY_PREFIX + jti;
    }

    public void save(String jti, long userId, Duration ttl) {
        if (redisAvailable) {
            try {
                redisTemplate.opsForValue().set(key(jti), String.valueOf(userId), ttl);
                return;
            } catch (Exception e) {
                markDown(e);
            }
        }
        memoryStore.put(jti, new Entry(userId, System.currentTimeMillis() + ttl.toMillis()));
    }

    public boolean exists(String jti) {
        if (redisAvailable) {
            try {
                return Boolean.TRUE.equals(redisTemplate.hasKey(key(jti)));
            } catch (Exception e) {
                markDown(e);
            }
        }
        Entry entry = memoryStore.get(jti);
        if (entry == null) {
            return false;
        }
        if (entry.expireAt() < System.currentTimeMillis()) {
            memoryStore.remove(jti);
            return false;
        }
        return true;
    }

    public void remove(String jti) {
        if (redisAvailable) {
            try {
                redisTemplate.delete(key(jti));
            } catch (Exception e) {
                markDown(e);
            }
        }
        memoryStore.remove(jti);
    }

    /** 踢人下线：删除该用户全部登录态（内存模式遍历，Redis 模式 scan） */
    public void removeAllOfUser(long userId) {
        if (redisAvailable) {
            try {
                var keys = redisTemplate.keys(KEY_PREFIX + "*");
                if (keys != null) {
                    for (String k : keys) {
                        String v = redisTemplate.opsForValue().get(k);
                        if (v != null && v.equals(String.valueOf(userId))) {
                            redisTemplate.delete(k);
                        }
                    }
                }
            } catch (Exception e) {
                markDown(e);
            }
        }
        memoryStore.entrySet().removeIf(e -> e.getValue().userId() == userId);
    }

    private void markDown(Exception e) {
        redisAvailable = false;
        log.warn("Redis 不可用，登录态降级为内存存储: {}", e.getMessage());
    }
}
