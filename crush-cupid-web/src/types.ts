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
  /** 语音风格指令（instruction）：控制情感、语气、语速、性格，最大100字符 */
  voiceInstruction?: string
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
  voiceInstruction?: string
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

export interface AdvisorCommand {
  name: string
  trigger: string
  title: string
  description: string
  promptName: string
  requiresCrush: boolean
}

export interface CrushReport {
  id: number
  crushId: number
  crushName?: string
  title?: string
  source?: string
  reportDate?: string
  markdown?: string
  createdAt?: string
}

export interface AiProvider {
  id: number
  name: string
  providerKey: string
  baseUrl: string
  apiKey?: string
  model: string
  temperature?: number
  topP?: number
  maxTokens?: number
  /** 能力列表：vision=视觉看图, audio=音频听语音（文本是所有 LLM 基本能力） */
  capabilities?: string[]
  /** 供应商类型：chat=对话大模型 / voice=语音大模型 */
  type?: 'chat' | 'voice'
  /** 语音合成默认音色（仅 type=voice 时用） */
  voice?: string
  isDefault?: boolean
}

export interface AiProviderPayload {
  name?: string
  providerKey?: string
  baseUrl?: string
  apiKey?: string
  model?: string
  temperature?: number
  topP?: number
  maxTokens?: number
  capabilities?: string[]
  type?: 'chat' | 'voice'
  voice?: string
  isDefault?: boolean
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

/** 关系分析结果（「她不一样」引擎输出） */
export interface RelationshipResult {
  crushId: number
  contact?: string
  /** stats.json 全量统计 */
  stats?: any
  /** analysis.json AI 深度鉴定 */
  analysis?: any
  /** HTML 报告访问 URL */
  reportUrl?: string
  /** 主动指数 0-100 */
  initiative?: number
  /** 被爱指数 0-100 */
  lovedIndex?: number
  /** 冷淡指数 0-100 */
  coldIndex?: number
  /** 消息总数 */
  totalMessages?: number
  /** 降级提示（AI 鉴定失败时返回） */
  errorMessage?: string
  /** 是否为历史缓存结果（聊天记录未变时直接复用，不再重新分析） */
  cached?: boolean
}

/**
 * 多条消息流式 chunk：后端按 index 切气泡，跳变即新气泡。
 * type=text 时 content 为增量文本；type=sticker 时 content 为表情包图片 URL（一次性下发）。
 */
export interface MultiChunk {
  index: number
  type?: 'text' | 'sticker'
  content: string
  /** 语音情感（[[emotion:情绪]] 解析后的标签，可为空） */
  emotion?: string
  done: boolean
}

/**
 * 聊天多模态输入片段：图片（URL/base64）、音频、文本附件（base64）。
 */
export interface ChatMedia {
  type: 'IMAGE_URL' | 'IMAGE_BASE64' | 'AUDIO_URL' | 'AUDIO_BASE64' | 'FILE_BASE64'
  mimeType?: string
  data: string
  fileName?: string
}

/**
 * 对话历史条目（后端 GET /api/chat/history 返回）。
 */
export interface ChatHistoryVO {
  role: 'user' | 'assistant' | 'system' | 'tool'
  content: string
  createdAt: string
  /** 关联的图片 URL（来自 chat_media 表）；无图片为 null/undefined */
  mediaUrl?: string
}
