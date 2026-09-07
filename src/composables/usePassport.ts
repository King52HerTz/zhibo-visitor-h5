import { computed, ref } from 'vue'
import type { Exhibit, GuideMode, PassportEntry } from '../types'

const STORAGE_KEY = 'zhibo-visitor-passport-demo'

function loadEntries(): PassportEntry[] {
  if (typeof window === 'undefined') return []
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) as PassportEntry[] : []
  } catch {
    return []
  }
}

const entries = ref<PassportEntry[]>(loadEntries())

function persist() {
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(entries.value))
  }
}

export function usePassport() {
  const count = computed(() => entries.value.length)

  function isSaved(exhibitId: string) {
    return entries.value.some((entry) => entry.exhibitId === exhibitId)
  }

  function toggle(exhibit: Exhibit, mode: GuideMode = 'quick') {
    if (isSaved(exhibit.id)) {
      entries.value = entries.value.filter((entry) => entry.exhibitId !== exhibit.id)
    } else {
      entries.value = [
        ...entries.value,
        { exhibitId: exhibit.id, visitedAt: new Date().toISOString(), mode },
      ]
    }
    persist()
  }

  function clear() {
    entries.value = []
    persist()
  }

  return { entries, count, isSaved, toggle, clear }
}
