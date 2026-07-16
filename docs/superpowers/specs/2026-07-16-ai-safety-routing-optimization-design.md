# AI Safety Routing Optimization Design

## Goal

Let Qwen answer ordinary care questions naturally while keeping emergency escalation, medical-decision refusal, ticket creation, and unsupported platform claims deterministic in the backend.

## Routing

The backend keeps one local pre-check before the cloud request:

- `CRITICAL`: explicit emergency, loss of consciousness, breathing difficulty, severe chest symptoms, fall injury, death, or self-harm wording. Return a fixed emergency response and create both an assistance ticket and a customer-service ticket.
- `WARNING`: explicit requests to start, stop, replace, increase, or reduce medication; requests for diagnosis, prescription, or treatment decisions. Return a fixed refusal and create an assistance ticket.
- `NORMAL`: all other questions, including general symptom care, medication reminders, blood-pressure recording, diet, and daily care, go to Qwen.

Broad single words such as `药`, `血压`, and `症状` must not independently trigger `WARNING`.

## Cloud Response Guard

The cloud response is accepted only when it does not:

- invent a CareNest phone number, address, service schedule, or service capability;
- claim round-the-clock availability or guaranteed platform accompaniment;
- provide a diagnosis, prescription, or instruction to start, stop, replace, increase, or reduce medication.

Rejected responses fall back to the local `NORMAL` guidance and are not persisted as accepted cloud advice. Logs record only the rejection category, never the user message or rejected response body.

## Business Effects

MySQL remains the source of truth. Ticket creation continues to depend on the backend safety level, never on free-form model text. The public API response shape and `NORMAL` / `WARNING` / `CRITICAL` values remain unchanged, so the current elder and family frontends need no contract change.

## Verification

- Unit tests cover emergency wording, explicit medical-decision wording, and ordinary health questions that must reach Qwen.
- Provider tests cover unsupported platform claims and unsafe medical instructions returned by the cloud model.
- Existing AI API and full `backend-user` tests must pass.
- Rebuild `backend-user`, verify health, then exercise one ordinary question and one deterministic risk-classification test without leaving synthetic tickets in the shared demo database.
