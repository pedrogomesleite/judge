// Resumo em texto do k6 só com as métricas de interesse, sem depender do jslib remoto (o handleSummary substitui o
// resumo padrão).

export const ESTATISTICAS = ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)', 'max', 'count'];

const COLUNAS = [['count', 'n'], ['avg', 'média'], ['med', 'p50'], ['p(90)', 'p90'], ['p(95)', 'p95'],
    ['p(99)', 'p99'], ['p(99.9)', 'p99.9'], ['max', 'máx']];

function numero(valor, casas = 0) {
    return valor === undefined || valor === null ? '-' : Number(valor).toFixed(casas);
}

function linhaTendencia(rotulo, metrica) {
    const valores = metrica ? metrica.values : {};
    const celulas = COLUNAS.map(([chave]) => numero(valores[chave]).padStart(8));
    return `${rotulo.padEnd(34)}${celulas.join('')}`;
}

/**
 * @param linhas pares [rótulo, nome da métrica], na ordem em que aparecem; métricas ausentes saem com "-".
 * @param taxas pares [rótulo, nome da métrica Rate].
 * @param contadores pares [rótulo, nome da métrica Counter].
 */
export function resumoEmTexto(data, titulo, linhas, taxas = [], contadores = []) {
    const m = data.metrics;
    const saida = ['', titulo, ''];
    saida.push(`${'(ms)'.padEnd(34)}${COLUNAS.map(([, nome]) => nome.padStart(8)).join('')}`);
    for (const [rotulo, nome] of linhas) {
        saida.push(linhaTendencia(rotulo, m[nome]));
    }
    saida.push('');
    for (const [rotulo, nome] of taxas) {
        const metrica = m[nome];
        const texto = metrica ? `${(metrica.values.rate * 100).toFixed(2)}% (${metrica.values.passes}/${metrica.values.passes + metrica.values.fails})` : '-';
        saida.push(`${rotulo.padEnd(34)}${texto}`);
    }
    for (const [rotulo, nome] of contadores) {
        saida.push(`${rotulo.padEnd(34)}${m[nome] ? m[nome].values.count : 0}`);
    }
    saida.push('');
    return saida.join('\n');
}

/**
 * Thresholds que sempre passam: só existem para o k6 calcular as submétricas por tag no resumo.
 * Use 'max>=0' para Trend e 'rate>=0' para Rate.
 */
export function submetricas(metrica, tag, valores, expressao = 'max>=0') {
    return Object.fromEntries(valores.map((valor) => [`${metrica}{${tag}:${valor}}`, [expressao]]));
}
