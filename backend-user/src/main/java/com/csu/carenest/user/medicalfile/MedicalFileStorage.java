package com.csu.carenest.user.medicalfile;

import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

public abstract class MedicalFileStorage {
    public abstract void put(String objectKey, String contentType, MultipartFile file);

    public abstract void remove(String objectKey);

    public abstract String presignedGet(String objectKey, Duration duration);
}
