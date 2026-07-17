# AI Symptom Triage and Follow-up Design

## Goal

Upgrade the CareNest AI assistant from a small keyword classifier into a
general symptom triage flow. The assistant must distinguish emergencies,
medical-decision requests, symptoms that need clarification, and ordinary
care questions. Ordinary symptoms must not create tickets before enough
information is collected.

## Scope

This change applies to elder and authorized family AI conversations. It keeps
the existing emergency-ticket path and MySQL conversation history. It does not
diagnose conditions, prescribe treatment, or replace a clinician.

## Risk Levels

### CRITICAL

Signals include breathing difficulty, chest pain, unconsciousness, confusion,
serious fall injury, self-harm risk, or an equivalent emergency description.
The service immediately creates one urgent assistance ticket and one linked
customer-service ticket per conversation and risk event.

### FOLLOW_UP

Signals include abdominal pain, dizziness, fever, vomiting, cough, fatigue,
minor fall, or another symptom without an immediate emergency signal. The
service returns a targeted clarification question and does not create a
ticket yet.

### WARNING

Requests for diagnosis, medication changes, dosage, prescriptions, or
treatment decisions receive a refusal that directs the user to a clinician.
These requests do not create an emergency ticket.

### NORMAL

Daily care, hydration, nutrition, sleep, reminders, and general non-clinical
support are sent to the configured model provider and persisted as normal
conversation messages.

## Conversation State

The current AI session gains a pending triage context:

- `triageLevel`
- `triageCategory`
- `triageQuestion`
- `triageAwaitingAnswer`
- `triageFingerprint`

The context is persisted with the session so switching pages or restarting
the frontend does not lose the follow-up state. A new user message is
evaluated together with the pending symptom context. Once the context is
resolved, it is cleared.

The fingerprint combines the session and symptom category. It prevents
duplicate urgent tickets when the same emergency is submitted repeatedly.

## Request Flow

```text
user message
  -> local classifier
  -> CRITICAL: persist result, create urgent tickets, return warning
  -> WARNING: return medical-decision refusal
  -> FOLLOW_UP: persist question, return question without ticket
  -> NORMAL: call cloud provider, guard response, persist answer
```

When a pending follow-up receives a high-risk answer, the combined original
symptom and answer is reclassified. If it becomes `CRITICAL`, the normal
ticket flow runs. If it remains `FOLLOW_UP`, the assistant asks the next
minimum question. If it becomes `NORMAL`, the assistant provides general
care guidance without making a diagnosis.

## API Contract

The existing message response adds:

```json
{
  "triageLevel": "FOLLOW_UP",
  "triageCategory": "ABDOMINAL_PAIN",
  "followUpRequired": true,
  "followUpQuestion": "请问疼痛持续多久，是否伴有发热、呕吐或便血？"
}
```

For normal, warning, and resolved messages, `followUpRequired` is `false` and
`followUpQuestion` is `null`. Existing `answer`, safety, and ticket fields
remain compatible.

## Safety Boundaries

- Local classification always runs before the cloud model.
- Cloud output cannot downgrade a local `CRITICAL` or `WARNING` decision.
- The assistant never claims that a family member, nurse, or emergency service
  was contacted unless an actual system operation confirms it.
- Follow-up questions must not contain diagnosis, medication, dosage, or
  treatment instructions.
- The source message and follow-up answer are stored for audit; API keys,
  tokens, and private provider payloads are never stored.

## UI Behavior

The elder assistant displays a follow-up question as a normal assistant
message and keeps the composer available. If an urgent ticket is created, the
existing emergency result state is displayed with the ticket status. The
conversation history API restores the triage context and its messages.

## Verification

Automated tests cover:

- each critical category and duplicate-ticket prevention;
- abdominal pain, dizziness, fever, vomiting, cough, fatigue, and minor fall
  follow-up questions;
- follow-up escalation to critical after a dangerous answer;
- warning medication and diagnosis requests;
- normal questions reaching the provider;
- persistence and restoration of pending triage state;
- family authorization and elder authorization;
- frontend rendering of follow-up questions and ticket results.

Manual acceptance uses an elder account and an authorized family account to
run ordinary, follow-up, escalation, warning, and emergency conversations.
