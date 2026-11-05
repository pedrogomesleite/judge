// Atividade e soluções C++ usadas nos testes de carga. Cada tipo de solução tem um veredito esperado, e a mistura
// (variável MIX) define a proporção de cada tipo, para a carga se parecer com a de uma turma real.

export const ATIVIDADE = {
    titulo: 'Teste de carga: soma de dois inteiros',
    enunciado: 'Leia dois inteiros A e B (-10^9 ≤ A, B ≤ 10^9) e imprima A + B.',
    limiteTempoMs: 1000,
    limiteMemoriaMb: 256,
};

// [entrada, saída esperada]. O primeiro caso já reprova a solução "errada" (imprime A - B).
export const CASOS = [
    ['1 2\n', '3\n'],
    ['-5 12\n', '7\n'],
    ['1000000000 1000000000\n', '2000000000\n'],
    ['0 0\n', '0\n'],
    ['-999999999 -1\n', '-1000000000\n'],
];

const LER_E_SOMAR = `#include <iostream>
int main() {
    long long a, b;
    std::cin >> a >> b;
    std::cout << a + b << "\\n";
}
`;

const CODIGOS = {
    // Correta e leve: o custo é praticamente só a compilação.
    aceita: LER_E_SOMAR,
    // Compila e roda, mas a saída está errada: para no primeiro caso.
    errada: `#include <iostream>
int main() {
    long long a, b;
    std::cin >> a >> b;
    std::cout << a - b << "\\n";
}
`,
    // Correta, mas gasta CPU em todos os casos (~0,1 a 0,3 s por caso, conforme a máquina). É a referência para medir a
    // estabilidade do tempo medido pelo isolate sob carga (vizinho barulhento).
    pesada: `#include <iostream>
int main() {
    volatile unsigned long long x = 0;
    for (unsigned long long i = 0; i < 150000000ULL; i++) x = x + i;
    long long a, b;
    std::cin >> a >> b;
    std::cout << a + b << "\\n";
}
`,
    // Laço infinito: consome o limite de tempo inteiro (1 s de CPU) no primeiro caso.
    tle: `int main() {
    volatile unsigned long long x = 0;
    for (;;) x = x + 1;
}
`,
    // Aloca e toca 512 MB com limite de 256 MB.
    memoria: `#include <cstring>
#include <cstdlib>
int main() {
    const unsigned long tamanho = 512UL << 20;
    char *p = static_cast<char *>(std::malloc(tamanho));
    std::memset(p, 1, tamanho);
    return p[tamanho - 1] == 1 ? 0 : 1;
}
`,
    // Não compila: o g++ falha dentro do sandbox.
    compilacao: `int main() {
    return x;
}
`,
};

export const VEREDITO_ESPERADO = {
    aceita: 'ACEITA',
    errada: 'RESPOSTA_ERRADA',
    pesada: 'ACEITA',
    tle: 'TEMPO_EXCEDIDO',
    memoria: 'MEMORIA_EXCEDIDA',
    compilacao: 'ERRO_COMPILACAO',
};

export const TIPOS = Object.keys(CODIGOS);

/**
 * Código do tipo com um marcador no fim. O marcador torna cada envio único e permite classificar as soluções no banco
 * (loadtest/sql/solucoes-do-teste.sql extrai o "tipo=").
 */
export function codigoPara(tipo, identificador) {
    return `${CODIGOS[tipo]}// juizin-loadtest tipo=${tipo} id=${identificador}\n`;
}

/** "aceita:60,errada:20" → [['aceita', 60], ['errada', 20]] */
export function lerMistura(texto) {
    const mistura = texto.split(',').map((parte) => {
        const [tipo, peso] = parte.trim().split(':');
        if (!CODIGOS[tipo]) {
            throw new Error(`Tipo de solução desconhecido em MIX: "${tipo}" (válidos: ${TIPOS.join(', ')})`);
        }
        const valor = parseInt(peso, 10);
        if (!(valor > 0)) {
            throw new Error(`Peso inválido para "${tipo}" em MIX: "${peso}"`);
        }
        return [tipo, valor];
    });
    if (mistura.length === 0) {
        throw new Error('MIX vazio');
    }
    return mistura;
}

/**
 * Sequência determinística e intercalada dos tipos (smooth weighted round-robin): com aceita:3,errada:1, sai
 * aceita, aceita, errada, aceita. A iteração N usa a posição N % tamanho, então execuções com a mesma mistura enviam a
 * mesma sequência de soluções, o que facilita comparar configurações.
 */
export function sequenciaDaMistura(mistura) {
    const total = mistura.reduce((soma, [, peso]) => soma + peso, 0);
    const atual = Object.fromEntries(mistura.map(([tipo]) => [tipo, 0]));
    const sequencia = [];
    for (let i = 0; i < total; i++) {
        let escolhido = null;
        for (const [tipo, peso] of mistura) {
            atual[tipo] += peso;
            if (escolhido === null || atual[tipo] > atual[escolhido]) {
                escolhido = tipo;
            }
        }
        atual[escolhido] -= total;
        sequencia.push(escolhido);
    }
    return sequencia;
}
