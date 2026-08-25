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
  totalMessages?: number
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
