package dev.ronaldofojr.wallet.account.api;

import dev.ronaldofojr.wallet.account.domain.Account;
import dev.ronaldofojr.wallet.account.domain.AccountNotFoundException;
import dev.ronaldofojr.wallet.account.domain.LedgerEntry;
import dev.ronaldofojr.wallet.account.repository.AccountRepository;
import dev.ronaldofojr.wallet.account.repository.LedgerEntryRepository;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;

    public AccountController(AccountRepository accounts, LedgerEntryRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        BigDecimal initial = request.initialBalance() == null ? BigDecimal.ZERO : request.initialBalance();
        Account saved = accounts.save(new Account(request.ownerName(), initial));
        return ResponseEntity.created(URI.create("/accounts/" + saved.getId())).body(AccountResponse.from(saved));
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return accounts.findById(id).map(AccountResponse::from)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    /**
     * Extrato paginado, do mais recente para o mais antigo. size fica entre 1 e 100;
     * valores fora disso (ou negativos) viram o limite mais proximo em vez de erro.
     */
    @GetMapping("/{id}/ledger")
    public LedgerPage ledger(@PathVariable UUID id,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size) {
        if (!accounts.existsById(id)) {
            throw new AccountNotFoundException(id);
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<LedgerEntry> result = ledger.findByAccountId(id,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new LedgerPage(result.map(LedgerEntryResponse::from).getContent(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public record LedgerPage(List<LedgerEntryResponse> content, int page, int size, long totalElements, int totalPages) {
    }

    public record LedgerEntryResponse(UUID transferId, String type, BigDecimal amount, BigDecimal balanceAfter,
                                      java.time.Instant createdAt) {
        static LedgerEntryResponse from(LedgerEntry e) {
            return new LedgerEntryResponse(e.getTransferId(), e.getType().name(), e.getAmount(),
                    e.getBalanceAfter(), e.getCreatedAt());
        }
    }
}
