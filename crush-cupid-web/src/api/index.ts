import http from './http'
import type {
  BuildEvent,
  ChatHistoryVO,
  Crush,
  CrushCreatePayload,
  MultiChunk,
  Result,
  SkillCatalog,
  Source,
  Version,
} from '@/types'

async function unwrap<T>(p: Promise<{ data: Result<T> }>): Promise<T> {
  const { data } = await p
  if (data.code !== 0) {
    throw new Error(data.message)
  }
  return data.data
}

export async function listCrushes(): Promise<Crush[]> {
  return unwrap(http.get<Result<Crush[]>>('/crush'))
}

export async function getCrush(id: number): Promise<Crush> {
  return unwrap(http.get<Result<Crush>>(`/crush/${id}`))
}

export async function createCrush(payload: CrushCreatePayload): Promise<Crush> {
  return unwrap(http.post<Result<Crush>>('/crush', payload))
}

export async function updateCrush(id: number, payload: Partial<CrushCreatePayload>): Promise<Crush> {
  return unwrap(http.put<Result<Crush>>(`/crush/${id}`, payload))
}

export async function deleteCrush(id: number): Promise<void> {
  await unwrap(http.delete<Result<void>>(`/crush/${id}`))
}

export async function getSkillCatalog(): Promise<SkillCatalog> {
  return unwrap(http.get<Result<SkillCatalog>>('/skill/catalog'))
}

export async function getSkillPrompt(name: string): Promise<string> {
  return unwrap(http.get<Result<string>>(`/skill/prompt/${name}`))
}

/**
 * 流式对话（SSE，POST）。后端每个 chunk 以 {index,content,done} JSON 编码在 data 行；
 * 前端按 index 切气泡，支持 crush 一次连发多条短消息。
 */
export async function streamChat(
  crushSlug: string,
  message: string,
  onChunk: (chunk: MultiChunk) => void,
): Promise<void> {
  await sseStream(
    '/api/chat',
    { crushSlug, message },
    onChunk,
  )
}

/**
 * 主动消息（SSE，POST）。crush 不依赖用户输入而主动发起连发多条消息。
 * contextHint 可选，给 crush 提供场景暗示（如「凌晨三点」「下雨天」）。
 */
export async function proactiveChat(
  crushSlug: string,
  contextHint: string,
  onChunk: (chunk: MultiChunk) => void,
): Promise<void> {
  await sseStream(
    '/api/chat/proactive',
    { crushSlug, contextHint },
    onChunk,
  )
}

/**
 * 语音合成：把 crush 文本回复送 CosyVoice 合成。后端用 Result<String> 返回 base64 mp3，
 * 这里解码成 Blob 供 <audio> 播放。
 * 注：axios baseURL 已是 /api，此处不要再带 /api 前缀，否则拼成 /api/api/...
 */
export async function synthesizeVoice(text: string, voice?: string): Promise<Blob> {
  const base64 = await unwrap<string>(http.post('/chat/voice', { text, voice }))
  // base64 -> bytes -> Blob
  const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0))
  return new Blob([bytes], { type: 'audio/mpeg' })
}

/**
 * 加载某 crush 的历史对话（已落库 PG）。前端进入对话页时调用。
 */
export async function getChatHistory(crushSlug: string): Promise<ChatHistoryVO[]> {
  return unwrap<ChatHistoryVO[]>(http.get('/chat/history', { params: { crushSlug } }))
}

/**
 * 主动消息推送监听（SSE，GET）。为当前查看的 crush 建立常驻连接，
 * 后端调度器生成新的主动消息后通过该连接推送（事件名 proactive），
 * 前端收到后应重新拉取该 crush 的历史以渲染新气泡。
 *
 * @returns 关闭函数（切换 crush / 页面卸载时调用）
 */
export function listenProactive(crushSlug: string, onMessage: (text: string) => void): () => void {
  const es = new EventSource(`/api/push/listen?crushSlug=${encodeURIComponent(crushSlug)}`)
  es.addEventListener('proactive', (e) => {
    onMessage((e as MessageEvent).data)
  })
  // EventSource 断线会自动重连，这里无需额外处理
  return () => es.close()
}

/**
 * SSE POST 通用消费器：解析 data 行为 MultiChunk 并回调。
 */
async function sseStream(
  url: string,
  body: Record<string, unknown>,
  onChunk: (chunk: MultiChunk) => void,
): Promise<void> {
  const resp = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })

  const contentType = resp.headers.get('content-type') || ''
  if (!resp.ok || !contentType.includes('text/event-stream')) {
    const data = (await resp.json().catch(() => null)) as Result<unknown> | null
    throw new Error((data && data.message) || '请求失败')
  }

  const reader = resp.body!.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  const consume = (line: string) => {
    if (!line.startsWith('data:')) return
    const payload = line.slice(5).trim()
    if (!payload) return
    try {
      onChunk(JSON.parse(payload) as MultiChunk)
    } catch {
      /* ignore malformed line */
    }
  }

  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''
    for (const line of lines) consume(line)
  }
  if (buffer.trim()) consume(buffer)
}

export async function importSource(
  crushId: number,
  payload: { type?: string; fileName?: string; content: string },
): Promise<Source> {
  return unwrap(http.post<Result<Source>>(`/crush/${crushId}/sources`, payload))
}

export async function uploadSource(crushId: number, file: File, type?: string): Promise<Source> {
  const form = new FormData()
  form.append('file', file)
  if (type) form.append('type', type)
  return unwrap(
    http.post<Result<Source>>(`/crush/${crushId}/sources/upload`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }),
  )
}

export async function listSources(crushId: number): Promise<Source[]> {
  return unwrap(http.get<Result<Source[]>>(`/crush/${crushId}/sources`))
}

export async function deleteSource(crushId: number, sourceId: number): Promise<void> {
  await unwrap(http.delete<Result<void>>(`/crush/${crushId}/sources/${sourceId}`))
}

export async function listVersions(crushId: number): Promise<Version[]> {
  return unwrap(http.get<Result<Version[]>>(`/crush/${crushId}/versions`))
}

/**
 * 构建 crush（SSE 进度流）。
 */
export async function buildCrush(crushId: number, onEvent: (ev: BuildEvent) => void): Promise<void> {
  const resp = await fetch(`/api/crush/${crushId}/build`, { method: 'POST' })
  const ct = resp.headers.get('content-type') || ''
  if (!resp.ok || !ct.includes('text/event-stream')) {
    const data = (await resp.json().catch(() => null)) as Result<unknown> | null
    throw new Error((data && data.message) || '请求失败')
  }
  const reader = resp.body!.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  const consume = (line: string) => {
    if (!line.startsWith('data:')) return
    const payload = line.slice(5).trim()
    if (!payload) return
    try {
      onEvent(JSON.parse(payload) as BuildEvent)
    } catch {
      /* ignore */
    }
  }
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''
    for (const line of lines) consume(line)
  }
  if (buffer.trim()) consume(buffer)
}
