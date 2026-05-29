# Stripe Payment Gateway Service

A merchant payment system built with **Spring Boot** and **Stripe API** for secure online payment processing using Stripe Checkout Session approach.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [API Endpoints](#api-endpoints)
- [Database Schema](#database-schema)
- [Setup and Installation](#setup-and-installation)
- [Configuration](#configuration)
- [Testing](#testing)
- [Webhook Setup](#webhook-setup)
- [Sequence Flow](#sequence-flow)

---

## Overview

This project integrates **Stripe Checkout Session API** with a Spring Boot backend to handle secure online payments. The system supports:

- Creating Stripe Checkout Sessions
- Redirecting users to Stripe's hosted payment page
- Handling asynchronous webhook events from Stripe
- Tracking transaction status in a MySQL database
- Verifying webhook signatures for security

---

## Architecture

```
Client (Browser/Postman)
        │
        ▼
PaymentController  ──►  PaymentService  ──►  Stripe API
        │                     │
        │                     ▼
        │               TransactionRepository
        │                     │
        │                     ▼
        │                  MySQL DB
        │
        ◄── Webhook (async) ── Stripe API
```

### Flow

1. Client sends payment request to Spring Boot backend
2. Backend creates a Stripe Checkout Session
3. Transaction saved as `PENDING` in MySQL
4. Client redirects to Stripe hosted payment page
5. User completes payment on Stripe's page
6. Stripe fires `checkout.session.completed` webhook
7. Backend verifies signature and updates status to `SUCCESS`

---

## Tech Stack

| Technology        | Version  | Purpose                        |
|-------------------|----------|--------------------------------|
| Java              | 21       | Programming language           |
| Spring Boot       | 3.4.5    | Backend framework              |
| Spring Data JPA   | -        | Database ORM                   |
| Stripe Java SDK   | 28.4.0   | Stripe API integration         |
| MySQL             | 8.x      | Production database            |
| Lombok            | -        | Boilerplate reduction          |
| Maven             | -        | Build tool                     |
| Stripe CLI        | 1.40.9   | Local webhook testing          |

---

## Project Structure

```
src/main/java/com/stripeservice/
├── config/
│   └── StripeConfig.java           # Initializes Stripe API key
├── controller/
│   └── PaymentController.java      # REST endpoints
├── dto/
│   └── PaymentRequest.java         # Request body DTO with validation
├── entity/
│   └── Transaction.java            # JPA entity with PaymentStatus enum
├── repository/
│   └── TransactionRepository.java  # JPA repository
├── service/
│   └── PaymentService.java         # Business logic + Stripe SDK calls
└── StripepaymentGatewayServiceApplication.java
```

---

## API Endpoints

### 1. Create Checkout Session

```
POST /api/payment/create-checkout-session
```

**Request Body:**
```json
{
    "productName": "iPhone 15",
    "quantity": 1,
    "amount": 50000,
    "currency": "inr"
}
```

> Note: `amount` is in smallest currency unit. For INR: `50000` = ₹500.00

**Response:**
```json
{
    "sessionId": "cs_test_xxxxxxxxxxxxxxxx",
    "sessionUrl": "https://checkout.stripe.com/c/pay/cs_test_xxx",
    "status": "created"
}
```

---

### 2. Webhook Handler

```
POST /api/payment/webhook
```

Called automatically by Stripe after payment events. Handles:
- `checkout.session.completed` → updates status to `SUCCESS`
- `checkout.session.expired` → updates status to `CANCELLED`

**Headers required:**
```
Stripe-Signature: whsec_xxxxxxxx
```

---

### 3. Check Payment Status

```
GET /api/payment/status/{sessionId}
```

**Response:**
```json
{
    "sessionId": "cs_test_xxxxxxxxxxxxxxxx",
    "status": "SUCCESS",
    "amount": 50000,
    "currency": "inr",
    "productName": "iPhone 15",
    "updatedAt": "2026-05-09T14:25:23"
}
```

---

### 4. Success & Cancel Pages

```
GET /api/payment/success
GET /api/payment/cancel
```

---

## Database Schema

### transactions table

| Column             | Type          | Description                        |
|--------------------|---------------|------------------------------------|
| id                 | BIGINT (PK)   | Auto-generated primary key         |
| session_id         | VARCHAR       | Stripe Checkout Session ID         |
| payment_intent_id  | VARCHAR       | Stripe Payment Intent ID           |
| amount             | BIGINT        | Amount in smallest currency unit   |
| currency           | VARCHAR       | Currency code (e.g. inr, usd)      |
| product_name       | VARCHAR       | Name of the product                |
| status             | ENUM          | PENDING / SUCCESS / FAILED / CANCELLED |
| created_at         | DATETIME      | Transaction creation timestamp     |
| updated_at         | DATETIME      | Last update timestamp              |

---

## Setup and Installation

### Prerequisites

- Java 21
- Maven
- MySQL 8.x
- Stripe account (free)
- Stripe CLI (for local webhook testing)

### Steps

**1. Clone the repository**
```bash
git clone https://github.com/yourusername/stripe-payment-gateway.git
cd stripe-payment-gateway
```

**2. Create MySQL database**
```sql
CREATE DATABASE stripe_payment_db;
```

**3. Configure `application.properties`**

See [Configuration](#configuration) section below.

**4. Build and run**
```bash
mvn clean install
mvn spring-boot:run
```

App starts at: `http://localhost:8080`

---

## Configuration

Create/update `src/main/resources/application.properties`:

```properties
spring.application.name=Stripepayment_gateway_service

# MySQL
spring.datasource.url=jdbc:mysql://localhost:3306/stripe_payment_db
spring.datasource.username=root
spring.datasource.password=YOUR_MYSQL_PASSWORD
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
spring.jpa.show-sql=true

# Stripe
stripe.secret.key=sk_test_YOUR_SECRET_KEY
stripe.webhook.secret=whsec_YOUR_WEBHOOK_SECRET
```

### Get Stripe Keys

1. Go to [dashboard.stripe.com](https://dashboard.stripe.com)
2. Enable **Test Mode**
3. Go to **Developers → API Keys**
4. Copy **Secret key** (`sk_test_...`)

---

## Webhook Setup

### Local Testing with Stripe CLI

**1. Download Stripe CLI**

[github.com/stripe/stripe-cli/releases/latest](https://github.com/stripe/stripe-cli/releases/latest)

**2. Login**
```bash
stripe login
```

**3. Start webhook listener**
```bash
stripe listen --forward-to localhost:8080/api/payment/webhook
```

**4. Copy the webhook secret**
```
> Ready! Your webhook signing secret is whsec_xxxxxxxx
```

Paste this value in `application.properties` as `stripe.webhook.secret`.

> Keep this terminal running while testing — both the app and Stripe CLI must be running simultaneously.

---

## Testing

### Test Cards (Stripe Test Mode)

| Scenario          | Card Number          | Result   |
|-------------------|----------------------|----------|
| Successful payment | 4242 4242 4242 4242 | SUCCESS  |
| Card declined      | 4000 0000 0000 0002 | FAILED   |
| 3D Secure required | 4000 0025 0000 3155 | Requires authentication |

Use any future expiry date (e.g. `12/26`) and any 3-digit CVV (e.g. `123`).

### Test with Postman

**Create a session:**
```
POST http://localhost:8080/api/payment/create-checkout-session
Content-Type: application/json

{
    "productName": "iPhone 15",
    "quantity": 1,
    "amount": 50000,
    "currency": "inr"
}
```

**Check status:**
```
GET http://localhost:8080/api/payment/status/{sessionId}
```

---

## Sequence Flow

```
Client          Controller        Service          DB          Stripe
  │                 │                │              │              │
  │──POST /create──►│                │              │              │
  │                 │──createSession►│              │              │
  │                 │                │──────────────────────────►  │
  │                 │                │◄─────── sessionId + URL ─── │
  │                 │                │──save PENDING──►│           │
  │                 │◄── sessionUrl ─│              │              │
  │◄── sessionUrl ──│                │              │              │
  │                 │                │              │              │
  │──────────── redirect to Stripe hosted page ──────────────────►│
  │                 │                │              │              │
  │                 │◄────────────── webhook (checkout.session.completed) ──│
  │                 │── verify sig ──┤              │              │
  │                 │──processEvent─►│              │              │
  │                 │                │──update SUCCESS──►│         │
  │                 │◄────────────── 200 OK ───────────────────────│
```

---

## Security

- **No card data stored** — Stripe.js handles card tokenization
- **Webhook signature verification** — every webhook verified using `Stripe-Signature` header
- **Test mode** — all development done in Stripe test mode, no real charges
- **Environment-based keys** — API keys stored in `application.properties`, not hardcoded

---

## Author

**Sagar**  
Stripe Payment Gateway Integration — Spring Boot Project
