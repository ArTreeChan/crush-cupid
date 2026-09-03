
<template>
  <PageContainer icon="🧠" title="军师" subtitle="独立对话 · 帮你分析怎么追 ta，不走模拟话术">
    <div class="advisor-page">
      <a-row :gutter="20" class="advisor-row">
        <!-- 左侧：crush 选择 + 报告入口 -->
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
              :disabled="!currentSlug || relLoading"
              @click="openRelationAnalysis"
            >
              <span>📚</span>&nbsp;关系分析
            </a-button>
            <div class="side-card__hint">
              <span>💡</span> 军师对话使用独立会话记忆，不会出现在「对话」页的聊天记录里。
            </div>
          </div>
        </a-col>

        <!-- 右侧：军师对话区 -->
        <a-col :span="18" class="advisor-col">
          <div class="chat-card">
            <div class="chat-card__head">
              <div class="chat-card__title">
                {{ currentSlug ? `🧠 军师 · ${currentName}` : '请先选择 crush' }}
              </div>
              <div class="chat-card__sub" v-if="currentCrush">
                {{ currentCrush.mbti || '—' }} · {{ currentCrush.zodiac || '—' }}
              </div>
            </div>

            <!-- 快捷指令 chips -->
            <div v-if="commands.length" class="quick-chips">
              <a-tag
                v-for="cmd in commands"
                :key="cmd.trigger"
                class="quick-chip"
                :color="cmd.requiresCrush ? 'purple' : 'geekblue'"
                :disabled="streaming"
                @click="sendQuick(cmd)"
              >
                {{ cmd.title }}
              </a-tag>
            </div>

            <div ref="msgBox" class="messages">
              <div v-if="messages.length === 0 && !streaming" class="empty">
                <div class="empty__icon">🧠</div>
                <div class="empty__text">说说你的情况，军师帮你出主意～</div>
              </div>
              <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
                <div class="avatar">{{ m.role === 'user' ? '🧑' : '🧠' }}</div>
                <div class="bubble">
                  <span>{{ m.content }}</span>
                  <span v-if="streaming && i === messages.length - 1" class="cursor">▋</span>
                </div>
              </div>
              <div v-if="streaming && !messages.length" class="msg assistant">
                <div class="avatar">🧠</div>
                <div class="bubble">
                  <span class="typing">军师思考中…</span>
                  <span class="cursor">▋</span>
                </div>
              </div>
            </div>

            <div class="input-row">
              <textarea
                ref="inputRef"
                v-model="input"
                :rows="2"
                placeholder="描述你的情况 / 粘贴聊天记录…（Enter 发送，Shift+Enter 换行）"
                :disabled="streaming"
                class="input-area native-textarea"
                @keydown="onKeydown"
              ></textarea>
              <a-button
                type="primary"
                size="large"
                class="send-btn"
                :loading="streaming"
                :disabled="!currentSlug || streaming || !input.trim()"
                @click="send"
              >
                问军师
              </a-button>
            </div>
          </div>
        </a-col>
      </a-row>

      <!-- 报告历史弹窗 -->
      <a-modal
        v-model:open="reportOpen"
        :title="`📚 关系报告 · ${currentName}`"
        width="760"
        :footer="null"
      >
        <div class="report-body">
          <div class="report-row report-row--actions">
            <a-button type="primary" :loading="reportBusy" @click="generateNow">生成新报告</a-button>
            <a-button
              :disabled="!reportMd"
              @click="downloadCurrent"
            >导出 Word (.docx)</a-button>
          </div>
          <a-spin :spinning="reportBusy">
            <pre v-if="reportMd" class="report-pre">{{ reportMd }}</pre>
            <div v-else class="report-placeholder">点击「生成新报告」由军师综合 ta 的资料生成一份关系分析报告。</div>
          </a-spin>
          <div class="report-history" v-if="reportHistory.length">
            <div class="report-history__title">📚 历史报告（{{ reportHistory.length }}）</div>
            <div v-for="r in reportHistory" :key="r.id" class="report-history__item">
              <div class="report-history__info">
                <div class="report-history__name">{{ r.title || '关系报告' }}</div>
                <div class="report-history__meta">
                  {{ r.reportDate || '—' }}
                  <a-tag v-if="r.source === 'scheduled'" color="blue">定时</a-tag>
                  <a-tag v-else color="green">手动</a-tag>
                </div>
              </div>
              <div class="report-history__ops" @click.stop>
                <a-button size="small" @click="downloadSaved(r)">下载</a-button>
                <a-button size="small" @click="loadDetail(r)">详情</a-button>
                <a-popconfirm title="删除该报告？" @confirm="removeReport(r)">
                  <a-button size="small" danger>删除</a-button>
                </a-popconfirm>
              </div>
            </div>
          </div>
        </div>
      </a-modal>

      <!-- 报告详情弹窗 -->
      <a-modal
        v-model:open="detailOpen"
        :title="'📑 报告详情'"
        width="780"
        :footer="null"
      >
        <a-spin :spinning="detailBusy">
          <pre class="report-pre">{{ detailContent }}</pre>
        </a-spin>
      </a-modal>

      <!-- 关系分析弹窗（她不一样引擎） -->
      <a-modal
        v-model:open="relOpen"
        width="860"
        :footer="null"
      >
        <template #title>
          <div class="rel-modal-title">
            <span>{{ relTitle }}</span>
            <a-dropdown v-if="relResult && relResult.reportUrl">
              <a-button size="small" type="primary" ghost class="rel-download-btn">⬇ 下载报告</a-button>
              <template #overlay>
                <a-menu @click="onDownloadMenuClick">
                  <a-menu-item key="html">HTML 版</a-menu-item>
                  <a-menu-item key="pdf">PDF 版</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
        </template>
        <template v-if="relLoading">
          <div class="rel-loading">
            <a-spin size="large" />
            <div class="rel-loading__text">正在分析聊天记录（统计 + AI 深度鉴定）…<br />约需 1~3 分钟，请稍候</div>
          </div>
        </template>
        <template v-else-if="relResult">
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
          <a-button v-if="relResult.reportUrl" type="primary" block size="large" class="rel-report-btn" @click="openReport(relResult.reportUrl)">
            打开完整 HTML 报告（统计图表 + 详细鉴定）
          </a-button>
        </template>
      </a-modal>

      <!-- 完整报告弹窗（iframe 嵌入 HTML 报告） -->
      <a-modal
        v-model:open="reportViewOpen"
        title="关系分析报告"
        width="960"
        :footer="null"
        destroy-on-close
      >
        <iframe :src="reportViewUrl" class="rel-iframe" />
      </a-modal>
    </div>
  </PageContainer>
