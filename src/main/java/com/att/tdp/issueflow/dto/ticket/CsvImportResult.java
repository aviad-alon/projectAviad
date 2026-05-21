package com.att.tdp.issueflow.dto.ticket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CsvImportResult {

    private int created;
    private int failed;

    @Builder.Default
    private List<String> errors = new ArrayList<>();
}
