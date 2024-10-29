package image.module.resize.service;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.webp.WebpWriter;
import image.module.resize.DataClient;
import image.module.resize.dto.ResizeRequestDto;
import image.module.resize.dto.ReceiveKafkaMessage;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;

@Slf4j
@Service
public class ResizeService {

  @Value("${minio.buckets.uploadBucket}")
  private String uploadBucket;

  @Value("${cdn-server.url}")
  private String cdnBaseUrl;

  private final MinioClient minioClient;

  private final DataClient dataClient;

  public ResizeService(MinioClient minioClient, DataClient dataClient) {
    this.minioClient = minioClient;
    this.dataClient = dataClient;
  }

  @KafkaListener(topics = "image-resize-topic", groupId = "image-resize-group")
  public void ResizeImage(ReceiveKafkaMessage receiveKafkaMessage) {
    String webPFileName = receiveKafkaMessage.getWebPFileName();
    Integer size = receiveKafkaMessage.getSize();

    // 1. 이름에서 확장자 제거
    String uploadName = deleteExtension(webPFileName);

    // 2. MINIO에서 기존에 있던 webpFile 다운로드
    File originalFile = downloadImage(webPFileName);

    // 3. MINIO에서 기존에 있던 webpFile 삭제
    removeOriginalWebPImage(webPFileName);

    // 4. 가로 세로 비교 / 둘 중 큰 변을 기준으로 리사이즈
    File resizeFile = resizeImage(uploadName, originalFile, size);

    // 5. 리사이즈 된 이미지 업로드
    uploadWebPImage(resizeFile);

    // 6. 리사이징 된 WebP 이미지 DB 생성
    ResizeRequestDto resizeRequestDto = ResizeRequestDto.createResizeImage(uploadName, size, cdnBaseUrl);
    dataClient.createResizeImage(resizeRequestDto);

    // 7. 임시 파일 삭제
    cleanupTemporaryFiles(originalFile, resizeFile);
  }


  public String deleteExtension(String fileName) {
    // 파일 이름에서 마지막 점(.)의 위치 찾기
    int dotIndex = fileName.lastIndexOf(".");

    if (dotIndex != -1) {
      // 마지막 점(.) 앞부분까지만 잘라내기
      return fileName.substring(0, dotIndex);
    } else {
      throw new IllegalArgumentException("파일 이름에 확장자가 포함되어 있지 않습니다.");
    }
  }

  public File downloadImage(String webPFileName) {
    File file = null;
    try (InputStream inputStream = minioClient.getObject(
            GetObjectArgs.builder()
                    .bucket(uploadBucket)
                    .object(webPFileName)
                    .build())) {

      // 임시 파일 생성
      file = File.createTempFile("image_", "_" + webPFileName);

      // InputStream을 파일로 저장
      try (FileOutputStream outputStream = new FileOutputStream(file)) {
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
          outputStream.write(buffer, 0, bytesRead);
        }
      }

    } catch (Exception e) {
      throw new IllegalArgumentException("이미지 다운로드 실패: " + e.getMessage());
    }
    return file;
  }

  @Async
  public void removeOriginalWebPImage(String webPFileName) {
    try {
      minioClient.removeObject(
              RemoveObjectArgs.builder()
                      .bucket(uploadBucket)
                      .object(webPFileName)
                      .build()
      );
    } catch (Exception e) {
      throw new RuntimeException("이미지 삭제 실패: " + e.getMessage());
    }
  }

  public File resizeImage(String uploadName, File originalFile, Integer resizingSize) {
    // 리사이즈된 이미지를 저장할 파일
    File resizedFile = new File(originalFile.getParent(), uploadName + "_" + resizingSize); // MINIO에 업로드 될 최종 이름

    try {
      // 원본 이미지 로드 (Scrimage 라이브러리)
      ImmutableImage image = ImmutableImage.loader().fromFile(originalFile);

      int width = image.width;
      int height = image.height;

      // 가로 세로 비율에 맞춰 리사이즈
      ImmutableImage resizedImage;
      if (width >= height) {
        // 가로 비율에 맞춰 리사이즈
        resizedImage = image.scaleToWidth(resizingSize); // 가로 기준 리사이즈
      } else {
        // 세로 비율에 맞춰 리사이즈
        resizedImage = image.scaleToHeight(resizingSize); // 세로 기준 리사이즈
      }

      return resizedImage.output(WebpWriter.DEFAULT, resizedFile);

    } catch (IOException e) {
      log.error("이미지 리사이징 실패: " + e.getMessage(), e);
      throw new IllegalArgumentException("이미지 리사이징 실패: " + e.getMessage());
    }
  }

  @Async
  public void uploadWebPImage(File resizingFile) {
    try (InputStream webpInputStream = new FileInputStream(resizingFile)) {
      minioClient.putObject(PutObjectArgs.builder()
              .bucket(uploadBucket)
              .object(resizingFile.getName())
              .stream(webpInputStream, resizingFile.length(), -1)
              .contentType("image/webp")
              .build());
    } catch (Exception e) {
      throw new IllegalArgumentException("WebP 파일 업로드 실패: " + e.getMessage());
    }
  }

  private void cleanupTemporaryFiles(File... files) {
    for (File file : files) {
      if (file.exists()) {
        boolean deleted = file.delete();
        if (!deleted) {
          throw new IllegalArgumentException("임시 파일 삭제 실패: " + file.getAbsolutePath());
        }
      }
    }
  }

}
