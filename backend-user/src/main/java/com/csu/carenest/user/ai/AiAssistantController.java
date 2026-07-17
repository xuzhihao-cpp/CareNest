package com.csu.carenest.user.ai;
import com.csu.carenest.user.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class AiAssistantController {
    private final AiAssistantService service; public AiAssistantController(AiAssistantService service){this.service=service;}
    @PostMapping("/ai/sessions") public ApiResponse<AiAssistantDtos.Session> create(@RequestHeader(value="Authorization",required=false)String a,@RequestBody AiAssistantDtos.CreateSessionRequest r){return ApiResponse.success(service.create(a,r));}
    @PostMapping("/ai/sessions/{id}/messages") public ApiResponse<AiAssistantDtos.MessageResult> message(@RequestHeader(value="Authorization",required=false)String a,@PathVariable("id") String id,@RequestBody AiAssistantDtos.MessageRequest r){return ApiResponse.success(service.message(a,id,r));}
    @PostMapping("/ai/sessions/{id}/close") public ApiResponse<Void> close(@RequestHeader(value="Authorization",required=false)String a,@PathVariable("id") String id){service.close(a,id);return ApiResponse.success(null);}
    @DeleteMapping("/ai/sessions/{id}") public ApiResponse<Void> delete(@RequestHeader(value="Authorization",required=false)String a,@PathVariable("id") String id){service.deleteHistory(a,id);return ApiResponse.success(null);}
    @GetMapping("/ai/sessions") public ApiResponse<AiAssistantDtos.PageResult<AiAssistantDtos.SessionSummary>> sessions(@RequestHeader(value="Authorization",required=false)String a,@RequestParam(name="elderId",required=false)String elderId,@RequestParam(name="page",defaultValue="1")int page,@RequestParam(name="size",defaultValue="20")int size){return ApiResponse.success(service.listSessions(a,elderId,page,size));}
    @GetMapping("/ai/sessions/{id}/messages") public ApiResponse<java.util.List<AiAssistantDtos.ConversationMessage>> messages(@RequestHeader(value="Authorization",required=false)String a,@PathVariable("id")String id){return ApiResponse.success(service.messages(a,id));}
    @GetMapping("/assistance/tickets") public ApiResponse<AiAssistantDtos.PageResult<AiAssistantDtos.AssistanceTicket>> tickets(@RequestHeader(value="Authorization",required=false)String a,@RequestParam(name="elderId",required=false)String elderId,@RequestParam(name="status",required=false)String status,@RequestParam(name="page",defaultValue="1")int page,@RequestParam(name="size",defaultValue="20")int size){return ApiResponse.success(service.tickets(a,elderId,status,page,size));}
}
