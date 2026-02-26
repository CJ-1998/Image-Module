package image.module.cdn.config;

import image.module.cdn.service.RedisService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisExpirationListener implements MessageListener {

    @Value("${cdn.image.path}")
    public String filePath;

    private final RedisService redisService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();
        log.info("Expired Key: " + expiredKey);

        // 여기서 TTL 만료 시 실행할 메서드를 호출
        try {
            handleExpiredKey(expiredKey);
        } catch (IOException e) {
            log.error("이미지 삭제 시 오류 발생!! ");
            log.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void handleExpiredKey(String key) throws IOException {
        if (key.contains("lock:")) {
            return;
        }

        // TTL 만료 시 실행할 비즈니스 로직
        String value = redisService.getValue("backup_" + key);

        // value에서 경로 받아 이미지 삭제
        deleteImage(value);

        redisService.deleteKey("backup_" + key);
        redisService.deleteKey(key + ":ttl");
        redisService.deleteKey(key + ":hitRate");
        redisService.deleteKey(key + ":originalFileName");
    }

    private void deleteImage(String imageName) throws IOException {
        log.info(imageName + " 이미지 삭제 시도");
        Path imagePath = Paths.get(imageName);
        Files.deleteIfExists(imagePath);
        log.info("이미지 삭제 성공");
    }
}
