package com.myfinaimanager.core.financialinstrument.infrastructure.reference.csv;

import com.myfinaimanager.core.financialinstrument.domain.exceptions.ReferenceDataImportException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Generic classpath/file CSV reading for the reference-data ingestion (RFC 4180 via Apache Commons
 * CSV — {@code commons-csv} is used <strong>only</strong> in this package). Returns each data row
 * as an insertion-ordered {@code header -> value} map. A malformed file raises
 * {@link ReferenceDataImportException} (a hard failure — the import run aborts and rolls back).
 */
@Component
public class CsvReferenceFileReader {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .build();

    private final ResourceLoader resourceLoader;

    public CsvReferenceFileReader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public List<Map<String, String>> read(String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new ReferenceDataImportException("reference-data file not found: " + location, null);
        }
        List<Map<String, String>> rows = new ArrayList<>();
        try (InputStream in = resource.getInputStream();
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, FORMAT)) {
            for (CSVRecord record : parser) {
                Map<String, String> row = new LinkedHashMap<>();
                parser.getHeaderNames().forEach(h -> row.put(h, record.isMapped(h) ? record.get(h) : null));
                rows.add(row);
            }
        } catch (IOException | RuntimeException e) {
            throw new ReferenceDataImportException("failed to parse reference-data file: " + location, null, e);
        }
        return rows;
    }
}
