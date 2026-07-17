package com.csu.carenest.user.ai;

import com.csu.carenest.user.auth.AuthService;
import com.csu.carenest.user.auth.RoleCode;
import com.csu.carenest.user.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class AiAssistantService {
    private final AuthService auth; private final AiAssistantRepository repo; private final AiProvider provider;
    public AiAssistantService(AuthService auth,AiAssistantRepository repo,AiProvider provider){this.auth=auth;this.repo=repo;this.provider=provider;}
    @Transactional public AiAssistantDtos.Session create(String token,AiAssistantDtos.CreateSessionRequest req){
        AuthService.CurrentUser u=auth.requireCurrentUser(token); String elderId=resolve(u,req==null?null:req.elderId()); AiAssistantRepository.Elder e=repo.elder(elderId).orElseThrow(()->new ApiException(404,"elder not found"));
        String id="ai_"+UUID.randomUUID().toString().replace("-","").substring(0,24); repo.createSession(id,elderId,u.userId(),req==null?null:req.sessionTitle(),req==null||req.sourceType()==null?"TEXT":req.sourceType(),id); return new AiAssistantDtos.Session(id,e.id(),e.name(),req==null?null:req.sessionTitle(),"ACTIVE","NORMAL",false,null,null,java.time.LocalDateTime.now().toString());
    }
    @Transactional public AiAssistantDtos.MessageResult message(String token,String sessionId,AiAssistantDtos.MessageRequest req){
        AuthService.CurrentUser u=auth.requireCurrentUser(token); authorizeSession(u,sessionId); if(req==null||req.content()==null||req.content().isBlank()) throw new ApiException(422,"content is required");
        AiProvider.Result result=provider.answer(req.content()); String trace="trace_"+UUID.randomUUID().toString().replace("-",""); String userMsg=messageId(); String aiMsg=messageId();
        repo.addMessage(userMsg,sessionId,"USER",req.messageType()==null?"TEXT":req.messageType(),summary(req.content()),req.content(),req.voiceLogId(),!"NORMAL".equals(result.safetyLevel()),trace); repo.addMessage(aiMsg,sessionId,"ASSISTANT","TEXT",summary(result.answer()),result.answer(),null,!"NORMAL".equals(result.safetyLevel()),trace); repo.updateSafety(sessionId,result.safetyLevel(),!"NORMAL".equals(result.safetyLevel()));
        String elderId=repo.sessionElder(sessionId).orElseThrow(); String assistance=null; boolean cs=false; if("CRITICAL".equals(result.safetyLevel())){ assistance="assist_"+UUID.randomUUID().toString().replace("-","").substring(0,24); repo.assistance(assistance,elderId,u.userId(),sessionId,result.category(),result.priority(),result.answer()); String ticket="cs_"+UUID.randomUUID().toString().replace("-","").substring(0,24);repo.customerTicket(ticket,assistance,elderId,u.userId(),result.category(),result.priority(),result.answer());cs=true; }
        return new AiAssistantDtos.MessageResult(sessionId,userMsg,aiMsg,result.answer(),result.safetyLevel(),!"NORMAL".equals(result.safetyLevel()),assistance,cs);
    }
    public AiAssistantDtos.PageResult<AiAssistantDtos.AssistanceTicket> tickets(String token,String elderId,String status,int page,int size){AuthService.CurrentUser u=auth.requireCurrentUser(token);String id=resolve(u,elderId);if(page<1||size<1||size>50)throw new ApiException(422,"invalid page");return new AiAssistantDtos.PageResult<>(repo.tickets(id,status,size,(page-1)*size),repo.ticketCount(id,status),page,size);}
    public AiAssistantDtos.PageResult<AiAssistantDtos.SessionSummary> listSessions(String token,String elderId,int page,int size){AuthService.CurrentUser u=auth.requireCurrentUser(token);if(page<1||size<1||size>50)throw new ApiException(422,"invalid page");if(u.roles().contains(RoleCode.ELDER)){AiAssistantRepository.Elder elder=repo.elderByUser(u.userId()).orElseThrow(()->new ApiException(404,"elder not found"));return new AiAssistantDtos.PageResult<>(repo.sessionsForElder(elder.id(),u.userId(),size,(page-1)*size),repo.sessionCountForElder(elder.id(),u.userId()),page,size);}if(u.roles().contains(RoleCode.FAMILY)){if(elderId!=null&&!repo.bound(u.userId(),elderId))throw new ApiException(403,"active family binding required");return new AiAssistantDtos.PageResult<>(repo.sessionsForFamily(u.userId(),elderId,size,(page-1)*size),repo.sessionCountForFamily(u.userId(),elderId),page,size);}throw new ApiException(403,"role not allowed");}
    public List<AiAssistantDtos.ConversationMessage> messages(String token,String sessionId){AuthService.CurrentUser u=auth.requireCurrentUser(token);authorizeSession(u,sessionId);return repo.messages(sessionId);}
    public String resolveAuthorizedElder(String token, String elderId) {
        return resolve(auth.requireCurrentUser(token), elderId);
    }
    public void close(String token,String session){AuthService.CurrentUser u=auth.requireCurrentUser(token);authorizeSession(u,session);repo.close(session);}
    @Transactional public void deleteHistory(String token,String session){
        AuthService.CurrentUser u=auth.requireCurrentUser(token);
        authorizeSession(u,session);
        if(!u.userId().equals(repo.sessionOwner(session))) throw new ApiException(403,"只能删除自己创建的对话");
        repo.close(session);
    }
    public String healthFeedbackAdvice(String type,String severity,String content){
        String prompt="长辈健康反馈：类型为"+feedbackTypeLabel(type)+"，程度为"+severityLabel(severity)
                +"。补充说明："+(content==null||content.isBlank()?"未补充":content.trim())
                +"。请用两句话给出安全、易懂、可执行的日常照护建议，不诊断、不调整用药。";
        try{
            AiProvider.Result result=provider.answer(prompt);
            if("CRITICAL".equals(result.safetyLevel())) return "当前情况可能存在紧急风险，请立即联系家属或专业医护人员；如症状持续加重，请及时拨打当地急救电话。";
            return result.answer();
        }catch(RuntimeException exception){
            return "请先休息并继续留意身体变化；如果不适持续或加重，请及时联系家属或专业医护人员。";
        }
    }
    private String resolve(AuthService.CurrentUser u,String requested){if(u.roles().contains(RoleCode.ELDER)){String id=repo.elderByUser(u.userId()).orElseThrow(()->new ApiException(404,"elder not found")).id();if(requested!=null&&!requested.equals(id))throw new ApiException(403,"not owner");return id;}if(u.roles().contains(RoleCode.FAMILY)){if(requested==null||!repo.bound(u.userId(),requested))throw new ApiException(403,"active family binding required");return requested;}throw new ApiException(403,"role not allowed");}
    private void authorizeSession(AuthService.CurrentUser u,String session){String elder=repo.sessionElder(session).orElseThrow(()->new ApiException(404,"session not found"));if(u.roles().contains(RoleCode.ELDER)){if(repo.sessionOwner(session).equals(u.userId()))return;resolve(u,elder);return;}if(u.roles().contains(RoleCode.FAMILY)){resolve(u,elder);return;}throw new ApiException(403,"role not allowed");}
    private String summary(String s){return s.length()>480?s.substring(0,480):s;}
    private String messageId(){return "msg_"+UUID.randomUUID().toString().replace("-","").substring(0,28);}
    private String feedbackTypeLabel(String type){return switch(type==null?"":type){case"PAIN"->"疼痛";case"DIZZINESS"->"头晕";case"SLEEP"->"睡眠变化";case"DIET"->"饮食变化";case"MENTAL_STATE"->"精神状态变化";default->"身体不适";};}
    private String severityLabel(String severity){return switch(severity==null?"":severity){case"LOW"->"轻微";case"MEDIUM"->"明显";case"HIGH"->"严重";default->"未标明";};}
}
