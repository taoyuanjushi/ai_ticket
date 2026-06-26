# Docker Compose Acceptance Report

Date: 2026-06-25

This report records the local Docker Compose end-to-end acceptance result for the AI Enhanced Ticket Management System.

## 1. Compose File And Startup

Current local Docker deployment uses:

```powershell
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

Validation command:

```powershell
docker compose -f docker-compose.prod.yml --env-file .env ps
```

Restart command:

```powershell
docker compose -f docker-compose.prod.yml --env-file .env down
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

Config validation:

```powershell
docker compose -f docker-compose.prod.yml --env-file .env config
```

Result: passed.

## 2. Current Deployment Topology

```text
Browser
-> frontend nginx http://localhost:8088
-> /api reverse proxy
-> java-backend:8080
-> python-ai:8001
-> java-backend Ticket API
-> mysql / redis
```

Current service summary:

| Service | Container | Port | Health |
| --- | --- | --- | --- |
| frontend | ticket-frontend | host 8088 -> container 80 | Up |
| java-backend | ticket-java-backend | container 8080 | Up |
| python-ai | ticket-python-ai | container 8001 | Up |
| mysql | ticket-mysql | container 3306 | Healthy |
| redis | ticket-redis | container 6379 | Healthy |

Important network settings:

- Frontend only exposes `http://localhost:8088`.
- Frontend nginx proxies `/api/...` to `http://java-backend:8080/...`.
- Java calls Python through `AI_SERVICE_BASE_URL=http://python-ai:8001`.
- Python calls Java through `JAVA_API_BASE_URL=http://java-backend:8080`.
- MySQL and Redis are not exposed to the host by this compose file.

## 3. Frontend Access

Access URL:

```text
http://localhost:8088
```

Result:

- `GET http://localhost:8088` returned HTTP 200.
- Static frontend assets loaded through nginx.
- Production bundle scan found no direct reference to:
  - `localhost:8001`
  - `127.0.0.1:8001`
  - `/agent/chat`

Validation command:

```powershell
docker exec ticket-frontend sh -c "if grep -R -E 'localhost:8001|127\.0\.0\.1:8001|/agent/chat' -n /usr/share/nginx/html; then exit 1; else echo NO_DIRECT_PYTHON_REFERENCES; fi"
```

Result:

```text
NO_DIRECT_PYTHON_REFERENCES
```

Manual browser DevTools Network screenshot was not captured in this run. The production bundle scan and API validation confirm that the deployed frontend path goes through Java `/api`.

## 4. Test Accounts

The acceptance data is stored in `docs/stage5-test-data.sql`.

| Username | Password | Role | Expected Permission |
| --- | --- | --- | --- |
| tom | 123456 | USER | Can view own tickets only. Cannot update status or view operation logs. |
| staff | 123456 | STAFF | Can process tickets and use AI write confirmation flow. |
| admin | 123456 | ADMIN | Can view global operation logs and broader ticket data. |

Login validation through nginx `/api`:

| Account | Result |
| --- | --- |
| tom | HTTP 200, token returned |
| staff | HTTP 200, token returned |
| admin | HTTP 200, token returned |

## 5. Permission Acceptance

Validated through `http://localhost:8088/api`.

| Check | Result |
| --- | --- |
| tom queries own visible tickets | Passed |
| tom opens another user's ticket detail | HTTP 403, passed |
| tom updates ticket status | HTTP 403, passed |
| tom opens global operation logs | HTTP 403, passed |
| staff queries tickets | HTTP 200, passed |
| admin opens global operation logs | HTTP 200, passed |

## 6. AI Query Acceptance

Staff request:

```text
查询我的工单
```

Request path:

```text
frontend /api/ai/chat
-> java-backend /ai/chat
-> python-ai /agent/chat
-> java-backend /tickets
```

Result:

- HTTP 200.
- Python recognized `IntentType.QUERY_TICKET`.
- Python called `GET http://java-backend:8080/tickets?...`.
- Response type was `NORMAL`.

Note: when using PowerShell for Chinese JSON requests, send the body as UTF-8 bytes. Otherwise the message can be corrupted before it reaches Java/Python and intent recognition may become `UNKNOWN`.

## 7. AI Create Pending And Confirm

Staff request:

```text
创建一个高优先级工单，标题是Docker验收创建，描述是Docker Compose完整链路验收创建工单
```

Result:

- Response type was `PENDING_CONFIRMATION`.
- Java created an `ai_pending_action` record.
- No ticket was created before confirmation.
- `payload_json` did not contain token or Authorization data.

Confirm request:

```text
确认
```

Result:

- Java confirmed the pending action.
- Java executed the real `CREATE_TICKET` business operation.
- A new ticket was inserted.
- `ai_pending_action.status` became `CONFIRMED`.
- Operation logs included AI pending creation and confirmed write execution.

Repeated confirm result:

- A second confirm did not create another ticket.
- The response indicated there was no pending action to execute.

## 8. Cancel Acceptance

Staff request:

```text
把 8 号工单改成处理中
```

Result:

- Response type was `PENDING_CONFIRMATION`.
- Java created an `UPDATE_TICKET_STATUS` pending action.

Cancel request:

```text
取消
```

Result:

- `ai_pending_action.status` became `CANCELLED`.
- Ticket status did not change.
- Confirm after cancel did not execute the write operation.
- Operation logs included AI cancellation records.

## 9. Reply Suggestion And Save Acceptance

Staff request:

```text
生成 8 号工单回复建议
```

Result:

- Response type was `JSON_RESULT`.
- Python first called Java ticket detail:

```text
GET http://java-backend:8080/tickets/8/detail
```

- Response data contained structured fields:

```json
{
  "suggestion": "...",
  "confidence": 0.8,
  "reason": "...",
  "risk_flags": []
}
```

