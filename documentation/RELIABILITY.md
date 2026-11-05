# Delivery, retries and saga recovery

Users, Products and Orders accept optional `Idempotency-Key` command keys, scoped to
identity and endpoint. Canonical JSON object ordering produces a SHA-256 fingerprint.
A PostgreSQL transaction advisory lock serialises identical keys; resource, outbox and
original response commit together. An identical retry replays that original response;
a changed body gives 409. The frontend retains its key across a failed request.

Payments instead has one deterministic payment identity per order. A per-order lock
serialises creation and retries return the existing payment with 200, including its
current state and originally chosen method. A new attempt after rejection/failure requires
a new order. These are explicit domain semantics, not a claim that arbitrary POSTs are safe.

Broker delivery is at least once. SQL inbox insertion, domain state and derived outbox
commit together. A concurrent duplicate either sees the inbox or loses its unique insert
and rolls back before retry. Outbox rows use write locks and publisher confirms.
Duplicate deliveries remain possible between confirm and local commit; consumers must
remain idempotent. `check_reliability.py` verifies duplicate event IDs against a real broker.

Products retains a reservation signal per order and locks that signal before inventory.
A cancellation/failure received before a delayed quantitycheck therefore blocks a later
reservation. Paid inventory cannot be released by an old cancellation. Orders retains
early payment outcomes until an eligible, matching payment claim exists. Matching requires
the order's deterministic payment UUID. Conflicting outcomes are not declared equivalent
histories: Payments itself allows only one terminal outcome, and operational intervention
must investigate contradictory producer data. No blanket commutativity theorem is claimed.

New validation requests expire after five minutes; unpaid reservations after thirty.
Maintenance stores compensation in the same transaction. Deadlines do not move when a
shipping address is edited. Historical demonstration fixtures are intentionally timeless.
Ambiguous PAYMENT_PENDING orders become REVIEW_REQUIRED and keep the car reserved.
Reconciliation requests the payment owner's stored outcome every five minutes; a late
COMPLETED/FAILED result still completes the order or releases the car. An unresolved or
missing payment stays visible for manual investigation rather than being silently released.
Only an administrator may record a verified payment result.

Status changes are appended to state_audit. Profile details are mutable; the system does
not claim immutable history for every field. The PDF job has its own queue, exits after
one event and acknowledges only after the Orders owner stores the first artifact. A lost
response/repeated event leaves the original PDF unchanged.

Local HTTP workers may be replicated only with these constraints: durable identity keys,
SQL inbox uniqueness, entity/signal locks and atomic projection CAS remain enabled. The
Kubernetes demonstration scales the stateless frontend, not unrestricted broker workers.
The current evidence covers selected interleavings; arbitrary faults, contradictory external
messages and unlimited worker scaling are outside the demonstrated guarantees.
