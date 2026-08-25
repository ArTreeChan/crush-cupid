<template>
  <div>
    <a-spin :spinning="loading">
      <a-descriptions v-if="catalog" title="远端 Skill 信息" bordered :column="1" size="small">
        <a-descriptions-item label="name">{{ catalog.skill.name }}</a-descriptions-item>
        <a-descriptions-item label="version">{{ catalog.skill.version }}</a-descriptions-item>
        <a-descriptions-item label="description">{{ catalog.skill.description }}</a-descriptions-item>
        <a-descriptions-item label="argument-hint">{{ catalog.skill.argumentHint }}</a-descriptions-item>
      </a-descriptions>

      <a-card title="可用 Prompts" size="small" style="margin-top: 16px">
        <a-list size="small" bordered :data-source="catalog?.prompts ?? []">
          <template #renderItem="{ item }">
            <a-list-item>
              <a @click="loadPrompt(item)">{{ item }}</a>
            </a-list-item>
          </template>
        </a-list>
      </a-card>
    </a-spin>

    <a-modal v-model:open="promptOpen" :title="`prompt: ${currentPrompt}`" width="720" :footer="null">
      <pre class="prompt-pre">{{ promptContent }}</pre>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getSkillCatalog, getSkillPrompt } from '@/api'
import type { SkillCatalog } from '@/types'

const catalog = ref<SkillCatalog | null>(null)
const loading = ref(false)
const promptOpen = ref(false)
const currentPrompt = ref('')
const promptContent = ref('')

async function load() {
  loading.value = true
  try {
    catalog.value = await getSkillCatalog()
  } finally {
    loading.value = false
  }
}

async function loadPrompt(name: string) {
  currentPrompt.value = name
  promptContent.value = '加载中…'
  promptOpen.value = true
  try {
    promptContent.value = await getSkillPrompt(name)
  } catch (e) {
    promptContent.value = e instanceof Error ? e.message : '加载失败'
  }
}

onMounted(load)
</script>

<style scoped>
.prompt-pre {
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 60vh;
  overflow-y: auto;
  background: #fafafa;
  padding: 12px;
  border-radius: 6px;
}
</style>
