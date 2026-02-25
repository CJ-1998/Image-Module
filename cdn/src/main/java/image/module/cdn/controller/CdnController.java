package image.module.cdn.controller;

import image.module.cdn.dto.ImageResponseDto;
import image.module.cdn.service.CdnService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/cdn")
public class CdnController {

    private final CdnService cdnService;

    @GetMapping("/{cdnImageName}")
    public ResponseEntity<Resource> getImage(HttpServletRequest request) {
        try {
            ImageResponseDto imageResponseDto = cdnService.getImage(request.getRequestURL().toString());
            return ResponseEntity.ok()
                    .headers(imageResponseDto.getHeaders())
                    .body(imageResponseDto.getImageBytes());
        } catch (IOException | InterruptedException e) {
            log.error("이미지 조회에서 IOException 발생");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/download/{cdnImageName}")
    public ResponseEntity<Resource> downloadImage(HttpServletRequest request) {
        try {
            ImageResponseDto imageResponseDto = cdnService.downloadImage(request.getRequestURL().toString());
            return ResponseEntity.ok()
                    .headers(imageResponseDto.getHeaders())
                    .body(imageResponseDto.getImageBytes());
        } catch (IOException | InterruptedException e) {
            log.error("이미지 다운로드에서 IOException 발생");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