</template>

<script setup lang="ts">
/**
 * 军师页面：独立于模拟对话的咨询界面。
 * 使用 /api/chat/advisor（军师人设 + 独立内存记忆），配合军师子命令快捷入口与关系报告。
 */
import { computed, nextTick, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  advisorStreamChat,
  analyzeRelationship,
  deleteReport,
  downloadReportLive,
  downloadSavedReport,
  generateReport,
  getReportDetail,
  listAdvisorCommands,
  listCrushes,
  listReports,
} from '@/api'
import type { AdvisorCommand, Crush, CrushReport, RelationshipResult } from '@/types'
import PageContainer from '@/components/PageContainer.vue'

interface AdvisorMessage {
  role: 'user' | 'assistant'
  content: string
}

const crushes = ref<Crush[]>([])
const loading = ref(false)
const currentSlug = ref<string>()
const messages = ref<AdvisorMessage[]>([])
const input = ref('')
const streaming = ref(false)
const commands = ref<AdvisorCommand[]>([])
const inputRef = ref<HTMLTextAreaElement>()
const msgBox = ref<HTMLElement>()

const reportOpen = ref(false)
const reportBusy = ref(false)
const reportMd = ref('')
const reportHistory = ref<CrushReport[]>([])
const detailOpen = ref(false)
const detailBusy = ref(false)
const detailContent = ref('')

// 关系分析（她不一样引擎）
const relOpen = ref(false)
const relLoading = ref(false)
const relTitle = ref('关系分析')
const relResult = ref<RelationshipResult | null>(null)
const reportViewOpen = ref(false)
const reportViewUrl = ref('')

