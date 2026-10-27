# Requirements Traceability

The original problem statement is not present in the workspace. The rows below map the requirements explicitly provided for this milestone to the verified implementation.

| Requirement | Implementation | Tests |
|---|---|---|
| DTOs at API boundaries | Layer-first `dto.request` and `dto.response` records | `OrderServiceTest`, `InventorySnapshotServiceTest` |
| Positive order quantities | `CreateOrderRequest @Min(1)` and `OrderService` guard | `OrderServiceTest`; API validation uses existing exception handler |
| Unknown products return not found | `ProductRepository.findByIdForUpdate` and `ResourceNotFoundException` | `OrderServiceTest`; snapshot service test |
| Insufficient stock is rejected | `Product.decreaseStock` and `InsufficientStockException` | `OrderServiceTest` |
| Consistent inventory update | One `@Transactional` `OrderService.process` decrements product, saves order, and saves snapshot | `OrderServiceTest`; persistence transaction is unverified until JDK is available |
| Inventory snapshot semantics | Immutable post-order snapshot: product, resulting stock, `ORDER_PROCESSED`, idempotency reference, timestamp | `OrderServiceTest`, `InventorySnapshotServiceTest` |
| Duplicate processing prevention | Unique idempotency key, post-lock recheck, and same-request replay behavior | `OrderServiceTest` replay and post-lock recheck cases |
| Stable replay response | `orders.remaining_stock` stores the stock resulting from the original order | `OrderServiceTest.replayReturnsOriginalRemainingStockAfterProductChanges` |
| Product lifecycle transitions | Existing `Product.updateStock`; order decrement reaches `OUT_OF_STOCK` at zero | Existing `ProductServiceTest`; order success test covers remaining stock |
| Versioned persistence | Flyway `V2__create_orders_and_inventory_snapshots.sql` | Backend migration execution unverified because `javac` is unavailable |
| Exact pricing priority | `RuleBasedSuggestionStrategy`: low stock +10%, otherwise demand > 2x category average +5%, otherwise HOLD | `RuleBasedSuggestionStrategyTest` priority, strict boundaries, decimal rounding, and zero-average cases |
| Exact reorder formula | `max(1, reorderThreshold * 3 - currentStock)` with overflow guard | `RuleBasedSuggestionStrategyTest` |
| Suggestion information | `Suggestion` and `SuggestionResponse`: current/recommended price, direction, quantity, confidence, rationale, trigger | `SuggestionServiceTest`, strategy tests |
| Suggestion lifecycle | `Suggestion` with pending/accepted/rejected transitions | `SuggestionServiceTest` |
| Duplicate pending suggestions | Product/type/trigger/status lookup before save | `SuggestionServiceTest.generateDoesNotCreateDuplicatePendingSuggestion` |
| Approval behavior | `SuggestionService.resolve`; price acceptance updates price, reorder acceptance simulates inbound stock, rejection has no side effect | `SuggestionServiceTest` price, reorder, rejection, and resolved-state cases |
| Suggestion persistence/API | Flyway V3, repository, DTOs, and controller | Backend verification blocked by unavailable `javac` |
| Product-scoped demo workflows | `ProductOrderRequest`, suggestion alias endpoints, frontend API client and console | Frontend verification blocked by command permission; backend uncompiled |
| Live approval console | React suggestion listing, generation, accept/reject controls | Frontend verification blocked by command permission |
| AI advisor adapter | `HttpAiAdvisorClient`, `AiAdvisorClient`, `FallbackSuggestionAdvisor`, advisor configuration | `FallbackSuggestionAdvisorTest`; runtime blocked by unavailable `javac` |
| AI validation and fallback | Strict type, price, quantity, confidence, rationale validation and rule fallback | Source-level adapter/fallback tests; runtime blocked |
| Automatic low-stock/demand triggers | `InventoryChangedEvent`, `InventoryRecommendationTriggerListener`, after-commit async listener | `InventoryRecommendationTriggerListenerTest`; runtime blocked |
| Stock update concurrency | `ProductService.updateStock` uses `findByIdForUpdate` | `ProductServiceTest`; runtime blocked |
| Consistent layer-first structure | `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `exception`, `event`, `advisor`, `config`, and `seed` packages; no legacy `domain`, `persistence`, or feature `api` packages remain | Static package/tree review; runtime blocked |
| Sanitized API failures | `GlobalExceptionHandler` maps validation and unexpected failures without exposing internals | Static review; runtime blocked |
| Required numeric input validation | Boxed numeric DTO fields use `@NotNull` with range constraints | Static review; runtime blocked |
| Database SKU uniqueness | Flyway V4 lower-case unique index matches case-insensitive service check | Static migration review; runtime blocked |
| Request validation | Boxed numeric DTO fields reject missing/negative values while allowing zero stock | `RequestValidationTest`; runtime blocked |
| Entity-to-response mapping | Explicit mapper classes preserve response fields without exposing JPA entities | `MapperTest`; runtime blocked |
| AI failure resilience | Unexpected advisor runtime failures also use deterministic fallback | `FallbackSuggestionAdvisorTest`; runtime blocked |

## Deliberate scope decisions

- An order contains exactly one product and has no payment, customer, shipping, or fulfillment model.
- A snapshot is captured after a successful order and records the resulting stock level, not a full historical copy of all product fields.
- The original requirements did not define these semantics; they are documented assumptions and should be revisited if the authoritative specification becomes available.
- The source review identified and fixed two defects: same-key concurrent requests could reach the unique constraint after a second stock mutation, and replay responses reported current rather than original remaining stock.
- Backend runtime verification remains pending because the workspace has only an OpenLogic JRE 21.0.12 and no installed JDK was found in the checked standard directories.
- Suggestion rules are a documented first implementation, not an AI recommendation claim.
- Category average means average `demandVelocity` for products in the same category; an absent or zero average deliberately does not qualify as a demand spike.
