package com.csu.carenest.careadmin.delivery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

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
        Resource[] resources = locateSeedResources();
        Arrays.sort(resources, Comparator.comparing(Resource::getFilename));
        if (resources.length == 0) {
            throw new IllegalStateException("No demo seed resources found for " + seedPattern);
        }
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(resources);
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.setContinueOnError(false);
        populator.setIgnoreFailedDrops(false);
        populator.execute(dataSource);
    }

    private Resource[] locateSeedResources() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Set<String> patterns = new LinkedHashSet<>();
        patterns.add(seedPattern);
        patterns.add("file:../db/seed/*.sql");
        patterns.add("file:../../db/seed/*.sql");
        for (String pattern : patterns) {
            try {
                Resource[] resources = resolver.getResources(pattern);
                if (resources.length > 0) {
                    return resources;
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to locate demo seed resources", exception);
            }
        }
        return new Resource[0];
    }
}
