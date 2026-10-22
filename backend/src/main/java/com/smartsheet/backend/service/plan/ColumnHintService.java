package com.smartsheet.backend.service.plan;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.smartsheet.backend.model.CsvData;
import com.smartsheet.backend.model.plan.ColumnHint;

// Looks at each column and decides what KIND of data it holds (number, date, email, text),
// plus a few counts. This lets the AI write good plans without seeing any value.
//
// Example: the AI learns "column 'joined' holds dates in DD/MM/YYYY format",
// so it can write a correct FORMAT_DATE step, without ever seeing a single date.
@Service
public class ColumnHintService {

    // A column "looks like" a type if at least this share of its filled cells match
    private static final int TYPE_THRESHOLD_PERCENT = 80;

    private static final Pattern NUMBER = Pattern.compile("[-+]?[0-9][0-9,]*(\\.[0-9]+)?");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern DOUBLE_SPACE = Pattern.compile("\\S[ \\t\\u00A0]{2,}\\S");

    // Order matters when two formats tie: DD/MM first (common in India), MM/DD after it
    private static final List<String> DATE_FORMAT_ORDER =
            List.of("YYYY-MM-DD", "DD/MM/YYYY", "MM/DD/YYYY", "DD-MM-YYYY", "DD.MM.YYYY");

    public List<ColumnHint> hints(CsvData data) {
        List<ColumnHint> hints = new ArrayList<>();
        for (int col = 0; col < data.headers().size(); col++) {
            hints.add(hintFor(col, data));
        }
        return hints;
    }

    private ColumnHint hintFor(int col, CsvData data) {
        int filled = 0;
        int numbers = 0;
        int emails = 0;
        int lower = 0;
        int upper = 0;
        int doubleSpaces = 0;

        // How many cells each date format can read (LinkedHashMap keeps DATE_FORMAT_ORDER)
        Map<String, Integer> dateMatches = new LinkedHashMap<>();
        Map<String, DateTimeFormatter> readers = new LinkedHashMap<>();
        for (String format : DATE_FORMAT_ORDER) {
            dateMatches.put(format, 0);
            readers.put(format, PlanExecutor.dateReader(format));
        }

        for (List<String> row : data.rows()) {
            String cell = col < row.size() ? row.get(col).strip() : "";
            if (cell.isEmpty()) {
                continue;
            }
            filled++;

            if (NUMBER.matcher(cell).matches()) {
                numbers++;
            }
            if (EMAIL.matcher(cell).matches()) {
                emails++;
            }
            if (DOUBLE_SPACE.matcher(cell).find()) {
                doubleSpaces++;
            }
            if (cell.chars().anyMatch(Character::isLetter)) {
                if (cell.equals(cell.toLowerCase(Locale.ROOT))) {
                    lower++;
                } else if (cell.equals(cell.toUpperCase(Locale.ROOT))) {
                    upper++;
                }
            }
            for (String format : DATE_FORMAT_ORDER) {
                if (canRead(cell, readers.get(format))) {
                    dateMatches.merge(format, 1, Integer::sum);
                }
            }
        }

        String name = data.headers().get(col);
        if (filled == 0) {
            return new ColumnHint(col, name, 0, "EMPTY", 0, null, false, 0, 0, 0);
        }

        // The date format that reads the most cells (the first one wins a tie)
        String bestDate = null;
        int bestDateCount = 0;
        for (Map.Entry<String, Integer> entry : dateMatches.entrySet()) {
            if (entry.getValue() > bestDateCount) {
                bestDate = entry.getKey();
                bestDateCount = entry.getValue();
            }
        }

        String looksLike = "TEXT";
        int matchPercent = 100;
        if (percent(bestDateCount, filled) >= TYPE_THRESHOLD_PERCENT) {
            looksLike = "DATE";
            matchPercent = percent(bestDateCount, filled);
        } else if (percent(numbers, filled) >= TYPE_THRESHOLD_PERCENT) {
            looksLike = "NUMBER";
            matchPercent = percent(numbers, filled);
        } else if (percent(emails, filled) >= TYPE_THRESHOLD_PERCENT) {
            looksLike = "EMAIL";
            matchPercent = percent(emails, filled);
        }

        String dateFormat = looksLike.equals("DATE") ? bestDate : null;
        // DD/MM and MM/DD both read every date when no day is above 12 -> we cannot be sure
        boolean unclear = looksLike.equals("DATE")
                && (bestDate.equals("DD/MM/YYYY") || bestDate.equals("MM/DD/YYYY"))
                && dateMatches.get("DD/MM/YYYY").equals(dateMatches.get("MM/DD/YYYY"));

        return new ColumnHint(col, name, filled, looksLike, matchPercent, dateFormat, unclear,
                lower, upper, doubleSpaces);
    }

    private static boolean canRead(String cell, DateTimeFormatter reader) {
        try {
            LocalDate.parse(cell, reader);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static int percent(int part, int whole) {
        return whole == 0 ? 0 : Math.round(100f * part / whole);
    }
}
