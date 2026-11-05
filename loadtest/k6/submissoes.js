// Cenários de carga do envio de soluções (ver loadtest/README.md).
//
// Cada iteração = um usuário enviando uma solução e esperando o veredito pelo SSE, como no frontend. O tempo medido aqui
// é o percebido pelo cliente; a decomposição por fase (publicação, fila, avaliação) sai do banco (loadtest/rodar.py).
//
// Variáveis (-e NOME=valor):
//   CENARIO       baseline | carga | rampa | pico                      (padrão: carga)
//   RUN_ID        identificador da execução; o usuário de teste é "lt-<RUN_ID>"
//   MIX           mistura de soluções, ex.: aceita:60,errada:20,pesada:10,compilacao:5,tle:5
//   ENVIOS        baseline: envios em sequência (50); pico: envios simultâneos (100)
//   TAXA          carga: envios por minuto (30);   DURACAO: duração da carga (5m)
//   TAXA_INICIAL, TAXA_FINAL, DEGRAUS, DURACAO_DEGRAU   rampa: de 10 a 120 envios/min em 6 degraus de 2m
//   VUS_INICIAIS, VUS_MAX   VUs pré-alocados e máximos dos executores por taxa de chegada (50, 500)
//   ESPERA_FINAL  quanto as iterações em curso podem durar depois do fim da carga (15m)
//   RESUMO_JSON   caminho do resumo completo em JSON (opcional)
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';
import { aguardarVeredito, enviar, preparar } from './lib/api.js';
import { ESTATISTICAS, resumoEmTexto, submetricas } from './lib/resumo.js';
import { codigoPara, lerMistura, sequenciaDaMistura, VEREDITO_ESPERADO } from './lib/solucoes.js';

const CENARIO = __ENV.CENARIO || 'carga';
const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;
const MISTURA = lerMistura(__ENV.MIX || 'aceita:60,errada:20,pesada:10,compilacao:5,tle:5');
const SEQUENCIA = sequenciaDaMistura(MISTURA);
const TIPOS_DA_MISTURA = MISTURA.map(([tipo]) => tipo);
const ESPERA_FINAL = __ENV.ESPERA_FINAL || '15m';

const envioMs = new Trend('juizin_envio_ms', true);
const tempoTotalMs = new Trend('juizin_tempo_total_ms', true);
const envioAceito = new Rate('juizin_envio_aceito');
const vereditoCorreto = new Rate('juizin_veredito_correto');
const falhaInfra = new Rate('juizin_falha_infra');
const semVeredito = new Counter('juizin_sem_veredito');

function inteiro(nome, padrao) {
    const valor = parseInt(__ENV[nome] || `${padrao}`, 10);
    if (!(valor > 0)) {
        throw new Error(`${nome} deve ser um inteiro positivo`);
    }
    return valor;
}

function porTaxaDeChegada() {
    return {
        timeUnit: '1m',
        preAllocatedVUs: inteiro('VUS_INICIAIS', 50),
        maxVUs: inteiro('VUS_MAX', 500),
        gracefulStop: ESPERA_FINAL,
    };
}

function degrausDaRampa() {
    const inicial = inteiro('TAXA_INICIAL', 10);
    const final = inteiro('TAXA_FINAL', 120);
    const degraus = inteiro('DEGRAUS', 6);
    const duracao = __ENV.DURACAO_DEGRAU || '2m';
    const estagios = [];
    for (let i = 0; i < degraus; i++) {
        const taxa = degraus === 1 ? final : Math.round(inicial + (i * (final - inicial)) / (degraus - 1));
        estagios.push({ target: taxa, duration: '5s' }, { target: taxa, duration: duracao });
    }
    return { startRate: inicial, stages: estagios };
}

