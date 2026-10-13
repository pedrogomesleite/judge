import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { login } from '@/api/auth'
import { buscarUsuarioAtual, cadastrarUsuario } from '@/api/usuarios'
import type { LoginRequest, UsuarioCreateRequest, UsuarioResponse } from '@/api/types'

const CHAVE_SESSAO = 'juizin.sessao'

interface Sessao {
  token: string
  expiraEm: string
}

function lerSessao(): Sessao | null {
  try {
    const valor = localStorage.getItem(CHAVE_SESSAO)
    return valor ? (JSON.parse(valor) as Sessao) : null
  } catch {
    return null
  }
}

function gravarSessao(sessao: Sessao | null) {
  try {
    if (sessao) {
      localStorage.setItem(CHAVE_SESSAO, JSON.stringify(sessao))
    } else {
      localStorage.removeItem(CHAVE_SESSAO)
    }
  } catch {
    // Armazenamento indisponível (ex.: navegação privada): a sessão vale só enquanto a página estiver aberta
  }
}

export const useAuthStore = defineStore('auth', () => {
  const sessao = ref<Sessao | null>(lerSessao())
  const usuario = ref<UsuarioResponse | null>(null)

  const autenticado = computed(() => sessao.value !== null)

  /** Token atual, ou null se não houver sessão. Uma sessão expirada é encerrada aqui. */
  function tokenValido(): string | null {
    const atual = sessao.value
    if (!atual) {
      return null
    }
    if (Date.parse(atual.expiraEm) <= Date.now()) {
      sair()
      return null
    }
    return atual.token
  }

  async function entrar(dados: LoginRequest) {
    const resposta = await login(dados)
    sessao.value = { token: resposta.token, expiraEm: resposta.expiresAt }
    gravarSessao(sessao.value)
    await carregarUsuario()
  }

  async function cadastrar(dados: UsuarioCreateRequest) {
    await cadastrarUsuario(dados)
    await entrar({ login: dados.login, senha: dados.senha })
  }

  async function carregarUsuario() {
    if (tokenValido()) {
      usuario.value = await buscarUsuarioAtual()
    }
  }

  function sair() {
    sessao.value = null
    usuario.value = null
    gravarSessao(null)
  }

  return { usuario, autenticado, tokenValido, entrar, cadastrar, carregarUsuario, sair }
})
