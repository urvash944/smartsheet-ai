package com.smartsheet.backend.model;

import java.util.List;

// A "record" is a short way to create a simple data class in Java.
// It holds:
//   headers -> the column names, e.g. ["name", "email", "city"]
//   rows    -> each row is a list of cell values, e.g. ["Asha", "asha@example.com", "Mumbai"]
//
// Spring automatically turns this into JSON when we send it to the frontend.
public record CsvData(List<String> headers, List<List<String>> rows) {
}