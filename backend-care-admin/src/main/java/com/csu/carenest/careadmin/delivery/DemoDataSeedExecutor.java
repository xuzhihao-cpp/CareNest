package com.csu.carenest.careadmin.delivery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

/** 执行成员1维护的幂等演示种子；成员3不复制或改写种子内容。 */
@Component
public class DemoDataSeedExecutor {

    private final DataSource dataSource;
    private final String seedPattern;

    public DemoDataSeedExecutor(
            DataSource dataSource,
            @Value("${carenest.demo-data.seed-pattern:file:./db/seed/*.sql}") String seedPattern) {
        this.dataSource = dataSource;
        this.seedPattern = seedPattern;
    }

    public void reset() {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver().getResources(seedPattern);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to locate demo seed resources", exception);
        }
        Arrays.sort(resources, Comparator.comparing(Resource::getFilename));
        if (resources.length == 0) {
            throw new IllegalStateException("No demo seed resources found for " + seedPattern);
        }
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(resources);
        populator.setContinueOnError(false);
        populator.setIgnoreFailedDrops(false);
        populator.execute(dataSource);
    }
}
