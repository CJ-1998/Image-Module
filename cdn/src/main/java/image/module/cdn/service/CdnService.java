package image.module.cdn.service;

import image.module.cdn.client.UrlServiceClient;
import image.module.cdn.dto.ImageResponseDto;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CdnService {

    private final RedisService redisService;
    private final UrlServiceClient urlServiceClient;

    // @Value는 static 변수에 주입되지 않는다고 함
    @Value("${server.port}")
    private String port;

    @Value("${cdn.image.path}")
    public String filePath;

    @Value("${cdn.image.url}")
    public String fileUrl;

    public String getPartCdnUrl() {
        return "http://" + fileUrl + ":" + port + "/cdn/";
    }

    public ImageResponseDto getImage(String cdnUrl) throws IOException, InterruptedException {
        String fileLocation = checkFileExist(cdnUrl);
        return getImageInfo(fileLocation);
    }

    public ImageResponseDto downloadImage(String cdnUrl) throws IOException, InterruptedException {
        String convertedCdnUrl = cdnUrl.replace("/download", "");
        String fileLocation = checkFileExist(convertedCdnUrl);

        ImageResponseDto imageResponseDto = getImageInfo(fileLocation);

        String imageOriginalName = getOriginalNameByPath(fileLocation);

        imageResponseDto.getHeaders().setContentDispositionFormData("attachment", imageOriginalName);

        return imageResponseDto;
    }

    public ImageResponseDto getImageInfo(String fileLocation) throws IOException {
        ImageResponseDto imageResponseDto = new ImageResponseDto();

        // byte[] imageBytes = getByteImage(fileLocation);
        // 1. byte[] 대신 FileSystemResource 할당 (파일의 경로 정보만 갖는 객체 생성)
        Resource resource = new FileSystemResource(fileLocation);

        // 2. 방어적 프로그래밍: 파일이 실제 존재하는지, 읽을 수 있는지 검사
        if (!resource.exists() || !resource.isReadable()) {
            log.error("파일을 읽을 수 없습니다. 경로: {}", fileLocation);
            throw new FileNotFoundException("이미지 파일을 읽을 수 없습니다: " + fileLocation);
        }

        // 파일의 MIME 타입을 동적으로 추출
        String imageType = getImageType(fileLocation);

        // 응답 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(imageType));

        // 스트리밍 시 Content-Length를 명시해주면 클라이언트(브라우저)에서 다운로드 진행률을 알 수 있다.
        headers.setContentLength(resource.contentLength());

        imageResponseDto.setImageBytes(resource);
        imageResponseDto.setHeaders(headers);

        return imageResponseDto;
    }

    private byte[] getByteImage(String fileLocation) throws IOException {
        Path imagePath = Paths.get(fileLocation);
        return Files.readAllBytes(imagePath);
    }

    private String getImageType(String fileLocation) throws IOException {
        Path imagePath = Paths.get(fileLocation);
        return Files.probeContentType(imagePath);
    }

    public String checkFileExist(String cdnUrl) throws IOException, InterruptedException {
        String fileLocation = redisService.getValue(cdnUrl);
        if (fileLocation == null) {

            // 2. Redis에서 락 시도 (5초 동안 락 유지)
            Boolean isLocked = redisService.setRedisLock(cdnUrl);

            if (isLocked) {
                fileLocation = getImageAndSave(cdnUrl);
            } else {
                while (fileLocation == null) {
                    Thread.sleep(100);  // 100ms 동안 대기 후 다시 Redis에서 조회
                    fileLocation = redisService.getValue(cdnUrl);
                }
            }
        }
        return fileLocation;
    }

    // @Cacheable(cacheNames = "fileLocationCache", key = "args[0]")
    // 같은 클래스에서 한 메서드가 다른 메서드 호출할 때 캐시 적용 안된다고 함
    // Redis Lock 구현 이전 코드
//    public String checkFileExist(String cdnUrl) throws IOException {
//        String fileLocation = redisService.getValue(cdnUrl);
//        if (fileLocation == null) {
//            fileLocation = getImageAndSave(cdnUrl);
//        }
//        return fileLocation;
//    }

    // 이미지 저장
    private String saveImageInCdn(byte[] imageBytes, String fileName) throws IOException {
        log.info("이미지 저장 시도");
        // 저장 경로 생성
        Path uploadPath = Paths.get(filePath);

        // 폴더가 존재하지 않으면 생성
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 이미지 파일 경로
        Path filePath = uploadPath.resolve(fileName);

        // 이미지 저장
        Files.write(filePath, imageBytes);

        log.info("이미지 저장 완료");

        checkVolume();

        return filePath.toString();
    }

    // 폴더 크기 계산
    private long calculateFolderSize(Path folder) throws IOException {
        final long[] size = {0};

        Files.walkFileTree(folder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                size[0] += attrs.size();
                return FileVisitResult.CONTINUE;
            }
        });

        return size[0];
    }

    @Async
    public void checkVolume() {
        log.info("이미지 저장 폴더 용량 체크");
        try {
            Path folderPath = Paths.get(filePath);

            // 폴더 크기 계산
            long folderSize = calculateFolderSize(folderPath);

            // 해당 폴더가 위치한 디스크의 경로를 파일 객체로 변환
            File disk = new File(folderPath.toString());

            // 디스크의 총 용량과 사용 가능한 용량을 얻음
            long totalDiskSpace = disk.getTotalSpace();     // 전체 디스크 용량 (bytes)
            long freeDiskSpace = disk.getFreeSpace();       // 사용 가능한 공간 (bytes)
            long usedDiskSpace = totalDiskSpace - freeDiskSpace;  // 사용 중인 공간 (bytes)

            // 폴더가 디스크에서 차지하는 비율 계산
            double folderUsagePercentage = (double) folderSize / totalDiskSpace * 100;

            // 결과 출력
            log.info("Total disk space: " + totalDiskSpace / (1024.0 * 1024 * 1024) + " GB");
            log.info("Used disk space: " + usedDiskSpace / (1024.0 * 1024 * 1024) + " GB");
            log.info("Folder size: " + folderSize / (1024.0 * 1024) + " MB");
            log.info(String.format("Folder usage: %.2f%%", folderUsagePercentage));
        } catch (IOException e) {
            log.error("폴더 용량 계산 중 요류 발생! ");
            e.printStackTrace();
        }

    }

    private String getImageAndSave(String cdnUrl) throws IOException {

        // fetch server에 정보 요청
        ResponseEntity<byte[]> imageResponse = urlServiceClient.fetchImageByte(
                URLEncoder.encode(cdnUrl, StandardCharsets.UTF_8));

        // Header 추출
        HttpHeaders headers = imageResponse.getHeaders();

        // Body 추출
        byte[] imageByte = imageResponse.getBody();

        // Header에서 필요한 값들 추출
        String headerFileName = URLDecoder.decode(headers.getFirst("fileName"), StandardCharsets.UTF_8);
        String headerCachingTime = headers.getFirst("cache-time");

        // 필요한 값들 가공
        String fileName = headerFileName.substring(0, headerFileName.lastIndexOf("."));
        Integer cachingTime = Integer.parseInt(headerCachingTime);

        // 확장자 추출
        String fileExtension = headerFileName.substring(headerFileName.lastIndexOf(".") + 1);

        // cdn에 저장할 이미지 이름 생성
        // String cdnImageName = cdnUrl.replace(getPartCdnUrl(), "");
        // URL의 마지막 '/' 이후의 문자열(고유 ID)만 안전하게 추출합니다.
        String cdnImageName = cdnUrl.substring(cdnUrl.lastIndexOf("/") + 1);

        // 만약 cdnImageName에 쿼리스트링(?xxx=yyy)이 붙어올 가능성이 있다면 더 안전하게 처리할 수 있습니다.
        if (cdnImageName.contains("?")) {
            cdnImageName = cdnImageName.substring(0, cdnImageName.indexOf("?"));
        }

        String saveFileName = fileName + "_" + cdnImageName + "." + fileExtension;
        log.info("저장될 파일명: " + saveFileName);

        // 이미지 저장
        String fileLocation = saveImageInCdn(imageByte, saveFileName);

        // redis에 값 저장
        redisService.setValue(cdnUrl, fileLocation, cachingTime);
        redisService.setBackupValue(cdnUrl, fileLocation);

        return fileLocation;
    }

    // 저장된 이미지 이름에서 원본 이미지 뽑는 메서드
    private String getOriginalNameByPath(String fileLocation) {
        // FILE_PATH/originalName_cdnImageName - FILE_PATH/
        String removeFilePath = fileLocation.replace(filePath, "");

        int startIndex = removeFilePath.indexOf('_');
        int endIndex = removeFilePath.indexOf('.');

        if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
            throw new IllegalArgumentException("잘못된 이미지 이름 형식 입니다: " + removeFilePath);
        }

        // originalName_cdnImageName.확장자 - _cdnImageName
        String originalImageName = removeFilePath.substring(0, startIndex) + removeFilePath.substring(endIndex);
        return URLEncoder.encode(originalImageName, StandardCharsets.UTF_8);
    }
}
