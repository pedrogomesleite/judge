// Cliente HTTP mínimo da API do Juizin para os cenários k6.
import http from 'k6/http';
import { fail } from 'k6';
import { ATIVIDADE, CASOS } from './solucoes.js';

export const API = (__ENV.API_URL || 'http://api:8080').replace(/\/$/, '');

// Quanto o cliente espera pelo veredito no SSE. Em picos, a espera na fila pode passar de minutos.
const TIMEOUT_VEREDITO = __ENV.TIMEOUT_VEREDITO || '15m';

const JSON_HEADERS = { 'Content-Type': 'application/json' };
const FINAIS = ['CONCLUIDA', 'FALHA'];

function autenticado(token, extras = {}) {
    return { Authorization: `Bearer ${token}`, ...extras };
}

function exigir(res, status, descricao) {
    if (res.status !== status) {
        fail(`${descricao}: esperado HTTP ${status}, recebido ${res.status} (${res.error || res.body})`);
    }
    return res.json();
}

/**
 * Prepara o teste: cria um usuário exclusivo da execução (login "lt-<RUN_ID>"), faz login e cria a atividade com os
 * casos de teste oficiais. O login identifica as soluções desta execução no banco (loadtest/sql).
 */
export function preparar(runId) {
    const login = `lt-${runId}`.slice(0, 60);
    const senha = 'loadtest-juizin';
    const tags = { tags: { endpoint: 'preparacao' } };

    exigir(http.post(`${API}/api/users`, JSON.stringify({ nome: `Teste de carga ${runId}`, login, senha }),
        { headers: JSON_HEADERS, ...tags }), 201, 'Cadastro do usuário de teste');
    const { token } = exigir(http.post(`${API}/api/auth/login`, JSON.stringify({ login, senha }),
        { headers: JSON_HEADERS, ...tags }), 200, 'Login');

    const linguagens = exigir(http.get(`${API}/api/languages`, tags), 200, 'Listar linguagens');
    const cpp = linguagens.find((linguagem) => linguagem.nome === 'C++');
    if (!cpp) {
        fail('Linguagem C++ não cadastrada (o LinguagemSeeder roda quando a API sobe)');
    }

    const atividade = exigir(http.post(`${API}/api/activities`, JSON.stringify(ATIVIDADE),
        { headers: autenticado(token, JSON_HEADERS), ...tags }), 201, 'Criar atividade');
    for (const [entrada, saidaEsperada] of CASOS) {
        exigir(http.post(`${API}/api/activities/${atividade.id}/test-cases`,
            JSON.stringify({ entrada, saidaEsperada, tipo: 'OFICIAL', publico: false }),
            { headers: autenticado(token, JSON_HEADERS), ...tags }), 201, 'Criar caso de teste');
    }

    return { login, token, atividadeId: atividade.id, linguagemId: cpp.id };
}

/** POST /api/submissions (resposta esperada: 202). */
export function enviar(contexto, codigo, tags = {}) {
    return http.post(`${API}/api/submissions`,
        JSON.stringify({ atividadeId: contexto.atividadeId, linguagemId: contexto.linguagemId, codigo }),
        {
            headers: autenticado(contexto.token, JSON_HEADERS),
            tags: { endpoint: 'envio', name: 'POST /api/submissions', ...tags },
        });
}

/**
 * Espera o veredito pelo mesmo caminho do frontend: o SSE da solução (ADR-003). A API encerra o stream quando a solução
 * termina, então a requisição só volta com o veredito. Devolve o último estado recebido, ou null se não terminou.
 */
export function aguardarVeredito(contexto, solucaoId, tags = {}) {
    const res = http.get(`${API}/api/submissions/${solucaoId}/events`, {
        headers: autenticado(contexto.token, { Accept: 'text/event-stream, application/json' }),
        timeout: TIMEOUT_VEREDITO,
        tags: { endpoint: 'sse', name: 'GET /api/submissions/{id}/events', ...tags },
    });
    const ultimo = res.status === 200 ? ultimoEvento(res.body) : null;
    if (ultimo && FINAIS.includes(ultimo.status)) {
        return ultimo;
    }
    // Stream interrompido (timeout, conexão): confere o estado atual antes de desistir
    const atual = http.get(`${API}/api/submissions/${solucaoId}`, {
        headers: autenticado(contexto.token),
        tags: { endpoint: 'consulta', name: 'GET /api/submissions/{id}', ...tags },
    });
    if (atual.status === 200 && FINAIS.includes(atual.json('status'))) {
        return atual.json();
    }
    return null;
}

/** Último "data:" do corpo SSE (cada evento traz o SolucaoResponse em JSON). */
function ultimoEvento(corpo) {
    if (!corpo) {
        return null;
    }
    const dados = corpo.split('\n').filter((linha) => linha.startsWith('data:'));
    if (dados.length === 0) {
        return null;
    }
    try {
        return JSON.parse(dados[dados.length - 1].slice('data:'.length));
    } catch (e) {
        return null;
    }
}
