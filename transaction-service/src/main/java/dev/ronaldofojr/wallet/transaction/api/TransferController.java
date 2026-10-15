package dev.ronaldofojr.wallet.transaction.api;

import dev.ronaldofojr.wallet.transaction.service.TransferService;
import dev.ronaldofojr.wallet.transaction.service.TransferService.CreationResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService service;

    public TransferController(TransferService service) {
        this.service = service;
    }

    /**
     * 202 quando a transferencia foi aceita (o processamento e assincrono, acompanhe pelo GET).
     * 200 quando a Idempotency-Key ja tinha sido usada com os mesmos dados.
     */
    @PostMapping
    public ResponseEntity<TransferResponse> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request) {
        CreationResult result = service.create(idempotencyKey, request.fromAccountId(),
                request.toAccountId(), request.amount());
        TransferResponse body = TransferResponse.from(result.transfer());
        if (result.created()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .location(URI.create("/transfers/" + body.id()))
                    .body(body);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public TransferResponse get(@PathVariable UUID id) {
        return TransferResponse.from(service.get(id));
    }
}
