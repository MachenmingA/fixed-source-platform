import { createRouter, createWebHistory } from 'vue-router'
import { auth } from './store'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('./views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('./layout/AppLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('./views/Dashboard.vue') },
      { path: 'cockpit', name: 'cockpit', component: () => import('./views/Cockpit.vue') },
      { path: 'enterprises', name: 'enterprises', component: () => import('./views/Enterprises.vue') },
      { path: 'enterprises/:id', name: 'enterprise-profile', component: () => import('./views/EnterpriseProfile.vue') },
      { path: 'events', name: 'events', component: () => import('./views/Events.vue') },
      { path: 'tasks', name: 'tasks', component: () => import('./views/Tasks.vue') },
      { path: 'collect', name: 'collect', component: () => import('./views/Collect.vue') },
      { path: 'monitoring', name: 'monitoring', component: () => import('./views/Monitoring.vue') },
      { path: 'data-quality', name: 'data-quality', component: () => import('./views/DataQuality.vue') },
      { path: 'rules', name: 'rules', component: () => import('./views/Rules.vue') },
      { path: 'backtest', name: 'backtest', component: () => import('./views/Backtest.vue') },
      { path: 'test-data', name: 'test-data', component: () => import('./views/TestData.vue') },
      { path: 'integration', name: 'integration', component: () => import('./views/Integration.vue') },
      { path: 'data-hub', name: 'data-hub', component: () => import('./views/DataHub.vue') },
      { path: 'reports', name: 'reports', component: () => import('./views/Reports.vue') }
    ]
  },
  { path: '/reports/:id', name: 'report-view', component: () => import('./views/ReportView.vue') },
  { path: '/m', redirect: '/m/tasks' },
  { path: '/m/tasks', name: 'mobile-tasks', component: () => import('./views/mobile/MobileTasks.vue') },
  { path: '/m/tasks/:taskNo', name: 'mobile-task-detail', component: () => import('./views/mobile/MobileTaskDetail.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!auth.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
