import { request } from './client'
import type { PageResult } from '@/types/api'
import type { AiMessageResponse, AiSession, AssistanceTicketItem, CustomerServiceTicket, CustomerServiceTicketDetail, TicketMessage } from '@/types/stageFortyOne'
export const createAiSession=(data:{elderId?:string;sessionTitle:string;sourceType:'TEXT'|'VOICE'})=>request<AiSession>({url:'/ai/sessions',method:'POST',data})
export const sendAiMessage=(id:string,data:{content:string;messageType:'TEXT'|'VOICE';voiceLogId?:string|null})=>request<AiMessageResponse>({url:`/ai/sessions/${id}/messages`,method:'POST',data})
export const listAssistanceTickets=(p:{elderId?:string;status?:string;page?:number;size?:number})=>request<PageResult<AssistanceTicketItem>>({url:`/assistance/tickets?${new URLSearchParams(Object.entries({elderId:p.elderId||'',status:p.status||'',page:String(p.page||1),size:String(p.size||20)}))}`,method:'GET'})
export const listCustomerServiceTickets=(p:{status?:string;priority?:string;keyword?:string;page?:number;size?:number})=>request<PageResult<CustomerServiceTicket>>({url:`/customer-service/tickets?${new URLSearchParams(Object.entries({status:p.status||'',priority:p.priority||'',keyword:p.keyword||'',page:String(p.page||1),size:String(p.size||20)}))}`,method:'GET'})
export const getCustomerServiceTicket=(id:string)=>request<CustomerServiceTicketDetail>({url:`/customer-service/tickets/${id}`,method:'GET'})
export const updateCustomerServiceTicketStatus=(id:string,targetStatus:string,handleResult:string)=>request<CustomerServiceTicket>({url:`/customer-service/tickets/${id}/status`,method:'POST',data:{targetStatus,handleResult}})
export const addCustomerServiceTicketMessage=(id:string,content:string)=>request<TicketMessage>({url:`/customer-service/tickets/${id}/messages`,method:'POST',data:{content,messageType:'TEXT'}})