const crushOptions = computed(() =>
  crushes.value.map((c) => ({ label: `${c.name} (${c.slug})`, value: c.slug })),
)
const currentName = computed(
  () => crushes.value.find((c) => c.slug === currentSlug.value)?.name ?? '',
)
const currentCrush = computed(() =>
  crushes.value.find((c) => c.slug === currentSlug.value),
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

async function loadCommands() {
  try {
    commands.value = await listAdvisorCommands()
  } catch {
    commands.value = []
  }
}

async function scrollToBottom() {
  await nextTick()
  if (msgBox.value) {
    msgBox.value.scrollTop = msgBox.value.scrollHeight
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.isComposing) return
  if (e.key !== 'Enter') return
  if (e.shiftKey || e.altKey || e.ctrlKey || e.metaKey) return
  e.preventDefault()
  void send()
}

/** 发送一条消息给军师并流式接收回复 */
async function sendUser(text: string) {
  if (!currentSlug.value || streaming.value) return
  const t = text.trim()
  if (!t) return
  messages.value.push({ role: 'user', content: t })
  input.value = ''
  streaming.value = true
  messages.value.push({ role: 'assistant', content: '' })
  const aiIdx = messages.value.length - 1
  await scrollToBottom()
  try {
    await advisorStreamChat(currentSlug.value, t, (chunk) => {
      if (!chunk.done && chunk.content) {
        messages.value[aiIdx].content += chunk.content
      }
      scrollToBottom()
    })
  } catch (e) {
    const msg = e instanceof Error ? e.message : '军师暂时掉线'
    messages.value[aiIdx].content = `[错误] ${msg}`
  } finally {
    streaming.value = false
    await scrollToBottom()
  }
}

function send() {
  void sendUser(input.value)
}

/** 点击快捷指令 chip：将指令语贴进输入框并触发（相当于替用户补全子命令） */
function sendQuick(cmd: AdvisorCommand) {
  if (streaming.value || !currentSlug.value) return
  void sendUser(`${cmd.title}：`)
}

/* ---------- 关系分析（她不一样引擎） ---------- */
async function openRelationAnalysis() {
  if (!currentCrush.value?.id) {
    message.warning('请先选择暗恋对象')
    return
  }
  relTitle.value = `关系分析 · ${currentCrush.value.name}`
  relOpen.value = true
  relLoading.value = true
  relResult.value = null
  try {
    const res = await analyzeRelationship(currentCrush.value.id)
    if (res.cached) message.info('聊天记录未变化，直接展示历史分析结果')
    relResult.value = res
  } catch (e) {
    message.error(e instanceof Error ? e.message : '分析失败')
    relOpen.value = false
  } finally {
    relLoading.value = false
  }
}

function openReport(url: string) {
  reportViewUrl.value = url
  reportViewOpen.value = true
}

/** 下载 she-love-me HTML 关系分析报告 */
async function downloadRelReport() {
  const url = relResult.value?.reportUrl
  if (!url) return
  try {
    const resp = await fetch(url)
    if (!resp.ok) throw new Error('下载失败：HTTP ' + resp.status)
    const blob = await resp.blob()
    const name = url.split('/').pop() || 'relationship_report.html'
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `关系分析报告_${name}`
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(a.href)
    message.success('报告已下载')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '下载失败')
  }
}

/** 下载下拉菜单 */
async function onDownloadMenuClick({ key }: { key: string }) {
  if (key === 'html') await downloadRelReport()
  else if (key === 'pdf') await downloadRelPdf()
}

/** 下载 she-love-me HTML 报告为 PDF（html2pdf.js：html2canvas 渲染 → jsPDF 导出） */
async function downloadRelPdf() {
  const url = relResult.value?.reportUrl
  if (!url) return
  const loadingKey = 'relPdf'
  message.loading({ content: '正在渲染报告为 PDF…', key: loadingKey })
  let iframe: HTMLIFrameElement | null = null
  try {
    const html2pdf = (await import('html2pdf.js')).default
    iframe = document.createElement('iframe')
    iframe.style.position = 'fixed'
    iframe.style.right = '0'
    iframe.style.bottom = '0'
    iframe.style.width = '0'
    iframe.style.height = '0'
    iframe.style.border = '0'
    document.body.appendChild(iframe)
    iframe.src = url
    await new Promise<void>((resolve, reject) => {
      const t = window.setTimeout(() => reject(new Error('报告加载超时')), 30000)
      iframe!.onload = () => { window.clearTimeout(t); resolve() }
      iframe!.onerror = () => { window.clearTimeout(t); reject(new Error('报告加载失败')) }
    })
    const doc = iframe.contentDocument || (iframe.contentWindow as unknown as { document: Document }).document
    if (!doc || !doc.body) throw new Error('无法读取报告内容')
    const name = url.split('/').pop() || 'relationship_report'
    const opt = {
      margin: 8,
      filename: `关系分析报告_${name.replace(/\.html$/i, '')}.pdf`,
      image: { type: 'jpeg', quality: 0.95 },
      html2canvas: { scale: 2, useCORS: true, windowWidth: doc.body.scrollWidth || undefined },
      jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' },
      pagebreak: { mode: ['avoid-all', 'css', 'legacy'] },
    }
    await html2pdf().set(opt).from(doc.body).save()
    iframe.remove()
    message.success({ content: 'PDF 已下载', key: loadingKey })
  } catch (e) {
    if (iframe) iframe.remove()
    message.error({ content: e instanceof Error ? e.message : 'PDF 生成失败', key: loadingKey })
  }
}

