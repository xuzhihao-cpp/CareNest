package com.csu.carenest.user.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiResponseGuardTest {
    private final AiResponseGuard guard = new AiResponseGuard();

    @Test
    void rejectsUnsupportedPlatformClaims() {
        assertEquals("PLATFORM_CLAIM", guard.rejectionReason("平台客服24小时在线").orElseThrow());
        assertEquals("PLATFORM_CLAIM", guard.rejectionReason("请拨打400-XXX-XXXX").orElseThrow());
        assertEquals("PLATFORM_CLAIM", guard.rejectionReason("CareNest会全程陪伴您").orElseThrow());
        assertEquals("PLATFORM_CLAIM", guard.rejectionReason("CareNest App的健康日记支持语音输入和自动存档").orElseThrow());
        assertEquals("PLATFORM_CLAIM", guard.rejectionReason("测完可拍照上传到CareNest平台，或交给照护员录入").orElseThrow());
        assertEquals("PLATFORM_CLAIM", guard.rejectionReason("平台每天9:00至18:00提供客服").orElseThrow());
        assertEquals("PLATFORM_CLAIM", guard.rejectionReason("请拨打95123联系平台").orElseThrow());
        assertEquals("PLATFORM_CLAIM", guard.rejectionReason("平台会自动安排照护员上门").orElseThrow());
        assertEquals("PLATFORM_CLAIM", guard.rejectionReason("我们支持一键预约护理服务").orElseThrow());
        assertEquals("PLATFORM_CLAIM", guard.rejectionReason("平台提供上门护理").orElseThrow());
        assertEquals("PLATFORM_CLAIM", guard.rejectionReason("CareNest可以帮您联系护理员").orElseThrow());
        assertEquals("PLATFORM_CLAIM", guard.rejectionReason("平台客服早上九点到下午五点在线").orElseThrow());
    }

    @Test
    void rejectsDiagnosisAndMedicationInstructions() {
        assertEquals("MEDICAL_DECISION", guard.rejectionReason("你这是胃炎").orElseThrow());
        assertEquals("MEDICAL_DECISION", guard.rejectionReason("建议立即停药").orElseThrow());
        assertEquals("MEDICAL_DECISION", guard.rejectionReason("每天服用两片").orElseThrow());
        assertEquals("MEDICAL_DECISION", guard.rejectionReason("收缩压大于140时需要立即处理").orElseThrow());
        assertEquals("MEDICAL_DECISION", guard.rejectionReason("收缩压140以上属于高血压").orElseThrow());
        assertEquals("MEDICAL_DECISION", guard.rejectionReason("140/90以上需要立即处理").orElseThrow());
        assertEquals("MEDICAL_DECISION", guard.rejectionReason("每天两片").orElseThrow());
        assertEquals("MEDICAL_DECISION", guard.rejectionReason("服用100mg，每日一次").orElseThrow());
        assertEquals("MEDICAL_DECISION", guard.rejectionReason("考虑是胃炎").orElseThrow());
        assertEquals("MEDICAL_DECISION", guard.rejectionReason("很可能患了高血压").orElseThrow());
        assertEquals("MEDICAL_DECISION", guard.rejectionReason("可能是胃炎").orElseThrow());
        assertEquals("MEDICAL_DECISION", guard.rejectionReason("正常血压是120/80").orElseThrow());
        assertEquals("MEDICAL_DECISION", guard.rejectionReason("血糖控制在7以内").orElseThrow());
        assertEquals("MEDICAL_DECISION", guard.rejectionReason("阿司匹林100mg每日一次").orElseThrow());
        assertEquals("MEDICAL_DECISION", guard.rejectionReason("建议改用阿司匹林").orElseThrow());
    }

    @Test
    void acceptsOrdinaryCareGuidance() {
        assertTrue(guard.rejectionReason("请记录今天的饮水量，如有明显不适请联系家属或医生。").isEmpty());
        assertTrue(guard.rejectionReason("不要自行停药，也不要擅自加量，请联系开药医生确认。").isEmpty());
        assertTrue(guard.rejectionReason("不要随意停药，不能突然加量，请联系医生确认。").isEmpty());
    }
}
