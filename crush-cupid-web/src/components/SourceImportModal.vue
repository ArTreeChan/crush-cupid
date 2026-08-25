<template>
  <a-modal v-model:open="open" title="导入原材料" :confirm-loading="submitting" @ok="submit">
    <a-tabs v-model:activeKey="mode">
      <a-tab-pane key="text" tab="粘贴文本">
        <a-textarea
          v-model:value="text"
          :rows="7"
          placeholder="粘贴聊天记录、回忆、口述内容…"
        />
      </a-tab-pane>
      <a-tab-pane key="file" tab="上传文件">
        <a-upload
          :before-upload="() => false"
          :max-count="1"
          v-model:file-list="fileList"
        >
          <a-button>选择文件</a-button>
        </a-upload>
        <div class="hint">支持 txt / json / html / csv（微信/QQ 导出等），直接读取文本内容</div>
      </a-tab-pane>
    </a-tabs>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import type { UploadFile } from 'ant-design-vue'
import { importSource, uploadSource } from '@/api'

const props = defineProps<{ crushId: number }>()
const emit = defineEmits<{ (e: 'imported'): void }>()

const open = defineModel<boolean>('open')

const mode = ref<'text' | 'file'>('text')
const text = ref('')
const fileList = ref<UploadFile[]>([])
const submitting = ref(false)

watch(open, (v) => {
  if (v) {
    text.value = ''
    fileList.value = []
    mode.value = 'text'
  }
})

async function submit() {
  if (mode.value === 'text') {
    if (!text.value.trim()) {
      message.warning('请输入内容')
      return
    }
    submitting.value = true
    try {
      await importSource(props.crushId, { type: 'TEXT', content: text.value.trim() })
      message.success('导入成功')
      open.value = false
      emit('imported')
    } finally {
      submitting.value = false
    }
  } else {
    const file = fileList.value[0]?.originFileObj as File | undefined
    if (!file) {
      message.warning('请选择文件')
      return
    }
    submitting.value = true
    try {
      await uploadSource(props.crushId, file)
      message.success('导入成功')
      open.value = false
      emit('imported')
    } finally {
      submitting.value = false
    }
  }
}
</script>

<style scoped>
.hint {
  margin-top: 10px;
  padding: 8px 12px;
  background: var(--cupid-gradient-soft);
  border-radius: var(--cupid-radius-sm);
  color: var(--cupid-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}
</style>
