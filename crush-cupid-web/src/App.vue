<template>
  <a-layout style="min-height: 100vh">
    <a-layout-sider :width="200">
      <div class="logo">💘 Cupid</div>
      <a-menu v-model:selectedKeys="selectedKeys" theme="dark" mode="inline" @click="onMenuClick">
        <a-menu-item key="/chat">对话</a-menu-item>
        <a-menu-item key="/crush">暗恋对象</a-menu-item>
        <a-menu-item key="/skill">Skill 目录</a-menu-item>
      </a-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-content style="padding: 16px">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const selectedKeys = computed(() => [route.path])

function onMenuClick({ key }: { key: string }) {
  router.push(key)
}
</script>

<style>
.logo {
  color: #fff;
  padding: 16px;
  font-weight: bold;
  font-size: 18px;
  text-align: center;
}
</style>