Save AI reply validation:

- `POST /tickets/8/ai-replies/pending` created a pending action.
- Reply count did not increase before confirmation.
- Confirm through `/ai/chat` saved a new reply.
- The saved reply type was `AI`.

## 10. Operation Log Acceptance

Validated APIs:

```text
GET /operation-logs?page=1&size=5
GET /tickets/{id}/logs?page=1&size=20
```

Result:

- admin can view global operation logs.
- tom receives HTTP 403 for global operation logs.
- AI pending creation, confirm, cancel, and write execution are recorded.
- Logs do not include full JWT tokens.
- Logs do not include database passwords.

## 11. Java And Python Call Chain Logs

Java logs show:

```text
Forward AI chat request to Python. userId=4, conversationId=utf8-query
Forward AI chat request to Python. userId=4, conversationId=utf8-reply
```

Python logs show:

```text
Received agent chat request user_id=4 conversation_id=utf8-query
Agent intent recognized: intent=IntentType.QUERY_TICKET
HTTP Request: GET http://java-backend:8080/tickets?... "HTTP/1.1 200"

Received agent chat request user_id=4 conversation_id=utf8-reply
Agent intent recognized: intent=IntentType.REPLY_SUGGESTION
HTTP Request: GET http://java-backend:8080/tickets/8/detail "HTTP/1.1 200"
```

Sensitive log scan result:

```text
JAVA_LOG_SENSITIVE_SCAN_OK
PYTHON_LOG_SENSITIVE_SCAN_OK
```

## 12. Redis Cache Security Acceptance

Scenario:

1. admin accessed ticket `9` detail and warmed Redis cache.
2. tom then requested the same ticket detail.

Result:

- admin request returned HTTP 200.
- tom request returned HTTP 403.
- tom response did not leak the cached ticket title.
- Redis cache did not bypass Java permission checks.

## 13. Issues Found And Fixed

### Docker Desktop was not running

Symptom:

```text
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine
```

Fix:

- Started Docker service and Docker Desktop.
- Re-ran compose startup.

### Existing MySQL volume skipped init SQL

Symptom:

- MySQL volume already existed.
- Docker init SQL did not run again.
- `ticket.assigned_to` was missing.

Fix:

```powershell
Get-Content docs/migrations/add_ticket_category_assigned_to.sql | docker exec -i -e MYSQL_PWD=<root-password> ticket-mysql mysql -uroot
```

### Stage 5 seed SQL was invalid

Symptom:

- `docs/stage5-test-data.sql` contained corrupted text and invalid SQL strings.
- Import failed with MySQL syntax error.

Fix:

- Rebuilt `docs/stage5-test-data.sql` with stable ASCII acceptance data.
- Re-imported the seed SQL.

### Seed password hash did not match documented password

Symptom:

- Login with `123456` failed.

Fix:

- Regenerated a BCrypt hash for `123456`.
- Updated all acceptance users in `docs/stage5-test-data.sql`.

### MyBatis stdout logging exposed sensitive rows

Symptom:

- Java logs printed SQL result rows including password hashes.

Fix:

- Changed MyBatis logging default to `NoLoggingImpl`.
- Added `MYBATIS_LOG_IMPL` environment configuration.
- Rebuilt `java-backend`.

Files changed for this fix:

- `java/hello-demo/src/main/resources/application.properties`
- `docker-compose.prod.yml`
- `.env.example`
- local `.env`

### Docker image pull had transient EOF

Symptom:

- Python base image pull briefly failed with a network EOF.

Result:

- Docker retried and the build completed successfully.

## 14. Current Acceptance Status

| Item | Status |
| --- | --- |
| Compose config validates | Passed |
| All containers are Up | Passed |
| MySQL healthy | Passed |
| Redis healthy | Passed |
| Frontend accessible | Passed |
| tom login | Passed |
| staff login | Passed |
| admin login | Passed |
| tom permission isolation | Passed |
| staff AI query | Passed |
| AI create pending | Passed |
| Confirm executes once | Passed |
| Repeated confirm does not duplicate | Passed |
| Cancel prevents execution | Passed |
| Reply suggestion JSON result | Passed |
| Save AI reply requires pending confirmation | Passed |
| Operation logs accessible by role | Passed |
| Java/Python call chain visible | Passed |
| Redis cache does not bypass permission | Passed |
| Frontend production bundle does not direct-call Python | Passed |
| Manual browser Network screenshot | Not captured |

## 15. Useful Commands

Check services:

```powershell
docker compose -f docker-compose.prod.yml --env-file .env ps
```

View logs:

```powershell
docker compose -f docker-compose.prod.yml --env-file .env logs --tail=200 java-backend
docker compose -f docker-compose.prod.yml --env-file .env logs --tail=200 python-ai
docker compose -f docker-compose.prod.yml --env-file .env logs --tail=100 frontend
```

Re-import acceptance data into an existing development database:

```powershell
$mysqlPwd = (docker exec ticket-mysql printenv MYSQL_ROOT_PASSWORD).Trim()
Get-Content docs/stage5-test-data.sql | docker exec -i -e MYSQL_PWD=$mysqlPwd ticket-mysql mysql -uroot
```

If you want a fully fresh local development database, use this only in development:

```powershell
docker compose -f docker-compose.prod.yml --env-file .env down -v
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

Warning: `down -v` deletes MySQL and Redis volumes.

## 16. Next Steps

1. Add a lightweight scripted acceptance runner for login, permission, AI query, pending, confirm, cancel, and reply suggestion.
2. Add frontend Playwright or Cypress checks for browser Network assertions.
3. Keep production MyBatis logging disabled by default.
4. Avoid using PowerShell default string encoding for Chinese JSON API checks; send UTF-8 bytes or test through the browser.
