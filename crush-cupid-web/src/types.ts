export interface Crush {
  id?: number
  name: string
  slug: string
  mbti?: string
  zodiac?: string
  occupation?: string
  gender?: string
  knowDuration?: string
  relationshipStatus?: string
  impression?: string
  personaLayer0?: string
  personaLayer1?: string
  personaLayer2?: string
  personaLayer3?: string
  personaLayer4?: string
  memoryOverview?: string
  memoryTimeline?: string
  memorySweet?: string
  memoryInteraction?: string
  currentStage?: number
  status?: string
  totalMessages?: number
  lastChatDate?: string
  voiceId?: string
  version?: number
  createdAt?: string
  updatedAt?: string
}

export interface CrushCreatePayload {
  name: string
  slug: string
  mbti?: string
  zodiac?: string
  occupation?: string
  gender?: string
  knowDuration?: string
  relationshipStatus?: string
  impression?: string
  voiceId?: string
}

export interface SkillMeta {
  name: string
  description: string
  version: string
  argumentHint?: string
  userInvocable: boolean
}

export interface SkillCatalog {
  skill: SkillMeta
  prompts: string[]
}

export interface Result<T> {
  code: number
  message: string
  data: T
}

export interface Source {
  id: number
  crushId: number
  type: string
  fileName?: string
  content?: string
  messageCount?: number
  createdAt?: string
}

export interface Version {
  id: number
  crushId: number
  version: number
  reason?: string
  snapshot?: string
  createdAt?: string
}

export interface BuildResult {
  crushId: number
  version: number
  status: string
  memorySummary?: string
  personaSummary?: string
}

export interface BuildEvent {
  type: 'progress' | 'done' | 'error'
  message?: string
  result?: BuildResult
}

/**
 * 多条消息流式 chunk：后端按 index 切气泡，跳变即新气泡。
 */
export interface MultiChunk {
  index: number
  content: string
  done: boolean
}

/**
 * 对话历史条目（后端 GET /api/chat/history 返回）。
 */
export interface ChatHistoryVO {
  role: 'user' | 'assistant' | 'system' | 'tool'
  content: string
  createdAt: string
}
