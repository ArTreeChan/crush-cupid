import { createRouter, createWebHistory } from 'vue-router'
import ChatView from '@/views/ChatView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/chat' },
    { path: '/chat', component: ChatView, meta: { title: '对话' } },
    { path: '/advisor', component: () => import('@/views/AdvisorView.vue'), meta: { title: '军师' } },
    { path: '/crush', component: () => import('@/views/CrushListView.vue'), meta: { title: '暗恋对象' } },
    { path: '/skill', component: () => import('@/views/SkillCatalogView.vue'), meta: { title: 'Skill 目录' } },
    { path: '/ai-provider', component: () => import('@/views/AiProviderView.vue'), meta: { title: '大模型 API' } },
  ],
})

export default router
