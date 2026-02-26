package image.module.cdn.service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisService {

    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    public RedisService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 값을 Redis에 저장하는 메서드
    public void setValue(String key, String value, Integer cachingTime, String originalFileName) {
        redisTemplate.opsForValue().set(key, value, cachingTime, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(key + ":ttl", String.valueOf(cachingTime));
        redisTemplate.opsForValue().set(key + ":hitRate", String.valueOf(0));
        redisTemplate.opsForValue().set(key + ":originalFileName", originalFileName);
    }

    // 원본 파일명을 redis에서 가져오는 메서드
    public String getOriginalFileName(String key) {
        return redisTemplate.opsForValue().get(key + ":originalFileName");
    }

    // 값을 Redis에서 가져오는 메서드
    public String getValue(String key) {
        // 이미지 저장 경로 가져옴
        String value = redisTemplate.opsForValue().get(key);
        if (value != null) {

            // ttl 값 가져옴
            String initialTTL = redisTemplate.opsForValue().get(key + ":ttl");
            if (initialTTL != null) {

                // ttl 연장
                redisTemplate.expire(key, Integer.parseInt(initialTTL), TimeUnit.MINUTES);

                // hitRate 값 가져옴
                String hitRate = redisTemplate.opsForValue().get(key + ":hitRate");
                if (hitRate != null) {

                    // hitRate 1 올림
                    Integer plusHitRate = Integer.parseInt(hitRate) + 1;
                    redisTemplate.opsForValue().set(key + ":hitRate", String.valueOf(plusHitRate));
                }
            }
        }
        return value;
    }

    // 값의 백업을 Redis에 저장하는 메서드
    // 왜냐하면 TTL하면 value 값을 받아올 수 없음
    public void setBackupValue(String key, String value) {
        redisTemplate.opsForValue().set("backup_" + key, value);
    }

    // 값을 Redis에서 삭제하는 메서드
    public void deleteKey(String key) {
        redisTemplate.delete(key);
    }

    // Lock 설정하는 메서드
    public Boolean setRedisLock(String key) {
        return redisTemplate.opsForValue()
                .setIfAbsent("lock:" + key, "lock_value", Duration.ofSeconds(5));
    }
}