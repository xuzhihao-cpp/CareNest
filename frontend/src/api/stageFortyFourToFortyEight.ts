import { request } from '@/api/client';
import type { PageResult } from '@/types/api';
import type {
  AdminNurseScore,
  AiAuditDetail,
  AiAuditSession,
  FollowUpMethod,
  NurseAppeal,
  NurseScore,
  ReviewComplaintResult,
  TicketFollowUp
} from '@/types/stageFortyFourToFortyEight';

export const listTicketFollowUps = (ticketId: string) => request<TicketFollowUp[]>({
  method: 'GET', url: `/admin/customer-service/tickets/${encodeURIComponent(ticketId)}/follow-ups`
});

export const addTicketFollowUp = (ticketId: string, data: {
  method: FollowUpMethod; content: string; nextFollowUpAt: string | null; result: string;
}) => request<TicketFollowUp>({
  method: 'POST', url: `/admin/customer-service/tickets/${encodeURIComponent(ticketId)}/follow-up`, data
});

export const submitFamilyReview = (orderId: string, data: {
  rating: number; tags: string[]; content: string; reasonType: null; fileIds: string[];
}) => request<ReviewComplaintResult>({
  method: 'POST', url: `/family/orders/${encodeURIComponent(orderId)}/reviews`, data
});

export const submitFamilyComplaint = (orderId: string, data: {
  rating: null; tags: string[]; content: string; reasonType: string; fileIds: string[];
}) => request<ReviewComplaintResult>({
  method: 'POST', url: `/family/orders/${encodeURIComponent(orderId)}/complaints`, data
});

export const listComplaints = () => request<ReviewComplaintResult[]>({
  method: 'GET', url: '/admin/complaints'
});

export const handleComplaint = (complaintId: string, status: 'RESOLVED' | 'REJECTED', content: string) =>
  request<ReviewComplaintResult>({
    method: 'POST', url: `/admin/complaints/${encodeURIComponent(complaintId)}/handle`,
    data: { rating: null, tags: [], content, reasonType: status, fileIds: [] }
  });

export const listNurseAppeals = () => request<NurseAppeal[]>({ method: 'GET', url: '/nurse/appeals' });

export const submitNurseAppeal = (data: {
  targetType: string; targetId: string; reason: string; fileIds: string[];
}) => request<NurseAppeal>({ method: 'POST', url: '/nurse/appeals', data });

export const reviewNurseAppeal = (
  appealId: string, targetId: string, decision: 'APPROVED' | 'REJECTED', reason: string
) => request<NurseAppeal>({
  method: 'POST', url: `/admin/nurse-appeals/${encodeURIComponent(appealId)}/review`,
  data: { targetType: decision, targetId, reason, fileIds: [] }
});

export const getMyScore = (page = 1, size = 50) => request<NurseScore>({
  method: 'GET', url: `/nurse/my-score?page=${page}&size=${size}`
});

export const getAdminNurseScore = (nurseId: string) => request<AdminNurseScore>({
  method: 'GET', url: `/nurses/${encodeURIComponent(nurseId)}/score-logs`
});

export const recalculateNurseScore = (nurseId: string, sourceEventId: string) =>
  request<AdminNurseScore>({
    method: 'POST', url: `/admin/nurses/${encodeURIComponent(nurseId)}/score/recalculate`,
    data: { nurseId, sourceEventId }
  });

export const listAiAuditSessions = (riskOnly = false) => request<PageResult<AiAuditSession>>({
  method: 'GET', url: `/admin/ai/sessions?page=1&size=50${riskOnly ? '&riskFlag=true' : ''}`
});

export const getAiAuditSession = (sessionId: string) => request<AiAuditDetail>({
  method: 'GET', url: `/admin/ai/sessions/${encodeURIComponent(sessionId)}`
});
