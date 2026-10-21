package com.smartsheet.backend.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.smartsheet.backend.model.CsvData;

// Reads an uploaded CSV file and turns it into headers + rows.
@Service
public class CsvService {

    // Very wide files are rare and would make the AI prompt huge
    private static final int MAX_COLUMNS = 200;

    // Older Excel versions on Windows save CSV files in this encoding
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    public CsvData parse(MultipartFile file) throws IOException {
        // 1. Turn the file's bytes into text, using the right encoding
        String text = decode(file.getBytes());

        // 2. Read the text as CSV
        try (CSVParser parser = CSVParser.parse(text, CSVFormat.DEFAULT)) {
            List<CSVRecord> records = parser.getRecords();

            if (records.isEmpty()) {
                throw new IllegalArgumentException("The CSV file is empty.");
            }

            List<String> headers = new ArrayList<>(records.get(0).toList());

            // Remove the invisible BOM character that Excel (and our download) adds
            if (!headers.isEmpty() && headers.get(0).startsWith("\uFEFF")) {
                headers.set(0, headers.get(0).substring(1));
            }

            if (headers.size() > MAX_COLUMNS) {
                throw new IllegalArgumentException(
                        "This file has " + headers.size() + " columns. The maximum is " + MAX_COLUMNS + ".");
            }

            List<List<String>> rows = new ArrayList<>();
            for (int i = 1; i < records.size(); i++) {
                rows.add(records.get(i).toList());
            }

            return new CsvData(headers, rows);
        }
    }

    // Tries strict UTF-8 first. "REPORT" means: throw an error on invalid bytes
    // instead of silently replacing them with "�".
    // If the bytes are not valid UTF-8, the file is almost certainly Windows-1252.
    private String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, WINDOWS_1252);
        }
    }
}