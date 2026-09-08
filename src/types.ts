export type GuideMode = 'quick' | 'deep' | 'child'

export type Exhibit = {
  id: string
  name: string
  era: string
  material: string
  category: string
  summary: string
  image: string
  tags: string[]
  observations: Array<{
    title: string
    detail: string
  }>
  sourceTitle: string
  sources?: Source[]
}

export type Source = {
  title: string
  page?: string
  excerpt: string
  status: 'verified' | 'interpretation' | 'missing'
}

export type PassportEntry = {
  exhibitId: string
  visitedAt: string
  mode: GuideMode
}
