
<template>
  <a-layout class="app-layout">
    <!-- 侧边栏 -->
    <a-layout-sider :width="220" class="app-sider">
      <div class="logo">
        <div class="logo__icon">💘</div>
        <div class="logo__text">
          <div class="logo__name">Cupid</div>
          <div class="logo__sub">暗恋模拟器</div>
        </div>
      </div>
      <a-menu
        v-model:selectedKeys="selectedKeys"
        theme="dark"
        mode="inline"
        class="app-menu"
        @click="onMenuClick"
      >
        <a-menu-item key="/chat">
          <span class="menu-icon">💬</span>
          <span>对话</span>
        </a-menu-item>
        <a-menu-item key="/crush">
          <span class="menu-icon">💞</span>
          <span>暗恋对象</span>
        </a-menu-item>
        <a-menu-item key="/skill">
          <span class="menu-icon">📚</span>
          <span>Skill 目录</span>
        </a-menu-item>
      </a-menu>
      <div class="sider-footer">made with 💗</div>
    </a-layout-sider>

    <!-- 主内容区 -->
    <a-layout class="app-main">
      <a-layout-content class="app-content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
/**
 * 应用根组件：负责整体布局与菜单路由跳转
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

/** 当前选中的菜单项（与路由路径绑定） */
const selectedKeys = computed(() => [route.path])

/** 点击菜单跳转 */
function onMenuClick({ key }: { key: string }) {
  router.push(key)
}
</script>

<style>
.app-layout {
  min-height: 100vh;
}

/* 侧边栏：深色玫红渐变背景 */
.app-sider {
  background: linear-gradient(180deg, #2a1f2e 0%, #3a2530 100%) !important;
  box-shadow: 4px 0 24px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
}

.app-sider .ant-layout-sider-children {
  display: flex;
  flex-direction: column;
  height: 100%;
}

/* Logo 区域 */
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 16px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.logo__icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: var(--cupid-gradient);
  font-size: 20px;
  box-shadow: 0 4px 12px rgba(255, 90, 122, 0.4);
  flex-shrink: 0;
}

.logo__text {
  min-width: 0;
}

.logo__name {
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.2;
}

.logo__sub {
  color: rgba(255, 255, 255, 0.55);
  font-size: 11px;
  margin-top: 2px;
}

/* 菜单：透明背景融入侧边栏 */
.app-menu {
  flex: 1;
  padding: 12px 8px;
  background: transparent !important;
  border-right: none !important;
}

.app-menu .ant-menu-item {
  border-radius: 10px;
  margin: 4px 0 !important;
  height: 42px;
  line-height: 42px;
  color: rgba(255, 255, 255, 0.75) !important;
  transition: all 0.25s ease;
}

.app-menu .ant-menu-item:hover {
  background: rgba(255, 90, 122, 0.15) !important;
  color: #fff !important;
}

.app-menu .ant-menu-item-selected {
  background: var(--cupid-gradient) !important;
  color: #fff !important;
  box-shadow: 0 4px 12px rgba(255, 90, 122, 0.35);
}

.menu-icon {
  display: inline-block;
  margin-right: 8px;
  font-size: 15px;
}

/* 侧边栏底部 */
.sider-footer {
  padding: 16px;
  text-align: center;
  color: rgba(255, 255, 255, 0.35);
  font-size: 11px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

/* 主内容区 */
.app-main {
  background: var(--cupid-bg-page);
}

.app-content {
  padding: 20px 24px;
  height: 100vh;
  overflow: hidden;
}
</style>
