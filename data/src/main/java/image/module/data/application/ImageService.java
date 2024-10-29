package image.module.data.application;

import image.module.data.domain.Image;
import image.module.data.domain.repository.ImageRepository;
import image.module.data.presentation.ImageRequest;
import image.module.data.presentation.OriginalFileInfo;
import image.module.data.presentation.ResizeRequestDto;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class ImageService {
  private final ImageRepository imageRepository;

  @Transactional
  public ImageResponse saveImage(ImageRequest request) {
    Image image = Image.create(request);
    imageRepository.save(image);
    image.assignOriginalFileUUID();
    return ImageResponse.fromEntity(image);
  }

  public ImageResponse getImageName(UUID id) {
    return ImageResponse.fromEntity(imageRepository.findById(id).orElse(null));
  }

  public ImageResponse getCDNImageName(String cdnUrl) {

    Image image = imageRepository.findByCdnUrl(cdnUrl);

    if (image != null) {
      return ImageResponse.fromEntity(image);
    } else {
      //이미지가 없을 경우 처리
      throw new EntityNotFoundException("Image not found" + cdnUrl);
    }

  }

  // 원본 이미지 cdnUrl 추가
  @Transactional
  public void createCdnUrl(OriginalFileInfo originalFileInfo) {
    try {
      Image image = imageRepository.findByStoredFileName(originalFileInfo.getStoredFileName()).orElseThrow(
              () -> new EntityNotFoundException("저장된 파일 이름을 찾을 수 없습니다")
      );
      image.createCdnUrl(originalFileInfo.getCdnBaseUrl());
      log.info("원본 이미지 cdnUrl 주입 성공");
    } catch (Exception e) {
      throw new IllegalArgumentException("원본 이미지 cdnUrl 주입 실패: " + e.getMessage());
    }
  }


  // 리사이즈 WebP 이미지 DB 저장
  public void createResizeImage(ResizeRequestDto resizeRequestDto) {
    try {
      Image image = imageRepository.findByStoredFileName(resizeRequestDto.getStoredFileName()).orElseThrow(
              () -> new IllegalArgumentException("저장된 파일 이름을 찾을 수 없습니다"));
      Image Resizeimage = Image.createResizeImage(image, resizeRequestDto);
      imageRepository.save(Resizeimage);
      log.info("리사이즈 이미지 생성 성공");
    } catch (Exception e) {
      throw new IllegalArgumentException("리사이즈 이미지 생성 실패" + e.getMessage());
    }
  }

  // 이미지 resizing cdn url 반환
  public ImageResponse getReCdnUrl(UUID originalFileUUID, Integer size) {
    Image image = imageRepository.findByOriginalFileUuidAndSize(originalFileUUID, size)
            .orElseThrow(() -> new RuntimeException("이미지를 찾을 수 없습니다."));

    return ImageResponse.fromEntity(image);
  }
}
