# Risk Analysis & Edge Case Documentation

## Online Shopping Platform — Comprehensive Risk Register

---

## 1. Scalability Risks

| ID | Risk | Severity | Likelihood | Impact | Mitigation |
|----|------|----------|-----------|--------|------------|
| SC-001 | Database connection pool exhaustion at 10K concurrent users | HIGH | MEDIUM | Service unavailability | HikariCP pool sizing (max=20/service), read replicas, connection timeouts |
| SC-002 | Cart service hotspot during flash sales | HIGH | HIGH | Cart data loss, timeouts | Redis Cluster (6+ nodes), rate limiting (100 req/s per user), circuit breaker |
| SC-003 | Product search query degradation with large catalog | MEDIUM | HIGH | Slow page loads (>3s) | PostgreSQL full-text search indexes, Elasticsearch for catalog >100K products |
| SC-004 | API Gateway becomes single point of failure | HIGH | LOW | Complete system outage | Multiple gateway instances behind ALB, active health checks, failover routing |
| SC-005 | Order service write amplification during peak | HIGH | MEDIUM | Order creation failures | Write-ahead logging, async order processing via message queue (future) |
| SC-006 | Redis memory exhaustion from unbounded carts | MEDIUM | LOW | Cart service crash | TTL enforcement (7 days), max items per cart (50), memory alerts at 80% |

---

## 2. Security Risks

| ID | Risk | Severity | Likelihood | Impact | Mitigation |
|----|------|----------|-----------|--------|------------|
| SE-001 | JWT token theft via XSS | CRITICAL | MEDIUM | Account takeover | HttpOnly cookies, CSP headers, 15-min access token TTL, refresh token rotation |
| SE-002 | SQL injection in product search | HIGH | LOW | Data exfiltration | Parameterized queries (JPA), WAF rules, input length limits (200 chars) |
| SE-003 | Payment data exposure in transit | CRITICAL | LOW | PCI-DSS violation, fines | TLS 1.3 everywhere, never store raw card data, tokenize via Stripe/PayPal |
| SE-004 | IDOR in order endpoints | HIGH | MEDIUM | Access to other users' orders | Server-side userId extraction from JWT, not from request params |
| SE-005 | Rate limiting bypass via distributed IPs | MEDIUM | MEDIUM | DDoS, resource exhaustion | IP + User-Agent fingerprinting, CAPTCHA for suspects, Cloudflare integration |
| SE-006 | Deserialization attacks on Redis cart data | MEDIUM | LOW | Remote code execution | Use JSON serialization (not Java serialization), content type validation |
| SE-007 | CSRF on cart/order mutations | HIGH | MEDIUM | Unauthorized orders | CSRF tokens for state-changing operations, SameSite cookie attribute |

---

## 3. AI Hallucination Risks

| ID | Risk | Severity | Likelihood | Impact | Mitigation |
|----|------|----------|-----------|--------|------------|
| AI-001 | Generated code references non-existent APIs | MEDIUM | HIGH | Runtime failures, 404 errors | Contract-first development with OpenAPI validation, integration tests |
| AI-002 | Incorrect business logic in price calculations | HIGH | MEDIUM | Financial loss | No AI in payment path; deterministic calculations with 95%+ unit test coverage |
| AI-003 | AI generates insecure patterns (hardcoded secrets, weak crypto) | HIGH | MEDIUM | Security vulnerabilities | Automated security scanning (SAST), code review checklist, secret scanning |
| AI-004 | Generated tests pass but don't validate actual requirements | MEDIUM | HIGH | False confidence in coverage | Mutation testing, manual test review, property-based testing for critical flows |
| AI-005 | AI generates deprecated library usage | LOW | HIGH | Tech debt, security patches missed | Dependency scanning (Dependabot), version pinning, quarterly audit |
| AI-006 | Inconsistent error handling across AI-generated services | MEDIUM | HIGH | Poor user experience, data leaks | Shared error response schema (ErrorResponse), centralized exception handlers |

---

## 4. Data Integrity Risks

