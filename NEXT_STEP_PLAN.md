# Next Step Plan

Date: 2026-06-29

## Recommended Next Steps

1. Fix the existing MyBatis lambda-cache failures in `TicketServiceCacheSecurityTest`.
   - Goal: make the whole Java service test class runnable again.
   - Likely path: initialize MyBatis table metadata in the affected pure unit tests, or move those LambdaUpdateWrapper paths into narrower integration tests.

2. Do a small cleanup pass for Python AI agent compatibility code.
   - Candidates: legacy in-memory pending-action store and LangChain tool wrappers that are now mostly metadata/invoke shells.
   - Keep this separate from bugfix work because the current Java-owned pending-action flow is already correct.

3. Align frontend operation-type constants with Java enum names.
   - This is low risk because `OperationLog.action` already accepts `string`.
   - Benefit: better autocomplete and fewer “unknown action” surprises in future UI filters.

4. Add one integration check for `/tickets/{id}/detail`.
   - Assert `replies[0].authorName`, `replies[0].authorRole`, `replyType`, and ascending `createdAt` order.
   - This would protect the reply closed-loop contract end to end.

5. Re-run production compose acceptance when Docker is available.
   - Use `docker-compose.prod.yml`.
   - Verify frontend `/api` -> Java -> Python, login roles, AI pending confirm/cancel, reply suggestion, dashboard, and operation logs.

## Hold For Later

- DTO/VO record conversion across the Java codebase.
- Larger operation-log schema normalization.
- Removing all legacy Python compatibility paths.

These are cleanup/refactor items, not blockers for the current bugfix.
