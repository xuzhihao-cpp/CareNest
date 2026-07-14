package com.csu.carenest.careadmin.healthsuggestion;

import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.ApiException;
import com.csu.carenest.careadmin.common.PageData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
public class HealthSuggestionService {
    private static final Set<String> FIELDS=Set.of("diseases","medications","allergies","riskTags","carePlan");
    private static final Set<String> SOURCES=Set.of("SERVICE_RECORD","SERVICE_REPORT");
    private final HealthSuggestionRepository repo; private final ObjectMapper json;
    public HealthSuggestionService(HealthSuggestionRepository repo,ObjectMapper json){this.repo=repo;this.json=json;}

    @Transactional
    public HealthSuggestionDtos.CreateResult create(CurrentUser user,String orderId,HealthSuggestionDtos.CreateRequest request){
        if(!user.hasRole(RoleCode.NURSE))throw error(403,"仅护理人员可以提交档案建议");
        var order=repo.order(orderId).orElseThrow(()->error(404,"订单不存在"));
        if(!repo.assigned(orderId,user.userId()))throw error(403,"该订单未分配给当前护理人员");
        validateRequest(request);
        repo.source(orderId,request.sourceType(),request.sourceId()).orElseThrow(()->error(422,"建议来源与订单不匹配"));
        JsonNode normalized=normalize(request.fieldName(),request.newValue());
        String newValue=write(sort(normalized));
        String oldValue=write(current(order.elderId(),request.fieldName()));
        if(repo.duplicate(orderId,request.sourceType(),request.sourceId(),request.fieldName(),newValue))throw error(409,"已有相同建议等待审核");
        String suggestion=id(),task=id();
        repo.insertSuggestion(suggestion,order,request,oldValue,newValue,user.userId());
        repo.insertTask(task,suggestion,order,request,oldValue,newValue,user.userId());
        repo.log(id(),user.userId(),suggestion,newValue);
        return new HealthSuggestionDtos.CreateResult(suggestion,"PENDING");
    }

    public PageData<HealthSuggestionDtos.ReviewTaskItem> list(CurrentUser user,int page,int size,String status,String source,String keyword){
        if(!(user.hasRole(RoleCode.ADMIN)||user.hasRole(RoleCode.CUSTOMER_SERVICE))||!repo.hasReviewPermission(user.userId()))throw error(403,"无健康档案审核权限");
        if(page<1||size<1||size>50)throw error(422,"分页参数不合法");
        if(text(source)&&!Set.of("SERVICE_RECORD","SERVICE_REPORT","MEDICAL_FILE","REPORT_ACK","SUGGESTION","MANUAL").contains(source))throw error(422,"来源类型不合法");
        String dbStatus=status;
        if(text(status)){dbStatus=switch(status){case "PENDING"->"PENDING";case "IN_REVIEW"->"APPROVED";case "ARCHIVED"->"ARCHIVED";case "REJECTED"->"REJECTED";default->throw error(422,"审核状态不合法");};}
        long total=repo.reviewCount(dbStatus,source,keyword);
        var rows=repo.reviews(dbStatus,source,keyword,(page-1)*size,size).stream().map(r->new HealthSuggestionDtos.ReviewTaskItem(r.taskId(),r.suggestionId(),uiStatus(r.status()),r.elderName(),r.serviceName(),r.sourceType(),r.sourceSummary(),r.fieldName(),read(r.oldValue()),read(r.newValue()),r.reason(),r.createdAt())).toList();
        return new PageData<>(rows,total,page,size);
    }

