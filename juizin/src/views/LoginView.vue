<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { mensagemDeErro } from '@/api/http'
import { destinoAposLogin } from '@/router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const login = ref('')
const senha = ref('')
const enviando = ref(false)
const erro = ref<string | null>(null)

async function entrar() {
  erro.value = null
  enviando.value = true
  try {
    await auth.entrar({ login: login.value, senha: senha.value })
    await router.replace(destinoAposLogin(route.query.redirect))
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    enviando.value = false
  }
}
</script>

<template>
  <div class="pagina-auth">
    <Card>
      <template #title>Entrar</template>
      <template #content>
        <form class="stack" @submit.prevent="entrar">
          <Message v-if="erro" severity="error">{{ erro }}</Message>

          <div class="campo">
            <label for="login">Login</label>
            <InputText id="login" v-model="login" autocomplete="username" required fluid />
          </div>

          <div class="campo">
            <label for="senha">Senha</label>
            <Password
              v-model="senha"
              input-id="senha"
              :input-props="{ autocomplete: 'current-password', required: true }"
              :feedback="false"
              toggle-mask
              fluid
            />
          </div>

          <Button type="submit" label="Entrar" icon="pi pi-sign-in" :loading="enviando" />

          <span class="texto-suave">
            Ainda não tem conta?
            <RouterLink :to="{ name: 'cadastro', query: route.query }">Criar conta</RouterLink>
          </span>
        </form>
      </template>
    </Card>
  </div>
</template>

<style scoped>
.pagina-auth {
  max-width: 420px;
  margin: 2rem auto;
}
</style>
