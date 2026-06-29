# Code Review Report

Date: 2026-06-29

Scope: current workspace changes for the AI ticket system, covering frontend, Java backend, Python AI service, and production compose wiring.

## Summary

- P0: none found.
- Low-risk P1 to fix: ticket detail replies do not expose or render reply author name/role.
- P2 / follow-up only: a few compatibility or cleanup items remain, but they are not blocking correctness or deployment.

## P1 Findings

### P1-1 Ticket detail replies lack author name and role

Evidence:

- `java/hello-demo/src/main/java/com/example/hello_demo/vo/TicketDetailVO.java:16` stores replies as `List<TicketReply>`.
- `java/hello-demo/src/main/java/com/example/hello_demo/service/TicketService.java:185-186` returns raw `TicketReply` rows in the detail response.
- `frontend/src/types/domain.ts:99` has no `authorName` / `authorRole` fields.
- `frontend/src/pages/TicketDetailPage.tsx:257` renders only `#{item.userId}`.

Impact:

On the ticket detail page, staff/admin/users can see a reply timeline but cannot quickly tell who wrote each reply beyond a numeric user id. This weakens the reply closed-loop view and makes audits/support handoff harder.

Minimal fix:

Enrich only the ticket detail response with reply author display fields from the existing `user` table, then render `authorName` / `authorRole` in the frontend. No schema change is needed.

## Checks Passed By Review

### Frontend does not call Python directly

Evidence:

- `frontend/src/api/http.ts:5` uses `VITE_API_BASE_URL || "/api"`.
- `frontend/Dockerfile:8-9` defaults `VITE_API_BASE_URL` to `/api`.
- `frontend/nginx.conf` proxies `/api/` to Java backend only.
- Search found no `8001` / `/agent/chat` usage under `frontend/src`.

Result: OK.

### Production compose routes through Java

Evidence:

- `docker-compose.prod.yml:55` sets Java `AI_SERVICE_BASE_URL=http://python-ai:8001`.
- `docker-compose.prod.yml:74` sets Python `JAVA_API_BASE_URL=http://java-backend:8080`.
- `docker-compose.prod.yml:93` builds frontend with `/api`.

Result: OK.

### Java owns permissions for sensitive actions

Evidence:

- `DashboardService.getStats()` calls `PermissionUtil.requireAdmin()`.
- ticket status/category/assignee changes call staff/admin checks in `TicketService`.
- direct AI reply save endpoint rejects direct saves and requires pending confirmation.
- operation logs use Java service-level permission checks.

Result: OK.

### AI write confirmation is Java-owned

Evidence:

- Python creates Java pending actions through `JavaTicketClient.create_pending_action`.
- Python confirms/cancels through Java pending action APIs.
- Java validates `pending_action` ownership and status before executing.
- Java rejects pending payload keys containing token/authorization.

Result: OK.

### No Python direct database access found

Evidence:

- Search under `ticket-agent-python/app` found no SQLAlchemy/MySQL/SQLite engine or raw SQL access.
- Python reads/writes ticket business data via Java APIs.

Result: OK.

## P2 Follow-Ups

1. Python still has legacy in-memory pending-action compatibility (`AgentStateService`, `pending_action_store`) and LangChain tool wrappers used mostly as metadata/invoke wrappers. This is cleanup, not a correctness fix.
2. Frontend `OperationType` does not list every Java enum value, but `OperationLog.action` is already typed as `OperationType | string`, so logs still render.
3. Mock API returns admin/dashboard and operation logs without mock-side role enforcement. Production Java enforces this, and the UI route guard blocks normal use, so this is test/dev fidelity only.

## Fix Decision

Fix only P1-1 now. Everything else is either already correct, deployment wiring, or cleanup outside the requested P0 / low-risk P1 scope.
