# Architecture Decision Record

## ADR-001: Layer product behavior by API, service, domain, and persistence

**Status:** Accepted  
**Decision:** Controllers use request/response records, services coordinate use cases, the `Product` entity owns stock status transitions, and repositories encapsulate persistence.

**Alternatives considered:** Exposing JPA entities directly; placing business rules in controllers; using an unstructured map-based API.

**Rationale and trade-offs:** The layered design keeps HTTP and persistence concerns out of domain behavior and makes service rules unit-testable. It adds small DTO and mapping costs, but prevents the API contract from being coupled to the database model.

## ADR-002: Use PostgreSQL with Flyway-managed schema

**Status:** Accepted  
**Decision:** Production persistence uses PostgreSQL and versioned Flyway migrations; Hibernate validates rather than changes the schema.

**Alternatives considered:** Hibernate auto-update; an embedded database in production.

**Rationale and trade-offs:** Explicit migrations make schema changes reviewable and repeatable. They require a migration for every schema change. H2 is retained only for fast tests.

## ADR-003: Keep demo seed data opt-in

**Status:** Accepted for the current milestone  
**Decision:** Demo records are inserted only when `app.seed.enabled` is explicitly true, and seeding is skipped when products already exist.

**Alternatives considered:** Always seed on startup; a manual SQL-only seed script; a separate demo profile.

**Rationale and trade-offs:** An explicit property makes the demo path easy while avoiding unexpected writes to a real database. The empty-database guard favors safety and means reseeding requires clearing the demo database intentionally.

## ADR-004: Process a single-product order transactionally

**Status:** Accepted for the current milestone  
**Decision:** `OrderService` processes one product and quantity per request. It obtains a pessimistic write lock for the product, decrements stock, saves a processed order, and saves one immutable post-order inventory snapshot in one transaction.

**Alternatives considered:** A multi-line cart/order aggregate; decrementing stock in the controller; asynchronous stock reservation; no idempotency protection.

**Rationale and trade-offs:** The single-product flow is enough to establish the inventory foundation without inventing storefront behavior. The transaction and row lock prevent concurrent orders from overselling the same row. The trade-off is that multi-product ordering and fulfillment are intentionally deferred.

## ADR-005: Use idempotency keys for order processing

**Status:** Accepted for the current milestone  
**Decision:** Every order request carries a caller-provided unique idempotency key. A repeated request with the same product and quantity returns the original order without changing stock; reuse with different values returns `409 Conflict`.

**Alternatives considered:** Client-side duplicate prevention; generated request IDs with no persistence; accepting duplicate orders.

**Rationale and trade-offs:** Persistence is the only reliable protection across retries and process restarts. It adds a unique database constraint and a small request-field burden, but makes retries safe and explainable in an interview.

## ADR-006: Recheck idempotency after acquiring the product lock

**Status:** Accepted for the current milestone  
**Decision:** Order processing performs an initial idempotency lookup, locks the product, then performs a second lookup before decrementing stock.

**Problem solved:** Two same-key requests can both observe no order before either transaction commits. The product lock serializes them, and the second lookup lets the later transaction return the committed order instead of decrementing again or relying on a unique-constraint error.

**Trade-off:** This adds one indexed lookup on the non-replay path, which is preferable to duplicate stock mutation and is straightforward to explain.

## ADR-007: Use one rule-based strategy for the first suggestion milestone

**Status:** Accepted for the current milestone  
**Decision:** `RuleBasedSuggestionStrategy` produces price and reorder drafts from stock, reorder threshold, demand velocity, category-average demand, current price, and trigger context. The service persists drafts and owns duplicate-pending and approval behavior.

**Alternatives considered:** Hard-coding rules inside the controller; adding an AI provider now; introducing multiple strategy implementations before there is a second real rule set.

