package com.csu.carenest.careadmin.phase;

public interface Phase25MedicalFileStorage {
    byte[] read(String bucket, String objectKey);
}
