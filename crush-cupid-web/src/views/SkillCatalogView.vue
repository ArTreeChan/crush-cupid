<!--
  @className SkillCatalogView.vue
  @description Skill 目录页：使用 PageContainer，卡片化展示远端 Skill 信息与可用 Prompts
  @author cupid
  @code view
  @createTime 2026-08-26
-->
<template>
  <PageContainer
    icon="📚"
    title="Skill 目录"
    subtitle="查看远端 Skill 信息与可用 Prompts"
  >
    <a-spin :spinning="loading" class="skill-spin">
      <div class="skill-page cupid-fade-in">
        <!-- Skill 基本信息 -->
        <div class="info-card" v-if="catalog">
          <div class="info-card__head">
            <div class="info-card__icon">📦</div>
            <div class="info-card__title">远端 Skill 信息</div>
          </div>
          <div class="info-grid">
            <div class="info-item">
              <div class="info-item__label">name</div>
              <div class="info-item__value">{{ catalog.skill.name }}</div>
            </div>
            <div class="info-item">
              <div class="info-item__label">version</div>
              <div class="info-item__value">
                <a-tag color="pink" class="version-tag">{{ catalog.skill.version }}</a-tag>
              </div>
            </div>
            <div class="info-item info-item--full">
              <div class="info-item__label">description</div>
              <div class="info-item__value">{{ catalog.skill.description }}</div>
            </div>
            <div class="info-item info-item--full">
              <div class="info-item__label">argument-hint</div>
              <div class="info-item__value info-item__value--mono">{{ catalog.skill.argumentHint }}</div>
            </div>
          </div>
        </div>

        <!-- 可用 Prompts -->
        <div class="prompts-card">
          <div class="prompts-card__head">
            <div class="prompts-card__title">
              <span>🗂️</span> 可用 Prompts
            </div>
            <div class="prompts-card__count" v-if="catalog">
              共 {{ catalog.prompts.length }} 个
            </div>
          </div>
          <div class="prompts-grid" v-if="catalog && catalog.prompts.length">
            <div
              v-for="item in catalog.prompts"
              :key="item"
              class="prompt-tile"
              @click="loadPrompt(item)"
            >
              <div class="prompt-tile__icon">📄</div>
              <div class="prompt-tile__name">{{ item }}</div>
              <div class="prompt-tile__action">查看 →</div>
            </div>
          </div>
          <a-empty v-else description="暂无 Prompts" />
        </div>
      </div>
    </a-spin>

    <!-- Prompt 详情弹窗 -->
    <a-modal
      v-model:open="promptOpen"
      :title="`📄 prompt: ${currentPrompt}`"
      width="760"
      :footer="null"
    >
      <pre class="prompt-pre">{{ promptContent }}</pre>
    </a-modal>
  </PageContainer>
</template>

<script setup lang="ts">
/**
 * Skill 目录页：加载远端 Skill 元信息与 Prompts
 */
import { onMounted, ref } from 'vue'
import { getSkillCatalog, getSkillPrompt } from '@/api'
import type { SkillCatalog } from '@/types'
import PageContainer from '@/components/PageContainer.vue'

const catalog = ref<SkillCatalog | null>(null)
const loading = ref(false)
const promptOpen = ref(false)
const currentPrompt = ref('')
const promptContent = ref('')

/** 加载 Skill 目录 */
async function load() {
  loading.value = true
  try {
    catalog.value = await getSkillCatalog()
  } finally {
    loading.value = false
  }
}

/** 加载并预览某个 Prompt */
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
.skill-spin {
  display: block;
}

.skill-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* Skill 信息卡片 */
.info-card {
  background: var(--cupid-bg-card);
  border: 1px solid var(--cupid-border);
  border-radius: var(--cupid-radius);
  box-shadow: var(--cupid-shadow-sm);
  overflow: hidden;
}

.info-card__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  background: var(--cupid-gradient-soft);
  border-bottom: 1px solid var(--cupid-border);
}

.info-card__icon {
  font-size: 18px;
}

.info-card__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--cupid-text);
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0;
}

.info-item {
  padding: 12px 20px;
  border-right: 1px solid var(--cupid-border);
  border-bottom: 1px solid var(--cupid-border);
}

.info-item:nth-child(2n) {
  border-right: none;
}

.info-item--full {
  grid-column: 1 / -1;
  border-right: none;
}

.info-item__label {
  font-size: 11px;
  color: var(--cupid-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 4px;
}

.info-item__value {
  color: var(--cupid-text);
  font-size: 14px;
  line-height: 1.6;
}

.info-item__value--mono {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 13px;
  background: #fafafa;
  padding: 4px 8px;
  border-radius: 6px;
  display: inline-block;
}

.version-tag {
  border-radius: 10px !important;
  font-weight: 600;
}

/* Prompts 卡片 */
.prompts-card {
  background: var(--cupid-bg-card);
  border: 1px solid var(--cupid-border);
  border-radius: var(--cupid-radius);
  box-shadow: var(--cupid-shadow-sm);
  overflow: hidden;
}

.prompts-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  background: var(--cupid-gradient-soft);
  border-bottom: 1px solid var(--cupid-border);
}

.prompts-card__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--cupid-text);
}

.prompts-card__count {
  font-size: 12px;
  color: var(--cupid-text-secondary);
}

.prompts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
  padding: 16px 20px;
}

.prompt-tile {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  background: var(--cupid-gradient-soft);
  border: 1px solid var(--cupid-border);
  border-radius: var(--cupid-radius-sm);
  cursor: pointer;
  transition: all 0.25s ease;
}

.prompt-tile:hover {
  background: #fff;
  border-color: var(--cupid-primary);
  box-shadow: var(--cupid-shadow);
  transform: translateY(-2px);
}

.prompt-tile__icon {
  font-size: 22px;
}

.prompt-tile__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--cupid-text);
  word-break: break-all;
}

.prompt-tile__action {
  font-size: 12px;
  color: var(--cupid-primary);
  font-weight: 600;
}

/* Prompt 预览 */
.prompt-pre {
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 60vh;
  overflow-y: auto;
  background: #1f1722;
  color: #ffd6df;
  padding: 16px;
  border-radius: var(--cupid-radius-sm);
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 13px;
  line-height: 1.7;
  margin: 0;
}
</style>
