package com.csu.carenest.user.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.csu.carenest.user.auth.AuthService;
import com.csu.carenest.user.auth.RoleCode;
import com.csu.carenest.user.auth.SysUser;
import com.csu.carenest.user.auth.SysUserMapper;
import com.csu.carenest.user.common.ForbiddenException;
import com.csu.carenest.user.flow.ElderFamilyBinding;
import com.csu.carenest.user.flow.ElderFamilyBindingMapper;
import com.csu.carenest.user.flow.ElderProfileMapper;
import com.csu.carenest.user.flow.ServiceAddressMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class StatusService {

    private static final List<String> DEMO_ACCOUNTS = List.of(
            "elder_demo", "family_demo", "nurse_demo", "admin_demo", "cs_demo"
    );
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final DataSource dataSource;
    private final AuthService authService;
    private final SysUserMapper sysUserMapper;
    private final ElderProfileMapper elderProfileMapper;
    private final ElderFamilyBindingMapper elderFamilyBindingMapper;
    private final ServiceAddressMapper serviceAddressMapper;
    private final String version;

    public StatusService(
            DataSource dataSource,
            AuthService authService,
            SysUserMapper sysUserMapper,
            ElderProfileMapper elderProfileMapper,
            ElderFamilyBindingMapper elderFamilyBindingMapper,
            ServiceAddressMapper serviceAddressMapper,
            @Value("${carenest.app.version:0.1.0}") String version) {
        this.dataSource = dataSource;
        this.authService = authService;
        this.sysUserMapper = sysUserMapper;
        this.elderProfileMapper = elderProfileMapper;
        this.elderFamilyBindingMapper = elderFamilyBindingMapper;
        this.serviceAddressMapper = serviceAddressMapper;
        this.version = version;
    }

    public HealthResponse health() {
        boolean dbConnected = false;
        try (Connection connection = dataSource.getConnection()) {
            dbConnected = connection.isValid(2);
        } catch (Exception ignored) {
            dbConnected = false;
        }
        return new HealthResponse(
                "UP",
                "CareNest",
                version,
                dbConnected,
                OffsetDateTime.now(SHANGHAI)
        );
    }

    public DemoDataStatusResponse demoDataStatus(String authorization) {
        AuthService.CurrentUser currentUser = authService.requireCurrentUser(authorization);
        if (!currentUser.roles().contains(RoleCode.ADMIN)) {
            throw new ForbiddenException();
        }

        LambdaQueryWrapper<SysUser> demoAccountQuery = Wrappers.lambdaQuery(SysUser.class)
                .in(SysUser::getUsername, DEMO_ACCOUNTS)
                .eq(SysUser::getAccountStatus, "ENABLED");
        List<String> accounts = sysUserMapper.selectList(demoAccountQuery).stream()
                .map(SysUser::getUsername)
                .sorted(Comparator.comparingInt(DEMO_ACCOUNTS::indexOf))
                .toList();

        List<Boolean> scenarios = new ArrayList<>();
        scenarios.add(accounts.containsAll(DEMO_ACCOUNTS));
        scenarios.add(elderProfileMapper.selectCount(Wrappers.emptyWrapper()) > 0);
        scenarios.add(elderFamilyBindingMapper.selectCount(Wrappers.lambdaQuery(ElderFamilyBinding.class)
                .eq(ElderFamilyBinding::getBindingStatus, "ACTIVE")) > 0);
        scenarios.add(serviceAddressMapper.selectCount(Wrappers.emptyWrapper()) > 0);

        int scenarioCount = (int) scenarios.stream().filter(Boolean::booleanValue).count();
        boolean ready = scenarioCount == scenarios.size();
        return new DemoDataStatusResponse(ready, accounts, scenarioCount);
    }
}
