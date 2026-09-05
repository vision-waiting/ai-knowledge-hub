import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({ history: createWebHistory(), routes: [{ path: '/login', component: { template: '<div>Login</div>' } }, { path: '/:pathMatch(.*)*', redirect: '/login' }] })
export default router

