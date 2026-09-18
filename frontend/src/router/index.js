import { createRouter, createWebHistory } from 'vue-router'
import Linens from '../views/Linens.vue'
import Batches from '../views/Batches.vue'
import Issues from '../views/Issues.vue'
import Losses from '../views/Losses.vue'
import Seals from '../views/Seals.vue'
import Shortages from '../views/Shortages.vue'

const routes = [
  { path: '/', redirect: '/linens' },
  { path: '/linens', component: Linens, meta: { title: '布草台账' } },
  { path: '/seals', component: Seals, meta: { title: '污染封存' } },
  { path: '/batches', component: Batches, meta: { title: '洗涤批次' } },
  { path: '/shortages', component: Shortages, meta: { title: '回洗追差' } },
  { path: '/issues', component: Issues, meta: { title: '楼层收发' } },
  { path: '/losses', component: Losses, meta: { title: '报损赔付' } }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
