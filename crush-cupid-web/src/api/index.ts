import http from './http'
import type {
  BuildEvent,
  Crush,
  CrushCreatePayload,
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
 * 流式对话（SSE，POST）。后端每个 chunk 以 JSON 字符串编码在 data 行。
 */
export async function streamChat(
  crushSlug: string,
  message: string,
  onChunk: (text: string) => void,
): Promise<string> {
  const resp = await fetch('/api/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ crushSlug, message }),
  })

  const contentType = resp.headers.get('content-type') || ''
  if (!resp.ok || !contentType.includes('text/event-stream')) {
    const data = (await resp.json().catch(() => null)) as Result<unknown> | null
    throw new Error((data && data.message) || '请求失败')
  }

  const reader = resp.body!.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let full = ''

  const consume = (line: string) => {
    if (!line.startsWith('data:')) return
    const payload = line.slice(5).trim()
    if (!payload) return
    let text: string
    try {
      text = JSON.parse(payload) as string
    } catch {
      text = payload
    }
    full += text
    onChunk(text)
  }

  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''
    for (const line of lines) consume(line)
  }
  // 流结束可能没有结尾换行，刷新剩余 buffer
  if (buffer.trim()) consume(buffer)
  return full
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
