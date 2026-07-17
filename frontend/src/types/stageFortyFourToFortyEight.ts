export type FollowUpMethod = 'PHONE' | 'ONLINE' | 'HOME' | 'AI' | 'CUSTOMER_SERVICE';
export type ComplaintStatus = 'PENDING' | 'PROCESSING' | 'RESOLVED' | 'REJECTED';
export type AppealStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type AppealTargetType = 'COMPLAINT' | 'METRIC' | 'EXCEPTION_PROOF';

export interface TicketFollowUp {
  followUpId: string;
  ticketStatus: string;
  method: FollowUpMethod;
  content: string;
  nextFollowUpAt: string | null;
  result: string;
  createdAt: string;
}

export interface ReviewComplaintResult {
  reviewId: string | null;
  complaintId: string | null;
  orderId: string;
  serviceName: string | null;
  complainantName: string | null;
  status: string;
  rating: number | null;
  tags: string[];
  reasonType: string | null;
  content: string | null;
  fileIds: string[];
  handleResult: string | null;
  createdAt: string;
}

export interface NurseAppeal {
  appealId: string;
  nurseId: string;
  nurseName: string | null;
  targetType: AppealTargetType;
  targetId: string;
  targetLabel: string;
  reason: string | null;
  fileIds: string[];
  status: AppealStatus;
  scoreAdjustment: number;
  reviewComment: string | null;
  createdAt: string;
}

export interface ScoreChangeItem {
  changeLogId: string;
  sourceEventType: string;
  sourceEventId: string;
  beforeScore: number;
  afterScore: number;
  scoreDelta: number;
  reason: string;
  createdAt: string;
  targetType: AppealTargetType | null;
  targetId: string | null;
}

export interface NurseScore {
  totalScore: number;
  level: 'EXCELLENT' | 'GOOD' | 'NEEDS_IMPROVEMENT';
  monthDelta: number;
  items: ScoreChangeItem[];
}

export interface AdminNurseScore {
  nurseId: string;
  totalScore: number;
  level: NurseScore['level'];
  changeLogs: ScoreChangeItem[];
}

export interface AiAuditSession {
  sessionId: string;
  elderName: string;
  requesterName: string;
  sessionTitle: string | null;
  sessionStatus: 'ACTIVE' | 'CLOSED';
  safetyLevel: 'NORMAL' | 'WARNING' | 'CRITICAL';
  riskFlag: boolean;
  latestMessageSummary: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AiAuditMessage {
  senderRole: 'USER' | 'ASSISTANT' | 'SYSTEM';
  messageType: 'TEXT' | 'VOICE' | 'SYSTEM';
  contentSummary: string | null;
  safetyFlag: boolean;
  createdAt: string;
}

export interface AiAuditDetail {
  session: AiAuditSession;
  messages: AiAuditMessage[];
}
