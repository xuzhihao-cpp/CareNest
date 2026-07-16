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
        String elderId=repo.sessionElder(sessionId).orElseThrow(); String assistance=null; boolean cs=false; if(!"NORMAL".equals(result.safetyLevel())){ assistance="assist_"+UUID.randomUUID().toString().replace("-","").substring(0,24); repo.assistance(assistance,elderId,u.userId(),sessionId,result.category(),result.priority(),result.answer()); if("CRITICAL".equals(result.safetyLevel())){String ticket="cs_"+UUID.randomUUID().toString().replace("-","").substring(0,24);repo.customerTicket(ticket,assistance,elderId,u.userId(),result.category(),result.priority(),result.answer());cs=true;} }
        return new AiAssistantDtos.MessageResult(sessionId,userMsg,aiMsg,result.answer(),result.safetyLevel(),!"NORMAL".equals(result.safetyLevel()),assistance,cs);
    }
    public AiAssistantDtos.PageResult<AiAssistantDtos.AssistanceTicket> tickets(String token,String elderId,String status,int page,int size){AuthService.CurrentUser u=auth.requireCurrentUser(token);String id=resolve(u,elderId);if(page<1||size<1||size>50)throw new ApiException(422,"invalid page");return new AiAssistantDtos.PageResult<>(repo.tickets(id,status,size,(page-1)*size),repo.ticketCount(id,status),page,size);}
    public void close(String token,String session){AuthService.CurrentUser u=auth.requireCurrentUser(token);authorizeSession(u,session);repo.close(session);}
    private String resolve(AuthService.CurrentUser u,String requested){if(u.roles().contains(RoleCode.ELDER)){String id=repo.elderByUser(u.userId()).orElseThrow(()->new ApiException(404,"elder not found")).id();if(requested!=null&&!requested.equals(id))throw new ApiException(403,"not owner");return id;}if(u.roles().contains(RoleCode.FAMILY)){if(requested==null||!repo.bound(u.userId(),requested))throw new ApiException(403,"active family binding required");return requested;}throw new ApiException(403,"role not allowed");}
    private void authorizeSession(AuthService.CurrentUser u,String session){String owner=repo.sessionOwner(session);if(owner.equals(u.userId()))return;String elder=repo.sessionElder(session).orElseThrow(()->new ApiException(404,"session not found"));resolve(u,elder);}
    private String summary(String s){return s.length()>480?s.substring(0,480):s;}
    private String messageId(){return "msg_"+UUID.randomUUID().toString().replace("-","").substring(0,28);}
}
