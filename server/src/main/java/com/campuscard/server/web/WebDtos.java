package com.campuscard.server.web;

import com.campuscard.dto.Page;
import com.campuscard.dto.Statistics;
import com.campuscard.model.CampusCard;
import com.campuscard.model.TransactionRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** HTTP request and response records. Money is represented as a decimal string on the wire. */
public final class WebDtos {
    private WebDtos() { }

    public record ErrorJson(String code, String message) { }
    public record CsrfJson(String token, String headerName) { }
    public record StatusJson(String status) { }

    public record LoginRequest(@NotBlank @Size(max = 50) String username,
                               @NotNull @Size(min = 1, max = 128) String password) { }
    public record PasswordRequest(@NotNull @Size(min = 1, max = 128) String oldPassword,
                                  @NotNull @Size(min = 8, max = 128) String newPassword) { }
    public record OperationRequest(@NotBlank @Size(max = 64) String operationId,
                                   @NotBlank @Size(max = 13) @Pattern(regexp = "[0-9]{1,10}(?:\\.[0-9]{1,2})?") String amount,
                                   @Size(max = 255) String description) { }
    public record CreateUserRequest(@NotBlank @Size(min = 3, max = 50) String username,
                                    @NotNull @Size(min = 8, max = 128) String password,
                                    @NotBlank @Size(max = 100) String fullName,
                                    @NotBlank @Size(max = 30) String studentNo) { }
    public record UpdateUserRequest(@NotBlank @Size(max = 100) String fullName,
                                    @NotBlank @Size(max = 30) String studentNo) { }
    public record EnabledRequest(@NotNull Boolean enabled) { }

    public record CardJson(long id, long userId, String cardNo, String balance) {
        public static CardJson from(CampusCard card) {
            return new CardJson(card.id(), card.userId(), card.cardNo(), card.balance().toPlainString());
        }
    }
    public record TransactionJson(long id, long cardId, long userId, String username, String fullName,
                                  String type, String amount, String balanceBefore, String balanceAfter,
                                  String description, long actorId, String operationId, String createdAt) {
        public static TransactionJson from(TransactionRecord r) {
            return new TransactionJson(r.id(), r.cardId(), r.userId(), r.username(), r.fullName(), r.type().name(),
                    r.amount().toPlainString(), r.balanceBefore().toPlainString(), r.balanceAfter().toPlainString(),
                    r.description(), r.actorId(), r.operationId(), r.createdAt().toString());
        }
    }
    public record PageJson<T>(List<T> items, long total, int page, int pageSize) {
        public PageJson { items = List.copyOf(items); }
        public static <A, B> PageJson<B> map(Page<A> page, java.util.function.Function<A, B> mapper) {
            return new PageJson<>(page.items().stream().map(mapper).toList(), page.total(), page.page(), page.pageSize());
        }
    }
    public record StatisticsJson(long users, long enabledUsers, String totalBalance,
                                 String rechargeAmount, String consumptionAmount, long transactionCount) {
        public static StatisticsJson from(Statistics x) {
            return new StatisticsJson(x.users(), x.enabledUsers(), x.totalBalance().toPlainString(),
                    x.rechargeAmount().toPlainString(), x.consumptionAmount().toPlainString(), x.transactionCount());
        }
    }
}
