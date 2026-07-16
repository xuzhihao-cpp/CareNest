import type { PageResult } from './api'

export type AiSafetyLevel = 'NORMAL' | 'WARNING' | 'CRITICAL'
export interface AiSession { sessionId:string; elderId:string; elderName:string; sessionTitle:string|null; sessionStatus:'ACTIVE'|'CLOSED'; safetyLevel:AiSafetyLevel; riskFlag:boolean; latestAssistanceTicketId:string|null; latestAssistanceStatus:string|null; createdAt:string }
export interface AiMessageResponse { sessionId:string; userMessageId:string; assistantMessageId:string; answer:string; safetyLevel:AiSafetyLevel; riskFlag:boolean; assistanceTicketId:string|null; customerServiceTicketCreated:boolean }
export interface AiSessionSummary { sessionId:string; elderId:string; elderName:string; sessionTitle:string|null; sessionStatus:'ACTIVE'|'CLOSED'; safetyLevel:AiSafetyLevel; latestMessagePreview:string|null; createdAt:string; updatedAt:string }
export interface AiConversationMessage { messageId:string; senderRole:'USER'|'ASSISTANT'; messageType:'TEXT'|'VOICE'; content:string; safetyFlag:boolean; createdAt:string }
export type AiSessionPage = PageResult<AiSessionSummary>
export interface AssistanceTicketItem { ticketId:string; elderId:string; elderName:string; category:string; priority:'NORMAL'|'URGENT'; ticketStatus:'PENDING'|'PROCESSING'|'RESOLVED'|'CLOSED'; description:string; sourceType:string; createdAt:string }
export interface CustomerServiceTicket { ticketId:string; assistanceTicketId:string|null; elderId:string; elderName:string; requesterName:string|null; category:string; priority:'NORMAL'|'URGENT'; ticketStatus:'PENDING'|'PROCESSING'|'RESOLVED'|'CLOSED'; description:string; sourceType:string; createdAt:string }
export interface TicketMessage { messageId:string; senderRole:string; content:string; createdAt:string }
export interface CustomerServiceTicketDetail { ticket:CustomerServiceTicket; messages:TicketMessage[] }
