
<template>
  <PageContainer
    icon="💬"
    title="对话"
    :subtitle="currentSlug ? `正在和 ${currentName} 聊天` : '选择一个暗恋对象开始对话'"
  >
    <div class="chat-page">
      <a-row :gutter="20" class="chat-row">
        <!-- 左侧：crush 选择 -->
        <a-col :span="6">
          <div class="side-card">
            <div class="side-card__title">选择暗恋对象</div>
            <a-select
              v-model:value="currentSlug"
              placeholder="选择 crush"
              size="large"
              style="width: 100%"
              :loading="loading"
              :options="crushOptions"
            />
            <a-button
              size="large"
              block
              class="side-card__btn"
              :disabled="!currentCrush"
              @click="importOpen = true"
            >
              <span>📥</span>&nbsp;补充原材料
            </a-button>
            <a-button
              size="large"
              block
              class="side-card__btn side-card__btn--nudge"
              :loading="streaming"
              :disabled="!currentCrush || streaming"
              @click="nudge"
            >
              <span>💌</span>&nbsp;等 ta 主动找我
            </a-button>
            <div class="side-card__hint">
              <span>💡</span> 还没有暗恋对象？去「暗恋对象」页新建一个。
            </div>
          </div>
        </a-col>

        <!-- 右侧：聊天区 -->
        <a-col :span="18" class="chat-col">
          <div class="chat-card">
            <div class="chat-card__head">
              <div class="chat-card__title">
                {{ currentSlug ? `和 ${currentName} 聊天` : '请先选择 crush' }}
              </div>
              <div class="chat-card__sub" v-if="currentCrush">
                {{ currentCrush.mbti || '—' }} · {{ currentCrush.zodiac || '—' }}
              </div>
            </div>

            <div ref="msgBox" class="messages">
              <div v-if="messages.length === 0 && !streaming" class="empty">
                <div class="empty__icon">💌</div>
                <div class="empty__text">开始你们的对话吧～</div>
              </div>
              <div
                v-for="(m, i) in messages"
                :key="i"
                :class="['msg', m.role]"
              >
                <div class="avatar">{{ m.role === 'user' ? '🧑' : '💗' }}</div>
                <div class="bubble">
                  <span>{{ m.content }}</span>
                  <span v-if="streaming && i === streamingBubbleIdx" class="cursor">▋</span>
                  <div
                    v-if="m.role === 'assistant' && m.content && !streaming"
                    class="bubble__voice"
                  >
                    <button
                      class="voice-btn"
                      :disabled="m.synthesizing"
                      :title="m.synthesizing ? '合成中…' : (m.audioUrl ? '播放/暂停' : '听 ta 说')"
                      @click="playVoice(m, i)"
                    >
                      <span v-if="m.synthesizing">⏳</span>
                      <span v-else>🎤</span>
                    </button>
                    <audio
                      v-if="m.audioUrl"
                      class="bubble-audio"
                      :src="m.audioUrl"
                      controls
                      preload="none"
                    />
                  </div>
                </div>
              </div>
              <div v-if="streaming && streamingBubbleIdx < 0" class="msg assistant">
                <div class="avatar">💗</div>
                <div class="bubble">
                  <span class="typing">正在输入…</span>
                  <span class="cursor">▋</span>
                </div>
              </div>
            </div>

            <div class="input-row">
              <a-textarea
                v-model:value="input"
                :rows="2"
                placeholder="说点什么…（Enter 发送）"
                :disabled="streaming"
                class="input-area"
                @pressEnter="send"
              />
              <a-button
                type="primary"
                size="large"
                class="send-btn"
                :loading="streaming"
                :disabled="!currentSlug || !input.trim()"
                @click="send"
              >
                发送
              </a-button>
            </div>
          </div>
        </a-col>
      </a-row>

      <SourceImportModal v-model:open="importOpen" :crush-id="currentCrush?.id ?? 0" />
    </div>
  </PageContainer>
</template>

<script setup lang="ts">
/**
 * 对话页面：加载 crush 列表、维护消息历史、流式发送。
 * 支持一次连发多条短消息（按 chunk.index 切气泡）、crush 主动发起对话、
 * 以及把 crush 的文本回复一键转 CosyVoice 语音播放。
 */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  getChatHistory,
  listCrushes,
  listenProactive,
  proactiveChat,
  streamChat,
  synthesizeVoice,
} from '@/api'
import type { Crush, MultiChunk } from '@/types'
import SourceImportModal from '@/components/SourceImportModal.vue'
import PageContainer from '@/components/PageContainer.vue'

