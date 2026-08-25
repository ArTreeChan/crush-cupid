import { createRouter, createWebHistory } from 'vue-router'
import ChatView from '@/views/ChatView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/chat' },
    { path: '/chat', component: ChatView, meta: { title: '对话' } },
    { path: '/crush', component: () => import('@/views/CrushListView.vue'), meta: { title: '暗恋对象' } },
    { path: '/skill', component: () => import('@/views/SkillCatalogView.vue'), meta: { title: 'Skill 目录' } },
  ],
})

export default router
