
<template>
  <PageContainer
    icon="💞"
    title="暗恋对象"
    subtitle="管理你的 crush 列表，构建专属 AI 人格"
  >
    <template #extra>
      <a-button type="primary" @click="openCreate">
        <span>➕</span>&nbsp;新建
      </a-button>
      <a-button @click="load">
        <span>🔄</span>&nbsp;刷新
      </a-button>
    </template>

    <div class="crush-list cupid-fade-in">
      <a-table
        :data-source="crushes"
        :columns="columns"
        row-key="id"
        :loading="loading"
        size="middle"
        class="crush-table"
        :pagination="{ pageSize: 10, hideOnSinglePage: true }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <div class="name-cell">
              <div class="name-cell__avatar">{{ record.name?.charAt(0) || '?' }}</div>
              <div class="name-cell__text">
                <div class="name-cell__name">{{ record.name }}</div>
                <div class="name-cell__slug">{{ record.slug }}</div>
              </div>
            </div>
          </template>
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'READY' ? 'green' : 'orange'" class="status-tag">
              {{ record.status || 'DRAFT' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'action'">
            <a-space size="middle">
              <a class="action-link" @click="openEdit(record)">编辑</a>
              <a-divider type="vertical" class="action-divider" />
              <a class="action-link" @click="openImport(record)">导入</a>
              <a-divider type="vertical" class="action-divider" />
              <a class="action-link action-link--primary" @click="build(record)">构建</a>
              <a-divider type="vertical" class="action-divider" />
              <a class="action-link action-link--primary" @click="analyze(record)">分析</a>
              <a-divider type="vertical" class="action-divider" />
              <a-popconfirm title="确定删除？" @confirm="remove(record)">
                <a class="action-link action-link--danger">删除</a>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>

      <SourceImportModal v-model:open="importOpen" :crush-id="importCrushId" @imported="load" />

      <!-- 新建/编辑弹窗 -->
      <a-modal
        v-model:open="modalOpen"
        :title="editing ? '编辑暗恋对象' : '新建暗恋对象'"
        :confirm-loading="saving"
        width="560"
        @ok="submit"
      >
        <a-form :model="form" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }" class="crush-form">
          <a-form-item label="花名" required>
            <a-input v-model:value="form.name" placeholder="如：小美" />
          </a-form-item>
          <a-form-item label="slug" required>
            <a-input v-model:value="form.slug" :disabled="!!editing" placeholder="如：xiaomei" />
          </a-form-item>
          <a-form-item label="MBTI"><a-input v-model:value="form.mbti" placeholder="如：INFJ" /></a-form-item>
          <a-form-item label="星座"><a-input v-model:value="form.zodiac" placeholder="如：双鱼" /></a-form-item>
          <a-form-item label="职业"><a-input v-model:value="form.occupation" /></a-form-item>
          <a-form-item label="性别"><a-input v-model:value="form.gender" /></a-form-item>
          <a-form-item label="认识时长"><a-input v-model:value="form.knowDuration" /></a-form-item>
          <a-form-item label="关系状态"><a-input v-model:value="form.relationshipStatus" /></a-form-item>
          <a-form-item label="印象"><a-textarea v-model:value="form.impression" :rows="3" /></a-form-item>
          <a-form-item label="音色ID">
            <a-input v-model:value="form.voiceId" placeholder="CosyVoice voice_id，空则走默认音色" />
          </a-form-item>
          <a-form-item label="语音风格">
            <a-textarea
              v-model:value="form.voiceInstruction"
              :rows="2"
              placeholder="控制情感、语气、语速、性格，如：用温柔撒娇的语气说话，带点笑意，语速稍慢（最大100字，空则不控制）"
            />
          </a-form-item>
        </a-form>
      </a-modal>

      <!-- 关系分析结果弹窗（「她不一样」引擎） -->
      <a-modal
        v-model:open="relOpen"
        :title="relTitle"
        width="860"
        :footer="null"
      >
        <template v-if="relLoading">
          <div class="rel-loading">
            <a-spin size="large" />
            <div class="rel-loading__text">正在分析聊天记录（统计 + AI 深度鉴定）…<br />约需 1~3 分钟，请稍候</div>
          </div>
        </template>
        <template v-else-if="relResult">
          <!-- AI 鉴定降级提示 -->
          <a-alert v-if="relResult.errorMessage" type="warning" show-icon class="rel-error" :message="relResult.errorMessage" />

          <!-- 三大指数 -->
          <div class="rel-scores">
            <div class="rel-score">
              <div class="rel-score__label">🔥 主动指数</div>
              <div class="rel-score__bar"><div class="rel-score__fill" :style="{ width: (relResult.initiative ?? 0) + '%' }"></div></div>
              <div class="rel-score__num">{{ relResult.initiative ?? '-' }}</div>
            </div>
            <div class="rel-score">
              <div class="rel-score__label">💜 被爱指数</div>
              <div class="rel-score__bar rel-score__bar--love"><div class="rel-score__fill" :style="{ width: (relResult.lovedIndex ?? 0) + '%' }"></div></div>
              <div class="rel-score__num">{{ relResult.lovedIndex ?? '-' }}</div>
            </div>
            <div class="rel-score">
              <div class="rel-score__label">🧊 冷淡指数</div>
              <div class="rel-score__bar rel-score__bar--cold"><div class="rel-score__fill" :style="{ width: (relResult.coldIndex ?? 0) + '%' }"></div></div>
              <div class="rel-score__num">{{ relResult.coldIndex ?? '-' }}</div>
            </div>
          </div>

          <!-- 关键统计 -->
          <a-descriptions :column="3" size="small" bordered class="rel-basic">
            <a-descriptions-item label="消息总数">{{ relResult.totalMessages ?? '-' }} 条</a-descriptions-item>
            <a-descriptions-item label="我/ta">
              {{ relResult.stats?.basic?.my_messages }} / {{ relResult.stats?.basic?.their_messages }}
            </a-descriptions-item>
            <a-descriptions-item label="时间跨度">
              {{ relResult.stats?.basic?.date_range?.[0] }} ~ {{ relResult.stats?.basic?.date_range?.[1] }}
            </a-descriptions-item>
            <a-descriptions-item label="我发起占比">
              {{ relResult.stats?.initiative ? Math.round(relResult.stats.initiative.my_start_ratio * 100) + '%' : '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="ta平均回复">
              {{ relResult.stats?.reply_speed?.their_avg_human || '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="我平均回复">
              {{ relResult.stats?.reply_speed?.my_avg_human || '-' }}
            </a-descriptions-item>
          </a-descriptions>

          <!-- AI 鉴定摘要 -->
          <div v-if="relResult.analysis" class="rel-verdict">
            <div class="rel-verdict__type">{{ relResult.analysis.relationship_label || relResult.analysis.relationship_type || '鉴定结果' }}</div>
            <div v-if="relResult.analysis.relationship_stage" class="rel-verdict__stage">
              <b>阶段：</b>{{ relResult.analysis.relationship_stage.stage }} — {{ relResult.analysis.relationship_stage.stage_description }}
            </div>
            <div v-if="relResult.analysis.relationship_trend" class="rel-verdict__trend">
              <b>趋势：</b>{{ relResult.analysis.relationship_trend }}
            </div>
          </div>

          <!-- 打开完整报告 -->
          <a-button v-if="relResult.reportUrl" type="primary" block size="large" class="rel-report-btn" @click="openReport(relResult.reportUrl)">
            打开完整 HTML 报告（统计图表 + 详细鉴定）
          </a-button>
        </template>
      </a-modal>

      <!-- 完整报告弹窗（iframe 嵌入 HTML 报告） -->
      <a-modal
        v-model:open="reportOpen"
        title="关系分析报告"
        width="960"
        :footer="null"
        destroy-on-close
      >
        <iframe :src="reportUrl" class="rel-iframe" />
      </a-modal>

      <!-- 构建结果弹窗 -->
      <a-modal
        v-model:open="buildOpen"
        :title="buildResult ? '✅ 构建完成' : '⏳ 构建中…'"
        width="640"
        :footer="null"
      >
        <div class="build-log">
          <div v-for="(line, i) in buildLog" :key="i" class="log-line">{{ line }}</div>
        </div>
        <template v-if="buildResult">
          <a-descriptions :column="1" size="small" bordered class="build-result">
            <a-descriptions-item label="性格">{{ buildResult.personaSummary }}</a-descriptions-item>
            <a-descriptions-item label="记忆">{{ buildResult.memorySummary }}</a-descriptions-item>
            <a-descriptions-item label="版本">v{{ buildResult.version }} · {{ buildResult.status }}</a-descriptions-item>
          </a-descriptions>
          <a-button type="primary" block size="large" class="build-done-btn" @click="buildOpen = false">
            完成
          </a-button>
        </template>
      </a-modal>
    </div>
  </PageContainer>
</template>

<script setup lang="ts">
/**
 * 暗恋对象列表页：增删改查 + 构建
 */
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { analyzeRelationship, buildCrush, createCrush, deleteCrush, listCrushes, updateCrush } from '@/api'
import type { BuildResult, Crush, CrushCreatePayload, RelationshipResult } from '@/types'
import SourceImportModal from '@/components/SourceImportModal.vue'
import PageContainer from '@/components/PageContainer.vue'

/** 表格列定义 */
const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
  { title: '花名', key: 'name' },
  { title: 'MBTI', dataIndex: 'mbti', key: 'mbti', width: 90 },
  { title: '星座', dataIndex: 'zodiac', key: 'zodiac', width: 90 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '操作', key: 'action', width: 260 },
]

const crushes = ref<Crush[]>([])
const loading = ref(false)
const modalOpen = ref(false)
const saving = ref(false)
const editing = ref<Crush | null>(null)

const importOpen = ref(false)
const importCrushId = ref(0)

const buildOpen = ref(false)
const buildLog = ref<string[]>([])
const buildResult = ref<BuildResult | null>(null)

// 关系分析（她不一样引擎）
const relOpen = ref(false)
const relLoading = ref(false)
const relTitle = ref('关系分析')
const relResult = ref<RelationshipResult | null>(null)
const reportOpen = ref(false)
const reportUrl = ref('')

/** 表单数据 */
const form = reactive<CrushCreatePayload>({
  name: '',
  slug: '',
  mbti: '',
  zodiac: '',
  occupation: '',
  gender: '',
  knowDuration: '',
  relationshipStatus: '',
  impression: '',
  voiceId: '',
  voiceInstruction: '',
})

/** 重置表单 */
function resetForm() {
  Object.assign(form, {
    name: '',
    slug: '',
    mbti: '',
    zodiac: '',
    occupation: '',
    gender: '',
    knowDuration: '',
    relationshipStatus: '',
    impression: '',
    voiceId: '',
    voiceInstruction: '',
  })
}

/** 加载列表 */
async function load() {
  loading.value = true
  try {
    crushes.value = await listCrushes()
  } finally {
    loading.value = false
  }
}

/** 打开新建弹窗 */
function openCreate() {
  editing.value = null
  resetForm()
  modalOpen.value = true
}

/** 打开编辑弹窗 */
function openEdit(record: Crush) {
  editing.value = record
  Object.assign(form, {
    name: record.name,
    slug: record.slug,
    mbti: record.mbti ?? '',
    zodiac: record.zodiac ?? '',
    occupation: record.occupation ?? '',
    gender: record.gender ?? '',
    knowDuration: record.knowDuration ?? '',
    relationshipStatus: record.relationshipStatus ?? '',
    impression: record.impression ?? '',
    voiceId: record.voiceId ?? '',
    voiceInstruction: record.voiceInstruction ?? '',
  })
  modalOpen.value = true
}

/** 打开导入弹窗 */
function openImport(record: Crush) {
  if (!record.id) return
  importCrushId.value = record.id
  importOpen.value = true
}

/** 构建 crush 人格 */
async function build(record: Crush) {
  if (!record.id) return
  buildOpen.value = true
  buildLog.value = []
  buildResult.value = null
  try {
    await buildCrush(record.id, (ev) => {
      if (ev.type === 'progress' && ev.message) buildLog.value.push(ev.message)
      if (ev.type === 'error') buildLog.value.push('[错误] ' + (ev.message || ''))
      if (ev.type === 'done' && ev.result) buildResult.value = ev.result
    })
  } catch (e) {
    buildLog.value.push('[错误] ' + (e instanceof Error ? e.message : '构建失败'))
  } finally {
    await load()
  }
}

/** 提交新建/编辑 */
async function submit() {
  if (!form.name?.trim() || !form.slug?.trim()) {
    message.warning('花名和 slug 必填')
    return
  }
  saving.value = true
  try {
    if (editing.value?.id) {
      await updateCrush(editing.value.id, form)
    } else {
      await createCrush(form)
    }
    message.success('保存成功')
    modalOpen.value = false
    await load()
  } finally {
    saving.value = false
  }
}

/** 关系分析：调用「她不一样」引擎（统计 + AI 鉴定 + 报告） */
async function analyze(record: Crush) {
  if (!record.id) return
  relResult.value = null
  relTitle.value = `关系分析 · ${record.name || record.slug}`
  relOpen.value = true
  relLoading.value = true
  try {
    const res = await analyzeRelationship(record.id)
    if (res.cached) message.info('聊天记录未变化，直接展示历史分析结果')
    relResult.value = res
  } catch (e) {
    message.error(e instanceof Error ? e.message : '分析失败')
  } finally {
    relLoading.value = false
  }
}

/** 打开完整 HTML 报告 */
function openReport(url: string) {
  reportUrl.value = url
  reportOpen.value = true
}

/** 删除 */
async function remove(record: Crush) {
  if (!record.id) return
  await deleteCrush(record.id)
  message.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.crush-list {
  background: var(--cupid-bg-card);
  border: 1px solid var(--cupid-border);
  border-radius: var(--cupid-radius);
  box-shadow: var(--cupid-shadow-sm);
  overflow: hidden;
}

/* 表格美化 */
.crush-table :deep(.ant-table-thead) {
  background: var(--cupid-gradient-soft) !important;
}

.crush-table :deep(.ant-table-thead > tr > th) {
  background: transparent !important;
  border-bottom: 1px solid var(--cupid-border) !important;
  font-weight: 600;
  color: var(--cupid-text);
}

.crush-table :deep(.ant-table-tbody > tr > td) {
  border-bottom: 1px solid var(--cupid-border) !important;
}

.crush-table :deep(.ant-table-tbody > tr:hover > td) {
  background: var(--cupid-gradient-soft) !important;
}

/* 名称单元格：头像 + 名称 */
.name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.name-cell__avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: var(--cupid-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 15px;
  flex-shrink: 0;
  box-shadow: var(--cupid-shadow-sm);
}

.name-cell__name {
  font-weight: 600;
  color: var(--cupid-text);
}

.name-cell__slug {
  font-size: 12px;
  color: var(--cupid-text-muted);
  margin-top: 1px;
}

.status-tag {
  border-radius: 12px !important;
  font-size: 12px !important;
  padding: 2px 10px !important;
}

/* 操作链接 */
.action-link {
  color: var(--cupid-text-secondary);
  cursor: pointer;
  font-size: 13px;
  transition: color 0.2s;
}

.action-link:hover {
  color: var(--cupid-primary);
}

.action-link--primary {
  color: var(--cupid-primary);
  font-weight: 600;
}

.action-link--danger:hover {
  color: #ff4d4f !important;
}

.action-divider {
  margin: 0 !important;
  background: var(--cupid-border) !important;
}

/* 构建日志 */
.build-log {
  max-height: 220px;
  overflow-y: auto;
  background: #1f1722;
  border-radius: var(--cupid-radius-sm);
  padding: 12px 14px;
  font-family: 'SFMono-Regular', Consolas, monospace;
}

.log-line {
  color: #ffb3c0;
  font-size: 12.5px;
  line-height: 1.9;
}

.build-result {
  margin-top: 14px;
}

.build-done-btn {
  margin-top: 14px;
  border-radius: var(--cupid-radius-sm) !important;
}

/* 关系分析 */
.rel-error {
  margin-bottom: 16px;
}

.rel-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 0;
  gap: 16px;
}

.rel-loading__text {
  color: var(--cupid-text-muted);
  font-size: 13px;
  text-align: center;
  line-height: 1.8;
}

.rel-scores {
  display: flex;
  gap: 20px;
  margin-bottom: 16px;
}

.rel-score {
  flex: 1;
  background: var(--cupid-bg-card);
  border: 1px solid var(--cupid-border);
  border-radius: var(--cupid-radius-sm);
  padding: 14px 16px;
}

.rel-score__label {
  font-size: 13px;
  color: var(--cupid-text-secondary);
  margin-bottom: 10px;
  font-weight: 600;
}

.rel-score__bar {
  height: 10px;
  border-radius: 5px;
  background: #f3edf0;
  overflow: hidden;
}

.rel-score__bar--love .rel-score__fill {
  background: linear-gradient(90deg, #f783ac, #faa2c1);
}

.rel-score__bar--cold .rel-score__fill {
  background: linear-gradient(90deg, #74c0fc, #a5d8ff);
}

.rel-score__fill {
  height: 100%;
  background: linear-gradient(90deg, #ff8787, #ffa8a8);
  border-radius: 5px;
  transition: width 0.6s ease;
}

.rel-score__num {
  margin-top: 8px;
  font-size: 20px;
  font-weight: 700;
  color: var(--cupid-text);
}

.rel-basic {
  margin-bottom: 16px;
}

.rel-verdict {
  background: var(--cupid-gradient-soft);
  border: 1px solid var(--cupid-border);
  border-radius: var(--cupid-radius-sm);
  padding: 16px;
  margin-bottom: 16px;
  line-height: 1.9;
}

.rel-verdict__type {
  font-size: 16px;
  font-weight: 700;
  color: var(--cupid-primary);
  margin-bottom: 6px;
}

.rel-verdict__stage,
.rel-verdict__trend {
  color: var(--cupid-text);
  font-size: 13.5px;
}

.rel-report-btn {
  border-radius: var(--cupid-radius-sm) !important;
  font-weight: 600;
}

.rel-iframe {
  width: 100%;
  height: 70vh;
  border: none;
  border-radius: var(--cupid-radius-sm);
  background: #fff;
}
</style>