/** 聊天消息结构 */
interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  /** 语音 URL（合成后生成，便于气泡内 <audio> 播放）；无则未合成 */
  audioUrl?: string
  /** 是否正在合成语音 */
  synthesizing?: boolean
  /** 合成中的 promise，用于并发去重与等待 */
  synthPromise?: Promise<void>
}

const crushes = ref<Crush[]>([])
const loading = ref(false)
const currentSlug = ref<string>()
const messages = ref<ChatMessage[]>([])
const input = ref('')
const streaming = ref(false)
const msgBox = ref<HTMLElement>()
/** 当前 crush 的主动消息监听关闭函数（切换 crush 时先关旧的） */
let closePush: (() => void) | null = null

/** 当前正在流式追加的气泡在 messages 中的索引（用于显示光标）；-1 表示无 */
const streamingBubbleIdx = ref(-1)

/** crush 下拉选项 */
const crushOptions = computed(() =>
  crushes.value.map((c) => ({ label: `${c.name} (${c.slug})`, value: c.slug })),
)
/** 当前 crush 名称 */
const currentName = computed(
  () => crushes.value.find((c) => c.slug === currentSlug.value)?.name ?? '',
)
/** 当前 crush 对象 */
const currentCrush = computed(() =>
  crushes.value.find((c) => c.slug === currentSlug.value),
)
const importOpen = ref(false)

/** 加载 crush 列表，默认选中第一个 */
async function loadCrushes() {
  loading.value = true
  try {
    crushes.value = await listCrushes()
    if (crushes.value.length && !currentSlug.value) {
      currentSlug.value = crushes.value[0].slug
    }
  } finally {
    loading.value = false
  }
}

/** 滚动到消息底部 */
async function scrollToBottom() {
  await nextTick()
  if (msgBox.value) {
    msgBox.value.scrollTop = msgBox.value.scrollHeight
  }
}

/** 历史消息分隔符，与后端 MessageSeparator.SEPARATOR 保持一致 */
const MSG_SEP = '|||'

/** 加载某 crush 的历史对话（已落库 PG） */
async function loadHistory(slug: string) {
  if (!slug) {
    messages.value = []
    return
  }
  try {
    const rows = await getChatHistory(slug)
    const list: ChatMessage[] = []
    for (const r of rows) {
      if (r.role !== 'user' && r.role !== 'assistant') continue
      if (r.role === 'assistant' && r.content.includes(MSG_SEP)) {
        // assistant 消息按分隔符切分，与流式显示保持一致
        r.content.split(MSG_SEP).forEach((text) => {
          const trimmed = text.trim()
          if (trimmed) list.push({ role: 'assistant', content: trimmed })
        })
      } else {
        list.push({ role: r.role, content: r.content })
      }
    }
    messages.value = list
    await scrollToBottom()
  } catch {
    messages.value = []
  }
}

// 切换 crush 时停止当前连播，并重新加载该 crush 的历史 + 建立主动推送监听
watch(currentSlug, (slug) => {
  stopPlayback()
  if (closePush) {
    closePush()
    closePush = null
  }
  if (slug) {
    loadHistory(slug)
    closePush = listenProactive(slug, onProactivePush)
  }
})

/**
 * 收到后端主动消息推送：重新拉取历史渲染新气泡，并给一个轻微提示音。
 * 若用户当前正看其它 crush 的历史，不强制切走，仅做轻提示。
 */
function onProactivePush(text: string) {
  if (currentSlug.value) {
    void loadHistory(currentSlug.value)
  }
  playNudgeTone()
}

/**
 * 本轮 assistant 多条气泡累积器：按 chunk.index 把 content 追加到对应气泡，
 * index 跳变即开新气泡；同时维护当前 streaming 气泡索引以显示光标。
 */
class MultiBubbleAccumulator {
  /** chunk.index -> messages 数组位置 */
  private map = new Map<number, number>()

  push(chunk: MultiChunk) {
    if (chunk.done) return
    let pos = this.map.get(chunk.index)
    if (pos === undefined) {
      messages.value.push({ role: 'assistant', content: '' })
      pos = messages.value.length - 1
      this.map.set(chunk.index, pos)
    }
    if (chunk.content) {
      messages.value[pos].content += chunk.content
    }
    streamingBubbleIdx.value = pos
  }

  reset() {
    this.map.clear()
    streamingBubbleIdx.value = -1
  }
}

