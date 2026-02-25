package image.module.cdn.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;

@Getter
@Setter
public class ImageResponseDto {
    private Resource imageBytes;
    private HttpHeaders headers;
}
