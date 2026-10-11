package com.campuscard.server.web;

import com.campuscard.api.ApplicationServices;
import com.campuscard.dto.Page;
import com.campuscard.dto.SessionContext;
import com.campuscard.dto.TransactionFilter;
import com.campuscard.model.TransactionRecord;
import com.campuscard.model.TransactionType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final ApplicationServices services;
    public TransactionController(ApplicationServices services) { this.services = services; }

    @GetMapping
    public WebDtos.PageJson<WebDtos.TransactionJson> query(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        SessionContext session = WebSession.coreContext(request);
        Page<TransactionRecord> result = services.records().query(session,
                new TransactionFilter(userId, type, from, to, page, pageSize));
        return WebDtos.PageJson.map(result, WebDtos.TransactionJson::from);
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        String csv = services.records().exportCsv(WebSession.coreContext(request),
                new TransactionFilter(userId, type, from, to, page, pageSize));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transactions.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