/** 发送消息并接收流式回复 */
async function send() {
  const text = input.value.trim()
  if (!text || !currentSlug.value || streaming.value) return

  messages.value.push({ role: 'user', content: text })
  const firstAssistantIdx = messages.value.length
  input.value = ''
  streaming.value = true
  const acc = new MultiBubbleAccumulator()
  await scrollToBottom()

  try {
    await streamChat(currentSlug.value, text, (chunk) => {
      acc.push(chunk)
      scrollToBottom()
    })
  } catch (e) {
    const msg = e instanceof Error ? e.message : '发送失败'
    messages.value.push({ role: 'assistant', content: `[错误] ${msg}` })
  } finally {
    acc.reset()
    streaming.value = false
    await scrollToBottom()
    void autoSynth(firstAssistantIdx, currentCrush.value?.voiceId)
  }
}

/** 让 crush 主动找你（一次连发多条） */
async function nudge() {
  if (!currentSlug.value || streaming.value) return
  streaming.value = true
  const firstAssistantIdx = messages.value.length
  const acc = new MultiBubbleAccumulator()
  await scrollToBottom()

  try {
    await proactiveChat(currentSlug.value, '', (chunk) => {
      acc.push(chunk)
      scrollToBottom()
    })
  } catch (e) {
    const msg = e instanceof Error ? e.message : '主动消息失败'
    messages.value.push({ role: 'assistant', content: `[错误] ${msg}` })
  } finally {
    acc.reset()
    streaming.value = false
    await scrollToBottom()
    void autoSynth(firstAssistantIdx, currentCrush.value?.voiceId)
  }
}

/** 顺序连播队列：从某条 assistant 气泡起，依次播放后续所有已合成语音 */
let playQueue: HTMLAudioElement[] = []

/** 停止当前连播 */
function stopPlayback() {
  playQueue.forEach((a) => {
    a.pause()
    a.src = ''
  })
  playQueue = []
}

/** 合成单条消息语音（幂等：已合成/合成中直接返回同一 promise），不播放 */
function synthesizeBubble(msg: ChatMessage, voice?: string): Promise<void> {
  if (msg.audioUrl) return Promise.resolve()
  if (msg.synthPromise) return msg.synthPromise
  msg.synthesizing = true
  msg.synthPromise = (async () => {
    try {
      const blob = await synthesizeVoice(msg.content, voice)
      msg.audioUrl = URL.createObjectURL(blob)
    } catch {
      msg.audioUrl = undefined
    } finally {
      msg.synthesizing = false
    }
  })()
  return msg.synthPromise
}

/** 后台自动合成：把一轮新产生的 assistant 消息逐条合成好（不播放），点 🎤 即秒播 */
async function autoSynth(fromIdx: number, voice?: string) {
  const snapshot = messages.value.slice(fromIdx)
  for (const m of snapshot) {
    if (m.role !== 'assistant' || !m.content) continue
    await synthesizeBubble(m, voice)
  }
}

/** 点击 🎤：从该条 assistant 气泡起，顺序连播后续所有语音 */
async function playVoice(msg: ChatMessage, index: number) {
  if (msg.role !== 'assistant' || !msg.content) return
  stopPlayback()
  const voice = currentCrush.value?.voiceId
  // 先把从 index 起的所有 assistant 消息补齐合成（幂等，已合成的秒过）
  for (let i = index; i < messages.value.length; i++) {
    const m = messages.value[i]
    if (m.role !== 'assistant' || !m.content) continue
    await synthesizeBubble(m, voice)
  }
  const audios = messages.value
    .slice(index)
    .filter((m) => m.role === 'assistant' && m.audioUrl)
    .map((m) => new Audio(m.audioUrl!))
  if (!audios.length) return
  playQueue = audios
  audios.forEach((a, i) => {
    a.addEventListener('ended', () => {
      if (i + 1 < audios.length) void audios[i + 1].play().catch(() => {})
    })
  })
  void audios[0].play().catch(() => {})
}

/** 收到主动消息时的轻微提示音（Web Audio，无需音频文件） */
function playNudgeTone() {
  try {
    const Ctx = window.AudioContext || (window as any).webkitAudioContext
    const ctx = new Ctx()
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.type = 'sine'
    osc.frequency.value = 880
    gain.gain.value = 0.04
    osc.connect(gain)
    gain.connect(ctx.destination)
    osc.start()
    osc.frequency.exponentialRampToValueAtTime(1320, ctx.currentTime + 0.15)
    gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.35)
    osc.stop(ctx.currentTime + 0.35)
    osc.onended = () => ctx.close()
  } catch {
    /* 忽略音频不可用 */
  }
}

