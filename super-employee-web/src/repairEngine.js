/** @author yangdb */
/** 本地修复引擎展示名（与后端 RepairEngineLabels 对齐） */
export const REPAIR_ENGINE_OPTIONS = [
  { value: 'auto', label: '自动（失败切换）' },
  { value: 'codex', label: 'CodeX CLI' },
  { value: 'cursor', label: 'Cursor CLI' },
  { value: 'claude', label: 'Claude Code CLI' }
]

export function repairEngineLabel(value) {
  return REPAIR_ENGINE_OPTIONS.find(item => item.value === value)?.label || value || '自动（失败切换）'
}

export function repairEngineDisplayName(engine) {
  const id = String(engine || '').trim().toLowerCase()
  if (id === 'cursor') return 'Cursor'
  if (id === 'claude' || id.startsWith('claude')) return 'Claude Code'
  if (id === 'codex') return 'CodeX'
  if (id === 'auto' || !id) return '本地修复'
  return String(engine)
}

export function repairEngineCliName(engine) {
  const id = String(engine || '').trim().toLowerCase()
  if (id === 'cursor') return 'Cursor CLI'
  if (id === 'claude' || id.startsWith('claude')) return 'Claude Code CLI'
  if (id === 'codex') return 'CodeX CLI'
  if (id === 'auto' || !id) return '本地修复引擎（自动切换）'
  return '本地修复引擎'
}
