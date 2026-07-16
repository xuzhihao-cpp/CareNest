import { readFileSync } from 'node:fs'

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8')
const shell = read('../src/apps/elder/ElderApp.vue')
const reminders = read('../src/components/StageThirtyTwoReminderCenter.vue')
const ai = read('../src/components/StageFortyOneAiAssistantPanel.vue')

for (const token of ['bottom-nav', '首页', '提醒', 'AI', '我的']) {
  if (!shell.includes(token)) throw new Error(`elder shell missing ${token}`)
}
if (shell.includes('reminder-entry')) throw new Error('detached reminder entry still exists')
if (!reminders.includes('next-reminder')) throw new Error('reminder center missing next-reminder hierarchy')
for (const token of ['conversation', 'starter-prompts', 'chat-composer']) {
  if (!ai.includes(token)) throw new Error(`AI assistant missing ${token}`)
}
console.log('elder care shell UI contract passed')
