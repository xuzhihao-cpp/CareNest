package com.csu.carenest.user.ai;

import com.csu.carenest.user.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

@Service
public class AiSpeechTranscriptionService {
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "audio/aac", "audio/mp3", "audio/mpeg", "audio/mp4", "audio/ogg", "audio/wav", "audio/webm"
    );

    private final AiAssistantService assistant;
    private final AiSpeechTranscriptionProvider provider;

    public AiSpeechTranscriptionService(AiAssistantService assistant, AiSpeechTranscriptionProvider provider) {
        this.assistant = assistant;
        this.provider = provider;
    }

    public AiAssistantDtos.SpeechTranscription transcribe(String authorization, String elderId, MultipartFile audio) {
        assistant.resolveAuthorizedElder(authorization, elderId);
        if (audio == null || audio.isEmpty()) throw new ApiException(422, "请先录制语音");
        if (audio.getSize() > MAX_BYTES) throw new ApiException(422, "语音文件不能超过10MB");
        String contentType = audio.getContentType() == null ? "" : audio.getContentType().toLowerCase().split(";")[0].trim();
        if (!SUPPORTED_TYPES.contains(contentType)) throw new ApiException(422, "语音格式不受支持");
        try {
            AiSpeechTranscriptionProvider.Result result = provider.transcribe(
                    audio.getBytes(), contentType, audio.getOriginalFilename()
            );
            return new AiAssistantDtos.SpeechTranscription(
                    result.transcript(), result.model(), "trace_" + UUID.randomUUID().toString().replace("-", "")
            );
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(422, "语音文件读取失败", exception);
        }
    }
}
