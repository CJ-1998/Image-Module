package image.module.cdn.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageResponseDto {
    private Resource imageResource;
    private HttpHeaders headers;

    public static ImageResponseDto from(Resource imageResource, HttpHeaders headers) {
        return ImageResponseDto.builder()
                .imageResource(imageResource)
                .headers(headers)
                .build();
    }
}
