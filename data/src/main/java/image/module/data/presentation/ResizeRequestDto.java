package image.module.data.presentation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResizeRequestDto
{
    private UUID id;
    private String storedFileName;
    private Integer size;
    private String cdnBaseUrl;
    private String type;
}
