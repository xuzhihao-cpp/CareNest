package com.csu.carenest.careadmin.customer;
import java.util.List;
public final class CustomerServiceTicketDtos { private CustomerServiceTicketDtos(){}
 public record TicketItem(String ticketId,String assistanceTicketId,String elderId,String elderName,String requesterName,String category,String priority,String ticketStatus,String description,String sourceType,String createdAt){}
 public record TicketMessageItem(String messageId,String senderRole,String content,String createdAt){}
 public record TicketDetail(TicketItem ticket,List<TicketMessageItem> messages){}
 public record StatusRequest(String targetStatus,String handleResult){}
 public record MessageRequest(String content,String messageType){}
 public record PageResult<T>(List<T> records,long total,int page,int size){}
}