**Rationale and trade-offs:** The strategy boundary is useful because future AI or alternative rule implementations can produce the same draft contract. Keeping one implementation avoids speculative abstraction. The exact priority is low stock (+10%), otherwise demand above twice category average (+5%), otherwise HOLD. Reorder quantity is `max(1, reorderThreshold * 3 - currentStock)`. Rules are explainable and testable; they are not a claim of production pricing science.

## ADR-008: Keep human approval as the only side-effect checkpoint

**Status:** Accepted for the current milestone  
**Decision:** Generation only persists pending suggestions. Price changes and simulated inbound stock happen only when a pending suggestion is accepted inside a transaction; rejection has no product side effect.

**Alternatives considered:** Automatically applying recommendations during generation; asynchronous auto-application; storing suggestions without an approval endpoint.

**Rationale and trade-offs:** Human review is an explicit product requirement and limits the blast radius of rule mistakes. It adds an approval step, but makes every inventory or price change auditable and explainable.

## ADR-009: Use a configuration-driven AI adapter with deterministic fallback

**Status:** Accepted

**Decision:** `HttpAiAdvisorClient` is an optional adapter selected by `ADVISOR_PROVIDER=ai`. Endpoint, API key, and timeout come from configuration. `FallbackSuggestionAdvisor` uses `RuleBasedSuggestionStrategy` when configuration, transport, status, parsing, or field validation fails.

**Alternatives considered:** Hard-coding a provider SDK; making AI mandatory; allowing unvalidated model output to reach persistence.

**Rationale and trade-offs:** The adapter keeps provider concerns outside business services and preserves a credential-free local path. A generic JSON contract avoids an undocumented vendor dependency, but a real provider may require a provider-specific request adapter later.

## ADR-010: Generate automatic recommendations after commit

**Status:** Accepted

**Decision:** Stock updates and successful orders publish `InventoryChangedEvent` inside their transactions. An `@TransactionalEventListener` with `AFTER_COMMIT` and `@Async` evaluates low-inventory and demand-spike triggers.

**Alternatives considered:** Calling generation synchronously from controllers/services; publishing before commit; Kafka.

**Rationale and trade-offs:** After-commit delivery prevents recommendations for rolled-back inventory changes, while asynchronous execution keeps API latency independent of advisor calls. In-process events can be lost if the process crashes, so durable messaging and retry telemetry remain future work.

## ADR-011: Use conventional layer-first backend packages

**Status:** Accepted

**Decision:** The backend uses conventional layer-first packages: `controller`, `service`, `repository`, `entity`, `dto.request`, `dto.response`, `mapper`, `exception`, `event`, `advisor`, `config`, and `seed`.

**Problem solved:** The original feature `api` packages mixed controllers and request/response records, and CORS configuration was located with common API models and exceptions.

**Alternatives considered:** A global layer-first package tree; leaving mixed `api` packages; a larger domain/persistence split.

**Rationale and trade-offs:** A conventional layer-first tree is explicit and easy to navigate for this small application. It avoids empty feature packages and makes technical boundaries visible. The trade-off is less locality by feature, but the codebase is small enough that centralized layers remain understandable.

## Pattern roles

- **Service Layer:** `OrderService` owns the transaction and business sequence; `InventorySnapshotService` owns snapshot queries.
- **Repository Pattern:** Spring Data repositories isolate JPA queries and the product row lock.
- **DTO Pattern:** Controllers expose records rather than entities, keeping persistence details out of HTTP contracts.
- **Strategy Pattern:** `SuggestionStrategy` and `RuleBasedSuggestionStrategy` isolate the deterministic recommendation algorithm behind a contract that can later be implemented by a validated AI adapter. The current implementation keeps one real strategy and does not pretend AI exists.
- **Approval checkpoint:** `SuggestionService.resolve` is the only path that mutates price or stock from a suggestion, preserving human approval as an auditable boundary.

## Unresolved decisions

- Order semantics and inventory snapshot retention.
- Pricing strategy boundaries and approval permissions.
- LLM provider, prompt contract, and fallback policy.
- Post-commit event trigger and async executor policy.
- Message broker and event delivery guarantees.
- Authentication and authorization model.