    private void validateRequest(HealthSuggestionDtos.CreateRequest r){
        if(r==null||!FIELDS.contains(r.fieldName()))throw error(422,"建议字段不合法");
        if(!SOURCES.contains(r.sourceType())||!text(r.sourceId()))throw error(422,"建议来源不合法");
        if(!text(r.reason())||r.reason().trim().length()<5||r.reason().trim().length()>255)throw error(422,"建议原因长度应为5到255字");
        if(r.newValue()==null||!r.newValue().isObject())throw error(422,"建议值必须为结构化对象");
    }
    private JsonNode normalize(String field,JsonNode value){
        return switch(field){
            case "diseases"->object(value,Set.of("diseaseName","status","diagnosedAt","remark"),Map.of("diseaseName",true,"status",true),Map.of("status",Set.of("ACTIVE","MONITORING","STABLE","RESOLVED")));
            case "medications"->medication(value);
            case "allergies"->object(value,Set.of("allergenName","reaction","severity","remark"),Map.of("allergenName",true,"severity",true),Map.of("severity",Set.of("MILD","MODERATE","SEVERE")));
            case "riskTags"->object(value,Set.of("tagCode","tagName"),Map.of("tagCode",true,"tagName",true),Map.of());
            case "carePlan"->object(value,Set.of("careGoals","dailyCare","precautions"),Map.of("careGoals",true,"dailyCare",true,"precautions",true),Map.of());
            default->throw error(422,"建议字段不合法");};
    }
    private JsonNode medication(JsonNode v){
        ObjectNode o=(ObjectNode)object(v,Set.of("medicationName","dosage","frequency","timePoints","startDate","endDate","remark"),Map.of("medicationName",true,"frequency",true,"startDate",true),Map.of("frequency",Set.of("ONCE_DAILY","TWICE_DAILY","THREE_TIMES_DAILY","EVERY_OTHER_DAY","WEEKLY","AS_NEEDED")));
        date(o,"startDate",true);date(o,"endDate",false);if(o.has("endDate")&&o.has("startDate")&&o.get("endDate").asText().compareTo(o.get("startDate").asText())<0)throw error(422,"结束日期不能早于开始日期");
        if(o.has("timePoints")){if(!o.get("timePoints").isArray())throw error(422,"用药时间格式不合法");for(JsonNode n:o.withArray("timePoints"))if(!n.isTextual()||!n.asText().matches("([01]\\d|2[0-3]):[0-5]\\d"))throw error(422,"用药时间格式不合法");}
        return o;
    }
    private JsonNode object(JsonNode v,Set<String> allowed,Map<String,Boolean> required,Map<String,Set<String>> enums){
        ObjectNode out=json.createObjectNode();v.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw error(422,"建议值包含未知字段");JsonNode n=v.get(k);if(n.isTextual())out.put(k,n.asText().trim());else out.set(k,n);});
        required.keySet().forEach(k->{if(!out.has(k)||!out.get(k).isTextual()||out.get(k).asText().isBlank())throw error(422,"建议值缺少必要字段");});
        enums.forEach((k,values)->{if(!values.contains(out.path(k).asText()))throw error(422,"建议值枚举不合法");});
        if(out.has("diagnosedAt"))date(out,"diagnosedAt",false);return out;
    }
    private void date(ObjectNode o,String field,boolean required){if(!o.has(field)||o.path(field).asText().isBlank()){if(required)throw error(422,"日期不能为空");o.remove(field);return;}try{LocalDate.parse(o.get(field).asText());}catch(Exception e){throw error(422,"日期格式不合法");}}

    private JsonNode current(String elder,String field){
        if(field.equals("carePlan")){var rows=repo.rows("care_plan",elder,"ORDER BY care_plan_id LIMIT 1");if(rows.isEmpty())return NullNode.instance;return read(String.valueOf(rows.get(0).get("PLAN_CONTENT")));}
        String table=switch(field){case"diseases"->"chronic_disease";case"medications"->"medication_plan";case"allergies"->"allergy_record";default->"risk_tag";};
        ArrayNode result=json.createArrayNode();
        for(Map<String,Object> row:repo.rows(table,elder,"ORDER BY 1")){
            ObjectNode item=json.createObjectNode();
            if(field.equals("diseases")){put(item,"diseaseName",value(row,"disease_name"));put(item,"status",value(row,"disease_status"));put(item,"diagnosedAt",value(row,"diagnosed_at"));put(item,"remark",value(row,"remark"));}
            else if(field.equals("medications")){put(item,"medicationName",value(row,"medication_name"));put(item,"dosage",value(row,"dosage"));put(item,"frequency",value(row,"frequency"));Object times=value(row,"time_points");if(times!=null)item.set("timePoints",read(String.valueOf(times)));put(item,"startDate",value(row,"start_date"));put(item,"endDate",value(row,"end_date"));put(item,"remark",value(row,"remark"));}
            else if(field.equals("allergies")){put(item,"allergenName",value(row,"allergen"));put(item,"reaction",value(row,"reaction"));put(item,"severity",value(row,"severity"));put(item,"remark",value(row,"remark"));}
            else{put(item,"tagCode",value(row,"tag_code"));put(item,"tagName",value(row,"tag_name"));}
            result.add(item);
        }
        return result;
    }
    private Object value(Map<String,Object> row,String key){for(var e:row.entrySet())if(e.getKey().equalsIgnoreCase(key))return e.getValue();return null;}
    private void put(ObjectNode node,String key,Object value){if(value!=null)node.put(key,String.valueOf(value));}
    private JsonNode sort(JsonNode n){if(n.isObject()){ObjectNode o=json.createObjectNode();List<String>keys=new ArrayList<>();n.fieldNames().forEachRemaining(keys::add);Collections.sort(keys);keys.forEach(k->o.set(k,sort(n.get(k))));return o;}if(n.isArray()){ArrayNode a=json.createArrayNode();n.forEach(x->a.add(sort(x)));return a;}return n;}
    private JsonNode read(String v){try{return v==null?NullNode.instance:json.readTree(v);}catch(Exception e){return TextNode.valueOf(v);}}
    private String write(JsonNode n){try{return json.writeValueAsString(n);}catch(JsonProcessingException e){throw error(422,"建议值无法序列化");}}
    private String uiStatus(String s){return switch(s){case"PENDING"->"PENDING";case"APPROVED","NEED_MORE"->"IN_REVIEW";case"ARCHIVED"->"ARCHIVED";default->"REJECTED";};}
    private static boolean text(String v){return v!=null&&!v.isBlank();}private static String id(){return UUID.randomUUID().toString().replace("-","");}private static ApiException error(int code,String msg){return new ApiException(code,msg);}
}
