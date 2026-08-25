<template>
  <div>
    <a-space style="margin-bottom: 16px">
      <a-button type="primary" @click="openCreate">新建</a-button>
      <a-button @click="load">刷新</a-button>
    </a-space>

    <a-table
      :data-source="crushes"
      :columns="columns"
      row-key="id"
      :loading="loading"
      size="small"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <a-space>
            <a @click="openEdit(record)">编辑</a>
            <a-popconfirm title="确定删除？" @confirm="remove(record)">
              <a style="color: red">删除</a>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="modalOpen"
      :title="editing ? '编辑暗恋对象' : '新建暗恋对象'"
      :confirm-loading="saving"
      @ok="submit"
    >
      <a-form :model="form" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="花名" required>
          <a-input v-model:value="form.name" placeholder="如：小美" />
        </a-form-item>
        <a-form-item label="slug" required>
          <a-input v-model:value="form.slug" :disabled="!!editing" placeholder="如：xiaomei" />
        </a-form-item>
        <a-form-item label="MBTI"><a-input v-model:value="form.mbti" /></a-form-item>
        <a-form-item label="星座"><a-input v-model:value="form.zodiac" /></a-form-item>
        <a-form-item label="职业"><a-input v-model:value="form.occupation" /></a-form-item>
        <a-form-item label="性别"><a-input v-model:value="form.gender" /></a-form-item>
        <a-form-item label="认识时长"><a-input v-model:value="form.knowDuration" /></a-form-item>
        <a-form-item label="关系状态"><a-input v-model:value="form.relationshipStatus" /></a-form-item>
        <a-form-item label="印象"><a-textarea v-model:value="form.impression" :rows="2" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { createCrush, deleteCrush, listCrushes, updateCrush } from '@/api'
import type { Crush, CrushCreatePayload } from '@/types'

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
  { title: '花名', dataIndex: 'name', key: 'name' },
  { title: 'slug', dataIndex: 'slug', key: 'slug' },
  { title: 'MBTI', dataIndex: 'mbti', key: 'mbti' },
  { title: '关系', dataIndex: 'relationshipStatus', key: 'relationshipStatus' },
  { title: '操作', key: 'action', width: 120 },
]

const crushes = ref<Crush[]>([])
const loading = ref(false)
const modalOpen = ref(false)
const saving = ref(false)
const editing = ref<Crush | null>(null)

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
})

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
  })
}

async function load() {
  loading.value = true
  try {
    crushes.value = await listCrushes()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  resetForm()
  modalOpen.value = true
}

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
  })
  modalOpen.value = true
}

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

async function remove(record: Crush) {
  if (!record.id) return
  await deleteCrush(record.id)
  message.success('已删除')
  await load()
}

onMounted(load)
</script>
