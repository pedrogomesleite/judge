-- Dados de exemplo: atividades de programação simples com casos de teste oficiais (públicos e privados)
-- e algumas soluções já avaliadas, com o resultado de cada caso de teste.
--
-- Pré-requisito: a API já subiu ao menos uma vez neste banco (o ddl-auto=update cria as tabelas e o
-- LinguagemSeeder cadastra o C++).
-- Pode ser executado mais de uma vez: ids fixos + ON CONFLICT DO NOTHING / NOT EXISTS.
--
-- Usuários (senha de ambos: juizin123):
--   professor  autor das atividades e dos casos oficiais (vê os casos privados na tela da atividade)
--   aluno      autor das soluções de exemplo
--
-- Uso: psql -h localhost -U postgres -d juizin -f scripts/seed-atividades.sql
-- No compose: docker compose exec -T postgres psql -U postgres -d juizin < scripts/seed-atividades.sql

SET client_encoding = 'UTF8';

BEGIN;

-- Usuários ------------------------------------------------------------------------------------------

INSERT INTO usuario (id, nome, login, senha_hash, criado_em)
VALUES (gen_random_uuid(), 'Professor Exemplo', 'professor',
        '$2a$10$nU2Pk9bLGHduZWy1MwIR5up3VcZF81biKr6di7Ukvzs0djTLOPCNy', now()),
       (gen_random_uuid(), 'Aluno Exemplo', 'aluno',
        '$2a$10$nU2Pk9bLGHduZWy1MwIR5up3VcZF81biKr6di7Ukvzs0djTLOPCNy', now())
ON CONFLICT (login) DO NOTHING;

-- Atividades ----------------------------------------------------------------------------------------

