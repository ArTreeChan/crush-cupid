<template>
  <div class="chat-page">
    <a-row :gutter="16">
      <a-col :span="6">
        <a-card title="选择暗恋对象" size="small">
          <a-select
            v-model:value="currentSlug"
            placeholder="选择 crush"
            style="width: 100%"
            :loading="loading"
            :options="crushOptions"
          />
          <div class="hint">还没有暗恋对象？去「暗恋对象」页新建一个。</div>
        </a-card>
      </a-col>
      <a-col :span="18">
        <a-card :title="currentSlug ? `和 ${currentName} 聊天` : '请先选择 crush'" size="small">
          <div ref="msgBox" class="messages">
            <div v-if="messages.length === 0 && !streaming" class="empty">开始你们的对话吧～</div>
            <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
              <div class="bubble">{{ m.content }}</div>
            </div>
            <div v-if="streaming" class="msg assistant">
              <div class="bubble">{{ streamingText || '…' }}</div>
            </div>
          </div>
          <div class="input-row">
            <a-textarea
              v-model:value="input"
              :rows="2"
              placeholder="说点什么…"
              :disabled="streaming"
              @pressEnter="send"
            />
            <a-button
              type="primary"
              :loading="streaming"
              :disabled="!currentSlug || !input.trim()"
              @click="send"
            >
              发送
            </a-button>
          </div>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { listCrushes, streamChat } from '@/api'
import type { Crush } from '@/types'

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

const crushOptions = computed(() =>
  crushes.value.map((c) => ({ label: `${c.name} (${c.slug})`, value: c.slug })),
)
const currentName = computed(
  () => crushes.value.find((c) => c.slug === currentSlug.value)?.name ?? '',
)

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

async function scrollToBottom() {
  await nextTick()
  if (msgBox.value) {
    msgBox.value.scrollTop = msgBox.value.scrollHeight
  }
}

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
  height: calc(100vh - 64px);
}
.messages {
  height: calc(100vh - 240px);
  overflow-y: auto;
  padding: 8px;
  background: #f5f5f5;
  border-radius: 6px;
}
.msg {
  display: flex;
  margin-bottom: 8px;
}
.msg.user {
  justify-content: flex-end;
}
.msg.assistant {
  justify-content: flex-start;
}
.bubble {
  max-width: 70%;
  padding: 8px 12px;
  border-radius: 8px;
  white-space: pre-wrap;
  word-break: break-word;
}
.msg.user .bubble {
  background: #1677ff;
  color: #fff;
}
.msg.assistant .bubble {
  background: #fff;
  border: 1px solid #eee;
}
.empty {
  color: #bbb;
  text-align: center;
  margin-top: 40px;
}
.input-row {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  align-items: flex-end;
}
.hint {
  margin-top: 8px;
  color: #999;
  font-size: 12px;
}
</style>
