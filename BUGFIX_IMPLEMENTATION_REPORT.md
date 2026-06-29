# Bugfix Implementation Report

Date: 2026-06-29

## Fixed

### P1-1 Ticket detail replies lacked author display fields

What changed:

- Added `TicketReplyVO` for ticket-detail reply output.
- Changed `TicketDetailVO.replies` from raw `TicketReply` entity rows to `TicketReplyVO`.
- Enriched replies in `TicketService.getTicketDetail()` with `authorName` and `authorRole` from the existing `user` table.
- Updated the frontend `TicketReply` type and ticket detail page to render `authorName` / `authorRole`, falling back to `#userId`.
- Updated mock replies to include author display fields.
- Versioned the ticket detail Redis cache key from `ticket:detail:` to `ticket:detail:v2:` so old cached detail payloads do not hide the new fields after deploy.

Key files:

- `java/hello-demo/src/main/java/com/example/hello_demo/vo/TicketReplyVO.java`
- `java/hello-demo/src/main/java/com/example/hello_demo/vo/TicketDetailVO.java`
- `java/hello-demo/src/main/java/com/example/hello_demo/service/TicketService.java`
- `java/hello-demo/src/main/java/com/example/hello_demo/constant/RedisKeyConstants.java`
- `frontend/src/types/domain.ts`
- `frontend/src/pages/TicketDetailPage.tsx`
- `frontend/src/api/mock.ts`

## Test Coverage Added

- Added `TicketServiceCacheSecurityTest.ticketDetailReturnsReplyAuthorDisplayFields`.
- Updated cached permission integration test helper to use the new reply VO shape.

## Verification

Passed:

- `frontend`: `npm.cmd run build`
- `frontend`: `npm.cmd run lint`
  - Result: 0 errors, 1 existing warning in `frontend/src/i18n/I18nProvider.tsx` about Fast Refresh exports.
- `ticket-agent-python`: `python -m compileall app`
- `java/hello-demo`: `.\mvnw.cmd "-Dtest=TicketServiceCacheSecurityTest#ticketDetailReturnsReplyAuthorDisplayFields,TicketPermissionIntegrationTest" test`
  - Result: 4 tests passed.

Observed but not fixed:

- Running the full `TicketServiceCacheSecurityTest` class currently hits 5 existing MyBatis lambda-cache test-environment errors in category/assignee tests. The new reply-author test passes when run directly, and Java main/test compilation succeeds before those existing failures.

## Not Changed

- No database schema changes.
- No Python direct database access added.
- No frontend direct Python calls added.
- No dependency changes.
- No broad frontend/backend rewrite.
