package com.csu.carenest.user.flow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.csu.carenest.user.auth.AuthService;
import com.csu.carenest.user.auth.OperationLog;
import com.csu.carenest.user.auth.OperationLogMapper;
import com.csu.carenest.user.auth.RoleCode;
import com.csu.carenest.user.common.ApiException;
import com.csu.carenest.user.common.ForbiddenException;
import com.csu.carenest.user.common.NotFoundException;
import com.csu.carenest.user.redis.HomeCacheInvalidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class UserSideFlowService {

    private static final String ACTIVE = "ACTIVE";
    private static final String PENDING = "PENDING";
    private static final String REVOKED = "REVOKED";
    private static final String SCOPE_UPDATE_PENDING = "PENDING";

    private final AuthService authService;
    private final ElderFamilyBindingMapper bindingMapper;
    private final AuthorizationScopeMapper authorizationScopeMapper;
    private final ElderProfileMapper elderProfileMapper;
    private final ElderContactMapper elderContactMapper;
    private final HealthArchiveChangeLogMapper healthArchiveChangeLogMapper;
    private final ServiceAddressMapper serviceAddressMapper;
    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final HomeCacheInvalidator homeCacheInvalidator;

    public UserSideFlowService(
            AuthService authService,
            ElderFamilyBindingMapper bindingMapper,
            AuthorizationScopeMapper authorizationScopeMapper,
            ElderProfileMapper elderProfileMapper,
            ElderContactMapper elderContactMapper,
            HealthArchiveChangeLogMapper healthArchiveChangeLogMapper,
            ServiceAddressMapper serviceAddressMapper,
            OperationLogMapper operationLogMapper,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            HomeCacheInvalidator homeCacheInvalidator) {
        this.authService = authService;
        this.bindingMapper = bindingMapper;
        this.authorizationScopeMapper = authorizationScopeMapper;
        this.elderProfileMapper = elderProfileMapper;
        this.elderContactMapper = elderContactMapper;
        this.healthArchiveChangeLogMapper = healthArchiveChangeLogMapper;
        this.serviceAddressMapper = serviceAddressMapper;
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.homeCacheInvalidator = homeCacheInvalidator;
    }

    @Transactional
    public BindingResponse createBinding(String authorization, BindingRequest request) {
        AuthService.CurrentUser currentUser = requireRole(authorization, RoleCode.FAMILY);
        ElderProfile elder = findElderByInviteCode(request.elderInviteCode());
        validateScopes(request.scopeCodes());
        boolean alreadyBound = bindingMapper.exists(Wrappers.<ElderFamilyBinding>lambdaQuery()
                .eq(ElderFamilyBinding::getElderId, elder.getElderId())
                .eq(ElderFamilyBinding::getFamilyId, currentUser.userId())
                .in(ElderFamilyBinding::getBindingStatus, PENDING, ACTIVE));
        if (alreadyBound) {
            throw new ApiException(409, "A pending or active binding already exists");
        }

        ElderFamilyBinding binding = new ElderFamilyBinding();
        binding.setBindingId(nextId("binding"));
        binding.setElderId(elder.getElderId());
        binding.setFamilyId(currentUser.userId());
        binding.setBindingStatus(PENDING);
        binding.setScopeCodes(writeJson(request.scopeCodes()));
        binding.setRelationType(request.relationType());
        binding.setInviterUserId(currentUser.userId());
        bindingMapper.insert(binding);
        saveOperationLog(currentUser, "CREATE_BINDING", "ELDER_FAMILY_BINDING", binding.getBindingId(), binding);
        evictBindingHomesAfterCommit(currentUser.userId(), elder);
        return toBindingResponse(binding, elder);
    }

    public List<BindingResponse> familyBindings(String authorization) {
        AuthService.CurrentUser currentUser = requireRole(authorization, RoleCode.FAMILY);
        return bindingMapper.selectList(Wrappers.<ElderFamilyBinding>lambdaQuery()
                        .eq(ElderFamilyBinding::getFamilyId, currentUser.userId())
                        .ne(ElderFamilyBinding::getBindingStatus, REVOKED))
                .stream()
                .map(binding -> toBindingResponse(binding, requireElder(binding.getElderId())))
                .toList();
    }

    public List<BindingResponse> elderBindings(String authorization) {
        AuthService.CurrentUser currentUser = requireRole(authorization, RoleCode.ELDER);
        List<ElderProfile> elders = elderProfileMapper.selectList(Wrappers.<ElderProfile>lambdaQuery()
                .eq(ElderProfile::getUserId, currentUser.userId()));
        if (elders.isEmpty()) {
            throw new NotFoundException();
        }
        List<String> elderIds = elders.stream().map(ElderProfile::getElderId).toList();
        return bindingMapper.selectList(Wrappers.<ElderFamilyBinding>lambdaQuery()
                        .in(ElderFamilyBinding::getElderId, elderIds)
                        .ne(ElderFamilyBinding::getBindingStatus, REVOKED))
                .stream()
                .map(binding -> toBindingResponse(
                        binding,
                        elders.stream()
                                .filter(elder -> elder.getElderId().equals(binding.getElderId()))
                                .findFirst()
                                .orElseThrow(NotFoundException::new)))
                .toList();
    }

    @Transactional
    public BindingResponse approveBinding(String authorization, String bindingId, BindingRequest request) {
        AuthService.CurrentUser currentUser = authService.requireCurrentUser(authorization);
        ElderFamilyBinding binding = requireBinding(bindingId);
        ElderProfile elder = requireElder(binding.getElderId());
        if (!isElderSelf(currentUser, elder)) {
            throw new ForbiddenException();
        }
        if (PENDING.equals(binding.getBindingStatus()) && !request.scopeCodes().isEmpty()) {
            validateScopes(request.scopeCodes());
            binding.setScopeCodes(writeJson(request.scopeCodes()));
        }
        boolean approvingScopeUpdate = ACTIVE.equals(binding.getBindingStatus())
                && SCOPE_UPDATE_PENDING.equals(binding.getScopeUpdateStatus());
        if (approvingScopeUpdate) {
            binding.setScopeCodes(binding.getPendingScopeCodes());
            binding.setPendingScopeCodes(null);
            binding.setScopeUpdateStatus(null);
        } else {
            binding.setBindingStatus(ACTIVE);
        }
        binding.setApproverUserId(currentUser.userId());
        bindingMapper.updateById(binding);
        if (approvingScopeUpdate) {
            // MyBatis-Plus ignores null fields on updateById, so clear the pending proposal explicitly.
            bindingMapper.update(null, Wrappers.<ElderFamilyBinding>lambdaUpdate()
                    .eq(ElderFamilyBinding::getBindingId, binding.getBindingId())
                    .set(ElderFamilyBinding::getPendingScopeCodes, null)
                    .set(ElderFamilyBinding::getScopeUpdateStatus, null));
        }
        saveOperationLog(currentUser, "APPROVE_BINDING", "ELDER_FAMILY_BINDING", binding.getBindingId(), binding);
        evictBindingHomesAfterCommit(binding.getFamilyId(), elder);
        return toBindingResponse(binding, elder);
    }

    @Transactional
    public BindingResponse updateBindingScopes(String authorization, String bindingId, BindingRequest request) {
        AuthService.CurrentUser currentUser = requireRole(authorization, RoleCode.FAMILY);
        ElderFamilyBinding binding = requireFamilyBinding(currentUser.userId(), bindingId);
        if (!ACTIVE.equals(binding.getBindingStatus()) && !PENDING.equals(binding.getBindingStatus())) {
            throw new ForbiddenException();
        }
        validateScopes(request.scopeCodes());
        if (PENDING.equals(binding.getBindingStatus())) {
            binding.setRelationType(request.relationType());
            binding.setScopeCodes(writeJson(request.scopeCodes()));
            binding.setPendingScopeCodes(null);
            binding.setScopeUpdateStatus(null);
            bindingMapper.updateById(binding);
            saveOperationLog(currentUser, "UPDATE_PENDING_BINDING", "ELDER_FAMILY_BINDING", binding.getBindingId(), binding);
            ElderProfile elder = requireElder(binding.getElderId());
            evictBindingHomesAfterCommit(binding.getFamilyId(), elder);
            return toBindingResponse(binding, elder);
        }
        binding.setPendingScopeCodes(writeJson(request.scopeCodes()));
        binding.setScopeUpdateStatus(SCOPE_UPDATE_PENDING);
        bindingMapper.updateById(binding);
        saveOperationLog(currentUser, "UPDATE_BINDING_SCOPES", "ELDER_FAMILY_BINDING", binding.getBindingId(), binding);
        ElderProfile elder = requireElder(binding.getElderId());
        evictBindingHomesAfterCommit(binding.getFamilyId(), elder);
        return toBindingResponse(binding, elder);
    }

    @Transactional
    public BindingResponse revokeBinding(String authorization, String bindingId, BindingRequest request) {
        AuthService.CurrentUser currentUser = requireRole(authorization, RoleCode.FAMILY);
        ElderFamilyBinding binding = requireFamilyBinding(currentUser.userId(), bindingId);
        binding.setBindingStatus(REVOKED);
        bindingMapper.updateById(binding);
        saveOperationLog(currentUser, "REVOKE_BINDING", "ELDER_FAMILY_BINDING", binding.getBindingId(), binding);
        ElderProfile elder = requireElder(binding.getElderId());
        evictBindingHomesAfterCommit(binding.getFamilyId(), elder);
        return toBindingResponse(binding, elder);
    }

    public List<ElderProfileResponse> familyElders(String authorization) {
        AuthService.CurrentUser currentUser = requireRole(authorization, RoleCode.FAMILY);
        Set<String> elderIds = new LinkedHashSet<>();
        return bindingMapper.selectList(Wrappers.<ElderFamilyBinding>lambdaQuery()
                        .eq(ElderFamilyBinding::getFamilyId, currentUser.userId())
                        .eq(ElderFamilyBinding::getBindingStatus, ACTIVE))
                .stream()
                .filter(binding -> elderIds.add(binding.getElderId()))
                .map(binding -> toProfileResponse(requireElder(binding.getElderId())))
                .toList();
    }

    public ElderProfileResponse elderProfile(String authorization, String elderId) {
        AuthService.CurrentUser currentUser = authService.requireCurrentUser(authorization);
        ElderProfile elder = requireElder(elderId);
        requireElderProfileAccess(currentUser, elderId, "HEALTH_VIEW", "HEALTH_EDIT", "ARCHIVE_EDIT");
        return toProfileResponse(elder);
    }

    @Transactional
    public ElderProfileResponse updateElderProfile(String authorization, String elderId, ElderProfileRequest request) {
        AuthService.CurrentUser currentUser = authService.requireCurrentUser(authorization);
        requireElderProfileAccess(currentUser, elderId, "HEALTH_EDIT", "ARCHIVE_EDIT");
        ElderProfile elder = requireElder(elderId);
        String beforeValue = writeJson(elder);

        elder.setElderName(request.name());
        elder.setGender(request.gender());
        elder.setBirthDate(LocalDate.parse(request.birthDate()));
        elder.setCareLevel(request.careLevel());
        EmergencyContactRequest primaryContact = request.emergencyContacts().get(0);
        elder.setEmergencyContactName(primaryContact.contactName());
        elder.setEmergencyContactPhone(primaryContact.contactPhone());
        elder.setUpdatedAt(LocalDateTime.now());
        elderProfileMapper.updateById(elder);
        upsertPrimaryContact(elderId, primaryContact);
        saveHealthChangeLog(currentUser, elderId, beforeValue, elder);
        evictProfileHomesAfterCommit(elder);
        return toProfileResponse(elder);
    }

    public List<ServiceAddressResponse> serviceAddresses(String authorization, String elderId) {
        AuthService.CurrentUser currentUser = requireRole(authorization, RoleCode.FAMILY);
        requireActiveBinding(currentUser.userId(), elderId);
        return serviceAddressMapper.selectList(Wrappers.<ServiceAddress>lambdaQuery()
                        .eq(ServiceAddress::getElderId, elderId)
                        .eq(ServiceAddress::getFamilyId, currentUser.userId()))
                .stream()
                .map(this::toAddressResponse)
                .toList();
    }

    @Transactional
    public ServiceAddressResponse createServiceAddress(
            String authorization,
            String elderId,
            ServiceAddressRequest request) {
        AuthService.CurrentUser currentUser = requireRole(authorization, RoleCode.FAMILY);
        requireActiveBinding(currentUser.userId(), elderId);
        if (Boolean.TRUE.equals(request.isDefault()) || serviceAddressMapper.selectCount(Wrappers.<ServiceAddress>lambdaQuery()
                .eq(ServiceAddress::getElderId, elderId)
                .eq(ServiceAddress::getFamilyId, currentUser.userId())) == 0) {
            clearDefaultAddress(elderId, currentUser.userId());
            request = new ServiceAddressRequest(request.contactName(), request.contactPhone(), request.regionCode(), request.detailAddress(), true);
        }
        ServiceAddress address = new ServiceAddress();
        address.setAddressId(nextId("address"));
        address.setElderId(elderId);
        address.setFamilyId(currentUser.userId());
        applyAddressRequest(address, request);
        serviceAddressMapper.insert(address);
        evictAddressHomesAfterCommit(currentUser.userId(), elderId);
        return toAddressResponse(address);
    }

    @Transactional
    public ServiceAddressResponse updateServiceAddress(
            String authorization,
            String addressId,
            ServiceAddressRequest request) {
        AuthService.CurrentUser currentUser = requireRole(authorization, RoleCode.FAMILY);
        ServiceAddress address = requireAddress(addressId);
        requireAddressOwner(currentUser.userId(), address);
        requireActiveBinding(currentUser.userId(), address.getElderId());
        if (Boolean.TRUE.equals(request.isDefault())) {
            clearDefaultAddress(address.getElderId(), currentUser.userId());
        }
        applyAddressRequest(address, request);
        serviceAddressMapper.updateById(address);
        ensureDefaultAddress(address.getElderId(), currentUser.userId());
        evictAddressHomesAfterCommit(currentUser.userId(), address.getElderId());
        return toAddressResponse(requireAddress(addressId));
    }

    @Transactional
    public ServiceAddressResponse deleteServiceAddress(String authorization, String addressId) {
        AuthService.CurrentUser currentUser = requireRole(authorization, RoleCode.FAMILY);
        ServiceAddress address = requireAddress(addressId);
        requireAddressOwner(currentUser.userId(), address);
        requireActiveBinding(currentUser.userId(), address.getElderId());
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            ServiceAddress replacement = serviceAddressMapper.selectOne(Wrappers.<ServiceAddress>lambdaQuery()
                    .eq(ServiceAddress::getElderId, address.getElderId())
                    .eq(ServiceAddress::getFamilyId, currentUser.userId())
                    .ne(ServiceAddress::getAddressId, addressId)
                    .orderByAsc(ServiceAddress::getAddressId)
                    .last("LIMIT 1"));
            if (replacement != null) {
                replacement.setIsDefault(true);
                serviceAddressMapper.updateById(replacement);
            }
        }
        serviceAddressMapper.deleteById(addressId);
        evictAddressHomesAfterCommit(currentUser.userId(), address.getElderId());
        return toAddressResponse(address);
    }

    private AuthService.CurrentUser requireRole(String authorization, RoleCode roleCode) {
        AuthService.CurrentUser currentUser = authService.requireCurrentUser(authorization);
        if (!currentUser.roles().contains(roleCode)) {
            throw new ForbiddenException();
        }
        return currentUser;
    }

    private void requireElderProfileAccess(AuthService.CurrentUser currentUser, String elderId, String... scopeCodes) {
        ElderProfile elder = requireElder(elderId);
        if (isElderSelf(currentUser, elder)) {
            return;
        }
        if (currentUser.roles().contains(RoleCode.FAMILY) && hasAnyActiveScope(currentUser.userId(), elderId, scopeCodes)) {
            return;
        }
        throw new ForbiddenException();
    }

    private boolean isElderSelf(AuthService.CurrentUser currentUser, ElderProfile elder) {
        return currentUser.roles().contains(RoleCode.ELDER) && currentUser.userId().equals(elder.getUserId());
    }

    private void requireActiveBinding(String familyId, String elderId) {
        if (!hasActiveBinding(familyId, elderId)) {
            throw new ForbiddenException();
        }
    }

    private boolean hasActiveBinding(String familyId, String elderId) {
        return bindingMapper.exists(Wrappers.<ElderFamilyBinding>lambdaQuery()
                .eq(ElderFamilyBinding::getFamilyId, familyId)
                .eq(ElderFamilyBinding::getElderId, elderId)
                .eq(ElderFamilyBinding::getBindingStatus, ACTIVE));
    }

    private boolean hasAnyActiveScope(String familyId, String elderId, String... requiredScopeCodes) {
        return bindingMapper.selectList(Wrappers.<ElderFamilyBinding>lambdaQuery()
                        .eq(ElderFamilyBinding::getFamilyId, familyId)
                        .eq(ElderFamilyBinding::getElderId, elderId)
                        .eq(ElderFamilyBinding::getBindingStatus, ACTIVE))
                .stream()
                .map(binding -> readScopes(binding.getScopeCodes()))
                .anyMatch(scopes -> {
                    for (String requiredScopeCode : requiredScopeCodes) {
                        if (scopes.contains(requiredScopeCode)) {
                            return true;
                        }
                    }
                    return false;
                });
    }

    private ElderProfile findElderByInviteCode(String elderInviteCode) {
        ElderProfile elder = elderProfileMapper.selectById(elderInviteCode);
        if (elder != null) {
            return elder;
        }
        elder = elderProfileMapper.selectOne(Wrappers.<ElderProfile>lambdaQuery()
                .eq(ElderProfile::getUserId, elderInviteCode));
        if (elder == null) {
            throw new NotFoundException();
        }
        return elder;
    }

    private ElderProfile requireElder(String elderId) {
        ElderProfile elder = elderProfileMapper.selectById(elderId);
        if (elder == null) {
            throw new NotFoundException();
        }
        return elder;
    }

    private ElderFamilyBinding requireBinding(String bindingId) {
        ElderFamilyBinding binding = bindingMapper.selectById(bindingId);
        if (binding == null) {
            throw new NotFoundException();
        }
        return binding;
    }

    private ElderFamilyBinding requireFamilyBinding(String familyId, String bindingId) {
        ElderFamilyBinding binding = requireBinding(bindingId);
        if (!familyId.equals(binding.getFamilyId())) {
            throw new ForbiddenException();
        }
        return binding;
    }

    private void validateScopes(List<String> scopeCodes) {
        for (String scopeCode : scopeCodes) {
            AuthorizationScope scope = authorizationScopeMapper.selectById(scopeCode);
            if (scope == null || !Boolean.TRUE.equals(scope.getEnabled())) {
                throw new NotFoundException();
            }
        }
    }

    private void upsertPrimaryContact(String elderId, EmergencyContactRequest contactRequest) {
        ElderContact contact = elderContactMapper.selectOne(Wrappers.<ElderContact>lambdaQuery()
                .eq(ElderContact::getElderId, elderId)
                .eq(ElderContact::getIsPrimary, true));
        if (contact == null) {
            contact = new ElderContact();
            contact.setContactId(nextId("contact"));
            contact.setElderId(elderId);
            contact.setIsPrimary(true);
            contact.setContactName(contactRequest.contactName());
            contact.setContactPhone(contactRequest.contactPhone());
            contact.setRelationType(contactRequest.relationType());
            elderContactMapper.insert(contact);
            return;
        }
        contact.setContactName(contactRequest.contactName());
        contact.setContactPhone(contactRequest.contactPhone());
        contact.setRelationType(contactRequest.relationType());
        elderContactMapper.updateById(contact);
    }

    private void saveHealthChangeLog(
            AuthService.CurrentUser currentUser,
            String elderId,
            String beforeValue,
            ElderProfile afterValue) {
        HealthArchiveChangeLog changeLog = new HealthArchiveChangeLog();
        changeLog.setChangeLogId(nextId("healthlog"));
        changeLog.setElderId(elderId);
        changeLog.setChangedBy(currentUser.userId());
        changeLog.setChangeType("PROFILE_UPDATE");
        changeLog.setBeforeValue(beforeValue);
        changeLog.setAfterValue(writeJson(afterValue));
        healthArchiveChangeLogMapper.insert(changeLog);
    }

    private ServiceAddress requireAddress(String addressId) {
        ServiceAddress address = serviceAddressMapper.selectById(addressId);
        if (address == null) {
            throw new NotFoundException();
        }
        return address;
    }

    private void requireAddressOwner(String familyId, ServiceAddress address) {
        if (!familyId.equals(address.getFamilyId())) {
            throw new ForbiddenException();
        }
    }

    private void clearDefaultAddress(String elderId, String familyId) {
        ServiceAddress update = new ServiceAddress();
        update.setIsDefault(false);
        serviceAddressMapper.update(update, Wrappers.<ServiceAddress>lambdaUpdate()
                .eq(ServiceAddress::getElderId, elderId)
                .eq(ServiceAddress::getFamilyId, familyId)
                .eq(ServiceAddress::getIsDefault, true));
    }

    private void ensureDefaultAddress(String elderId, String familyId) {
        boolean hasDefault = serviceAddressMapper.exists(Wrappers.<ServiceAddress>lambdaQuery()
                .eq(ServiceAddress::getElderId, elderId)
                .eq(ServiceAddress::getFamilyId, familyId)
                .eq(ServiceAddress::getIsDefault, true));
        if (hasDefault) {
            return;
        }
        ServiceAddress fallback = serviceAddressMapper.selectOne(Wrappers.<ServiceAddress>lambdaQuery()
                .eq(ServiceAddress::getElderId, elderId)
                .eq(ServiceAddress::getFamilyId, familyId)
                .orderByAsc(ServiceAddress::getAddressId)
                .last("LIMIT 1"));
        if (fallback != null) {
            fallback.setIsDefault(true);
            serviceAddressMapper.updateById(fallback);
        }
    }

    private void applyAddressRequest(ServiceAddress address, ServiceAddressRequest request) {
        address.setContactName(request.contactName());
        address.setContactPhone(request.contactPhone());
        address.setRegionCode(request.regionCode());
        address.setProvinceCode(provinceCode(request.regionCode()));
        address.setCityCode(cityCode(request.regionCode()));
        address.setDetailAddress(request.detailAddress());
        address.setIsDefault(request.isDefault());
    }

    private BindingResponse toBindingResponse(ElderFamilyBinding binding, ElderProfile elder) {
        return new BindingResponse(
                binding.getBindingId(),
                binding.getElderId(),
                elder.getElderName(),
                binding.getRelationType(),
                binding.getBindingStatus(),
                readScopes(binding.getScopeCodes()),
                binding.getPendingScopeCodes() == null ? List.of() : readScopes(binding.getPendingScopeCodes()),
                SCOPE_UPDATE_PENDING.equals(binding.getScopeUpdateStatus())
        );
    }

    private ElderProfileResponse toProfileResponse(ElderProfile elder) {
        ElderContact primaryContact = elderContactMapper.selectOne(Wrappers.<ElderContact>lambdaQuery()
                .eq(ElderContact::getElderId, elder.getElderId())
                .eq(ElderContact::getIsPrimary, true));
        EmergencyContactRequest contact = primaryContact == null
                ? new EmergencyContactRequest(
                        elder.getEmergencyContactName(), elder.getEmergencyContactPhone(), "OTHER")
                : new EmergencyContactRequest(
                        primaryContact.getContactName(), primaryContact.getContactPhone(), primaryContact.getRelationType());
        return new ElderProfileResponse(
                elder.getElderId(),
                profileVersion(elder),
                elder.getElderName(),
                elder.getGender(),
                elder.getBirthDate() == null ? "" : elder.getBirthDate().toString(),
                elder.getCareLevel(),
                List.of(contact));
    }

    private ServiceAddressResponse toAddressResponse(ServiceAddress address) {
        return new ServiceAddressResponse(
                address.getAddressId(),
                address.getRegionCode() + " " + address.getDetailAddress(),
                address.getIsDefault(),
                address.getContactName(),
                address.getContactPhone(),
                address.getRegionCode(),
                address.getDetailAddress()
        );
    }

    private String profileVersion(ElderProfile elder) {
        LocalDateTime updatedAt = elder.getUpdatedAt();
        return elder.getElderId() + ":" + (updatedAt == null ? "0" : updatedAt.toString());
    }

    private List<String> readScopes(String rawJson) {
        try {
            return objectMapper.readValue(rawJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid scope_codes JSON", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("JSON serialization failed", exception);
        }
    }

    private void saveOperationLog(
            AuthService.CurrentUser currentUser,
            String operationType,
            String bizType,
            String bizId,
            Object afterValue) {
        OperationLog log = new OperationLog();
        log.setLogId(nextId("op"));
        log.setOperatorId(currentUser.userId());
        log.setRoleCode(currentUser.roles().isEmpty() ? null : currentUser.roles().get(0).name());
        log.setOperationType(operationType);
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setAfterValue(writeJson(afterValue));
        log.setTraceId("flow-" + UUID.randomUUID().toString().replace("-", ""));
        operationLogMapper.insert(log);
    }

    private void evictBindingHomesAfterCommit(String familyId, ElderProfile elder) {
        homeCacheInvalidator.evictAfterCommit(RoleCode.FAMILY.name(), familyId);
        homeCacheInvalidator.evictAfterCommit(RoleCode.ELDER.name(), elder.getUserId());
    }

    private void evictProfileHomesAfterCommit(ElderProfile elder) {
        homeCacheInvalidator.evictAfterCommit(RoleCode.ELDER.name(), elder.getUserId());
        List<ElderFamilyBinding> bindings = bindingMapper.selectList(
                Wrappers.<ElderFamilyBinding>lambdaQuery()
                        .eq(ElderFamilyBinding::getElderId, elder.getElderId())
                        .eq(ElderFamilyBinding::getBindingStatus, ACTIVE));
        for (ElderFamilyBinding binding : bindings) {
            homeCacheInvalidator.evictAfterCommit(RoleCode.FAMILY.name(), binding.getFamilyId());
        }
    }

    private void evictAddressHomesAfterCommit(String familyId, String elderId) {
        homeCacheInvalidator.evictAfterCommit(RoleCode.FAMILY.name(), familyId);
        ElderProfile elder = elderProfileMapper.selectById(elderId);
        if (elder != null) {
            homeCacheInvalidator.evictAfterCommit(RoleCode.ELDER.name(), elder.getUserId());
        }
    }

    private String provinceCode(String regionCode) {
        if (regionCode.length() >= 2) {
            return regionCode.substring(0, 2) + "0000";
        }
        return regionCode;
    }

    private String cityCode(String regionCode) {
        if (regionCode.length() >= 4) {
            return regionCode.substring(0, 4) + "00";
        }
        return regionCode;
    }

    private String nextId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
