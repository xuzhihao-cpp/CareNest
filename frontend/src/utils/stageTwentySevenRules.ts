import type {
  QualificationAuditStatus,
  QualificationSkillOption
} from '@/types/stageTwentySix';
import type { QualificationReviewDecision } from '@/types/stageTwentySeven';

export const QUALIFICATION_REVIEW_COMMENT_MAX_LENGTH = 500;

const decisionLabels: Record<QualificationReviewDecision, string> = {
  APPROVED: '通过审核',
  REJECTED: '驳回申请',
  NEED_MORE: '要求补充材料'
};

export function qualificationReviewDecisionLabel(decision: QualificationReviewDecision) {
  return decisionLabels[decision];
}

export function canReviewQualificationStatus(status: QualificationAuditStatus) {
  return status === 'PENDING';
}

export function areQualificationSkillsResolved(
  skillCodes: string[],
  options: QualificationSkillOption[],
  dictionaryAvailable: boolean
) {
  if (!dictionaryAvailable || !skillCodes.length || !options.length) return false;
  const availableCodes = new Set(options.map((item) => item.value));
  return skillCodes.every((code) => availableCodes.has(code));
}

export function validateQualificationReview(input: {
  applicationStatus: QualificationAuditStatus;
  decision: QualificationReviewDecision | '';
  reviewComment: string;
  allMaterialsReviewed: boolean;
}) {
  if (!canReviewQualificationStatus(input.applicationStatus)) return '该申请已处理，不能重复审核。';
  if (!input.allMaterialsReviewed) return '请先核对全部技能和证明材料。';
  if (!input.decision) return '请选择审核决定。';
  const comment = input.reviewComment.trim();
  if ((input.decision === 'REJECTED' || input.decision === 'NEED_MORE') && !comment) {
    return input.decision === 'REJECTED' ? '驳回申请时必须填写原因。' : '要求补充材料时必须填写说明。';
  }
  if (comment.length > QUALIFICATION_REVIEW_COMMENT_MAX_LENGTH) {
    return `审核意见不能超过 ${QUALIFICATION_REVIEW_COMMENT_MAX_LENGTH} 个字符。`;
  }
  return '';
}

export function qualificationReviewErrorMessage(code: number) {
  if (code === 401) return '登录状态已失效，请重新登录。';
  if (code === 403) return '当前账号没有审核护理资质的权限。';
  if (code === 409) return '该申请已由其他审核人员处理，列表已刷新。';
  if (code === 422) return '审核决定或审核意见不符合要求，请检查后重试。';
  if (code === 502) return '审核响应内容不完整，请刷新列表确认最终状态。';
  return '护理资质审核暂时无法提交，请稍后重试。';
}

export function qualificationCertificatePreviewErrorMessage(code: number) {
  if (code === 401) return '登录状态已失效，请重新登录后查看证明材料。';
  if (code === 403) return '当前账号没有查看这份证明材料的权限。';
  if (code === 404) return '这份证明材料已不存在，请刷新申请列表后确认。';
  if (code === 502) return '证明材料响应不完整，请稍后重试。';
  return '证明材料暂时无法打开，请稍后重试。';
}
