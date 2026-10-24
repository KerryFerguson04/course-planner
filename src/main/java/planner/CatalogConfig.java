package planner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import planner.domain.Catalog;

import java.io.IOException;
import java.io.InputStream;

/** Loads the catalog once at startup and shares it as a singleton bean. */
@Configuration
public class CatalogConfig {
    @Bean
    public Catalog catalog(@Value("${planner.catalog}") Resource source) throws IOException {
        try (InputStream in = source.getInputStream()) {
            return Catalog.load(in);
        }
    }
}