| ID | Risk | Severity | Likelihood | Impact | Mitigation |
|----|------|----------|-----------|--------|------------|
| DI-001 | Distributed transaction failure (order + payment + inventory) | HIGH | MEDIUM | Paid but unfulfilled orders | Saga pattern with compensating transactions, idempotency keys |
| DI-002 | Race condition: two users add last item to cart | HIGH | MEDIUM | Overselling | Optimistic locking on inventory, stock reservation on checkout |
| DI-003 | Cart-to-order price mismatch (price changed between cart add and checkout) | HIGH | LOW | Revenue loss or overcharge | Re-validate prices at checkout, price snapshot with timestamp |
| DI-004 | Orphaned payments without corresponding orders | MEDIUM | LOW | Accounting discrepancies | Payment-to-order reconciliation job (hourly), dead letter queue monitoring |

---

## 5. Frontend — UI Risk Analysis

### 5.1 Race Conditions

| ID | Scenario | Severity | Mitigation |
|----|----------|----------|------------|
| UI-RC-001 | User clicks "Add to Cart" rapidly — multiple items added | MEDIUM | Debounce button clicks (300ms), optimistic UI with queue |
| UI-RC-002 | Product listing fetch + filter change creates stale data | HIGH | AbortController to cancel previous fetch, request ID tracking |
| UI-RC-003 | Checkout submitted twice (double click) | CRITICAL | Disable button on submit, idempotency key in request |
| UI-RC-004 | Multiple tabs open with same cart — data divergence | MEDIUM | localStorage event listener for cross-tab sync, or server-side cart |
| UI-RC-005 | Slow network: user sees stale prices from cached response | MEDIUM | Cache-Control headers, stale-while-revalidate strategy |

### 5.2 State Bugs

| ID | Scenario | Severity | Mitigation |
|----|----------|----------|------------|
| UI-SB-001 | Direct state mutation in cart reducer | HIGH | useReducer (immutable updates), ESLint no-mutation rules |
| UI-SB-002 | Cart state inconsistent after failed API call | HIGH | Optimistic update with rollback on error, error boundary |
| UI-SB-003 | Form state lost on navigation between pages | MEDIUM | State lifted to App level or persisted to sessionStorage |
| UI-SB-004 | Quantity showing NaN after rapid increment/decrement | LOW | Input validation, parseInt with fallback, min/max bounds |
| UI-SB-005 | Checkout form submits with stale cart (items removed in another tab) | HIGH | Re-fetch cart on checkout mount, validate before submission |

### 5.3 API Mismatch Risks

| ID | Scenario | Severity | Mitigation |
|----|----------|----------|------------|
| UI-AM-001 | Frontend expects `content` array but API returns flat array | HIGH | Defensive response parsing: `data.content \|\| data` |
| UI-AM-002 | API field name change (e.g., `imageUrl` → `image_url`) | HIGH | Contract-first development, OpenAPI code generation, API versioning |
| UI-AM-003 | Backend returns 500 but frontend shows blank page | MEDIUM | Error boundaries, fallback UI, retry button |
| UI-AM-004 | CORS misconfiguration blocks frontend fetch | CRITICAL | Backend CORS config for frontend origin, preflight cache (3600s) |
| UI-AM-005 | API pagination format change breaks infinite scroll | MEDIUM | Abstraction layer for API calls, response normalization |

---

## 6. Edge Case Documentation

### 6.1 Cart Service Edge Cases

| ID | Scenario | Expected Behavior | Test Reference |
|----|----------|-------------------|----------------|
| EC-CART-001 | Add item when product is out of stock | Return 409 Conflict with "Insufficient stock" message | TC-CART-007 |
| EC-CART-002 | Add 0 quantity to cart | Return 400 Bad Request — validation fails (min=1) | N/A (validation layer) |
| EC-CART-003 | Add same product twice | Increment existing quantity, don't duplicate line item | TC-CART-004 |
| EC-CART-004 | Remove item not in cart | Return 404 "Item not in cart" | TC-CART-005 |
| EC-CART-005 | Apply expired coupon | Return 400 "Invalid or expired coupon" | TC-CART-013 |
| EC-CART-006 | Cart TTL expires (7 days) | Redis key auto-deleted, next GET creates fresh cart | N/A (Redis TTL) |
| EC-CART-007 | Product deleted after being added to cart | Checkout validates stock — fails with "Product not found" | N/A |
| EC-CART-008 | Cart contains 50+ items (max limit) | Return 400 "Cart item limit reached" | N/A |
| EC-CART-009 | Concurrent add from two sessions | Last write wins (Redis), both operations succeed | TC-CART-015 |
| EC-CART-010 | Price changed between add-to-cart and checkout | Re-fetch prices at checkout, show delta to user | N/A |

