# Accepted implementation plan

Accepted by the user on 4 November 2026. All remaining P1 and P2 tasks are in scope.
No paid infrastructure or additional external accounts. Existing data must survive.

| Decision | Chosen behaviour |
| --- | --- |
| Access | Public catalogue; customers own their profile/orders/payments; administrators manage all business resources |
| Registration | Local Keycloak self-registration linked automatically to a Users profile; administrator customer creation remains available |
| Deadlines | Validation 5 minutes; unpaid reservation 30 minutes; ambiguous payments require manual review without releasing possibly sold stock |
| AI | Local model drafts car descriptions, human approval, no customer/payment input |
| Runtime | Compose for normal use; Kubernetes, AI and monitoring may be demonstrated separately within 16 GB RAM |
| Isolated job | Event-triggered PDF order confirmation, downloadable from the order, no external mail account |
| Catalogue | Image URLs with fallback, price/year/mileage/brand filters and pagination |
| Data | State audit history, preserved data, actual local backup/restore verification |
| Performance | Separate 10,000-car fixture and 20-user workload, measured p95 compared with a two-second target |
| Documents | English Report.md and PDF with contents and relevant appendices |
| Delivery | Remove tasks only after acceptance evidence; remote runs and actual group contributions must not be invented |

## Working agreement and proposed allocation

A lightweight Scrum-inspired process: short sprints, brief daily standups, a shared
To Do / In Progress / Review / Done board, sprint review and a short retrospective.
This is the agreed process going forward, not evidence that past meetings occurred.

| Owner | Main area | Reviewer |
| --- | --- | --- |
| Ahmad Abdel Razak Hussein Alkaseb | Users, identity and access control | Jeppe |
| Jeppe Rønning Koch | Products, catalogue, GraphQL and AI | Asger |
| Asger Valdemar Bergøe | Orders, Payments, reliability and confirmation job | Laith |
| Laith Abdel Razak Hussein Alkaseb | Kubernetes, observability, CI/release and restore | Ahmad |

Frontend, tests, contracts and report follow each workstream and receive shared
acceptance review. This allocation is a plan, not attribution of existing code.
Individual reflections and historical examples remain for the authors to confirm.
