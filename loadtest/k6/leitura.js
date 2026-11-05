// Carga nos endpoints síncronos de leitura do catálogo público (sem login), para comparar com o fluxo assíncrono.
//
// Variáveis: TAXA (requisições por segundo, 50), DURACAO (2m), VUS_INICIAIS (20), VUS_MAX (200), RESUMO_JSON.
import exec from 'k6/execution';
import http from 'k6/http';
import { check, fail } from 'k6';
import { API } from './lib/api.js';
import { ESTATISTICAS, resumoEmTexto, submetricas } from './lib/resumo.js';

const ENDPOINTS = ['atividades', 'atividade', 'casos-publicos', 'linguagens'];

export const options = {
    scenarios: {
        leitura: {
            executor: 'constant-arrival-rate',
            rate: parseInt(__ENV.TAXA || '50', 10),
            timeUnit: '1s',
            duration: __ENV.DURACAO || '2m',
            preAllocatedVUs: parseInt(__ENV.VUS_INICIAIS || '20', 10),
            maxVUs: parseInt(__ENV.VUS_MAX || '200', 10),
        },
    },
    summaryTrendStats: ESTATISTICAS,
    thresholds: {
        ...submetricas('http_req_duration', 'endpoint', ENDPOINTS),
        http_req_failed: ['rate>=0'],
    },
};

export function setup() {
    const res = http.get(`${API}/api/activities`);
    if (res.status !== 200 || res.json().length === 0) {
        fail(`Nenhuma atividade no catálogo (HTTP ${res.status}); rode um cenário de envio ou o scripts/seed-atividades.sql antes`);
    }
    return { atividadeId: res.json()[0].id };
}

// Uma requisição por iteração, alternando os endpoints: a taxa total é TAXA, dividida entre os quatro.
export default function ({ atividadeId }) {
    const endpoint = ENDPOINTS[exec.scenario.iterationInTest % ENDPOINTS.length];
    const urls = {
        atividades: `${API}/api/activities`,
        atividade: `${API}/api/activities/${atividadeId}`,
        'casos-publicos': `${API}/api/activities/${atividadeId}/test-cases/public`,
        linguagens: `${API}/api/languages`,
    };
    const res = http.get(urls[endpoint], { tags: { endpoint } });
    check(res, { 'HTTP 200': (r) => r.status === 200 });
}

export function handleSummary(data) {
    const linhas = [
        ['Todas as leituras', 'http_req_duration'],
        ...ENDPOINTS.map((endpoint) => [`  ${endpoint}`, `http_req_duration{endpoint:${endpoint}}`]),
    ];
    const titulo = `Juizin — leitura do catálogo (${__ENV.TAXA || 50} req/s por ${__ENV.DURACAO || '2m'})`;
    const saidas = {
        stdout: resumoEmTexto(data, titulo, linhas, [['Requisições com erro', 'http_req_failed']],
            [['Iterações descartadas (sem VU)', 'dropped_iterations']]),
    };
    if (__ENV.RESUMO_JSON) {
        saidas[__ENV.RESUMO_JSON] = JSON.stringify(data, null, 2);
    }
    return saidas;
}