/* ---------- 报告 ---------- */
async function openReports() {
  if (!currentSlug.value) return
  reportOpen.value = true
  reportMd.value = ''
  await loadReportHistory()
}

async function loadReportHistory() {
  if (!currentSlug.value) {
    reportHistory.value = []
    return
  }
  try {
    reportHistory.value = await listReports(currentSlug.value)
  } catch {
    reportHistory.value = []
  }
}

async function generateNow() {
  if (!currentSlug.value) return
  reportBusy.value = true
  reportMd.value = ''
  try {
    const report = await generateReport(currentSlug.value)
    reportMd.value = report.markdown || ''
    message.success('报告已生成并保存')
    await loadReportHistory()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '生成失败')
  } finally {
    reportBusy.value = false
  }
}

async function downloadCurrent() {
  if (!currentSlug.value) return
  try {
    await downloadReportLive(currentSlug.value, reportMd.value || undefined)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '下载失败')
  }
}

async function downloadSaved(r: CrushReport) {
  try {
    await downloadSavedReport(r.id, currentName.value)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '下载失败')
  }
}

async function loadDetail(r: CrushReport) {
  detailOpen.value = true
  detailBusy.value = true
  detailContent.value = r.markdown || '加载中…'
  try {
    if (!r.markdown) {
      const full = await getReportDetail(r.id)
      detailContent.value = full.markdown || '（无内容）'
    }
  } catch {
    detailContent.value = '加载失败'
  } finally {
    detailBusy.value = false
  }
}

async function removeReport(r: CrushReport) {
  try {
    await deleteReport(r.id)
    message.success('已删除')
    await loadReportHistory()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败')
  }
}

onMounted(() => {
  void loadCrushes()
  void loadCommands()
})
</script>

<style scoped>
.advisor-page {
  height: 100%;
}

.advisor-row {
  height: 100%;
}

.advisor-col {
  height: 100%;
}

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

.quick-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 20px;
  border-bottom: 1px solid var(--cupid-border);
}

.quick-chip {
  cursor: pointer;
  margin: 0;
  padding: 4px 12px;
  border-radius: 999px;
}

.quick-chip:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 18px 20px;
  background:
    radial-gradient(circle at 20% 20%, rgba(114, 86, 255, 0.04), transparent 40%),
    radial-gradient(circle at 80% 80%, rgba(255, 142, 83, 0.03), transparent 40%);
}

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

.cursor {
  display: inline-block;
  color: var(--cupid-primary);
  animation: blink 1s steps(2) infinite;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  50.01%, 100% { opacity: 0; }
}

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

.native-textarea {
  resize: none;
  border: 1px solid var(--ant-color-border, #d9d9d9);
  padding: 8px 12px;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.5;
  background: var(--ant-color-bg-container, #fff);
  color: var(--ant-color-text, rgba(0,0,0,0.88));
  outline: none;
  transition: border-color 0.2s;
}

.native-textarea:focus {
  border-color: var(--ant-color-primary, #69b1ff);
  box-shadow: 0 0 0 2px rgba(105,177,255,0.2);
}

.native-textarea:disabled {
  background: var(--ant-color-bg-container-disabled, #f5f5f5);
  cursor: not-allowed;
}

.send-btn {
  border-radius: var(--cupid-radius-sm) !important;
  min-width: 90px;
  height: 60px !important;
}

.report-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.report-row--actions {
  display: flex;
  gap: 10px;
}

.report-placeholder {
  padding: 20px;
  text-align: center;
  color: var(--cupid-text-muted);
  background: var(--cupid-gradient-soft);
  border-radius: 10px;
  font-size: 13px;
}

.report-pre {
  max-height: 360px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  background: #fafafa;
  border: 1px solid var(--cupid-border);
  border-radius: 10px;
  padding: 14px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--cupid-text);
}

.report-history {
  border-top: 1px dashed var(--cupid-border);
  padding-top: 8px;
}

.report-history__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--cupid-text-secondary);
  margin: 8px 0;
}

.report-history__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border: 1px solid var(--cupid-border);
  border-radius: 10px;
  margin-bottom: 8px;
  transition: all 0.2s;
}

.report-history__item:hover {
  border-color: var(--cupid-primary);
}

.report-history__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--cupid-text);
}

.report-history__meta {
  font-size: 12px;
  color: var(--cupid-text-muted);
  margin-top: 2px;
}

.report-history__ops {
  display: flex;
  gap: 6px;
}

/* 关系分析（她不一样引擎） */
.rel-modal-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 4px;
}

.rel-download-btn {
  font-size: 12px;
}

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
