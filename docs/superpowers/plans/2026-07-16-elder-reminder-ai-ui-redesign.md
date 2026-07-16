# Elder Reminder And AI UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace detached reminder/AI buttons with a fixed four-destination elder mobile shell and redesign reminder and AI workspaces for large, clear, task-focused interaction.

**Architecture:** `ElderApp.vue` owns primary navigation and routes existing features into home and account views. The reminder and AI components remain API-owning feature components, with local presentation state only. Existing stage 32 and 41 backend contracts remain unchanged.

**Tech Stack:** Vue 3 script setup, uni-app H5, TypeScript, existing request client, CSS scoped styles, lucide-vue-next icons, Playwright/in-app browser verification.

## Global Constraints

- Preserve every existing elder workflow and real API call.
- Use fixed `首页 / 提醒 / AI / 我的` navigation with stable tap targets at least 44px high.
- Do not display internal session, message, reminder, or ticket IDs.
- Do not add gradients, decorative blobs, nested cards, or marketing copy.
- Keep the primary elder workspace within 440px on wide screens.
- Preserve local medical safety guardrails and Qwen behavior.

---

### Task 1: Lock UI Structure With Smoke Tests

**Files:**
- Create: `frontend/scripts/test-elder-care-shell.mjs`
- Modify: `frontend/package.json`

**Interfaces:**
- Consumes: source text from `ElderApp.vue`, `StageThirtyTwoReminderCenter.vue`, and `StageFortyOneAiAssistantPanel.vue`.
- Produces: `pnpm test:elder-care-ui`, a regression check for navigation labels, reminder hierarchy, conversation state, and removal of detached entries.

- [ ] Write a Node assertion script that requires `bottom-nav`, all four destination labels, `next-reminder`, `conversation`, `starter-prompts`, and `chat-composer`, and rejects `reminder-entry`.
- [ ] Add `"test:elder-care-ui": "node scripts/test-elder-care-shell.mjs"` to `frontend/package.json`.
- [ ] Run `pnpm --dir frontend run test:elder-care-ui` and verify it fails because the new shell selectors are absent.
- [ ] Commit the failing UI contract test with `test: define elder care shell ui contract`.

### Task 2: Build The Elder Mobile Shell

**Files:**
- Modify: `frontend/src/apps/elder/ElderApp.vue`
- Modify: `frontend/package.json`
- Modify: `pnpm-lock.yaml`

**Interfaces:**
- Consumes: existing elder feature components and `AuthUser`.
- Produces: `primaryView: 'home' | 'reminders' | 'ai' | 'account'`, a stable `.bottom-nav`, and full-height `.elder-shell` content.

- [ ] Add `lucide-vue-next` through `pnpm --dir frontend add lucide-vue-next`.
- [ ] Replace detached reminder/AI buttons and the top horizontal feature list with the four-item bottom navigation.
- [ ] Render overview/health/feedback/emergency in `home`; reminder and AI components in their dedicated views; profile/medical/binding/reports/logout in `account`.
- [ ] Use icon plus text buttons with `aria-current`, stable badge dimensions, safe-area padding, and reduced-motion support.
- [ ] Run the UI smoke test and TypeScript check; verify shell assertions pass except feature-component assertions that remain for Tasks 3-4.

### Task 3: Redesign Reminder Center Around The Next Action

**Files:**
- Modify: `frontend/src/components/StageThirtyTwoReminderCenter.vue`

**Interfaces:**
- Consumes: `getElderReminders`, `getElderReminderRecords`, `actOnElderReminder`, `ReminderItem`, and `ReminderStatus`.
- Produces: computed `nextReminder`, `todayReminders`, `laterReminders`, `completedReminders`, and the `.next-reminder` workspace.

- [ ] Add date grouping and timing-label computed state without changing API data.
- [ ] Build a next-reminder focal section with one dominant completion action and two secondary actions.
- [ ] Render remaining reminders in `今天 / 稍后 / 已完成` sections and keep execution records behind a segmented switch.
- [ ] Replace corrupted or technical copy with concise Chinese user language; preserve retry, loading, empty, success, and error states.
- [ ] Add completion transition and reduced-motion fallback without delaying API refresh.
- [ ] Run `test:stage32`, `test:elder-care-ui`, and `typecheck`.

### Task 4: Build A Continuous AI Conversation

**Files:**
- Modify: `frontend/src/components/StageFortyOneAiAssistantPanel.vue`
- Modify: `frontend/src/types/stageFortyOne.ts`

**Interfaces:**
- Consumes: `createAiSession` and `sendAiMessage`.
- Produces: local `ChatMessage[]`, `.conversation`, `.starter-prompts`, `.chat-composer`, inline warning/critical safety messages, and a thinking state.

- [ ] Define a local display-message type with `role`, `content`, `safetyLevel`, and assistance flags; do not imply server-persisted history retrieval.
- [ ] Add an initial assistant greeting and three starter prompts.
- [ ] Append the user's message immediately, append the assistant response on success, and retain prior messages for the component lifetime.
- [ ] Render safety guidance immediately below its triggering assistant message and use user-facing assistance language without IDs.
- [ ] Add a fixed composer with send icon, Enter-to-send on H5, disabled/loading behavior, and viewport-safe spacing.
- [ ] Run `test:stage41`, `test:elder-care-ui`, `typecheck`, and `build:h5`.

### Task 5: Browser And Real-API Acceptance

**Files:**
- Modify: `docs/stage-check/phase-41-43-ai-assistant-full-stack.md` if present, otherwise create it.

**Interfaces:**
- Consumes: Docker frontend at `http://localhost:3000` and real stage 32/41 APIs.
- Produces: documented mobile/desktop visual and workflow evidence.

- [ ] Rebuild frontend with `docker compose --env-file docker/env/.env -f docker-compose.yml -f docker-compose.app.yml up -d --build frontend`.
- [ ] Verify 390x844 and 1280x900 screenshots: no overlap, clipping, blank panels, hidden composer, or bottom-nav obstruction.
- [ ] Verify reminder load/action states and AI normal/critical messages through the browser; inspect console for runtime errors.
- [ ] Run final `test:stage32`, `test:stage41`, `test:elder-care-ui`, `typecheck`, and `build:h5`.
- [ ] Record commands and observed outcomes in the stage-check document and commit implementation with focused commit messages.

## Self-Review

- Spec coverage: fixed shell, next-reminder hierarchy, continuous AI conversation, safety messages, responsive width, motion, accessibility, real API and browser verification are assigned to Tasks 2-5.
- Placeholder scan: no unfinished implementation placeholders are present.
- Type consistency: `primaryView` and `ChatMessage` are local presentation types; backend request/response types remain unchanged.
