package image.module.data.presentation;

import image.module.data.application.ImageResponse;
import image.module.data.application.ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping
public class ImageController {

  private final ImageService imageService;

  @PostMapping("/image/upload")
  public ImageResponse uploadImage(@RequestBody ImageRequest imageRequest) {
    return imageService.saveImage(imageRequest);
  }

  //fetch -> 객체 조회
  @GetMapping("/image/getImageName")
  public ImageResponse getImageName(@RequestParam("id") UUID id) {

    ImageResponse getImageName = imageService.getImageName(id);

    return getImageName;
  }

  //fetch -> cdn 주소로 객체 조회
  @GetMapping("/image/getCDNImageName")
  public ImageResponse getCDNImageName(@RequestParam("cdnUrl") String cdnUrl) {

    ImageResponse getCDNImageName = imageService.getCDNImageName(cdnUrl);

    return getCDNImageName;
  }

  // 원본 이미지 cdnUrl 추가
  @PostMapping("/image/create/cdnUrl")
  public void createCdnUrl(
          @RequestBody OriginalFileInfo originalFileInfo
  ) {
    imageService.createCdnUrl(originalFileInfo);
  }

  // 리사이즈 WebP 이미지 DB 저장
  @PostMapping("/image/create/resizeImage")
  public void createResizeImage(
          @RequestBody ResizeRequestDto resizeRequestDto
  ) {
    imageService.createResizeImage(resizeRequestDto);
  }

  //원본 uuid 와 size의 cdn url 반환
  @GetMapping("/image/getReCdnUrl")
  public ImageResponse getReCdnUrl(@RequestParam("originalFileUUID") UUID originalFileUUID,
                                   @RequestParam("size") Integer size) {
    return imageService.getReCdnUrl(originalFileUUID, size);
  }
}
