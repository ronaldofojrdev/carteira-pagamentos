package dev.ronaldofojr.wallet.account.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ronaldofojr.wallet.account.IntegrationTestBase;
import dev.ronaldofojr.wallet.account.domain.Account;
import dev.ronaldofojr.wallet.account.domain.LedgerEntry;
import dev.ronaldofojr.wallet.account.repository.AccountRepository;
import dev.ronaldofojr.wallet.account.repository.LedgerEntryRepository;
import dev.ronaldofojr.wallet.contracts.CommandType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class LedgerPaginationIT extends IntegrationTestBase {

    @Autowired
    MockMvc mvc;
    @Autowired
    AccountRepository accounts;
    @Autowired
    LedgerEntryRepository ledger;

    private UUID accountId;

    @BeforeEach
    void createAccountWith25Entries() throws InterruptedException {
        Account account = accounts.save(new Account("Extrato", new BigDecimal("0.00")));
        accountId = account.getId();
        for (int i = 1; i <= 25; i++) {
            // valor = posicao, para saber qual entrada veio em cada pagina
            ledger.save(new LedgerEntry(accountId, UUID.randomUUID(), CommandType.CREDIT, new BigDecimal(i), new BigDecimal(i)));
            Thread.sleep(2); // garante created_at diferente entre as entradas
        }
    }

    @Test
    void firstPageHasTheDefaultSizeAndTheNewestEntriesFirst() throws Exception {
        mvc.perform(get("/accounts/" + accountId + "/ledger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.content[0].amount").value(25))
                .andExpect(jsonPath("$.content[19].amount").value(6))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void secondPageHasTheRemainingOldestEntries() throws Exception {
        mvc.perform(get("/accounts/" + accountId + "/ledger?page=1"))
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.content[0].amount").value(5))
                .andExpect(jsonPath("$.content[4].amount").value(1));
    }

    @Test
    void customPageSizeIsRespected() throws Exception {
        mvc.perform(get("/accounts/" + accountId + "/ledger?size=10&page=2"))
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void pageBeyondTheLastIsEmptyButKeepsTheTotals() throws Exception {
        mvc.perform(get("/accounts/" + accountId + "/ledger?page=99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(25));
    }

    @Test
    void sizeAndPageOutOfRangeFallBackToTheNearestLimit() throws Exception {
        mvc.perform(get("/accounts/" + accountId + "/ledger?size=100000"))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content.length()").value(25));
        mvc.perform(get("/accounts/" + accountId + "/ledger?size=0&page=-4"))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void unknownAccountReturns404AndAnEmptyLedgerIsNotAnError() throws Exception {
        mvc.perform(get("/accounts/" + UUID.randomUUID() + "/ledger")).andExpect(status().isNotFound());

        UUID empty = accounts.save(new Account("Vazia", new BigDecimal("0.00"))).getId();
        mvc.perform(get("/accounts/" + empty + "/ledger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }
}
