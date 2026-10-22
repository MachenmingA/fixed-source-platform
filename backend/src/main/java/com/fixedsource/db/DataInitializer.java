package com.fixedsource.db;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final SchemaService schemaService;
    private final SeedService seedService;

    public DataInitializer(SchemaService schemaService, SeedService seedService) {
        this.schemaService = schemaService;
        this.seedService = seedService;
    }

    @Override
    public void run(String... args) {
        schemaService.initialize();
        seedService.seedIfNeeded();
        seedService.ensureSupplementalData();
    }
}