INSERT INTO atividade (id, autor_id, titulo, enunciado, limite_tempo_ms, limite_memoria_mb, criado_em)
SELECT v.id::uuid, professor.id, v.titulo, v.enunciado, v.limite_tempo_ms, v.limite_memoria_mb, now()
FROM (VALUES
    ('a0000000-0000-4000-8000-000000000001', 'Par ou ímpar',
     'Leia um número inteiro N e informe se ele é par ou ímpar.

Entrada
Uma linha com um inteiro N (-10^9 ≤ N ≤ 10^9).

Saída
Imprima PAR se N for par ou IMPAR caso contrário.', 1000, 256),

    ('a0000000-0000-4000-8000-000000000002', 'Maior de três',
     'Leia três números inteiros e imprima o maior deles.

Entrada
Uma linha com três inteiros A, B e C (-10^6 ≤ A, B, C ≤ 10^6), separados por espaço.

Saída
Imprima o maior dos três valores.', 1000, 256),

    ('a0000000-0000-4000-8000-000000000003', 'Fatorial',
     'Leia um inteiro N e imprima N! (o fatorial de N), isto é, o produto 1 × 2 × … × N. Por definição, 0! = 1.

Entrada
Um inteiro N (0 ≤ N ≤ 20).

Saída
Imprima o valor de N!.

Dica: 20! não cabe em um int de 32 bits.', 1000, 256),

    ('a0000000-0000-4000-8000-000000000004', 'Sequência de Fibonacci',
     'A sequência de Fibonacci começa com F(0) = 0 e F(1) = 1, e cada termo seguinte é a soma dos dois anteriores: F(N) = F(N-1) + F(N-2).

Entrada
Um inteiro N (0 ≤ N ≤ 90).

Saída
Imprima F(N).', 1000, 256),

    ('a0000000-0000-4000-8000-000000000005', 'Inverter palavra',
     'Leia uma palavra e imprima-a de trás para frente.

Entrada
Uma linha com uma palavra de 1 a 100 letras minúsculas, sem espaços nem acentos.

Saída
Imprima a palavra invertida.', 1000, 64),

    ('a0000000-0000-4000-8000-000000000006', 'Contando vogais',
     'Leia uma frase e conte quantas vogais (a, e, i, o, u) ela possui, sem diferenciar maiúsculas de minúsculas.

Entrada
Uma linha com 1 a 1000 caracteres: letras sem acento, dígitos, espaços e pontuação.

Saída
Imprima a quantidade de vogais da frase.', 1000, 64),

    ('a0000000-0000-4000-8000-000000000007', 'Número primo',
     'Um número natural é primo quando possui exatamente dois divisores: 1 e ele mesmo. Leia N e informe se ele é primo.

Entrada
Um inteiro N (1 ≤ N ≤ 10^9).

Saída
Imprima SIM se N for primo ou NAO caso contrário.

Atenção ao tempo limite: N pode chegar a 10^9.', 1000, 256),

    ('a0000000-0000-4000-8000-000000000008', 'Soma de um vetor',
     'Leia N números inteiros e imprima a soma de todos eles.

Entrada
A primeira linha contém um inteiro N (1 ≤ N ≤ 10^5).
A segunda linha contém N inteiros Xi (-10^9 ≤ Xi ≤ 10^9), separados por espaço.

Saída
Imprima a soma dos N números.

Dica: a soma pode passar do limite de um int de 32 bits.', 1000, 256),

    ('a0000000-0000-4000-8000-000000000009', 'Palíndromo',
     'Uma palavra é um palíndromo quando é lida da mesma forma da esquerda para a direita e da direita para a esquerda, como "arara".

Entrada
Uma linha com uma palavra de 1 a 100 letras minúsculas, sem acentos.

Saída
Imprima SIM se a palavra for um palíndromo ou NAO caso contrário.', 1000, 64),

    ('a0000000-0000-4000-8000-000000000010', 'Tabuada',
     'Leia um inteiro N e imprima a tabuada de multiplicação de N, de 1 a 10.

Entrada
Um inteiro N (1 ≤ N ≤ 1000).

Saída
Imprima 10 linhas no formato N x I = R, para I de 1 a 10, em que R = N × I.', 1000, 64)
) AS v (id, titulo, enunciado, limite_tempo_ms, limite_memoria_mb)
JOIN usuario professor ON professor.login = 'professor'
ON CONFLICT (id) DO NOTHING;

-- Casos de teste oficiais ---------------------------------------------------------------------------
-- id c0000000-...-0000000AACC: AA = atividade, CC = caso. publico = true aparece como exemplo, sem login.
-- O autor de um caso OFICIAL é sempre o autor da atividade.

INSERT INTO caso_de_teste (id, atividade_id, autor_id, grupo_id, entrada, saida_esperada, tipo, publico)
SELECT v.id::uuid, a.id, a.autor_id, NULL, v.entrada, v.saida_esperada, 'OFICIAL', v.publico
FROM (VALUES
    -- Par ou ímpar
    ('c0000000-0000-4000-8000-000000000101', 'a0000000-0000-4000-8000-000000000001', E'4\n', E'PAR\n', true),
    ('c0000000-0000-4000-8000-000000000102', 'a0000000-0000-4000-8000-000000000001', E'7\n', E'IMPAR\n', true),
    ('c0000000-0000-4000-8000-000000000103', 'a0000000-0000-4000-8000-000000000001', E'0\n', E'PAR\n', false),
    ('c0000000-0000-4000-8000-000000000104', 'a0000000-0000-4000-8000-000000000001', E'-3\n', E'IMPAR\n', false),
    ('c0000000-0000-4000-8000-000000000105', 'a0000000-0000-4000-8000-000000000001', E'1000000000\n', E'PAR\n', false),

    -- Maior de três
    ('c0000000-0000-4000-8000-000000000201', 'a0000000-0000-4000-8000-000000000002', E'3 9 5\n', E'9\n', true),
    ('c0000000-0000-4000-8000-000000000202', 'a0000000-0000-4000-8000-000000000002', E'-1 -7 -3\n', E'-1\n', false),
    ('c0000000-0000-4000-8000-000000000203', 'a0000000-0000-4000-8000-000000000002', E'8 2 8\n', E'8\n', false),
    ('c0000000-0000-4000-8000-000000000204', 'a0000000-0000-4000-8000-000000000002', E'-5 0 -5\n', E'0\n', false),

    -- Fatorial
    ('c0000000-0000-4000-8000-000000000301', 'a0000000-0000-4000-8000-000000000003', E'5\n', E'120\n', true),
    ('c0000000-0000-4000-8000-000000000302', 'a0000000-0000-4000-8000-000000000003', E'0\n', E'1\n', false),
    ('c0000000-0000-4000-8000-000000000303', 'a0000000-0000-4000-8000-000000000003', E'10\n', E'3628800\n', false),
    ('c0000000-0000-4000-8000-000000000304', 'a0000000-0000-4000-8000-000000000003', E'20\n', E'2432902008176640000\n', false),

    -- Sequência de Fibonacci
    ('c0000000-0000-4000-8000-000000000401', 'a0000000-0000-4000-8000-000000000004', E'10\n', E'55\n', true),
    ('c0000000-0000-4000-8000-000000000402', 'a0000000-0000-4000-8000-000000000004', E'0\n', E'0\n', false),
    ('c0000000-0000-4000-8000-000000000403', 'a0000000-0000-4000-8000-000000000004', E'1\n', E'1\n', false),
    ('c0000000-0000-4000-8000-000000000404', 'a0000000-0000-4000-8000-000000000004', E'90\n', E'2880067194370816120\n', false),

    -- Inverter palavra
    ('c0000000-0000-4000-8000-000000000501', 'a0000000-0000-4000-8000-000000000005', E'juizin\n', E'niziuj\n', true),
    ('c0000000-0000-4000-8000-000000000502', 'a0000000-0000-4000-8000-000000000005', E'a\n', E'a\n', false),
    ('c0000000-0000-4000-8000-000000000503', 'a0000000-0000-4000-8000-000000000005', E'programacao\n', E'oacamargorp\n', false),

    -- Contando vogais
    ('c0000000-0000-4000-8000-000000000601', 'a0000000-0000-4000-8000-000000000006', E'Ola mundo\n', E'4\n', true),
    ('c0000000-0000-4000-8000-000000000602', 'a0000000-0000-4000-8000-000000000006', E'AEIOU aeiou\n', E'10\n', false),
    ('c0000000-0000-4000-8000-000000000603', 'a0000000-0000-4000-8000-000000000006', E'xyz 123\n', E'0\n', false),
    ('c0000000-0000-4000-8000-000000000604', 'a0000000-0000-4000-8000-000000000006', E'Programar em C++ e divertido!\n', E'9\n', false),

    -- Número primo
    ('c0000000-0000-4000-8000-000000000701', 'a0000000-0000-4000-8000-000000000007', E'7\n', E'SIM\n', true),
    ('c0000000-0000-4000-8000-000000000702', 'a0000000-0000-4000-8000-000000000007', E'1\n', E'NAO\n', false),
    ('c0000000-0000-4000-8000-000000000703', 'a0000000-0000-4000-8000-000000000007', E'91\n', E'NAO\n', false),
    ('c0000000-0000-4000-8000-000000000704', 'a0000000-0000-4000-8000-000000000007', E'999999937\n', E'SIM\n', false),

    -- Soma de um vetor
    ('c0000000-0000-4000-8000-000000000801', 'a0000000-0000-4000-8000-000000000008', E'5\n1 2 3 4 5\n', E'15\n', true),
    ('c0000000-0000-4000-8000-000000000802', 'a0000000-0000-4000-8000-000000000008', E'1\n-7\n', E'-7\n', false),
    ('c0000000-0000-4000-8000-000000000803', 'a0000000-0000-4000-8000-000000000008', E'3\n1000000000 1000000000 1000000000\n', E'3000000000\n', false),
    ('c0000000-0000-4000-8000-000000000804', 'a0000000-0000-4000-8000-000000000008', E'4\n-1 1 -1 1\n', E'0\n', false),

    -- Palíndromo
    ('c0000000-0000-4000-8000-000000000901', 'a0000000-0000-4000-8000-000000000009', E'arara\n', E'SIM\n', true),
    ('c0000000-0000-4000-8000-000000000902', 'a0000000-0000-4000-8000-000000000009', E'juizin\n', E'NAO\n', true),
    ('c0000000-0000-4000-8000-000000000903', 'a0000000-0000-4000-8000-000000000009', E'a\n', E'SIM\n', false),
    ('c0000000-0000-4000-8000-000000000904', 'a0000000-0000-4000-8000-000000000009', E'abba\n', E'SIM\n', false),
    ('c0000000-0000-4000-8000-000000000905', 'a0000000-0000-4000-8000-000000000009', E'abca\n', E'NAO\n', false),

    -- Tabuada
    ('c0000000-0000-4000-8000-000000001001', 'a0000000-0000-4000-8000-000000000010', E'3\n',
     E'3 x 1 = 3\n3 x 2 = 6\n3 x 3 = 9\n3 x 4 = 12\n3 x 5 = 15\n3 x 6 = 18\n3 x 7 = 21\n3 x 8 = 24\n3 x 9 = 27\n3 x 10 = 30\n', true),
    ('c0000000-0000-4000-8000-000000001002', 'a0000000-0000-4000-8000-000000000010', E'7\n',
     E'7 x 1 = 7\n7 x 2 = 14\n7 x 3 = 21\n7 x 4 = 28\n7 x 5 = 35\n7 x 6 = 42\n7 x 7 = 49\n7 x 8 = 56\n7 x 9 = 63\n7 x 10 = 70\n', false),
    ('c0000000-0000-4000-8000-000000001003', 'a0000000-0000-4000-8000-000000000010', E'1000\n',
     E'1000 x 1 = 1000\n1000 x 2 = 2000\n1000 x 3 = 3000\n1000 x 4 = 4000\n1000 x 5 = 5000\n1000 x 6 = 6000\n1000 x 7 = 7000\n1000 x 8 = 8000\n1000 x 9 = 9000\n1000 x 10 = 10000\n', false)
) AS v (id, atividade_id, entrada, saida_esperada, publico)
JOIN atividade a ON a.id = v.atividade_id::uuid
ON CONFLICT (id) DO NOTHING;

-- Soluções do aluno (C++, fora de grupo) ------------------------------------------------------------
-- tempo_ms e memoria_kb da solução = maior valor entre os casos de teste.

INSERT INTO solucao (id, autor_id, atividade_id, grupo_id, linguagem_id, codigo, status, veredito,
                     tempo_ms, memoria_kb, enviado_em, publicada_em)
SELECT v.id::uuid, aluno.id, v.atividade_id::uuid, NULL, cpp.id, v.codigo, 'CONCLUIDA', v.veredito,
       v.tempo_ms, v.memoria_kb, now() - v.enviado_ha::interval, now() - v.enviado_ha::interval + interval '1 second'
FROM (VALUES
    -- Par ou ímpar: em C++, -3 % 2 == -1, então o caso privado -3 sai como PAR
    ('b0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001', 'RESPOSTA_ERRADA', 2, 3420, '3 days',
     '#include <iostream>
using namespace std;

int main() {
    int n;
    cin >> n;
    if (n % 2 == 1) {
        cout << "IMPAR" << endl;
    } else {
        cout << "PAR" << endl;
    }
    return 0;
}
'),
    ('b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000001', 'ACEITA', 3, 3424, '2 days 23 hours',
     '#include <iostream>
using namespace std;

int main() {
    int n;
    cin >> n;
    if (n % 2 == 0) {
        cout << "PAR" << endl;
    } else {
        cout << "IMPAR" << endl;
    }
    return 0;
}
'),
    ('b0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000003', 'ACEITA', 2, 3408, '2 days',
     '#include <iostream>
using namespace std;

int main() {
    int n;
    cin >> n;
    long long fatorial = 1;
    for (int i = 2; i <= n; i++) {
        fatorial *= i;
    }
    cout << fatorial << endl;
    return 0;
}
'),
    -- Número primo: testar todos os divisores até N estoura o tempo no caso privado 999999937
    ('b0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000007', 'TEMPO_EXCEDIDO', 1000, 3416, '1 day',
     '#include <iostream>
using namespace std;

int main() {
    int n;
    cin >> n;
    bool primo = n >= 2;
    for (int d = 2; d < n; d++) {
        if (n % d == 0) {
            primo = false;
            break;
        }
    }
    cout << (primo ? "SIM" : "NAO") << endl;
    return 0;
}
'),
    ('b0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000007', 'ACEITA', 4, 3416, '23 hours',
     '#include <iostream>
using namespace std;

int main() {
    int n;
    cin >> n;
    bool primo = n >= 2;
    for (long long d = 2; d * d <= n; d++) {
        if (n % d == 0) {
            primo = false;
            break;
        }
    }
    cout << (primo ? "SIM" : "NAO") << endl;
    return 0;
}
'),
    ('b0000000-0000-4000-8000-000000000006', 'a0000000-0000-4000-8000-000000000009', 'ACEITA', 2, 3484, '5 hours',
     '#include <iostream>
#include <string>
using namespace std;

int main() {
    string palavra;
    cin >> palavra;
    string invertida(palavra.rbegin(), palavra.rend());
    cout << (palavra == invertida ? "SIM" : "NAO") << endl;
    return 0;
}
')
) AS v (id, atividade_id, veredito, tempo_ms, memoria_kb, enviado_ha, codigo)
JOIN usuario aluno ON aluno.login = 'aluno'
JOIN linguagem cpp ON cpp.nome = 'C++'
ON CONFLICT (id) DO NOTHING;

-- Resultado de cada caso de teste oficial nas soluções acima ----------------------------------------

INSERT INTO resultado_caso_de_teste (id, solucao_id, caso_de_teste_id, veredito, tempo_ms, memoria_kb)
SELECT gen_random_uuid(), v.solucao_id::uuid, v.caso_id::uuid, v.veredito, v.tempo_ms, v.memoria_kb
FROM (VALUES
    -- Par ou ímpar (RESPOSTA_ERRADA)
    ('b0000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000101', 'ACEITA', 2, 3420),
    ('b0000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000102', 'ACEITA', 2, 3420),
    ('b0000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000103', 'ACEITA', 1, 3412),
    ('b0000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000104', 'RESPOSTA_ERRADA', 2, 3420),
    ('b0000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000105', 'ACEITA', 2, 3416),

    -- Par ou ímpar (ACEITA)
    ('b0000000-0000-4000-8000-000000000002', 'c0000000-0000-4000-8000-000000000101', 'ACEITA', 2, 3424),
    ('b0000000-0000-4000-8000-000000000002', 'c0000000-0000-4000-8000-000000000102', 'ACEITA', 2, 3420),
    ('b0000000-0000-4000-8000-000000000002', 'c0000000-0000-4000-8000-000000000103', 'ACEITA', 3, 3424),
    ('b0000000-0000-4000-8000-000000000002', 'c0000000-0000-4000-8000-000000000104', 'ACEITA', 2, 3416),
    ('b0000000-0000-4000-8000-000000000002', 'c0000000-0000-4000-8000-000000000105', 'ACEITA', 2, 3420),

    -- Fatorial (ACEITA)
    ('b0000000-0000-4000-8000-000000000003', 'c0000000-0000-4000-8000-000000000301', 'ACEITA', 1, 3404),
    ('b0000000-0000-4000-8000-000000000003', 'c0000000-0000-4000-8000-000000000302', 'ACEITA', 1, 3400),
    ('b0000000-0000-4000-8000-000000000003', 'c0000000-0000-4000-8000-000000000303', 'ACEITA', 2, 3404),
    ('b0000000-0000-4000-8000-000000000003', 'c0000000-0000-4000-8000-000000000304', 'ACEITA', 2, 3408),

    -- Número primo (TEMPO_EXCEDIDO)
    ('b0000000-0000-4000-8000-000000000004', 'c0000000-0000-4000-8000-000000000701', 'ACEITA', 2, 3412),
    ('b0000000-0000-4000-8000-000000000004', 'c0000000-0000-4000-8000-000000000702', 'ACEITA', 1, 3408),
    ('b0000000-0000-4000-8000-000000000004', 'c0000000-0000-4000-8000-000000000703', 'ACEITA', 2, 3412),
    ('b0000000-0000-4000-8000-000000000004', 'c0000000-0000-4000-8000-000000000704', 'TEMPO_EXCEDIDO', 1000, 3416),

    -- Número primo (ACEITA)
    ('b0000000-0000-4000-8000-000000000005', 'c0000000-0000-4000-8000-000000000701', 'ACEITA', 2, 3412),
    ('b0000000-0000-4000-8000-000000000005', 'c0000000-0000-4000-8000-000000000702', 'ACEITA', 1, 3404),
    ('b0000000-0000-4000-8000-000000000005', 'c0000000-0000-4000-8000-000000000703', 'ACEITA', 1, 3412),
    ('b0000000-0000-4000-8000-000000000005', 'c0000000-0000-4000-8000-000000000704', 'ACEITA', 4, 3416),

    -- Palíndromo (ACEITA)
    ('b0000000-0000-4000-8000-000000000006', 'c0000000-0000-4000-8000-000000000901', 'ACEITA', 2, 3480),
    ('b0000000-0000-4000-8000-000000000006', 'c0000000-0000-4000-8000-000000000902', 'ACEITA', 2, 3484),
    ('b0000000-0000-4000-8000-000000000006', 'c0000000-0000-4000-8000-000000000903', 'ACEITA', 1, 3476),
    ('b0000000-0000-4000-8000-000000000006', 'c0000000-0000-4000-8000-000000000904', 'ACEITA', 2, 3480),
    ('b0000000-0000-4000-8000-000000000006', 'c0000000-0000-4000-8000-000000000905', 'ACEITA', 2, 3484)
) AS v (solucao_id, caso_id, veredito, tempo_ms, memoria_kb)
WHERE NOT EXISTS (
    SELECT 1
    FROM resultado_caso_de_teste r
    WHERE r.solucao_id = v.solucao_id::uuid
      AND r.caso_de_teste_id = v.caso_id::uuid
);

COMMIT;