### 6.2 Payment Service Edge Cases

| ID | Scenario | Expected Behavior | Test Reference |
|----|----------|-------------------|----------------|
| EC-PAY-001 | Payment gateway times out (30s) | Return TIMEOUT status, mark order PAYMENT_PENDING, allow retry | TC-PAY-005 |
| EC-PAY-002 | Duplicate payment for same order | Return DUPLICATE with original transaction ID (idempotency) | TC-PAY-008 |
| EC-PAY-003 | Payment succeeds but callback fails | Webhook retry (3x), manual reconciliation queue | N/A |
| EC-PAY-004 | User pays $0.00 order (100% coupon) | Skip payment gateway, mark order CONFIRMED directly | TC-PAY-003 |
| EC-PAY-005 | Refund requested after 30 days | Business rule: deny refund, return 400 "Refund window expired" | N/A |
| EC-PAY-006 | Partial refund on multi-item order | Calculate per-item refund, update order status to PARTIAL_REFUND | TC-PAY-014 |
| EC-PAY-007 | Payment in unsupported currency | Return 400 "Unsupported currency" (only USD, EUR, INR) | N/A |
| EC-PAY-008 | Concurrent refund requests for same order | Distributed lock on order ID, second request gets 409 | N/A |
| EC-PAY-009 | Card declined after 3 retries | Mark order PAYMENT_FAILED, notify user, hold inventory 30 min | TC-PAY-007 |
| EC-PAY-010 | Payment completes but inventory is 0 | Auto-refund, cancel order, emit OUT_OF_STOCK event | TC-PAY-010 |

### 6.3 Order Service Edge Cases

| ID | Scenario | Expected Behavior | Test Reference |
|----|----------|-------------------|----------------|
| EC-ORD-001 | Create order from empty cart | Return 400 "Cannot create order from empty cart" | N/A |
| EC-ORD-002 | Cancel order after shipment | Return 400 "Cannot cancel shipped order" | N/A |
| EC-ORD-003 | Update status with invalid transition (e.g., DELIVERED → PENDING) | Return 400 "Invalid status transition" | N/A |
| EC-ORD-004 | User tries to access another user's order | Return 403 Forbidden (userId mismatch) | N/A |
| EC-ORD-005 | Order with 100+ line items | Process normally but with pagination in response | N/A |
| EC-ORD-006 | Order total exceeds payment gateway limit ($999,999.99) | Split into multiple payment charges or reject | N/A |

---

## 7. Test Coverage Strategy

### 7.1 Coverage Targets

| Service | Unit | Integration | E2E | Contract |
|---------|------|-------------|-----|----------|
| product-service | 90% | 80% | 60% | 100% (OpenAPI) |
| cart-service | 85% | 75% | 60% | 100% (OpenAPI) |
| order-service | 90% | 85% | 70% | 100% (OpenAPI) |
| payment-service | 95% | 90% | 80% | 100% (OpenAPI) |
| user-service | 85% | 75% | 65% | 100% (OpenAPI) |

### 7.2 Testing Pyramid

```
                    ┌─────────────┐
                    │    E2E      │  — Cypress / Selenium
                    │  (15-20%)   │  — Critical happy paths
                    ├─────────────┤
                    │ Integration │  — Spring Boot Test
                    │  (25-30%)   │  — REST API + DB tests
                    ├─────────────┤
                    │    Unit     │  — JUnit 5 + Mockito
                    │  (50-60%)   │  — Service + model tests
                    └─────────────┘
```

### 7.3 Regression Checklist

