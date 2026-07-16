package com.csu.carenest.careadmin.attention;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 阶段31服务前注意事项接口的冻结读写模型。 */
public final class AttentionNoticeDtos {

    private AttentionNoticeDtos() {
    }

    public record AttentionNoticeResponse(List<AttentionNoticeItem> items) {
    }

    public record AttentionNoticeItem(
            String noticeId,
            String level,
            String content,
            String source,
            boolean requiredAck,
            boolean acknowledged,
            String acknowledgedAt) {
    }

    public record AckRequest(
            @NotEmpty
            @Size(max = 100)
            List<@NotBlank String> noticeIds) {
    }
}
