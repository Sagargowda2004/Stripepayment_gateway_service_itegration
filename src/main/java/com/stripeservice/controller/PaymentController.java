package com.stripeservice.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripeservice.dto.PaymentRequest;
import com.stripeservice.entity.Transaction;
import com.stripeservice.repository.TransactionRepository;
import com.stripeservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final TransactionRepository transactionRepository;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    // ── 1. Create checkout session ─────────────────────────────────────
    @PostMapping("/create-checkout-session")
    public ResponseEntity<Map<String, Object>> createCheckoutSession(
            @Valid @RequestBody PaymentRequest request) {

        Map<String, Object> response = new HashMap<>();
        try {
            response = paymentService.createCheckoutSession(request);
            return ResponseEntity.ok(response);
        } catch (StripeException e) {
            response.put("error", e.getMessage());
            response.put("status", "failed");
            return ResponseEntity.status(500).body(response);
        }
    }

    // ── 2. Webhook — Stripe calls this after payment ────────────────────────
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;

        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.status(400).body("Invalid signature");
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Webhook error: " + e.getMessage());
        }

        switch (event.getType()) {

            case "checkout.session.completed" -> {
                //  Fixed for Stripe SDK 28.x
                Session session = (Session) event.getData().getObject();

                transactionRepository.findBySessionId(session.getId())
                        .ifPresent(tx -> {
                            tx.setStatus(Transaction.PaymentStatus.SUCCESS);
                            tx.setPaymentIntentId(session.getPaymentIntent());
                            tx.setUpdatedAt(LocalDateTime.now());
                            transactionRepository.save(tx);
                        });
            }

            case "checkout.session.expired" -> {
                //  Fixed for Stripe SDK 28.x
                Session session = (Session) event.getData().getObject();

                transactionRepository.findBySessionId(session.getId())
                        .ifPresent(tx -> {
                            tx.setStatus(Transaction.PaymentStatus.CANCELLED);
                            tx.setUpdatedAt(LocalDateTime.now());
                            transactionRepository.save(tx);
                        });
            }

            default -> System.out.println("Unhandled event type: " + event.getType());
        }

        return ResponseEntity.ok("Webhook received");
    }

    // ── 3. Check payment status ─────────────────────────────────────────────
    @GetMapping("/status/{sessionId}")
    public ResponseEntity<Map<String, Object>> getPaymentStatus(
            @PathVariable String sessionId) {

        Map<String, Object> response = new HashMap<>();

        transactionRepository.findBySessionId(sessionId)
                .ifPresentOrElse(
                        tx -> {
                            response.put("sessionId", tx.getSessionId());
                            response.put("status", tx.getStatus());
                            response.put("amount", tx.getAmount());
                            response.put("currency", tx.getCurrency());
                            response.put("productName", tx.getProductName());
                            response.put("updatedAt", tx.getUpdatedAt());
                        },
                        () -> response.put("error", "Transaction not found")
                );

        return ResponseEntity.ok(response);
    }

    // ── 4. Success and Cancel pages ───────────────────────────────────────
    @GetMapping("/success")
    public ResponseEntity<String> success() {
        return ResponseEntity.ok("Payment Successful! Thank you for your purchase.");
    }

    @GetMapping("/cancel")
    public ResponseEntity<String> cancel() {
        return ResponseEntity.ok("Payment Cancelled.");
    }
}