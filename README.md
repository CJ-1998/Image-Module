## 📖서비스/프로젝트 소개

이커머스 회사 내부에서 이미지 관련 처리를 돕는 이미지 처리 모듈 프로젝트

빠른 이미지 업로드와 조회는 물론, 다양한 이미지 크기에 대한 유연한 처리를 통해 보다 매끄럽고 직관적인 사용자 경험 제공
<br>
<br>
## 🏗️인프라 설계도
![image](https://github.com/user-attachments/assets/d3bcd442-09ed-42e2-8f85-665ab47d303b)
<br>
<br>
## 🔑주요 기능

<aside>


**1️⃣Image Upload Server**

- 📤 이미지 업로드
- 🗄️ 이미지 정보 DB 저장 (원본 이름, 확장자 등)
- ✅ 확장자 체크
- ⏳ 원하는 이미지 사이즈와 캐싱 시간 요청
</aside>

<aside>


**2️⃣Image Convert Server**

- 📤 이미지 업로드, 다운로드, 삭제
- ✂️ 원본 이미지 메타데이터 제거
- 🌐 이미지 WebP 파일 변환
</aside>

<aside>


**3️⃣Image Resizing Server**

- 📏 사용자가 원하는 크기로 이미지 리사이징
- 🗄️ 리사이징된 이미지 정보 DB 저장
- 🌐 WebP 파일 업로드
</aside>

<aside>


**4️⃣Image Fetch Server**

- 🔗 Backend Client에게 CDN URL 반환
- 📦 CDN Server에 필요 이미지 전달
- 🔄 WebP 타입에서 원래 타입으로 변환
</aside>

<aside>


**5️⃣CDN Server**

- 🌐 브라우저의 이미지 조회 요청 처리
- 📥 브라우저의 이미지 다운로드 요청 처리
- 🗃️ 이미지 캐싱 처리
- ⚖️ CDN Server 용량 체크
</aside>
<br>
<br>

## ⚙️적용 기술
![image](https://github.com/user-attachments/assets/7ecff12a-3bdf-46ff-ba60-4020224089a1)
<br>
<br>

## 🤝CONTRIBUTORS
| 팀원명   | 포지션       | 담당(개인별 기대점)                                                                                                                                      | GitHub 링크 |
| -------- | ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------- |
| **안지연** | UploadServer | - 이미지 minio 업로드<br>- 원본 데이터 저장<br>- 서버 배포<br>- AWS EC2, RDS<br>- Dockerfile 생성<br>- docker-compose.yml 생성                                      | (https://github.com/jiyeonahn) |
| **서병준** | convertServer<br>resizeServer | - 이미지 원본 다운로드<br>- 원본 이미지 메타데이터 저장<br>- 원본 CDN 주소 저장<br>- WebP로의 변환<br>- 사용자가 원하는 크기로 리사이징<br>- 리사이징된 이미지 저장 및 DB 저장<br>- WebP 이미지 업로드 | (https://github.com/mad-cost) |
| **최준**   | CdnServer    | - CDN 서버 구축<br>- 이미지 캐싱<br>- 이미지 조회 및 다운로드<br>- CDN Server 최적화<br>- Backend Client 라이브러리 작성                                      | (https://github.com/CJ-1998) |
| **노민경** | fetchServer  | - 해둔 이미지의 CDN URL 반환<br>- CDN URL 값 전달 및 조회, 메시지 반환<br>- 이미지 메타데이터 반환<br>- File Name, Type, Caching Time, 이미지 바이트 반환            | (https://github.com/minjjings) |


