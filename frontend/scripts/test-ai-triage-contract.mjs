import { readFileSync } from 'node:fs'

const component = readFileSync(new URL('../src/components/StageFortyOneAiAssistantPanel.vue', import.meta.url), 'utf8')
const types = readFileSync(new URL('../src/types/stageFortyOne.ts', import.meta.url), 'utf8')
const api = readFileSync(new URL('../src/api/stageFortyOne.ts', import.meta.url), 'utf8')

for (const field of ['triageLevel', 'triageCategory', 'followUpRequired', 'followUpQuestion']) {
  if (!types.includes(field)) throw new Error(`missing type field: ${field}`)
  if (!component.includes(field)) throw new Error(`missing component field: ${field}`)
}
if (!api.includes('/ai/sessions/')) throw new Error('ai session API contract is missing')
if (!component.includes("messageType: pendingVoiceReview.value ? 'VOICE' : 'TEXT'")) {
  throw new Error('voice message type contract was removed')
}
if (!component.includes('assistanceCreated')) throw new Error('critical ticket result is not rendered')
console.log('ai triage contract passed')
