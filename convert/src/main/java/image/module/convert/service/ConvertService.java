package image.module.convert.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.webp.WebpWriter;
import image.module.convert.DataClient;
import image.module.convert.dto.OriginalImageResponse;
import image.module.convert.dto.SendKafkaMessage;
import image.module.convert.dto.OriginalFileInfo;
import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.imgscalr.Scalr;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

@Slf4j
@Service
public class ConvertService {
  @Value("${minio.buckets.downloadBucket}")
  private String originalBucket;

  @Value("${minio.buckets.uploadBucket}")
  private String uploadBucket;

  @Value("${cdn-server.url}")
  private String cdnBaseUrl;

  private final MinioClient minioClient;
  private final DataClient dataClient;

  private final KafkaTemplate<String, SendKafkaMessage> kafkaTemplate;

  public ConvertService(MinioClient minioClient, DataClient dataClient, KafkaTemplate<String, SendKafkaMessage> kafkaTemplate) {
    this.minioClient = minioClient;
    this.dataClient = dataClient;
    this.kafkaTemplate = kafkaTemplate;
  }

  // 전체 이미지 처리 로직을 관리하는 메서드
  @KafkaListener(topics = "image-upload-topic", groupId = "image-upload-group")
  public void removeMetadataAndCovertWebP(OriginalImageResponse originalImage) {
    String storedFileName = originalImage.getStoredFileName();
    Integer size = originalImage.getRequestSize();

    // 1. 확장자 추출
    String extension = extractExtensionFrom(storedFileName);

    // 2. 이미지 다운로드
    File originalFile = downloadImage(storedFileName);

    // 3. MINIO 원본 이미지 삭제
    removeOriginalImage(storedFileName);

    // 4. EXIF 메타 데이터 삭제 및 이미지 회전 처리
    File checkedRotate = removeMetadataAndFixOrientation(originalFile, extension);

    // 5. 메타데이터 삭제 이미지 업로드
    uploadRemoveExifImage(checkedRotate, storedFileName, extension);

    // 6. 원본 이미지 cdnUrl 추가
    OriginalFileInfo originalFileInfo = OriginalFileInfo.createCdnUrl(storedFileName, cdnBaseUrl);
    dataClient.createCdnUrl(originalFileInfo);

    // 7. WebP로 변환
    File webpFile = convertToWebp(storedFileName, originalFile);

    // 8. WebP 이미지 업로드
    uploadWebPImage(webpFile);

    kafkaTemplate.send("image-resize-topic", SendKafkaMessage.createMessage(webpFile.getName(), size));

    // 9. 임시 파일 삭제
    cleanupTemporaryFiles(originalFile, checkedRotate, webpFile);
  }


  public String extractExtensionFrom(String fileName) {
    try {
      StatObjectResponse statObject = minioClient.statObject(
              StatObjectArgs.builder()
                      .bucket(originalBucket)
                      .object(fileName)
                      .build()
      );

      // Content-Type 출력
      String contentType = statObject.contentType(); // ex) image/jpeg
      // Content-Type에서 확장자 가져오기 / ex) jpeg
      return extractExtensionFromContentType(contentType);

    } catch (Exception e) {
      throw new RuntimeException("확장자 추출 실패: " + e.getMessage(), e);
    }
  }

  // 확장자 추출
  public String extractExtensionFromContentType(String contentType) {
    if ("image/jpeg".equals(contentType) || "image/jpg".equals(contentType)) {
      return "jpg";
    } else if ("image/png".equals(contentType)) {
      return "png";
    } else {
      throw new IllegalArgumentException("지원되지 않는 파일 형식입니다: " + contentType);
    }
  }


  public File downloadImage(String fileName) {
    File file = null;
    try (InputStream inputStream = minioClient.getObject(
            GetObjectArgs.builder()
                    .bucket(originalBucket)
                    .object(fileName)
                    .build())) {

      // 임시 파일 생성
      file = File.createTempFile("image_", "_" + fileName);

      // InputStream을 File로 저장
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
  public void removeOriginalImage(String fileName) {
    try {
      minioClient.removeObject(
              RemoveObjectArgs.builder()
                      .bucket(originalBucket)
                      .object(fileName)
                      .build()
      );
    } catch (Exception e) {
      throw new RuntimeException("이미지 삭제 실패: " + e.getMessage());
    }
  }

  public File removeMetadataAndFixOrientation(File originalFile, String extension) {
    try {
      BufferedImage bufferedImage = ImageIO.read(originalFile);

      int orientation = 1;
      Metadata metadata;
      Directory directory;

      try {
        metadata = ImageMetadataReader.readMetadata(originalFile);
        directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        if (directory != null) {
          orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
        }
      } catch (Exception e) {
        orientation = 1; // 메타데이터 읽기 실패 시 기본 방향(1) 설정
      }

      switch (orientation) {
        case 3:
          bufferedImage = Scalr.rotate(bufferedImage, Scalr.Rotation.CW_180);
          break;
        case 6:
          bufferedImage = Scalr.rotate(bufferedImage, Scalr.Rotation.CW_90);
          break;
        case 8:
          bufferedImage = Scalr.rotate(bufferedImage, Scalr.Rotation.CW_270);
          break;
        default:
          break; // orientation 1일 때는 아무 작업도 하지 않음
      }

      // 임시 파일 생성
      File imageRotateFile = File.createTempFile("rotate-", "." + extension);
      // 새로운 파일로 저장
      ImageIO.write(bufferedImage, extension, imageRotateFile);

      return imageRotateFile;
    } catch (Exception e) {
      throw new IllegalArgumentException("메타 데이터 삭제 및 이미지 회전 오류 발생: " + e.getMessage(), e);
    }
  }

  @Async
  public void uploadRemoveExifImage(File checkedRotate, String fileName, String extension) {
    try {
      minioClient.putObject(
              PutObjectArgs.builder()
                      .bucket(originalBucket)
                      .object(fileName)
                      .stream(new FileInputStream(checkedRotate), checkedRotate.length(), -1)
                      .contentType("image/" + extension)
                      .build()
      );
    } catch (Exception e) {
      throw new RuntimeException("이미지 업로드 실패: " + e.getMessage());
    }
  }

  public File convertToWebp(String fileName, File originalFile) {
    try {
      String uploadFileName = FilenameUtils.getBaseName(fileName) + ".webp"; // MINIO에 업로드할 최종 파일 이름
      File outputFile = new File(originalFile.getParent(), uploadFileName);

      return ImmutableImage.loader()
              .fromFile(originalFile)
              .output(WebpWriter.DEFAULT, outputFile); // 손실 압축
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  @Async
  public void uploadWebPImage(File webpFile) {
    try (InputStream webpInputStream = new FileInputStream(webpFile)) {
      minioClient.putObject(PutObjectArgs.builder()
              .bucket(uploadBucket)
              .object(webpFile.getName())
              .stream(webpInputStream, webpFile.length(), -1)
              .contentType("image/webp")
              .build());
    } catch (Exception e) {
      throw new IllegalArgumentException("WebP 파일 업로드 실패: " + e.getMessage());
    }
  }

  public void cleanupTemporaryFiles(File... files) {
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