// Modelo aberto (taxa de chegada) em carga e rampa: novos envios chegam no ritmo definido mesmo que o sistema atrase,
// o que evita a "omissão coordenada" de um modelo fechado (N usuários em laço).
const CENARIOS = {
    // Um envio por vez: tempo de serviço sem fila (linha de base da decomposição).
    baseline: () => ({ executor: 'per-vu-iterations', vus: 1, iterations: inteiro('ENVIOS', 50), maxDuration: '2h' }),
    // Taxa constante: percentis em regime estável.
    carga: () => ({ executor: 'constant-arrival-rate', rate: inteiro('TAXA', 30), duration: __ENV.DURACAO || '5m', ...porTaxaDeChegada() }),
    // Taxa crescente em degraus: encontra o ponto de saturação (joelho da curva).
    rampa: () => ({ executor: 'ramping-arrival-rate', ...degrausDaRampa(), ...porTaxaDeChegada() }),
    // Todos ao mesmo tempo (fim de prova): profundidade da fila, tempo de drenagem e vazão máxima.
    pico: () => {
        const envios = inteiro('ENVIOS', 100);
        return { executor: 'shared-iterations', vus: envios, iterations: envios, maxDuration: '2h' };
    },
};

if (!CENARIOS[CENARIO]) {
    throw new Error(`CENARIO inválido: "${CENARIO}" (válidos: ${Object.keys(CENARIOS).join(', ')})`);
}

export const options = {
    scenarios: { [CENARIO]: CENARIOS[CENARIO]() },
    setupTimeout: '2m',
    summaryTrendStats: ESTATISTICAS,
    thresholds: {
        ...submetricas('juizin_tempo_total_ms', 'tipo', TIPOS_DA_MISTURA),
        ...submetricas('juizin_veredito_correto', 'tipo', TIPOS_DA_MISTURA, 'rate>=0'),
    },
};

export function setup() {
    return preparar(RUN_ID);
}

export default function (contexto) {
    const iteracao = exec.scenario.iterationInTest;
    const tipo = SEQUENCIA[iteracao % SEQUENCIA.length];
    const tags = { tipo };

    const inicio = Date.now();
    const res = enviar(contexto, codigoPara(tipo, `${contexto.login}-${iteracao}`), tags);
    envioMs.add(res.timings.duration, tags);
    envioAceito.add(res.status === 202, tags);
    if (res.status !== 202) {
        return;
    }

    const solucao = aguardarVeredito(contexto, res.json('id'), tags);
    if (!solucao) {
        semVeredito.add(1, tags);
        return;
    }
    tempoTotalMs.add(Date.now() - inicio, tags);
    falhaInfra.add(solucao.status === 'FALHA', tags);
    vereditoCorreto.add(solucao.veredito === VEREDITO_ESPERADO[tipo], tags);
}

export function handleSummary(data) {
    const linhas = [
        ['Envio (POST → 202)', 'juizin_envio_ms'],
        ['Tempo total (envio → veredito)', 'juizin_tempo_total_ms'],
        ...TIPOS_DA_MISTURA.map((tipo) => [`  tipo=${tipo}`, `juizin_tempo_total_ms{tipo:${tipo}}`]),
    ];
    const taxas = [
        ['Envios aceitos (202)', 'juizin_envio_aceito'],
        ['Veredito igual ao esperado', 'juizin_veredito_correto'],
        ...TIPOS_DA_MISTURA.map((tipo) => [`  tipo=${tipo}`, `juizin_veredito_correto{tipo:${tipo}}`]),
        ['Falha de infraestrutura (FALHA)', 'juizin_falha_infra'],
    ];
    const contadores = [
        ['Sem veredito no prazo', 'juizin_sem_veredito'],
        ['Iterações descartadas (sem VU)', 'dropped_iterations'],
    ];
    // RUN_ID sem valor muda a cada VU; o login vem do setup, que roda uma vez só
    const login = data.setup_data ? data.setup_data.login : '?';
    const titulo = `Juizin — cenário ${CENARIO} (usuário ${login}, mistura ${MISTURA.map((par) => par.join(':')).join(',')})`;
    const saidas = { stdout: resumoEmTexto(data, titulo, linhas, taxas, contadores) };
    if (__ENV.RESUMO_JSON) {
        saidas[__ENV.RESUMO_JSON] = JSON.stringify(data, null, 2);
    }
    return saidas;
}
