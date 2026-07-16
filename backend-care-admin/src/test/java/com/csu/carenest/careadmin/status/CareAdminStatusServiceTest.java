package com.csu.carenest.careadmin.status;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证阶段55健康状态与数据库就绪状态保持一致。 */
class CareAdminStatusServiceTest {

    @Test
    void connectedDatabaseIsReady() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:health;DB_CLOSE_DELAY=-1");

        CareAdminHealthResponse response =
                new CareAdminStatusService(dataSource, "test").health();

        assertEquals("UP", response.status());
        assertTrue(response.ready());
        assertTrue(response.dbConnected());
    }

    @Test
    void unavailableDatabaseIsNotReady() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("unavailable"));

        CareAdminHealthResponse response =
                new CareAdminStatusService(dataSource, "test").health();

        assertEquals("DOWN", response.status());
        assertFalse(response.ready());
        assertFalse(response.dbConnected());
    }
}
