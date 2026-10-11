package com.campuscard.server.web;

import com.campuscard.api.ApplicationServices;
import com.campuscard.dto.SessionContext;
import com.campuscard.exception.BusinessException;
import com.campuscard.model.TransactionRecord;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api")
public class CardController {
    private final ApplicationServices services;
    private final ObjectMapper mapper;

    public CardController(ApplicationServices services, ObjectMapper mapper) {
        this.services = services;
        this.mapper = mapper;
    }

    @GetMapping("/card")
    public WebDtos.CardJson card(HttpServletRequest request) {
        return WebDtos.CardJson.from(services.cards().getCard(WebSession.coreContext(request)));
    }

    @PostMapping("/card/recharge")
    public WebDtos.TransactionJson recharge(@Valid @RequestBody WebDtos.OperationRequest body, HttpServletRequest request) {
        TransactionRecord r = services.cards().recharge(WebSession.coreContext(request), money(body.amount()),
                body.description(), body.operationId());
        return WebDtos.TransactionJson.from(r);
    }

    @PostMapping("/card/consume")
    public WebDtos.TransactionJson consume(@Valid @RequestBody WebDtos.OperationRequest body, HttpServletRequest request) {
        TransactionRecord r = services.cards().consume(WebSession.coreContext(request), money(body.amount()),
                body.description(), body.operationId());
        return WebDtos.TransactionJson.from(r);
    }

    @GetMapping("/card/operations/{operationId}")
    public ResponseEntity<byte[]> findOperation(@PathVariable String operationId, HttpServletRequest request) {
        TransactionRecord record = services.cards().findOperation(WebSession.coreContext(request), operationId).orElse(null);
        byte[] json = mapper.writeValueAsBytes(record == null ? null : WebDtos.TransactionJson.from(record));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.CACHE_CONTROL, "no-store").body(json);
    }

    private static BigDecimal money(String amount) {
        try { return new BigDecimal(amount); }
        catch (NumberFormatException exception) { throw new BusinessException("VALIDATION", "金额格式无效"); }
    }
}
