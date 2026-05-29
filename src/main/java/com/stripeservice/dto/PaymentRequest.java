package com.stripeservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PaymentRequest {

    @NotBlank(message = "Product name is required")
    private String productName;

    @Min(value = 1, message = "Quantity must be at least 1")
    private long quantity;

    @Min(value = 50, message = "Amount must be at least 50 cents")
    private long amount;      // in cents — e.g. 1000 = ₹10.00

    private String currency;  // e.g. "inr" or "usd"

}