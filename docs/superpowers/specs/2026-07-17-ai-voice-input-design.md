# AI Voice Input Design

## Goal

Add Mandarin-first voice input to the elder AI care assistant. The user records a short question, reviews the transcription, edits it if needed, and explicitly sends the final text to the existing AI conversation. Emergency assistance remains available and is not removed.

## Scope

Included:

- Elder H5 voice recording with start/stop controls.
- Maximum recording duration of 60 seconds.
- Upload to the user backend as multipart audio.
- Alibaba Cloud Model Studio `qwen3-asr-flash` synchronous transcription.
- Transcription preview, edit, retry, and explicit send.
- Family assistant compatibility where the selected elder is already authorized.
- Backend validation, authentication, failure handling, and focused tests.

Not included:

- Continuous listening.
- Real-time partial transcription.
- Text-to-speech playback.
- Persisting or replaying original recordings.
- Browser-only Web Speech API as a fallback.

## Architecture

The frontend calls:

```text
POST /api/v1/ai/speech/transcriptions
Content-Type: multipart/form-data
audio: recorded file
```

The user backend authenticates the caller, validates the audio, reads it in memory, and calls Alibaba Cloud Model Studio using the server-side workspace endpoint, API key, and ASR model configuration. The backend returns the transcript and does not write the source audio to MySQL, MinIO, application logs, or chat history.

The existing AI message endpoint remains the only endpoint that creates a conversation message:

```text
POST /api/v1/ai/sessions/{sessionId}/messages
```

After the user confirms the transcript, the frontend sends the final text with `messageType=VOICE`. The conversation stores the final text and existing safety metadata, not the original audio.

The ASR model and endpoint are configured independently from the text-generation model while sharing the existing Alibaba Cloud workspace credentials where the account has access. The frontend never receives the API key.

## Frontend Behavior

The AI assistant adds a large microphone control next to the text composer:

1. Tap once to start recording.
2. Show a prominent recording state and elapsed time.
3. Tap again to stop, or stop automatically at 60 seconds.
4. Show an in-progress transcription state.
5. Put the returned transcript into the editable composer without sending it.
6. Allow editing, retrying recognition, discarding, or sending.
7. Mark the sent message as `VOICE` while displaying the final text in the conversation.

The unsent transcript is isolated by role and elder identity using the existing assistant draft/session scoping. It is cleared after successful send or explicit discard.

The first implementation targets H5 using the existing platform recording abstraction. Audio format negotiation must prefer a backend-supported format and reject unsupported results before upload.

## Backend Validation and Privacy

The endpoint requires the same authenticated elder/family ownership and binding checks used by the AI session APIs. Family requests must identify an elder that the family member is authorized to access.

The backend enforces:

- Non-empty audio.
- Supported audio MIME type and actual file signature where available.
- Maximum size of 10 MB.
- Maximum duration of 60 seconds when duration metadata is available.
- No audio content in logs or exception messages.
- Timeout and bounded response handling for the cloud provider.

The backend retains only the returned transcript for the subsequent user-confirmed AI message. Temporary byte buffers are released after the request. Failed requests return a user-safe error and do not create an AI message.

## Error States

- Microphone permission denied: explain how to enable permission and keep text input available.
- Recording unsupported: hide or disable voice capture and keep text input available.
- Empty or unusable audio: ask the user to record again.
- Invalid format or size: reject before cloud invocation.
- Cloud timeout, rate limit, or provider error: preserve the recording for retry during the current view, without sending it to AI.
- Empty transcription: ask the user to speak closer or more clearly.
- Session/authentication failure: use the existing session error behavior and do not persist a partial message.

## Testing and Acceptance

Backend tests cover:

- Successful transcription response parsing.
- Provider timeout, non-success status, malformed response, and empty transcript.
- Audio validation and authentication/elder ownership.
- Confirmation boundary: transcription alone does not create a conversation message.

Frontend checks cover:

- Recording start/stop/auto-stop state transitions.
- Transcription preview is editable and not auto-sent.
- Retry and discard behavior.
- `VOICE` message submission after confirmation.
- Draft isolation between elders and roles.
- Existing emergency assistance flow remains available.

Manual acceptance uses at least ten ordinary Mandarin care questions in a quiet environment. Each case verifies recording, transcription preview, correction, send, conversation persistence, and absence of the source audio from stored application data.

## Success Criteria

The feature is complete only when an elder can ask a Mandarin care question without typing, review the recognized text, correct it, send it to the existing AI session, and later see the final text in conversation history. A recognition failure must not silently send a guessed or empty question.
