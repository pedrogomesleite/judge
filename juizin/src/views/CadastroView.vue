<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { ApiError, mensagemDeErro } from '@/api/http'
import { destinoAposLogin } from '@/router'
import { useAuthStore } from '@/stores/auth'

// Mesmos limites do UsuarioCreateRequest
const SENHA_MIN = 8

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const form = reactive({ nome: '', login: '', senha: '', confirmacao: '' })
const enviando = ref(false)
const erro = ref<string | null>(null)
const errosCampos = ref<Record<string, string>>({})

async function cadastrar() {
  erro.value = null
  errosCampos.value = {}
  if (form.senha !== form.confirmacao) {
    errosCampos.value = { confirmacao: 'As senhas não conferem' }
    return
  }

  enviando.value = true
  try {
    await auth.cadastrar({ nome: form.nome, login: form.login, senha: form.senha })
    await router.replace(destinoAposLogin(route.query.redirect))
  } catch (e) {
    erro.value = mensagemDeErro(e)
    if (e instanceof ApiError) {
      errosCampos.value = e.campos
    }
  } finally {
    enviando.value = false
  }
}
</script>

<template>
  <div class="pagina-auth">
    <Card>
      <template #title>Criar conta</template>
      <template #content>
        <form class="stack" @submit.prevent="cadastrar">
          <Message v-if="erro" severity="error">{{ erro }}</Message>

          <div class="campo">
            <label for="nome">Nome</label>
            <InputText id="nome" v-model="form.nome" maxlength="120" autocomplete="name" required fluid />
            <small v-if="errosCampos.nome" class="erro-campo">{{ errosCampos.nome }}</small>
          </div>

          <div class="campo">
            <label for="login">Login</label>
            <InputText id="login" v-model="form.login" minlength="3" maxlength="60" autocomplete="username" required fluid />
            <small v-if="errosCampos.login" class="erro-campo">{{ errosCampos.login }}</small>
          </div>

          <div class="campo">
            <label for="senha">Senha</label>
            <Password
              v-model="form.senha"
              input-id="senha"
              :input-props="{ autocomplete: 'new-password', minlength: SENHA_MIN, maxlength: 72, required: true }"
              prompt-label="Mínimo de 8 caracteres"
              weak-label="Fraca"
              medium-label="Média"
              strong-label="Forte"
              toggle-mask
              fluid
            />
            <small v-if="errosCampos.senha" class="erro-campo">{{ errosCampos.senha }}</small>
          </div>

          <div class="campo">
            <label for="confirmacao">Confirmar senha</label>
            <Password
              v-model="form.confirmacao"
              input-id="confirmacao"
              :input-props="{ autocomplete: 'new-password', required: true }"
              :feedback="false"
              toggle-mask
              fluid
            />
            <small v-if="errosCampos.confirmacao" class="erro-campo">{{ errosCampos.confirmacao }}</small>
          </div>

          <Button type="submit" label="Criar conta" icon="pi pi-user-plus" :loading="enviando" />

          <span class="texto-suave">
            Já tem conta?
            <RouterLink :to="{ name: 'login', query: route.query }">Entrar</RouterLink>
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
