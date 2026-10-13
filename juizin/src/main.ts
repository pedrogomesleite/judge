import { createApp } from 'vue'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice'
import Aura from '@primeuix/themes/aura'

import 'primeicons/primeicons.css'
import './assets/main.css'

import App from './App.vue'
import router from './router'
import { configurarAutenticacao } from './api/http'
import { useAuthStore } from './stores/auth'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(PrimeVue, {
  theme: {
    preset: Aura,
    // Tema claro fixo por enquanto: nenhuma tela aplica a classe .app-dark
    options: { darkModeSelector: '.app-dark' },
  },
})
app.use(ToastService)

const auth = useAuthStore()
configurarAutenticacao(
  () => auth.tokenValido(),
  () => {
    auth.sair()
    const atual = router.currentRoute.value
    if (atual.name !== 'login') {
      router.push({ name: 'login', query: { redirect: atual.fullPath } })
    }
  },
)
auth.carregarUsuario().catch(() => {
  // API fora do ar: o nome do usuário aparece no próximo carregamento; um 401 já encerra a sessão
})

app.mount('#app')
