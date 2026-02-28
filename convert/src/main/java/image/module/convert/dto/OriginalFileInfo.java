package image.module.convert.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OriginalFileInfo {

    private UUID id;
    private String storedFileName;
    private String cdnBaseUrl;

    public static OriginalFileInfo createCdnUrl(String storedFileName) {
        String cdnUrl = UUID.randomUUID().toString(); //cdn 이름 확정

        return OriginalFileInfo.builder()
                .storedFileName(storedFileName)
                .cdnBaseUrl(cdnUrl)
                .build();
    }
}
