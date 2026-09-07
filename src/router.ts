import { createRouter, createWebHistory } from 'vue-router'
import HomeView from './views/HomeView.vue'
import ExploreView from './views/ExploreView.vue'
import RecognizeView from './views/RecognizeView.vue'
import RecognitionResultView from './views/RecognitionResultView.vue'
import ExhibitDetailView from './views/ExhibitDetailView.vue'
import GuideView from './views/GuideView.vue'
import PassportView from './views/PassportView.vue'
import SourcesView from './views/SourcesView.vue'
import OfflineView from './views/OfflineView.vue'

export const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/explore', name: 'explore', component: ExploreView },
    { path: '/recognize', name: 'recognize', component: RecognizeView },
    { path: '/recognitions/:id', name: 'recognition-result', component: RecognitionResultView },
    { path: '/exhibits/:id', name: 'exhibit-detail', component: ExhibitDetailView },
    { path: '/guide', name: 'guide', component: GuideView },
    { path: '/guide/:sessionId', name: 'guide-session', component: GuideView },
    { path: '/passport', name: 'passport', component: PassportView },
    { path: '/about/sources', name: 'sources', component: SourcesView },
    { path: '/offline', name: 'offline', component: OfflineView },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})
