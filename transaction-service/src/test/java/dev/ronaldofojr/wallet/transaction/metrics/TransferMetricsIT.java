package dev.ronaldofojr.wallet.transaction.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ronaldofojr.wallet.contracts.AccountReply;
import dev.ronaldofojr.wallet.contracts.CommandType;
import dev.ronaldofojr.wallet.transaction.IntegrationTestBase;
import dev.ronaldofojr.wallet.transaction.service.TransferService;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** O registro de metricas e compartilhado entre os testes, entao comparamos a diferenca (antes e depois). */
@AutoConfigureMockMvc
class TransferMetricsIT extends IntegrationTestBase {

    @Autowired
    TransferService service;
    @Autowired
    MeterRegistry registry;
    @Autowired
    MockMvc mvc;

    private final UUID from = UUID.randomUUID();
    private final UUID to = UUID.randomUUID();

    private double created() {
        return registry.counter("wallet.transfers.created").count();
    }

    private double finished(String status) {
        return registry.counter("wallet.transfers.finished", "status", status).count();
    }

    private double inProgress() {
        return registry.get("wallet.transfers.in.progress").gauge().value();
    }

    private UUID newTransfer(String key) {
        return service.create(key, from, to, new BigDecimal("10.00")).transfer().getId();
    }

    @Test
    void createdCounterGoesUpOncePerNewTransferAndNotOnIdempotentReplays() {
        double before = created();

        newTransfer("m-1");
        newTransfer("m-1");
        newTransfer("m-1");
        newTransfer("m-2");

        assertThat(created() - before).isEqualTo(2.0);
    }

    @Test
    void completedTransferIsCountedOnceEvenIfTheReplyIsDuplicated() {
        double before = finished("COMPLETED");
        UUID id = newTransfer("m-3");

        service.handleReply(AccountReply.ok(id, CommandType.DEBIT));
        service.handleReply(AccountReply.ok(id, CommandType.CREDIT));
        service.handleReply(AccountReply.ok(id, CommandType.CREDIT));

        assertThat(finished("COMPLETED") - before).isEqualTo(1.0);
    }

    @Test
    void failedAndRefundedTransfersAreCountedUnderTheirOwnStatus() {
        double failedBefore = finished("FAILED");
        double refundedBefore = finished("REFUNDED");
        double completedBefore = finished("COMPLETED");

        UUID refused = newTransfer("m-4");
        service.handleReply(AccountReply.failed(refused, CommandType.DEBIT, "Saldo insuficiente"));

        UUID refunded = newTransfer("m-5");
        service.handleReply(AccountReply.ok(refunded, CommandType.DEBIT));
        service.handleReply(AccountReply.failed(refunded, CommandType.CREDIT, "conta destino nao existe"));
        service.handleReply(AccountReply.ok(refunded, CommandType.REFUND));

        assertThat(finished("FAILED") - failedBefore).isEqualTo(1.0);
        assertThat(finished("REFUNDED") - refundedBefore).isEqualTo(1.0);
        assertThat(finished("COMPLETED") - completedBefore).isZero();
    }

    @Test
    void intermediateStatesAreNotCountedAsFinished() {
        double before = finished("PENDING") + finished("DEBITED") + finished("COMPENSATING");
        UUID id = newTransfer("m-6");

        service.handleReply(AccountReply.ok(id, CommandType.DEBIT));
        service.handleReply(AccountReply.failed(id, CommandType.CREDIT, "erro"));

        assertThat(finished("PENDING") + finished("DEBITED") + finished("COMPENSATING")).isEqualTo(before);
    }

    @Test
    void inProgressGaugeFollowsTheTransfersThatHaveNotFinishedYet() {
        assertThat(inProgress()).isZero(); // o cleanState do teste apaga tudo antes

        UUID one = newTransfer("m-7");
        newTransfer("m-8");
        assertThat(inProgress()).isEqualTo(2.0);

        service.handleReply(AccountReply.failed(one, CommandType.DEBIT, "Saldo insuficiente"));
        assertThat(inProgress()).isEqualTo(1.0);
    }

    @Test
    void metricsAreExposedByTheActuatorEndpoint() throws Exception {
        newTransfer("m-9");

        mvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").value(org.hamcrest.Matchers.hasItems(
                        "wallet.transfers.created", "wallet.transfers.in.progress")));
        mvc.perform(get("/actuator/metrics/wallet.transfers.created"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].statistic").value("COUNT"));
    }
}
