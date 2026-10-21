package com.smartsheet.backend.service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.smartsheet.backend.model.CsvData;

// @Service tells Spring: "Create one object of this class and share it
// with any class that needs it (like our controller)."
@Service
public class CsvService {

    // Reads an uploaded CSV file and turns it into headers + rows.
    public CsvData parse(MultipartFile file) throws IOException {

        // "try-with-resources": Java automatically closes the reader and parser
        // when we are done, even if an error happens. This prevents memory leaks.
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
                CSVParser parser = CSVParser.parse(reader, CSVFormat.DEFAULT)) {

            // Read every line of the CSV into a list of records
            List<CSVRecord> records = parser.getRecords();

            if (records.isEmpty()) {
                throw new IllegalArgumentException("The CSV file is empty.");
            }

            // The first line is the header row (column names)
            List<String> headers = new ArrayList<>(records.get(0).toList());

            // Files saved from Excel sometimes start with an invisible character
            // called a BOM (\uFEFF). Remove it so the first column name is clean.
            if (!headers.isEmpty() && headers.get(0).startsWith("\uFEFF")) {
                headers.set(0, headers.get(0).substring(1));
            }

            // Every line after the header is a data row
            List<List<String>> rows = new ArrayList<>();
            for (int i = 1; i < records.size(); i++) {
                rows.add(records.get(i).toList());
            }

            return new CsvData(headers, rows);
        }
    }
}