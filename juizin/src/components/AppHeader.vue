<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import Menubar from 'primevue/menubar'
import Button from 'primevue/button'
import type { MenuItem } from 'primevue/menuitem'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const itens = computed<MenuItem[]>(() => [
  { label: 'Atividades', icon: 'pi pi-list', route: { name: 'atividades' } },
  ...(auth.autenticado
    ? [
        { label: 'Meus grupos', icon: 'pi pi-users', route: { name: 'grupos' } },
        { label: 'Minhas respostas', icon: 'pi pi-code', route: { name: 'solucoes' } },
      ]
    : []),
])

function sair() {
  auth.sair()
  router.push({ name: 'atividades' })
}
</script>

<template>
  <Menubar :model="itens" class="app-header">
    <template #start>
      <RouterLink :to="{ name: 'atividades' }" class="marca">
        <i class="pi pi-check-square" />
        Juizin
      </RouterLink>
    </template>

    <template #item="{ item, props }">
      <RouterLink v-slot="{ href, navigate }" :to="item.route" custom>
        <a :href="href" v-bind="props.action" @click="navigate">
          <span :class="item.icon" />
          <span>{{ item.label }}</span>
        </a>
      </RouterLink>
    </template>

    <template #end>
      <div v-if="auth.autenticado" class="row">
        <span class="texto-suave"><i class="pi pi-user" /> {{ auth.usuario?.nome }}</span>
        <Button label="Sair" icon="pi pi-sign-out" severity="secondary" text @click="sair" />
      </div>
      <div v-else class="row">
        <Button as="router-link" :to="{ name: 'login' }" label="Entrar" icon="pi pi-sign-in" text />
        <Button as="router-link" :to="{ name: 'cadastro' }" label="Criar conta" />
      </div>
    </template>
  </Menubar>
</template>

<style scoped>
.app-header {
  border-radius: 0;
  border-width: 0 0 1px;
  padding-inline: max(1rem, calc((100% - 1100px) / 2 + 1rem));
}

.marca {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  margin-right: 1rem;
  font-size: 1.25rem;
  font-weight: 700;
  text-decoration: none;
}
</style>
