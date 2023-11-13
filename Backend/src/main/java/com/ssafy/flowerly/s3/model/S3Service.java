package com.ssafy.flowerly.s3.model;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.ssafy.flowerly.entity.FileInfo;
import com.ssafy.flowerly.entity.StoreImage;
import com.ssafy.flowerly.entity.StoreInfo;
import com.ssafy.flowerly.entity.type.UploadType;
import com.ssafy.flowerly.exception.CustomException;
import com.ssafy.flowerly.exception.ErrorCode;
import com.ssafy.flowerly.util.CustomMultipartFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import marvin.image.MarvinImage;
import org.marvinproject.image.transform.scale.Scale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.transaction.Transactional;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private final AmazonS3Client amazonS3Client;
    private final S3Repository s3Repository;
    private final StoreImageRepository storeImageRepository;
    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public List<String> upload(MultipartFile[] mFiles, UploadType uploadType) {
        //파일 업로드
        List<FileInfo> uploadImage = Arrays.stream(mFiles)
                .filter(mFile -> mFile.getSize() > 0)
                .map(mFile -> multiPartToFileInfoAndResize(mFile, uploadType))
                .collect(Collectors.toList());
        //이후 이미지 src만 반환한다.
        return uploadImage.stream().map(fileInfo -> {return fileInfo.getUploadFileUrl();})
                .collect(Collectors.toList());
    }

    public String uploadOneImage(MultipartFile uploadImg, UploadType uploadType) {
        FileInfo temp = multiPartToFileInfoAndResize(uploadImg, uploadType);
        s3Repository.save(temp);
        return temp.getUploadFileUrl();
    }

    public List<String> updateStoreImage(int[] imageIDs, MultipartFile[] mFiles){
        //배열이 비어 있으면 대표사진이 없다는 것 -> 모두 추가하고 url을 던져준다.
        if(imageIDs.length == 0){
            return upload(mFiles, UploadType.STORE_THUMBNAIL);
        }else{//배열이 있으면 이미지들을 찾아서 수정한다.
            List<String> uploadUrls = upload(mFiles, UploadType.STORE_THUMBNAIL);
            List<StoreImage> findStoreThumbNail = storeImageRepository.findByStoreInfoIdIn(imageIDs);

            //이미지를 교체하고
            for(int i = 0 ; i < imageIDs.length; ++i){
                findStoreThumbNail.get(i).updateImage(uploadUrls.get(i));
            }
            //벌크 연산처리한다.
            storeImageRepository.saveAll(findStoreThumbNail);

            //수정된 이미지 url만 반환한다.
            return findStoreThumbNail.stream()
                    .map(image -> {return image.getImageUrl();})
                    .collect(Collectors.toList());
        }
    }

    public String uploadBase64Image(String base64, UploadType uploadType) {
        byte[] decodeFile = Base64.getMimeDecoder().decode(base64.substring(base64.indexOf(",") + 1));
        String uploadFileName = uploadType.name() + "_" + UUID.randomUUID();
        String uploadPath = uploadType.name() + "/" + uploadFileName;

        try (InputStream fis = new ByteArrayInputStream(decodeFile)) {
            // S3에 업로드할 객체 생성
            ObjectMetadata objMeta = new ObjectMetadata();
            objMeta.setContentLength(decodeFile.length);
            objMeta.setContentType("image/png");

            amazonS3Client.putObject(new PutObjectRequest(bucket, uploadPath, fis, objMeta));
            String uploadFileUrl = amazonS3Client.getUrl(bucket, uploadPath).toString();

            s3Repository.save(FileInfo.builder()
                    .originalFileName(uploadFileName)
                    .uploadFileName(uploadFileName)
                    .uploadFilePath(uploadType.name())
                    .uploadFileUrl(uploadFileUrl)
                    .build());

            return uploadFileUrl;
        } catch (IOException e) {
            // 예외 처리 로직을 추가하세요.
            e.printStackTrace(); // 또는 다른 예외 처리 방식을 선택하세요.
            return null;
        }
    }

    private FileInfo multiPartToFileInfoAndResize(MultipartFile uploadImg, UploadType uploadType) {
        try {
            String uploadFileName = getUUIDFileName(Objects.requireNonNull(uploadImg.getOriginalFilename()));
            String fileFormat = Objects.requireNonNull(uploadImg.getContentType()).substring(uploadImg.getContentType().lastIndexOf("/") + 1); //파일 확장자명 추출
            String uploadPath = uploadType.name() + "/" + uploadFileName;

            //파일 리사이즈 수행
            MultipartFile resizedImage = resizer(uploadFileName, fileFormat, uploadImg, 1000); //오늘의 핵심 메서드

            //S3에 업로드할 객체 생성
            ObjectMetadata objMeta = new ObjectMetadata();
            objMeta.setContentLength(resizedImage.getSize()); //사이즈를 전달한다.
            objMeta.setContentType(resizedImage.getContentType()); //이미지 타입을 전달한다.
            amazonS3Client.putObject(new PutObjectRequest(bucket, uploadPath, resizedImage.getInputStream(), objMeta));

            String uploadFileUrl = amazonS3Client.getUrl(bucket, uploadPath).toString();
            return FileInfo.builder()
                    .originalFileName(uploadImg.getOriginalFilename())
                    .uploadFileName(uploadFileName)
                    .uploadFilePath(uploadType.name())
                    .uploadFileUrl(uploadFileUrl)
                    .build();

        } catch (IOException e) {
            log.error("업로드 예외 발생");
            return null;
        }
    }


    private String getUUIDFileName(String fileName) {
        String ext = fileName.substring(fileName.indexOf(".") + 1);
        return UUID.randomUUID() + fileName + "." + ext;
    }


    @Transactional
    public MultipartFile resizer(String fileName, String fileFormat, MultipartFile originalImage, int width) {
        try {
            BufferedImage image = ImageIO.read(originalImage.getInputStream());// MultipartFile -> BufferedImage Convert

            int originWidth = image.getWidth();
            int originHeight = image.getHeight();

            // origin 이미지가 1000보다 작으면 패스
            if (originWidth < width)
                return originalImage;

            MarvinImage imageMarvin = new MarvinImage(image);

            Scale scale = new Scale();
            scale.load();
            scale.setAttribute("newWidth", width);
            scale.setAttribute("newHeight", width * originHeight / originWidth);//비율유지를 위해 높이 유지
            scale.process(imageMarvin.clone(), imageMarvin, null, null, false);

            BufferedImage imageNoAlpha = imageMarvin.getBufferedImageNoAlpha();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(imageNoAlpha, fileFormat, baos);
            baos.flush();

            return new CustomMultipartFile(fileName, fileFormat, originalImage.getContentType(), baos.toByteArray());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일을 줄이는데 실패했습니다.");
        }
    }

    public String makeFlowerBouquet(String imageUrl){
        return uploadBase64Image(urlToBase64(imageUrl), UploadType.FlOWER);
    }

    private String urlToBase64(String imageUrl) {
        try{
            if(imageUrl == null || imageUrl.trim().equals("")) throw new CustomException(ErrorCode.INVALID_IMAGE_URL);

            URL url = new URL(imageUrl);
            BufferedImage img = ImageIO.read(url);

            // Write the image to a ByteArrayOutputStream
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", baos);
            baos.flush();

            // Convert the ByteArrayOutputStream to a byte array
            byte[] imageBytes = baos.toByteArray();
            baos.close();

            // Encode the byte array to Base64
            String imageStr = Base64.getEncoder().encodeToString(imageBytes);
            // Determine the file extension
            String fileExtName = imageUrl.substring(imageUrl.lastIndexOf(".") + 1);

            // 밑에 changeString은 img 태그안에 쓰이는 용입니다. 위에만 참고하셔도 괜찮아요!
            return "data:image/"+ fileExtName +";base64, "+ imageStr;
        }catch(IOException e){
            throw new CustomException(ErrorCode.IOException);
        }
    }
}
