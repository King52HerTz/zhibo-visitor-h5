import { computed, onMounted, onUnmounted, ref } from 'vue'
import silkImage from '../assets/exhibits/silk.svg'
import lacquerImage from '../assets/exhibits/lacquer.svg'
import silkBookImage from '../assets/exhibits/silk-book.svg'
import { demoExhibits, demoSources } from '../mock/data'
import type { Exhibit, Source } from '../types'

type CatalogStatus = 'bundled' | 'syncing' | 'fresh' | 'cached' | 'error'
type CatalogSource = Omit<Source, 'page'> & { page?: string | number }
type CatalogExhibit = Omit<Exhibit, 'image' | 'sourceTitle' | 'sources'> & {
  image_key: string
  image_url?: string
  sources: CatalogSource[]
}
type CatalogPayload = {
  version: string
  published_at: string
  content_source: 'bundled' | 'remote'
  exhibits: CatalogExhibit[]
}

const CACHE_KEY = 'zhibo-visitor-catalog-v1'
const baseUrl = (import.meta.env.VITE_VISITOR_GATEWAY_BASE_URL || '/api/visitor/v1').replace(/\/$/, '')
const imageMap: Record<string, string> = { silk: silkImage, lacquer: lacquerImage, 'silk-book': silkBookImage }

function bundledExhibits(): Exhibit[] {
  return demoExhibits.map((exhibit) => ({
    ...exhibit,
    sources: demoSources.filter((source) => source.title === exhibit.sourceTitle),
  }))
}

function isCatalogPayload(value: unknown): value is CatalogPayload {
  if (!value || typeof value !== 'object') return false
  const payload = value as Partial<CatalogPayload>
  return typeof payload.version === 'string'
    && typeof payload.published_at === 'string'
    && (payload.content_source === 'bundled' || payload.content_source === 'remote')
    && Array.isArray(payload.exhibits)
    && payload.exhibits.length > 0
    && payload.exhibits.every((item) => item && typeof item.id === 'string' && typeof item.name === 'string' && typeof item.image_key === 'string' && Array.isArray(item.observations) && Array.isArray(item.sources))
}

function toExhibits(payload: CatalogPayload): Exhibit[] {
  return payload.exhibits.map((item) => ({
    id: item.id,
    name: item.name,
    era: item.era,
    material: item.material,
    category: item.category,
    summary: item.summary,
    image: item.image_url || imageMap[item.image_key] || silkImage,
    tags: item.tags,
    observations: item.observations,
    sourceTitle: item.sources[0]?.title || '暂无公开来源',
    sources: item.sources.map((source) => ({ ...source, page: source.page === undefined ? undefined : String(source.page) })),
  }))
}

function loadCache(): CatalogPayload | null {
  if (typeof window === 'undefined') return null
  try {
    const value: unknown = JSON.parse(window.localStorage.getItem(CACHE_KEY) || 'null')
    return isCatalogPayload(value) ? value : null
  } catch {
    return null
  }
}

const initialCache = loadCache()
const exhibits = ref<Exhibit[]>(initialCache ? toExhibits(initialCache) : bundledExhibits())
const version = ref(initialCache?.version || '')
const publishedAt = ref(initialCache?.published_at || '')
const contentSource = ref<'bundled' | 'remote'>(initialCache?.content_source || 'bundled')
const status = ref<CatalogStatus>(initialCache ? 'cached' : 'bundled')
const isOnline = ref(typeof navigator === 'undefined' ? true : navigator.onLine)
let syncPromise: Promise<void> | null = null

export function syncCatalog(force = false): Promise<void> {
  if (syncPromise) return syncPromise
  syncPromise = (async () => {
    const previousStatus = status.value
    status.value = 'syncing'
    try {
      const response = await fetch(`${baseUrl}/catalog`, {
        headers: !force && version.value ? { 'If-None-Match': `"${version.value}"` } : {},
      })
      if (response.status === 304) {
        status.value = 'fresh'
        return
      }
      if (!response.ok) throw new Error('目录同步失败')
      const payload: unknown = await response.json()
      if (!isCatalogPayload(payload)) throw new Error('目录格式无效')

      // 先完成整包校验，再一次性替换缓存，下载中断不会破坏上一版。
      const nextExhibits = toExhibits(payload)
      window.localStorage.setItem(CACHE_KEY, JSON.stringify(payload))
      exhibits.value = nextExhibits
      version.value = payload.version
      publishedAt.value = payload.published_at
      contentSource.value = payload.content_source
      status.value = 'fresh'
    } catch {
      status.value = previousStatus === 'cached' || version.value ? 'cached' : 'error'
    } finally {
      syncPromise = null
    }
  })()
  return syncPromise
}

export function useCatalog() {
  const themes = computed(() => [...new Set(exhibits.value.flatMap((item) => item.tags))])
  const sources = computed(() => {
    const unique = new Map<string, Source>()
    exhibits.value.flatMap((item) => item.sources || []).forEach((source) => unique.set(`${source.title}-${source.page || ''}`, source))
    return [...unique.values()]
  })
  const contentLabel = computed(() => {
    if (!isOnline.value) return '离线浏览 · 最近内容'
    if (status.value === 'syncing') return '正在同步馆藏'
    if (status.value === 'fresh') return contentSource.value === 'remote' ? '馆藏已是最新' : '使用内置首版'
    if (status.value === 'cached') return '使用本机缓存'
    return '使用内置首版'
  })

  const updateOnline = () => {
    isOnline.value = navigator.onLine
    if (isOnline.value) void syncCatalog()
  }
  onMounted(() => {
    window.addEventListener('online', updateOnline)
    window.addEventListener('offline', updateOnline)
    void syncCatalog()
  })
  onUnmounted(() => {
    window.removeEventListener('online', updateOnline)
    window.removeEventListener('offline', updateOnline)
  })

  return {
    exhibits,
    themes,
    sources,
    status,
    isOnline,
    version,
    publishedAt,
    contentSource,
    contentLabel,
    sync: syncCatalog,
  }
}
