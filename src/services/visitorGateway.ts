export type GatewaySource = {
  title: string
  page?: string | number
  excerpt: string
}

export type GatewayEvent = {
  type: string
  data: {
    delta?: string
    stage?: string
    message?: string
    code?: string
    sources?: GatewaySource[]
  }
}

export type RecognitionStatus = 'READY' | 'NOT_FOUND' | 'ERROR' | 'CONFIRMED'
export type KnowledgeStatus = 'VERIFIED' | 'PARTIAL' | 'NOT_FOUND'

export type RecognitionCandidate = {
  candidate_id: string
  name: string
  confidence: number
  visual_basis: string[]
  knowledge_status: KnowledgeStatus
  sources: GatewaySource[]
}

export type RecognitionResult = {
  recognition_id: string
  status: RecognitionStatus
  observations: string[]
  candidates: RecognitionCandidate[]
  message: string
  created_at: string
  confirmed_candidate_id?: string
}

type GuideInput = {
  message: string
  mode: 'quick' | 'deep' | 'child'
  museum?: string
  exhibition?: string
  exhibitId?: string
}

const baseUrl = (import.meta.env.VITE_VISITOR_GATEWAY_BASE_URL || '/api/visitor/v1').replace(/\/$/, '')
const sessionStorageKey = 'zhibo-visitor-session'

function storedSession() {
  return typeof window === 'undefined' ? '' : window.sessionStorage.getItem(sessionStorageKey) || ''
}

async function createSession(signal?: AbortSignal) {
  const response = await fetch(`${baseUrl}/sessions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({}),
    signal,
  })
  if (!response.ok) throw new Error('无法创建游客会话')
  const data = await response.json() as { session_id?: string }
  if (!data.session_id) throw new Error('游客会话响应无效')
  window.sessionStorage.setItem(sessionStorageKey, data.session_id)
  return data.session_id
}

async function ensureSession(signal?: AbortSignal) {
  const current = storedSession()
  return current || createSession(signal)
}

function parseEvent(eventName: string, data: string): GatewayEvent | null {
  if (!data || data === '[DONE]') return { type: eventName || 'done', data: {} }
  try {
    return { type: eventName || 'message', data: JSON.parse(data) as GatewayEvent['data'] }
  } catch {
    return null
  }
}

async function requestStream(
  sessionId: string,
  input: GuideInput,
  onEvent: (event: GatewayEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(`${baseUrl}/guide/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Visitor-Session': sessionId,
    },
    body: JSON.stringify({
      session_id: sessionId,
      message: input.message,
      mode: input.mode,
      museum: input.museum,
      exhibition: input.exhibition,
      exhibit_id: input.exhibitId,
    }),
    signal,
  })
  if (response.status === 401) throw new Error('SESSION_EXPIRED')
  if (!response.ok) throw new Error(response.status === 429 ? 'RATE_LIMITED' : 'GATEWAY_UNAVAILABLE')
  const reader = response.body?.getReader()
  if (!reader) throw new Error('GATEWAY_UNAVAILABLE')

  const decoder = new TextDecoder()
  let buffer = ''
  let eventName = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      const block = buffer.slice(0, boundary)
      buffer = buffer.slice(boundary + 2)
      const data = block.split('\n').filter((line) => line.startsWith('data:')).map((line) => line.slice(5).trimStart()).join('\n')
      const blockEventName = block.split('\n').find((line) => line.startsWith('event:'))?.slice(6).trim() || eventName
      const event = parseEvent(blockEventName, data)
      if (event) onEvent(event)
      eventName = ''
      boundary = buffer.indexOf('\n\n')
    }
  }
}

export async function streamGuide(
  input: GuideInput,
  onEvent: (event: GatewayEvent) => void,
  signal?: AbortSignal,
) {
  let sessionId = await ensureSession(signal)
  try {
    await requestStream(sessionId, input, onEvent, signal)
  } catch (error) {
    if (error instanceof Error && error.message === 'SESSION_EXPIRED') {
      window.sessionStorage.removeItem(sessionStorageKey)
      sessionId = await createSession(signal)
      await requestStream(sessionId, input, onEvent, signal)
      return
    }
    throw error
  }
}

function gatewayError(status: number, body?: { detail?: string }) {
  if (status === 401) return new Error('SESSION_EXPIRED')
  if (status === 429) return new Error('访问有些频繁，请稍后再试。')
  return new Error(typeof body?.detail === 'string' ? body.detail : '游客网关暂时不可用')
}

function uploadOnce(
  sessionId: string,
  file: File,
  onProgress: (percent: number) => void,
  signal?: AbortSignal,
) {
  return new Promise<RecognitionResult>((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Aborted', 'AbortError'))
      return
    }
    const request = new XMLHttpRequest()
    const onAbort = () => request.abort()
    request.open('POST', `${baseUrl}/recognitions`)
    request.setRequestHeader('X-Visitor-Session', sessionId)
    request.responseType = 'json'
    request.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress(Math.round((event.loaded / event.total) * 100))
    }
    request.onload = () => {
      signal?.removeEventListener('abort', onAbort)
      if (request.status >= 200 && request.status < 300) {
        onProgress(100)
        resolve(request.response as RecognitionResult)
        return
      }
      reject(gatewayError(request.status, request.response as { detail?: string }))
    }
    request.onerror = () => {
      signal?.removeEventListener('abort', onAbort)
      reject(new Error('无法连接游客网关'))
    }
    request.onabort = () => {
      signal?.removeEventListener('abort', onAbort)
      reject(new DOMException('Aborted', 'AbortError'))
    }
    signal?.addEventListener('abort', onAbort, { once: true })
    const form = new FormData()
    form.append('image', file, file.name)
    request.send(form)
  })
}

export async function createRecognition(
  file: File,
  onProgress: (percent: number) => void,
  signal?: AbortSignal,
) {
  let sessionId = await ensureSession(signal)
  try {
    return await uploadOnce(sessionId, file, onProgress, signal)
  } catch (error) {
    if (error instanceof Error && error.message === 'SESSION_EXPIRED') {
      window.sessionStorage.removeItem(sessionStorageKey)
      sessionId = await createSession(signal)
      return uploadOnce(sessionId, file, onProgress, signal)
    }
    throw error
  }
}

async function recognitionRequest(path: string, init: RequestInit, signal?: AbortSignal) {
  const request = async (sessionId: string) => {
    const response = await fetch(`${baseUrl}${path}`, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        'X-Visitor-Session': sessionId,
        ...init.headers,
      },
      signal,
    })
    if (!response.ok) {
      const body = await response.json().catch(() => undefined) as { detail?: string } | undefined
      throw gatewayError(response.status, body)
    }
    return response.json() as Promise<RecognitionResult>
  }
  let sessionId = await ensureSession(signal)
  try {
    return await request(sessionId)
  } catch (error) {
    if (error instanceof Error && error.message === 'SESSION_EXPIRED') {
      window.sessionStorage.removeItem(sessionStorageKey)
      sessionId = await createSession(signal)
      return request(sessionId)
    }
    throw error
  }
}

export function getRecognition(recognitionId: string, signal?: AbortSignal) {
  return recognitionRequest(`/recognitions/${encodeURIComponent(recognitionId)}`, { method: 'GET' }, signal)
}

export function confirmRecognition(recognitionId: string, candidateId: string, signal?: AbortSignal) {
  return recognitionRequest(
    `/recognitions/${encodeURIComponent(recognitionId)}/confirm`,
    { method: 'POST', body: JSON.stringify({ candidate_id: candidateId }) },
    signal,
  )
}