- [ ] All products load on product listing page
- [ ] Product search filters correctly
- [ ] Category filter shows only matching products
- [ ] Add to cart works for in-stock items
- [ ] Add to cart shows error for out-of-stock items
- [ ] Cart quantity increment/decrement works
- [ ] Cart item removal works
- [ ] Coupon codes SAVE10, SAVE20, FLAT50 apply correctly
- [ ] Invalid coupon shows error message
- [ ] Cart total calculates correctly with discount
- [ ] Checkout form validation works for all required fields
- [ ] Payment method selection works
- [ ] Order placement succeeds with valid data
- [ ] Order confirmation shows order ID
- [ ] Cart clears after successful order
- [ ] Navigation between pages preserves cart state
- [ ] Loading skeletons show during data fetch
- [ ] Error state shows on API failure with retry button
- [ ] Empty cart state shows with CTA to browse products
- [ ] ARIA labels present on all interactive elements
- [ ] Keyboard navigation works across all pages
- [ ] Screen reader announces toast notifications
- [ ] Responsive layout works on mobile (320px - 768px)
- [ ] No console errors or warnings in production build

### 7.4 Retry Logic for Failed Test Generation

```
Test Generation Retry Strategy:
─────────────────────────────
1. First attempt: Generate tests with temperature=0.2
2. If validation fails (syntax error, missing assertions):
   → Retry with temperature=0.1 (more deterministic)
   → Include error message as context in prompt
3. If still failing after 2 attempts:
   → Regenerate with explicit examples
   → Lower max_tokens to reduce hallucination risk
4. After 3 failures:
   → Flag for human review
   → Generate test skeleton with TODOs
   → Log failure reason for pattern analysis

Max retries: 3
Backoff: Fixed (no delay needed for generation)
Success criteria: Tests compile + pass lint + have ≥1 assertion per test
```

---

## 8. Schema Validation Failure Simulation

### 8.1 Product Creation — Invalid SKU

**Request:**
```json
POST /api/v1/products
{
  "name": "Test Product",
  "price": 29.99,
  "category": "Electronics",
  "stock": 10,
  "sku": "invalid-sku"
}
```

**Expected Response (400):**
```json
{
  "timestamp": "2026-04-12T16:00:00",
  "status": 400,
  "error": "Validation Failed",
  "message": "Request validation failed. See fieldErrors for details.",
  "path": "/api/v1/products",
  "traceId": "abc-123-def",
  "fieldErrors": [
    {
      "field": "sku",
      "message": "SKU must match pattern: XX-000000 (e.g., ELEC-001234)",
      "rejectedValue": "invalid-sku"
    }
  ]
}
```

### 8.2 Cart Add — Missing Required Fields

**Request:**
```json
POST /api/v1/cart/add
{}
```

**Expected Response (400):**
```json
{
  "timestamp": "2026-04-12T16:00:00",
  "status": 400,
  "error": "Validation Failed",
  "message": "Request validation failed. See fieldErrors for details.",
  "path": "/api/v1/cart/add",
  "traceId": "abc-124-def",
  "fieldErrors": [
    { "field": "productId", "message": "Product ID is required", "rejectedValue": "null" },
    { "field": "quantity", "message": "Quantity is required", "rejectedValue": "null" }
  ]
}
```

### 8.3 Retry vs Re-prompt Logic

```
Decision Matrix: When to Retry vs Re-prompt
════════════════════════════════════════════

RETRY (automatic, same request):
  ✓ HTTP 429 (Rate Limited) → Retry after Retry-After header
  ✓ HTTP 500 (Server Error) → Retry 3x with exponential backoff
  ✓ HTTP 503 (Service Unavailable) → Retry 3x with 1s, 2s, 4s delays
  ✓ Connection timeout → Retry 3x with exponential backoff
  ✓ Network error → Retry 3x

RE-PROMPT (user action required):
  ✗ HTTP 400 (Validation Error) → Show field errors, user corrects input
  ✗ HTTP 401 (Unauthorized) → Redirect to login
  ✗ HTTP 403 (Forbidden) → Show access denied message
  ✗ HTTP 404 (Not Found) → Show "product not found" message
  ✗ HTTP 409 (Conflict) → Show "item out of stock" or "duplicate"
  ✗ HTTP 422 (Unprocessable) → Show business logic error to user

NEVER RETRY:
  ✗ Payment failures (idempotency risk)
  ✗ Order cancellation failures (state already changed)
  ✗ User data deletion (irreversible)
```
