package com.csu.carenest.user.medicalfile;

import com.csu.carenest.user.common.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.validation.Valid;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MedicalFileController {
    private final MedicalFileService service;

    public MedicalFileController(MedicalFileService service) {
        this.service = service;
    }

    @PostMapping(value = "/api/v1/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MedicalFileDtos.UploadResult> upload(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(service.upload(authorization, file));
    }

    @PostMapping("/api/v1/elders/{elderId}/medical-files")
    public ApiResponse<MedicalFileDtos.RegisterResult> register(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("elderId") String elderId,
            @Valid @RequestBody MedicalFileDtos.RegisterRequest request) {
        return ApiResponse.success(service.register(authorization, elderId, request));
    }

    @GetMapping("/api/v1/elders/{elderId}/medical-files")
    public ApiResponse<List<MedicalFileDtos.MedicalFileItem>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("elderId") String elderId) {
        return ApiResponse.success(service.list(authorization, elderId));
    }
}
