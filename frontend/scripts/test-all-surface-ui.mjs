import { readFileSync } from 'node:fs'

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8')
const elder = read('../src/apps/elder/ElderApp.vue')
const family = read('../src/apps/family/FamilyApp.vue')
const nurse = read('../src/apps/nurse/NurseApp.vue')
const admin = read('../src/apps/admin/AdminApp.vue')
const ai = read('../src/components/StageFortyOneAiAssistantPanel.vue')
const healthArchive = read('../src/components/StageNineteenHealthArchivePanel.vue')
const recommendations = read('../src/components/StageTwentyNineRecommendationPanel.vue')

function requireTokens(source, name, tokens) {
  for (const token of tokens) {
    if (!source.includes(token)) throw new Error(`${name} is missing UI contract token: ${token}`)
  }
}

requireTokens(elder, 'elder shell', ['shell-header', 'shell-main', 'bottom-nav'])
requireTokens(family, 'family shell', ['shell-header', 'shell-main', 'bottom-nav', 'primaryView'])
requireTokens(nurse, 'nurse shell', ['shell-header', 'shell-main', 'bottom-nav', 'primaryView'])
requireTokens(admin, 'admin shell', ['admin-app', 'admin-nav', 'admin-main'])

for (const [name, source] of [['family', family], ['nurse', nurse]]) {
  requireTokens(source, `${name} mobile layout`, [
    'max-width:100%',
    'overflow-x:hidden',
    '@media(min-width:768px)'
  ])
}

requireTokens(family, 'family bottom navigation', ['repeat(5,minmax(0,1fr))', 'safe-area-inset-bottom'])
requireTokens(nurse, 'nurse bottom navigation', ['repeat(4,minmax(0,1fr))', 'safe-area-inset-bottom'])
requireTokens(ai, 'AI composer', ['chat-composer', 'min-width:0', 'max-width:100%'])
requireTokens(healthArchive, 'health archive controls', ['min-height:42px', 'align-items:center'])
requireTokens(recommendations, 'recommendation controls', ['min-height:80rpx', 'min-height:40px'])
requireTokens(nurse, 'nurse record actions', ['suggestion-record-entry', 'min-height:40px'])

console.log('all user-facing surface UI contracts passed')
