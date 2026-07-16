# AI conversation history and routing design

## Goal

Persist AI conversations on the CareNest server so an elder or authorized family member can leave the AI assistant, return later, and continue an existing conversation. Provide a conversation list instead of creating an isolated, invisible session on every component mount.

At the same time, simplify input routing: only clear emergencies use the local emergency path and create assistance/customer-service tickets. All non-emergency questions call the configured cloud model. Cloud output safety constraints remain in place.

## Existing state

- `ai_assistant_session` and `ai_assistant_message` already persist sessions and messages in MySQL.
- The backend currently exposes create-session, send-message, and close-session operations, but no user-facing session or message history queries.
- The frontend keeps the active session and rendered messages only in component memory. Remounting the component loses that state and creates a new session on the next message.
- The local classifier currently has both `CRITICAL` and `WARNING` paths. Both create assistance tickets; only `CRITICAL` also creates a customer-service ticket.

## Product behavior

### Conversation list

- The AI assistant displays conversations available to the current user, ordered by latest activity descending.
- Each item displays its title, last-message preview, and latest activity time.
- Selecting an item loads its complete message history from the server.
- A `New conversation` command creates and selects an empty session.
- Entering the AI assistant restores the last selected accessible session. If it is unavailable, the frontend selects the latest session. If no session exists, the page shows the welcome state and creates a session only when the user starts a conversation or explicitly requests a new one.
- The active session ID may be cached locally only as a navigation preference. MySQL remains the source of truth for conversation content.

### AI routing

- Clear emergency expressions and severe emergency symptoms remain `CRITICAL`.
- A `CRITICAL` message uses the local emergency response and creates both an assistance ticket and a customer-service ticket through the existing transaction.
- Every non-critical input calls the cloud AI provider, including ordinary medication, blood pressure, symptom, and care questions.
- Non-critical input does not create an assistance ticket merely because it contains medical keywords.
- Cloud failures return the existing safe fallback without creating a ticket.
- The cloud system prompt and response guard continue preventing diagnosis, medication/dosage decisions, concrete clinical thresholds, fabricated telephone numbers, and unsupported CareNest capabilities.

## Backend API

### List sessions

`GET /api/v1/ai/sessions?page=1&size=20&elderId={optional}`

Returns sessions accessible to the authenticated elder or family member. Each record contains:

- session ID
- elder ID and display name
- title
- status and safety level
- latest message preview
- created time
- latest activity time

An elder account is always scoped to itself. A family account may omit `elderId` to list sessions across actively bound elders or provide an actively bound elder ID to filter the result. The endpoint uses the same elder ownership and active family-binding rules as existing AI operations and must not expose another elder's sessions.

### Read messages

`GET /api/v1/ai/sessions/{sessionId}/messages`

Returns persisted user and assistant messages in ascending creation order. The response contains message ID, sender role, message type, full text, safety flag, and creation time. The endpoint reuses session authorization before reading data.

### Existing write operations

- `POST /api/v1/ai/sessions` remains the session creation endpoint.
- `POST /api/v1/ai/sessions/{sessionId}/messages` remains the message endpoint.
- Sending a message updates the session's `updated_at` so list ordering reflects actual activity.
- Existing response fields remain compatible with the frontend.

No delete API is included in this MVP. Closing a session remains available but is not required for switching conversations.

## Frontend design

- Extend `stageFortyOne` API and types with session-list and message-history contracts.
- The AI assistant panel owns three independent states: session list, active session ID, and active message list.
- On mount or elder change, load the session list first, resolve the active session, then load its messages.
- Switching sessions replaces the active message list with server data and stores only the selected ID locally.
- Creating a new conversation adds it to the list and selects it without discarding older conversations.
- Elder accounts create conversations for themselves. Family accounts select an actively bound elder before creating a conversation; when exactly one active binding exists, that elder is selected automatically.
- After sending a message, append the returned exchange and refresh/reorder the session list without clearing the current conversation.
- Loading, empty, request-failure, and retry states are visible. Failed history loading must not silently replace server history with mock content.
- The list uses the existing mobile elder/family visual language and remains usable on the desktop family layout.

## Data and authorization

- No new table is required.
- Repository queries must use parameterized SQL and return only sessions authorized for the current user.
- Message content remains stored in `ai_assistant_message.content_text`; summaries are used only for list previews.
- The session's `updated_at` is updated in the same transaction as persisted messages and ticket side effects.
- Family users can list/read only sessions for elders with an active binding, consistent with existing AI session access.

## Error handling

- Missing sessions return 404; inaccessible sessions return 403.
- Invalid pagination returns 422.
- A failed session-list or message-history request leaves existing rendered data intact and exposes a retry action.
- A failed cloud request persists the user message and the safe fallback assistant message, preserving a complete conversation timeline.

## Verification

- Repository/service tests cover owner and active-family session listing, unauthorized access, sort order, message order, and activity-time updates.
- API tests cover list and history response contracts.
- Routing tests prove clear emergencies create both ticket types without calling the cloud provider.
- Routing tests prove ordinary medical/care questions call the cloud provider and create no ticket.
- Frontend type checking covers new contracts.
- Playwright verifies: send messages, leave the AI view, return and recover history; create a second conversation; switch between conversations; refresh and recover the selected or latest conversation.
- Docker acceptance verifies persisted rows and role visibility against MySQL through the real `localhost:3000` API route.

## Out of scope

- Deleting, renaming, searching, exporting, or sharing conversations.
- Streaming responses and multi-model selection.
- Using Redis as the conversation source of truth.
- Replacing the existing cloud output safety guard.
