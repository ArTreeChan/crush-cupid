
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
            <div class="side-card__hint">
              <span>💡</span> 还没有暗恋对象？去「暗恋对象」页新建一个。
            </div>
          </div>
        </a-col>

        <!-- 右侧：聊天区 -->
        <a-col :span="18">
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
                <div class="bubble">{{ m.content }}</div>
              </div>
              <div v-if="streaming" class="msg assistant">
                <div class="avatar">💗</div>
                <div class="bubble">
                  <span class="typing">{{ streamingText || '正在输入…' }}</span>
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
 * 对话页面：加载 crush 列表、维护消息历史、流式发送
 */
import { computed, nextTick, onMounted, ref } from 'vue'
import { listCrushes, streamChat } from '@/api'
import type { Crush } from '@/types'
import SourceImportModal from '@/components/SourceImportModal.vue'
import PageContainer from '@/components/PageContainer.vue'

/** 聊天消息结构 */
interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

const crushes = ref<Crush[]>([])
const loading = ref(false)
const currentSlug = ref<string>()
const messages = ref<ChatMessage[]>([])
const input = ref('')
const streaming = ref(false)
const streamingText = ref('')
const msgBox = ref<HTMLElement>()

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

/** 发送消息并接收流式回复 */
async function send() {
  const text = input.value.trim()
  if (!text || !currentSlug.value || streaming.value) return

  messages.value.push({ role: 'user', content: text })
  input.value = ''
  streaming.value = true
  streamingText.value = ''
  await scrollToBottom()

  try {
    await streamChat(currentSlug.value, text, (chunk) => {
      streamingText.value += chunk
      scrollToBottom()
    })
    messages.value.push({ role: 'assistant', content: streamingText.value })
  } catch (e) {
    const msg = e instanceof Error ? e.message : '发送失败'
    messages.value.push({ role: 'assistant', content: `[错误] ${msg}` })
  } finally {
    streaming.value = false
    streamingText.value = ''
    await scrollToBottom()
  }
}

onMounted(loadCrushes)
</script>

<style scoped>
.chat-page {
  height: 100%;
}

.chat-row {
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
