import { createRouter, createWebHistory, type RouteLocationRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    /** Exige login: sem sessão, vai para o login e volta depois. */
    requiresAuth?: boolean
    /** Só para visitantes (login e cadastro): com sessão, vai para os grupos. */
    guestOnly?: boolean
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: { name: 'atividades' } },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/signup',
      name: 'cadastro',
      component: () => import('@/views/CadastroView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/activities',
      name: 'atividades',
      component: () => import('@/views/AtividadesView.vue'),
    },
    {
      // ?groupId=<uuid> envia a resposta dentro do grupo
      path: '/activities/:id',
      name: 'atividade',
      component: () => import('@/views/AtividadeDetalheView.vue'),
      props: true,
    },
    {
      path: '/groups',
      name: 'grupos',
      component: () => import('@/views/GruposView.vue'),
      meta: { requiresAuth: true },
    },
    {
      // Link de convite compartilhado pelos membros: sem sessão, passa pelo login e volta aqui
      path: '/groups/join/:inviteCode',
      name: 'convite',
      component: () => import('@/views/ConviteView.vue'),
      props: true,
      meta: { requiresAuth: true },
    },
    {
      path: '/submissions',
      name: 'solucoes',
      component: () => import('@/views/SolucoesView.vue'),
      meta: { requiresAuth: true },
    },
    { path: '/:pathMatch(.*)*', redirect: { name: 'atividades' } },
  ],
})

router.beforeEach((to) => {
  const logado = useAuthStore().tokenValido() !== null
  if (to.meta.requiresAuth && !logado) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && logado) {
    return { name: 'grupos' }
  }
  return true
})

/** Destino depois do login. Só aceita caminhos internos, para o ?redirect não virar open redirect. */
export function destinoAposLogin(redirect: unknown): RouteLocationRaw {
  if (typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')) {
    return redirect
  }
  return { name: 'grupos' }
}

export default router
