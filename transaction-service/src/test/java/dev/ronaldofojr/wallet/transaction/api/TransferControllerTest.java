package dev.ronaldofojr.wallet.transaction.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ronaldofojr.wallet.transaction.domain.IdempotencyConflictException;
import dev.ronaldofojr.wallet.transaction.domain.InvalidTransferException;
import dev.ronaldofojr.wallet.transaction.domain.Transfer;
import dev.ronaldofojr.wallet.transaction.domain.TransferNotFoundException;
import dev.ronaldofojr.wallet.transaction.service.TransferService;
import dev.ronaldofojr.wallet.transaction.service.TransferService.CreationResult;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransferController.class)
class TransferControllerTest {

    private final UUID from = UUID.randomUUID();
    private final UUID to = UUID.randomUUID();

    @Autowired
    MockMvc mvc;
    @MockitoBean
    TransferService service;

    private String body(BigDecimal amount) {
        return "{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amount\":%s}".formatted(from, to, amount);
    }

    private Transfer transfer() {
        return new Transfer("k", from, to, new BigDecimal("10.00"));
    }

    @Test
    void newTransferReturns202WithLocation() throws Exception {
        Transfer transfer = transfer();
        when(service.create(eq("k1"), any(), any(), any())).thenReturn(new CreationResult(transfer, true));

        mvc.perform(post("/transfers").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(BigDecimal.TEN)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/transfers/" + transfer.getId()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void replayedKeyReturns200() throws Exception {
        when(service.create(eq("k1"), any(), any(), any())).thenReturn(new CreationResult(transfer(), false));

        mvc.perform(post("/transfers").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(BigDecimal.TEN)))
                .andExpect(status().isOk());
    }

    @Test
    void missingIdempotencyKeyReturns400() throws Exception {
        mvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON).content(body(BigDecimal.TEN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidAmountReturns400() throws Exception {
        mvc.perform(post("/transfers").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(BigDecimal.ZERO)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/transfers").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(new BigDecimal("1.005"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingFieldsReturn400() throws Exception {
        mvc.perform(post("/transfers").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void keyReusedWithDifferentDataReturns409() throws Exception {
        when(service.create(any(), any(), any(), any())).thenThrow(new IdempotencyConflictException("k1"));

        mvc.perform(post("/transfers").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(BigDecimal.TEN)))
                .andExpect(status().isConflict());
    }

    @Test
    void sameAccountReturns422() throws Exception {
        when(service.create(any(), any(), any(), any())).thenThrow(new InvalidTransferException("iguais"));

        mvc.perform(post("/transfers").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(BigDecimal.TEN)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void getReturnsTransferOr404() throws Exception {
        Transfer transfer = transfer();
        when(service.get(transfer.getId())).thenReturn(transfer);
        UUID unknown = UUID.randomUUID();
        when(service.get(unknown)).thenThrow(new TransferNotFoundException(unknown));

        mvc.perform(get("/transfers/" + transfer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(10.0));
        mvc.perform(get("/transfers/" + unknown)).andExpect(status().isNotFound());
    }
}
