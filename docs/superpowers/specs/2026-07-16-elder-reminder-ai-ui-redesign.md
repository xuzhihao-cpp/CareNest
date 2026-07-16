# Elder Reminder And AI UI Redesign

## Objective

Replace the two detached top-level buttons for the reminder center and AI care
assistant with a coherent mobile application shell. The result must prioritize
large tap targets, clear status, and low cognitive load for elder users while
preserving all existing backend contracts and real API behavior.

## Visual Thesis

A calm, high-contrast care application with white and soft-gray surfaces, deep
green actions, restrained amber reminder states, and coral emergency states.
The interface uses spacing, typography, icons, and section dividers before
borders or shadows. Routine screens must not use gradients, decorative blobs,
or nested cards.

## Navigation

The elder application uses a fixed four-item bottom navigation:

1. `首页`: current elder overview and frequently used care functions.
2. `提醒`: full reminder workspace with an unresolved-count indicator.
3. `AI`: full conversational AI care assistant.
4. `我的`: profile, binding, medical files, reports, and sign-out actions.

The existing detached reminder and AI buttons are removed. Primary destinations
occupy the available viewport above the bottom navigation. The navigation
respects mobile safe areas and does not resize when badges or labels change.
When the software keyboard opens in the AI view, the composer remains visible
without covering the latest message.

Existing elder features remain reachable. The redesign changes navigation and
presentation only; it does not remove profile, health, feedback, emergency,
medical-file, binding, or report workflows.

## Reminder Experience

### Primary hierarchy

The first visible section is the next actionable reminder. It shows the title,
scheduled time, concise content, and a human-readable timing label. Its dominant
action is `完成`; `稍后 30 分钟` and `需要协助` are secondary actions.

Remaining reminders appear below in plain grouped sections:

- `今天`
- `稍后`
- `已完成`

Each reminder row has one stable height range, a status indicator, and no
internal identifier. Completed or non-actionable rows do not show action
controls. Execution history is available through a `提醒 / 记录` segmented
control and is not mixed into the active task list.

### States and feedback

- Loading uses a restrained inline state without shifting the page shell.
- Empty state says that there are currently no reminders and does not imply an
  API failure.
- API failure shows one retry action and preserves the navigation shell.
- Completing a reminder gives immediate success feedback, then refreshes the
  next reminder and grouped list.
- `需要协助` uses a distinct coral treatment but is not styled as a completed
  task.

## AI Assistant Experience

### Conversation

The AI view is a continuous message workspace rather than a single answer
panel. It includes:

- A compact header identifying `AI 照护助手` and online availability.
- An initial assistant greeting.
- Three short starter questions before the first user message.
- Distinct user and assistant messages with readable line length.
- A fixed bottom composer with text input and an icon-based send action.
- A visible thinking state while a model request is pending.

Messages remain visible for the lifetime of the current component session. The
frontend does not invent persisted history because the current API has no
message-history endpoint. A future history endpoint can replace the local
message collection without changing visual components.

### Safety and assistance

`WARNING` and `CRITICAL` feedback appears directly after the assistant message
that triggered it:

- `WARNING`: the platform assistance request has been submitted and will be
  reviewed.
- `CRITICAL`: an urgent assistance ticket has been created, with explicit advice
  to contact family, platform support, or local emergency services immediately.

Internal session, message, and ticket IDs are never displayed. Ticket states use
user-facing Chinese labels such as `平台已收到`, `客服处理中`, and `已解决`.
Safety presentation does not alter the backend's local guardrail and Qwen cloud
provider behavior.

## Component Boundaries

- `ElderApp.vue` owns the application shell, primary navigation, safe-area
  spacing, and mapping existing elder functions into `首页` and `我的`.
- `StageThirtyTwoReminderCenter.vue` owns reminder grouping, next-reminder
  hierarchy, actions, records, and reminder-specific states.
- `StageFortyOneAiAssistantPanel.vue` owns local conversation messages, starter
  questions, model-request state, safety feedback, and the composer.
- Existing API and type modules remain the source of network contracts.
- A mature icon package may be added for familiar navigation and command icons;
  icons must include accessible labels where meaning is not obvious.

## Responsive Behavior

The primary target is a 375-440px mobile viewport. On wider screens, the elder
application remains a centered mobile-width workspace rather than stretching
conversation lines or reminder controls across the desktop. Text must wrap
without overlapping badges or actions. The bottom navigation and composer use
stable dimensions and safe-area padding.

## Motion

- Primary view changes use a fast opacity and 6-8px vertical transition.
- New messages enter from the bottom with a short fade and translation.
- Successful reminder completion briefly confirms the action before the row is
  removed by the refreshed data.

Motion is disabled or reduced for users requesting reduced motion. No looping
decorative animation is used.

## Accessibility

- Primary body text is at least 16px equivalent on H5.
- Tap targets are at least 44px high.
- Color is never the only status signal.
- Icon-only controls have accessible labels.
- Focus, disabled, loading, success, warning, and error states remain visible.
- Emergency language is direct and does not rely on animation.

## Verification

Implementation acceptance requires:

1. Frontend typecheck and H5 production build pass.
2. Existing stage 32 and stage 41 frontend contract smoke tests pass.
3. Playwright or in-app browser screenshots at mobile and desktop widths show no
   overlap, clipping, blank content, or bottom-navigation obstruction.
4. Real Docker APIs verify reminder completion, snooze, assistance request,
   normal Qwen reply, warning escalation, and critical escalation.
5. Browser console contains no runtime errors during both workflows.

## Out Of Scope

- Speech recognition or voice-first interaction.
- Persisted AI message-history retrieval.
- Changes to AI safety classification, Qwen provider selection, or ticket state
  transitions.
- Redesign of family, nurse, or admin navigation beyond shared component fixes
  required to keep existing usage functional.