onMounted(loadCrushes)
onUnmounted(() => {
  if (closePush) {
    closePush()
    closePush = null
  }
})
</script>

<style scoped>
.chat-page {
  height: 100%;
}

.chat-row {
  height: 100%;
}

/* ant-col 默认无高度，需显式 100% 才能让 .chat-card 的 height:100% 生效，否则 .messages 不滚动 */
.chat-col {
  height: 100%;
}

/* 左侧选择卡片 */
.side-card {
  background: var(--cupid-bg-card);
  border: 1px solid var(--cupid-border);
  border-radius: var(--cupid-radius);
  padding: 18px;
  box-shadow: var(--cupid-shadow-sm);
}

.side-card__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--cupid-text);
  margin-bottom: 12px;
}

.side-card__btn {
  margin-top: 12px;
  border-radius: var(--cupid-radius-sm) !important;
}

.side-card__btn--nudge {
  border: 1px dashed var(--cupid-primary) !important;
  color: var(--cupid-primary) !important;
  background: var(--cupid-gradient-soft) !important;
}

.side-card__btn--nudge:hover {
  background: #fff !important;
}

.side-card__hint {
  margin-top: 14px;
  padding: 10px 12px;
  background: var(--cupid-gradient-soft);
  border-radius: var(--cupid-radius-sm);
  color: var(--cupid-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

/* 右侧聊天卡片 */
.chat-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--cupid-bg-card);
  border: 1px solid var(--cupid-border);
  border-radius: var(--cupid-radius);
  box-shadow: var(--cupid-shadow-sm);
  overflow: hidden;
}

.chat-card__head {
  padding: 14px 20px;
  border-bottom: 1px solid var(--cupid-border);
  background: var(--cupid-gradient-soft);
}

.chat-card__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--cupid-text);
}

.chat-card__sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--cupid-text-secondary);
}

/* 消息列表 */
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 18px 20px;
  background:
    radial-gradient(circle at 20% 20%, rgba(255, 90, 122, 0.03), transparent 40%),
    radial-gradient(circle at 80% 80%, rgba(255, 142, 83, 0.03), transparent 40%);
}

/* 消息条目 */
.msg {
  display: flex;
  margin-bottom: 14px;
  align-items: flex-end;
  gap: 8px;
}

.msg.user {
  flex-direction: row-reverse;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  box-shadow: var(--cupid-shadow-sm);
  flex-shrink: 0;
}

.bubble {
    max-width: 70%;
    padding: 10px 14px;
    border-radius: 16px;
    white-space: pre-wrap;
    word-break: break-word;
    font-size: 14px;
    line-height: 1.6;
  }

  .msg.user .bubble {
    background: var(--cupid-gradient);
    color: #fff;
    border-bottom-right-radius: 4px;
    box-shadow: 0 4px 12px rgba(255, 90, 122, 0.25);
  }

  .msg.assistant .bubble {
    background: #fff;
    color: var(--cupid-text);
    border: 1px solid var(--cupid-border);
    border-bottom-left-radius: 4px;
  }

  .bubble__voice {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 8px;
    border-top: 1px dashed var(--cupid-border);
    padding-top: 8px;
  }

  .voice-btn {
    border: none;
    background: transparent;
    cursor: pointer;
    font-size: 16px;
    padding: 2px 6px;
    border-radius: 50%;
    transition: background 0.15s;
  }

  .voice-btn:hover:not(:disabled) {
    background: rgba(255, 105, 180, 0.12);
  }

  .voice-btn:disabled {
    cursor: progress;
    opacity: 0.6;
  }

  .bubble-audio {
    height: 32px;
    max-width: 240px;
  }

/* 打字光标动画 */
.cursor {
  display: inline-block;
  color: var(--cupid-primary);
  animation: blink 1s steps(2) infinite;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  50.01%, 100% { opacity: 0; }
}

/* 空状态 */
.empty {
  text-align: center;
  margin-top: 60px;
}

.empty__icon {
  font-size: 40px;
  opacity: 0.6;
}

.empty__text {
  margin-top: 12px;
  color: var(--cupid-text-muted);
  font-size: 14px;
}

/* 输入区 */
.input-row {
  display: flex;
  gap: 12px;
  padding: 14px 20px;
  border-top: 1px solid var(--cupid-border);
  background: #fff;
  align-items: flex-end;
}

.input-area {
  flex: 1;
  border-radius: var(--cupid-radius-sm) !important;
}

.send-btn {
  border-radius: var(--cupid-radius-sm) !important;
  min-width: 90px;
  height: 60px !important;
}
</style>
