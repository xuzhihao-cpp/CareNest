# Phase 41-43 AI Assistant Full-Stack API

## Scope

The MVP uses MySQL as the source of truth. Docker defaults to the deterministic
local safety provider and can switch to an OpenAI-compatible cloud provider with
environment variables. Local classification always runs first, so cloud output
can never override medication, diagnosis, treatment, self-harm, or emergency
safety decisions.

## User APIs (`backend-user`)

### `POST /api/v1/ai/sessions`

Elder users may omit `elderId` or use their own elder profile. Family users must
provide an elder with an active binding.

Request: `{ "elderId": "elder_001", "sessionTitle": "日常照护咨询", "sourceType": "TEXT" }`

Response data: `sessionId`, `elderId`, `elderName`, `sessionTitle`,
`sessionStatus`, `safetyLevel`, `riskFlag`, `latestAssistanceTicketId`,
`latestAssistanceStatus`, and `createdAt`.

### `GET /api/v1/ai/sessions`

Returns the current elder's sessions, or family sessions filtered by an
authorized `elderId`. Internal identifiers are response fields for API
correlation only and must not be shown as primary UI labels.

### `POST /api/v1/ai/sessions/{sessionId}/messages`

Request: `{ "content": "胸口很闷，呼吸困难", "messageType": "TEXT", "voiceLogId": null }`

Response data: `sessionId`, user and assistant message IDs, `answer`,
`safetyLevel`, `riskFlag`, `assistanceTicketId`, and
`customerServiceTicketCreated`.

Safety classification:

- `NORMAL`: daily care and product usage guidance.
- `WARNING`: medication, dosage, diagnosis, or treatment changes. Refuse the
  clinical decision and direct the user to the original medical advice,
  physician, or platform service. This level does not falsely claim that a
  ticket was created.
- `CRITICAL`: chest pain, breathing difficulty, unconsciousness, serious fall, or
  self-harm wording. Create urgent assistance and customer-service tickets.

### `POST /api/v1/ai/sessions/{sessionId}/close`

Closes a session owned by the elder or an active family binding.

### `DELETE /api/v1/ai/sessions/{sessionId}`

Removes a conversation from the creator's history. Only the user who created
the session may delete it. The session is retained as `CLOSED` for safety audit
and is excluded from user history lists; messages and risk tickets are not
physically deleted.

### `GET /api/v1/assistance/tickets`

Lists assistance tickets for elder self or a family member with an active
binding. Supports `elderId`, `status`, `page`, and `size`.

## Customer-service APIs (`backend-care-admin`)

- `GET /api/v1/customer-service/tickets`: list by `status`, `priority`, `keyword`, `page`, `size`.
- `GET /api/v1/customer-service/tickets/{ticketId}`: detail and messages.
- `POST /api/v1/customer-service/tickets/{ticketId}/status`: transition to
  `PROCESSING`, `RESOLVED`, or `CLOSED` with optional `handleResult`.
- `POST /api/v1/customer-service/tickets/{ticketId}/messages`: add a text reply.
- `GET /api/v1/admin/ai/sessions`: admin/customer-service AI audit list.
- `GET /api/v1/admin/ai/sessions/{sessionId}`: risk session detail with
  privacy-limited message summaries.

Only `ADMIN` and `CUSTOMER_SERVICE` may use management APIs. Allowed ticket
transitions are `PENDING -> PROCESSING`, `PROCESSING -> RESOLVED`, and either
active state to `CLOSED`. An urgent ticket cannot be resolved or closed before
at least one follow-up record has been stored.

## Cloud provider configuration

```dotenv
AI_PROVIDER=cloud
AI_ENDPOINT=https://dashscope.aliyuncs.com/compatible-mode/v1
DASHSCOPE_API_KEY=<secret>
AI_MODEL=qwen-plus
AI_TIMEOUT=15s
```

The endpoint is OpenAI-compatible and the backend appends
`/chat/completions`. Missing keys, network failures, non-2xx responses,
malformed payloads, or unsafe model output fall back to the local safe response.

## Persistence and privacy

The existing `ai_assistant_session`, `ai_assistant_message`,
`assistance_ticket`, `customer_service_ticket`, and `ticket_message` tables are
used without duplicate schema. Audit content is limited to the submitted text
and a short summary; passwords, tokens, identity numbers, and storage secrets
are never persisted.
