-- Dados de exemplo para os grupos: alunos, atividades extras, membros, pesos, casos comunitários e
-- envios avaliados dentro dos grupos (com o resultado de cada caso de teste), para o placar e o relatório.
--
-- Pré-requisitos:
--   1. scripts/seed-atividades.sql já executado (usuário professor e as atividades a...01 a a...10);
--   2. o usuário caioaugusto@gmail.com e os grupos "Algoritmos de Programação" I, II e III já existem
--      (o script usa os ids deles; se não existirem, as linhas ligadas a eles são ignoradas).
-- Pode ser executado mais de uma vez: ids fixos/determinísticos + ON CONFLICT DO NOTHING / NOT EXISTS.
--
-- Alunos (senha de todos: juizin123): ana.souza, bruno.lima, carla.mendes, ... (ver aluno_seed)
--
-- Uso: docker compose exec -T postgres psql -U postgres -d juizin -v ON_ERROR_STOP=1 < scripts/seed-grupos.sql

SET client_encoding = 'UTF8';

BEGIN;

-- Alunos --------------------------------------------------------------------------------------------

CREATE TEMP TABLE aluno_seed (n int PRIMARY KEY, login text, nome text) ON COMMIT DROP;

INSERT INTO aluno_seed (n, login, nome)
VALUES (1, 'ana.souza', 'Ana Souza'),
       (2, 'bruno.lima', 'Bruno Lima'),
       (3, 'carla.mendes', 'Carla Mendes'),
       (4, 'diego.ferreira', 'Diego Ferreira'),
       (5, 'eduarda.costa', 'Eduarda Costa'),
       (6, 'felipe.rocha', 'Felipe Rocha'),
       (7, 'gabriela.alves', 'Gabriela Alves'),
       (8, 'henrique.martins', 'Henrique Martins'),
       (9, 'isabela.ribeiro', 'Isabela Ribeiro'),
       (10, 'joao.santos', 'João Pedro Santos'),
       (11, 'larissa.oliveira', 'Larissa Oliveira'),
       (12, 'lucas.carvalho', 'Lucas Carvalho'),
       (13, 'mariana.gomes', 'Mariana Gomes'),
       (14, 'matheus.barbosa', 'Matheus Barbosa'),
       (15, 'natalia.pereira', 'Natália Pereira'),
       (16, 'otavio.nunes', 'Otávio Nunes'),
       (17, 'paula.teixeira', 'Paula Teixeira'),
       (18, 'rafael.moreira', 'Rafael Moreira'),
       (19, 'sofia.cardoso', 'Sofia Cardoso'),
       (20, 'thiago.araujo', 'Thiago Araújo');

INSERT INTO usuario (id, nome, login, senha_hash, criado_em)
SELECT gen_random_uuid(), s.nome, s.login,
       '$2a$10$nU2Pk9bLGHduZWy1MwIR5up3VcZF81biKr6di7Ukvzs0djTLOPCNy',
       timestamptz '2026-06-01 12:00:00+00' + s.n * interval '1 day 3 hours'
FROM aluno_seed s
ON CONFLICT (login) DO NOTHING;

-- Atividades extras, criadas pelo Caio --------------------------------------------------------------

INSERT INTO atividade (id, autor_id, titulo, enunciado, limite_tempo_ms, limite_memoria_mb, criado_em)
SELECT v.id::uuid, caio.id, v.titulo, v.enunciado, v.limite_tempo_ms, v.limite_memoria_mb, now() - interval '60 days'
FROM (VALUES
    ('a0000000-0000-4000-8000-000000000011', 'Média de notas',
     'Leia N notas e imprima a média aritmética delas.

Entrada
A primeira linha contém um inteiro N (1 ≤ N ≤ 100).
A segunda linha contém N notas reais entre 0 e 10, separadas por espaço.

Saída
Imprima a média com exatamente duas casas decimais.', 1000, 64),

    ('a0000000-0000-4000-8000-000000000012', 'Ano bissexto',
     'Um ano é bissexto quando é divisível por 4, exceto os divisíveis por 100 que não são divisíveis por 400. Assim, 2000 e 2024 são bissextos, mas 1900 não é.

Entrada
Um inteiro A (1 ≤ A ≤ 10^9).

Saída
Imprima SIM se A for bissexto ou NAO caso contrário.', 1000, 64),

    ('a0000000-0000-4000-8000-000000000013', 'Celsius para Fahrenheit',
     'Leia uma temperatura em graus Celsius e converta-a para Fahrenheit, usando F = C × 9 / 5 + 32.

Entrada
Um inteiro C (-273 ≤ C ≤ 10^4).

Saída
Imprima F com exatamente uma casa decimal.', 1000, 64),

    ('a0000000-0000-4000-8000-000000000014', 'Soma dos dígitos',
     'Leia um número natural N e imprima a soma dos seus dígitos.

Entrada
Um inteiro N (0 ≤ N ≤ 10^18).

Saída
Imprima a soma dos dígitos de N.

Dica: N não cabe em um int de 32 bits.', 1000, 64)
) AS v (id, titulo, enunciado, limite_tempo_ms, limite_memoria_mb)
JOIN usuario caio ON caio.login = 'caioaugusto@gmail.com'
ON CONFLICT (id) DO NOTHING;

INSERT INTO caso_de_teste (id, atividade_id, autor_id, grupo_id, entrada, saida_esperada, tipo, publico)
SELECT v.id::uuid, a.id, a.autor_id, NULL, v.entrada, v.saida_esperada, 'OFICIAL', v.publico
FROM (VALUES
    -- Média de notas
    ('c0000000-0000-4000-8000-000000001101', 'a0000000-0000-4000-8000-000000000011', E'4\n7.5 8 6 9.5\n', E'7.75\n', true),
    ('c0000000-0000-4000-8000-000000001102', 'a0000000-0000-4000-8000-000000000011', E'1\n10\n', E'10.00\n', false),
    ('c0000000-0000-4000-8000-000000001103', 'a0000000-0000-4000-8000-000000000011', E'3\n0 5 6\n', E'3.67\n', false),
    ('c0000000-0000-4000-8000-000000001104', 'a0000000-0000-4000-8000-000000000011', E'5\n6 6 6 6 6\n', E'6.00\n', false),

    -- Ano bissexto
    ('c0000000-0000-4000-8000-000000001201', 'a0000000-0000-4000-8000-000000000012', E'2024\n', E'SIM\n', true),
    ('c0000000-0000-4000-8000-000000001202', 'a0000000-0000-4000-8000-000000000012', E'1900\n', E'NAO\n', true),
    ('c0000000-0000-4000-8000-000000001203', 'a0000000-0000-4000-8000-000000000012', E'2000\n', E'SIM\n', false),
    ('c0000000-0000-4000-8000-000000001204', 'a0000000-0000-4000-8000-000000000012', E'2023\n', E'NAO\n', false),
    ('c0000000-0000-4000-8000-000000001205', 'a0000000-0000-4000-8000-000000000012', E'2100\n', E'NAO\n', false),

    -- Celsius para Fahrenheit
    ('c0000000-0000-4000-8000-000000001301', 'a0000000-0000-4000-8000-000000000013', E'0\n', E'32.0\n', true),
    ('c0000000-0000-4000-8000-000000001302', 'a0000000-0000-4000-8000-000000000013', E'100\n', E'212.0\n', false),
    ('c0000000-0000-4000-8000-000000001303', 'a0000000-0000-4000-8000-000000000013', E'-40\n', E'-40.0\n', false),
    ('c0000000-0000-4000-8000-000000001304', 'a0000000-0000-4000-8000-000000000013', E'37\n', E'98.6\n', false),

    -- Soma dos dígitos
    ('c0000000-0000-4000-8000-000000001401', 'a0000000-0000-4000-8000-000000000014', E'12345\n', E'15\n', true),
    ('c0000000-0000-4000-8000-000000001402', 'a0000000-0000-4000-8000-000000000014', E'0\n', E'0\n', false),
    ('c0000000-0000-4000-8000-000000001403', 'a0000000-0000-4000-8000-000000000014', E'999999999999999999\n', E'162\n', false),
    ('c0000000-0000-4000-8000-000000001404', 'a0000000-0000-4000-8000-000000000014', E'1000000\n', E'1\n', false)
) AS v (id, atividade_id, entrada, saida_esperada, publico)
JOIN atividade a ON a.id = v.atividade_id::uuid
ON CONFLICT (id) DO NOTHING;

-- Grupos novos ----------------------------------------------------------------------------------------
-- d0...04: do Caio, ainda aguardando o início (mostra a tela de preparação do grupo).
-- d0...05: do professor, em andamento, com o Caio como estudante (mostra a visão do estudante).

INSERT INTO grupo_atividade (id, criador_id, nome, descricao, status, data_inicio, data_fim, codigo_convite)
SELECT v.id::uuid, criador.id, v.nome, v.descricao, v.status, v.data_inicio, v.data_fim,
       rtrim(translate(encode(decode(md5(random()::text), 'hex'), 'base64'), '+/', '-_'), '=')
FROM (VALUES
    ('d0000000-0000-4000-8000-000000000004', 'caioaugusto@gmail.com', 'Estruturas de Dados',
     'Lista de exercícios introdutória de Estruturas de Dados. As atividades valem pesos diferentes conforme a dificuldade.',
     'AGUARDANDO_INICIO', timestamptz '2026-11-16 08:00:00-03', timestamptz '2026-12-18 23:59:00-03'),
    ('d0000000-0000-4000-8000-000000000005', 'professor', 'Maratona de Programação da Turma',
     'Treino para a maratona de programação: resolva o máximo de problemas até o fim do prazo.',
     'EM_ANDAMENTO', now() - interval '10 days', now() + interval '20 days')
) AS v (id, criador_login, nome, descricao, status, data_inicio, data_fim)
JOIN usuario criador ON criador.login = v.criador_login
ON CONFLICT (id) DO NOTHING;

-- Atividades e pesos de cada grupo ------------------------------------------------------------------

INSERT INTO grupo_atividade_item (id, grupo_id, atividade_id, ordem, peso)
SELECT gen_random_uuid(), g.id, a.id, v.ordem, v.peso
FROM (VALUES
    -- Algoritmos de Programação (FINALIZADO)
    ('01489c96-d021-46e0-b7b4-e6b553fb048f', 'a0000000-0000-4000-8000-000000000001', 1, 1),
    ('01489c96-d021-46e0-b7b4-e6b553fb048f', 'a0000000-0000-4000-8000-000000000002', 2, 1),
    ('01489c96-d021-46e0-b7b4-e6b553fb048f', 'a0000000-0000-4000-8000-000000000003', 3, 2),
    ('01489c96-d021-46e0-b7b4-e6b553fb048f', 'a0000000-0000-4000-8000-000000000005', 4, 2),
    ('01489c96-d021-46e0-b7b4-e6b553fb048f', 'a0000000-0000-4000-8000-000000000010', 5, 3),

    -- Algoritmos de Programação II (EM_ANDAMENTO)
    ('75953d90-67a3-4f01-afd6-bdeb1230dce3', 'a0000000-0000-4000-8000-000000000004', 1, 2),
    ('75953d90-67a3-4f01-afd6-bdeb1230dce3', 'a0000000-0000-4000-8000-000000000006', 2, 1),
    ('75953d90-67a3-4f01-afd6-bdeb1230dce3', 'a0000000-0000-4000-8000-000000000007', 3, 3),
    ('75953d90-67a3-4f01-afd6-bdeb1230dce3', 'a0000000-0000-4000-8000-000000000008', 4, 2),
    ('75953d90-67a3-4f01-afd6-bdeb1230dce3', 'a0000000-0000-4000-8000-000000000009', 5, 1),
    ('75953d90-67a3-4f01-afd6-bdeb1230dce3', 'a0000000-0000-4000-8000-000000000012', 6, 2),

    -- Algoritmos de Programação III (EM_ANDAMENTO)
    ('db09ea60-e280-49d8-8460-92e7926ebe9f', 'a0000000-0000-4000-8000-000000000011', 1, 1),
    ('db09ea60-e280-49d8-8460-92e7926ebe9f', 'a0000000-0000-4000-8000-000000000013', 2, 1),
    ('db09ea60-e280-49d8-8460-92e7926ebe9f', 'a0000000-0000-4000-8000-000000000014', 3, 2),
    ('db09ea60-e280-49d8-8460-92e7926ebe9f', 'a0000000-0000-4000-8000-000000000007', 4, 3),
    ('db09ea60-e280-49d8-8460-92e7926ebe9f', 'a0000000-0000-4000-8000-000000000010', 5, 1),

    -- Estruturas de Dados (AGUARDANDO_INICIO)
    ('d0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000003', 1, 1),
    ('d0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000004', 2, 2),
    ('d0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000008', 3, 2),
    ('d0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000014', 4, 3),

    -- Maratona de Programação da Turma (EM_ANDAMENTO)
    ('d0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000002', 1, 1),
    ('d0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000005', 2, 1),
    ('d0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000006', 3, 2),
    ('d0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000009', 4, 2),
    ('d0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000012', 5, 3)
) AS v (grupo_id, atividade_id, ordem, peso)
JOIN grupo_atividade g ON g.id = v.grupo_id::uuid
JOIN atividade a ON a.id = v.atividade_id::uuid
ON CONFLICT (grupo_id, atividade_id) DO NOTHING;

-- Membros ---------------------------------------------------------------------------------------------
-- Os números se referem a aluno_seed.n. O Thiago (20) é monitor (AVALIADOR) do grupo II.

INSERT INTO membro_grupo (id, grupo_id, usuario_id, papel, entrou_em)
SELECT gen_random_uuid(), g.id, u.id, v.papel,
       least(now(), g.data_inicio) - (1 + s.n % 5) * interval '1 day' - s.n * interval '37 minutes'
FROM (VALUES
    ('01489c96-d021-46e0-b7b4-e6b553fb048f', 'ESTUDANTE', ARRAY[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]),
    ('75953d90-67a3-4f01-afd6-bdeb1230dce3', 'ESTUDANTE', ARRAY[5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19]),
    ('75953d90-67a3-4f01-afd6-bdeb1230dce3', 'AVALIADOR', ARRAY[20]),
    ('db09ea60-e280-49d8-8460-92e7926ebe9f', 'ESTUDANTE', ARRAY[1, 2, 9, 11, 13, 15, 16, 17, 18, 19]),
    ('d0000000-0000-4000-8000-000000000004', 'ESTUDANTE', ARRAY[2, 3, 4, 5, 6, 7, 8, 9]),
    ('d0000000-0000-4000-8000-000000000005', 'ESTUDANTE', ARRAY[3, 6, 9, 12, 15, 18, 20])
) AS v (grupo_id, papel, alunos)
CROSS JOIN LATERAL unnest(v.alunos) AS a (n)
JOIN aluno_seed s ON s.n = a.n
JOIN usuario u ON u.login = s.login
JOIN grupo_atividade g ON g.id = v.grupo_id::uuid
ON CONFLICT (grupo_id, usuario_id) DO NOTHING;

-- Criadores dos grupos novos como AVALIADOR; na Maratona, o Caio participa como ESTUDANTE
INSERT INTO membro_grupo (id, grupo_id, usuario_id, papel, entrou_em)
SELECT gen_random_uuid(), g.id, u.id, v.papel, least(now(), g.data_inicio) - v.antes::interval
FROM (VALUES ('d0000000-0000-4000-8000-000000000004', 'caioaugusto@gmail.com', 'AVALIADOR', '10 days'),
             ('d0000000-0000-4000-8000-000000000005', 'professor', 'AVALIADOR', '3 days'),
             ('d0000000-0000-4000-8000-000000000005', 'caioaugusto@gmail.com', 'ESTUDANTE', '1 day')
     ) AS v (grupo_id, login, papel, antes)
JOIN usuario u ON u.login = v.login
JOIN grupo_atividade g ON g.id = v.grupo_id::uuid
ON CONFLICT (grupo_id, usuario_id) DO NOTHING;

-- Casos de teste comunitários (visíveis só para os membros do grupo) --------------------------------

INSERT INTO caso_de_teste (id, atividade_id, autor_id, grupo_id, entrada, saida_esperada, tipo, publico)
SELECT v.id::uuid, a.id, u.id, g.id, v.entrada, v.saida_esperada, 'COMUNITARIO', false
FROM (VALUES
    ('c1000000-0000-4000-8000-000000000001', '75953d90-67a3-4f01-afd6-bdeb1230dce3', 'a0000000-0000-4000-8000-000000000007', 'felipe.rocha', E'2\n', E'SIM\n'),
    ('c1000000-0000-4000-8000-000000000002', '75953d90-67a3-4f01-afd6-bdeb1230dce3', 'a0000000-0000-4000-8000-000000000007', 'mariana.gomes', E'1000000007\n', E'SIM\n'),
    ('c1000000-0000-4000-8000-000000000003', '75953d90-67a3-4f01-afd6-bdeb1230dce3', 'a0000000-0000-4000-8000-000000000009', 'joao.santos', E'reviver\n', E'SIM\n'),
    ('c1000000-0000-4000-8000-000000000004', '75953d90-67a3-4f01-afd6-bdeb1230dce3', 'a0000000-0000-4000-8000-000000000008', 'isabela.ribeiro', E'2\n-1000000000 -1000000000\n', E'-2000000000\n'),
    ('c1000000-0000-4000-8000-000000000005', '75953d90-67a3-4f01-afd6-bdeb1230dce3', 'a0000000-0000-4000-8000-000000000012', 'sofia.cardoso', E'1600\n', E'SIM\n'),
    ('c1000000-0000-4000-8000-000000000006', 'db09ea60-e280-49d8-8460-92e7926ebe9f', 'a0000000-0000-4000-8000-000000000013', 'ana.souza', E'-273\n', E'-459.4\n'),
    ('c1000000-0000-4000-8000-000000000007', 'db09ea60-e280-49d8-8460-92e7926ebe9f', 'a0000000-0000-4000-8000-000000000011', 'paula.teixeira', E'2\n0 10\n', E'5.00\n'),
    ('c1000000-0000-4000-8000-000000000008', 'd0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000005', 'carla.mendes', E'ovo\n', E'ovo\n')
) AS v (id, grupo_id, atividade_id, autor_login, entrada, saida_esperada)
JOIN grupo_atividade g ON g.id = v.grupo_id::uuid
JOIN atividade a ON a.id = v.atividade_id::uuid
JOIN usuario u ON u.login = v.autor_login
ON CONFLICT (id) DO NOTHING;

-- Código-fonte usado nos envios ---------------------------------------------------------------------
-- correto: enviado nos ACEITA; errado: nos demais vereditos; ERRO_COMPILACAO = correto sem um ponto e vírgula.

CREATE TEMP TABLE codigo_seed (atividade_id uuid PRIMARY KEY, correto text, errado text) ON COMMIT DROP;

INSERT INTO codigo_seed (atividade_id, correto, errado)
VALUES
('a0000000-0000-4000-8000-000000000001', $$#include <iostream>
using namespace std;

int main() {
    int n;
    cin >> n;
    cout << (n % 2 == 0 ? "PAR" : "IMPAR") << endl;
    return 0;
}
$$, $$#include <iostream>
using namespace std;

int main() {
    int n;
    cin >> n;
    if (n % 2 == 1) cout << "IMPAR" << endl;
    else cout << "PAR" << endl;
    return 0;
}
$$),
('a0000000-0000-4000-8000-000000000002', $$#include <iostream>
#include <algorithm>
using namespace std;

int main() {
    int a, b, c;
    cin >> a >> b >> c;
    cout << max({a, b, c}) << endl;
    return 0;
}
$$, $$#include <iostream>
using namespace std;

int main() {
    int a, b, c, maior = 0;
    cin >> a >> b >> c;
    if (a > maior) maior = a;
    if (b > maior) maior = b;
    if (c > maior) maior = c;
    cout << maior << endl;
    return 0;
}
$$),
('a0000000-0000-4000-8000-000000000003', $$#include <iostream>
using namespace std;

int main() {
    int n;
    cin >> n;
    long long fat = 1;
    for (int i = 2; i <= n; i++) fat *= i;
    cout << fat << endl;
    return 0;
}
$$, $$#include <iostream>
using namespace std;

int main() {
    int n, fat = 1;
    cin >> n;
    for (int i = 2; i <= n; i++) fat *= i;
    cout << fat << endl;
    return 0;
}
$$),
('a0000000-0000-4000-8000-000000000004', $$#include <iostream>
using namespace std;

int main() {
    int n;
    cin >> n;
    long long a = 0, b = 1;
    for (int i = 0; i < n; i++) {
        long long c = a + b;
        a = b;
        b = c;
    }
    cout << a << endl;
    return 0;
}
$$, $$#include <iostream>
using namespace std;

int fib(int n) {
    if (n < 2) return n;
    return fib(n - 1) + fib(n - 2);
}

int main() {
    int n;
    cin >> n;
    cout << fib(n) << endl;
    return 0;
}
$$),
('a0000000-0000-4000-8000-000000000005', $$#include <iostream>
#include <string>
#include <algorithm>
using namespace std;

int main() {
    string s;
    cin >> s;
    reverse(s.begin(), s.end());
    cout << s << endl;
    return 0;
}
$$, $$#include <iostream>
#include <string>
using namespace std;

int main() {
    string s;
    cin >> s;
    for (int i = s.size(); i > 0; i--) cout << s[i];
    cout << endl;
    return 0;
}
$$),
('a0000000-0000-4000-8000-000000000006', $$#include <iostream>
#include <string>
#include <cctype>
using namespace std;

int main() {
    string frase;
    getline(cin, frase);
    int vogais = 0;
    for (char c : frase) {
        char m = tolower(c);
        if (m == 'a' || m == 'e' || m == 'i' || m == 'o' || m == 'u') vogais++;
    }
    cout << vogais << endl;
    return 0;
}
$$, $$#include <iostream>
#include <string>
using namespace std;

int main() {
    string frase;
    cin >> frase;
    int vogais = 0;
    for (char c : frase) {
        if (c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u') vogais++;
    }
    cout << vogais << endl;
    return 0;
}
$$),
('a0000000-0000-4000-8000-000000000007', $$#include <iostream>
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
$$, $$#include <iostream>
using namespace std;

int main() {
    int n;
    cin >> n;
    bool primo = true;
    for (int d = 2; d < n; d++) {
        if (n % d == 0) primo = false;
    }
    cout << (primo ? "SIM" : "NAO") << endl;
    return 0;
}
$$),
('a0000000-0000-4000-8000-000000000008', $$#include <iostream>
using namespace std;

int main() {
    int n;
    cin >> n;
    long long soma = 0, x;
    for (int i = 0; i < n; i++) {
        cin >> x;
        soma += x;
    }
    cout << soma << endl;
    return 0;
}
$$, $$#include <iostream>
using namespace std;

int main() {
    int n, x, soma = 0;
    cin >> n;
    for (int i = 0; i < n; i++) {
        cin >> x;
        soma += x;
    }
    cout << soma << endl;
    return 0;
}
$$),
('a0000000-0000-4000-8000-000000000009', $$#include <iostream>
#include <string>
using namespace std;

int main() {
    string s;
    cin >> s;
    string invertida(s.rbegin(), s.rend());
    cout << (s == invertida ? "SIM" : "NAO") << endl;
    return 0;
}
$$, $$#include <iostream>
#include <string>
using namespace std;

int main() {
    string s;
    cin >> s;
    cout << (s.front() == s.back() ? "SIM" : "NAO") << endl;
    return 0;
}
$$),
('a0000000-0000-4000-8000-000000000010', $$#include <iostream>
using namespace std;

int main() {
    int n;
    cin >> n;
    for (int i = 1; i <= 10; i++) {
        cout << n << " x " << i << " = " << n * i << endl;
    }
    return 0;
}
$$, $$#include <iostream>
using namespace std;

int main() {
    int n;
    cin >> n;
    for (int i = 1; i <= 10; i++) {
        cout << n << "x" << i << "=" << n * i << endl;
    }
    return 0;
}
$$),
('a0000000-0000-4000-8000-000000000011', $$#include <cstdio>

int main() {
    int n;
    scanf("%d", &n);
    double soma = 0, nota;
    for (int i = 0; i < n; i++) {
        scanf("%lf", &nota);
        soma += nota;
    }
    printf("%.2f\n", soma / n);
    return 0;
}
$$, $$#include <cstdio>

int main() {
    int n;
    scanf("%d", &n);
    double soma = 0, nota;
    for (int i = 0; i < n; i++) {
        scanf("%lf", &nota);
        soma += nota;
    }
    printf("%.1f\n", soma / n);
    return 0;
}
$$),
('a0000000-0000-4000-8000-000000000012', $$#include <iostream>
using namespace std;

int main() {
    long long a;
    cin >> a;
    bool bissexto = (a % 4 == 0 && a % 100 != 0) || a % 400 == 0;
    cout << (bissexto ? "SIM" : "NAO") << endl;
    return 0;
}
$$, $$#include <iostream>
using namespace std;

int main() {
    long long a;
    cin >> a;
    cout << (a % 4 == 0 ? "SIM" : "NAO") << endl;
    return 0;
}
$$),
('a0000000-0000-4000-8000-000000000013', $$#include <cstdio>

int main() {
    int c;
    scanf("%d", &c);
    printf("%.1f\n", c * 9.0 / 5.0 + 32);
    return 0;
}
$$, $$#include <cstdio>

int main() {
    int c;
    scanf("%d", &c);
    printf("%.1f\n", (double) (c * (9 / 5) + 32));
    return 0;
}
$$),
('a0000000-0000-4000-8000-000000000014', $$#include <iostream>
#include <string>
using namespace std;

int main() {
    string n;
    cin >> n;
    int soma = 0;
    for (char d : n) soma += d - '0';
    cout << soma << endl;
    return 0;
}
$$, $$#include <iostream>
using namespace std;

int main() {
    int n, soma = 0;
    cin >> n;
    while (n > 0) {
        soma += n % 10;
        n /= 10;
    }
    cout << soma << endl;
    return 0;
}
$$);

-- Envios nos grupos ---------------------------------------------------------------------------------
-- Gerados de forma determinística a partir do md5 de (grupo, aluno, atividade): cada aluno tem uma
-- "habilidade" fixa, que decide se ele tenta a atividade, quantas vezes e se termina com ACEITA.
-- As datas ficam entre o início do grupo e agora (ou o fim do grupo).

CREATE TEMP TABLE tentativa ON COMMIT DROP AS
WITH base AS (
    SELECT g.id AS grupo_id, g.data_inicio, g.data_fim, m.usuario_id, i.atividade_id, a.limite_tempo_ms,
           decode(md5(g.id::text || m.usuario_id::text || i.atividade_id::text), 'hex') AS h,
           0.35 + 0.6 * get_byte(decode(md5(u.login), 'hex'), 0) / 255.0 AS habilidade
    FROM grupo_atividade g
    JOIN membro_grupo m ON m.grupo_id = g.id AND m.papel = 'ESTUDANTE'
    JOIN usuario u ON u.id = m.usuario_id
    JOIN grupo_atividade_item i ON i.grupo_id = g.id
    JOIN atividade a ON a.id = i.atividade_id
    WHERE g.id IN ('01489c96-d021-46e0-b7b4-e6b553fb048f', '75953d90-67a3-4f01-afd6-bdeb1230dce3',
                   'db09ea60-e280-49d8-8460-92e7926ebe9f', 'd0000000-0000-4000-8000-000000000005')
), plano AS (
    SELECT b.*,
           1 + get_byte(b.h, 1) % 4 AS n,
           get_byte(b.h, 2) / 255.0 < b.habilidade AS aceita
    FROM base b
    WHERE get_byte(b.h, 0) / 255.0 < 0.5 + 0.5 * b.habilidade
)
SELECT md5(p.grupo_id::text || p.usuario_id::text || p.atividade_id::text || k)::uuid AS id,
       p.grupo_id, p.usuario_id, p.atividade_id, p.limite_tempo_ms,
       CASE
           WHEN p.aceita AND k = p.n THEN 'ACEITA'
           ELSE (ARRAY['RESPOSTA_ERRADA', 'RESPOSTA_ERRADA', 'RESPOSTA_ERRADA', 'RESPOSTA_ERRADA',
                       'ERRO_COMPILACAO', 'ERRO_COMPILACAO', 'TEMPO_EXCEDIDO', 'ERRO_EXECUCAO'])[1 + get_byte(p.h, 3 + k) % 8]
       END AS veredito,
       get_byte(p.h, 8 + k) AS semente,
       least(now() - interval '1 hour',
             p.data_inicio + (least(now(), p.data_fim) - p.data_inicio) * (0.1 + 0.85 * get_byte(p.h, 13) / 255.0))
           + (k - 1) * interval '19 minutes' AS enviado_em
FROM plano p
CROSS JOIN LATERAL generate_series(1, p.n) AS k;

-- Resultado de cada caso oficial: todos ACEITA, ou ACEITA até o caso que falhou (a execução para nele).
-- ERRO_COMPILACAO não executa nenhum caso.
CREATE TEMP TABLE resultado_seed ON COMMIT DROP AS
WITH caso AS (
    SELECT id, atividade_id, row_number() OVER (PARTITION BY atividade_id ORDER BY id) AS pos,
           count(*) OVER (PARTITION BY atividade_id) AS total
    FROM caso_de_teste
    WHERE tipo = 'OFICIAL'
)
SELECT t.id AS solucao_id, c.id AS caso_id,
       CASE WHEN t.veredito = 'ACEITA' OR c.pos < f.falha THEN 'ACEITA' ELSE t.veredito END AS veredito,
       CASE
           WHEN t.veredito = 'TEMPO_EXCEDIDO' AND c.pos = f.falha THEN t.limite_tempo_ms
           ELSE 1 + (t.semente + c.pos) % 4
       END AS tempo_ms,
       3400 + (t.semente * 7 + c.pos * 13) % 120 AS memoria_kb
FROM tentativa t
JOIN caso c ON c.atividade_id = t.atividade_id
CROSS JOIN LATERAL (SELECT CASE WHEN c.total > 1 THEN 2 + t.semente % (c.total - 1) ELSE 1 END AS falha) f
WHERE t.veredito <> 'ERRO_COMPILACAO'
  AND (t.veredito = 'ACEITA' OR c.pos <= f.falha);

INSERT INTO solucao (id, autor_id, atividade_id, grupo_id, linguagem_id, codigo, status, veredito,
                     tempo_ms, memoria_kb, enviado_em, publicada_em)
SELECT t.id, t.usuario_id, t.atividade_id, t.grupo_id, cpp.id,
       CASE t.veredito
           WHEN 'ACEITA' THEN cs.correto
           WHEN 'ERRO_COMPILACAO' THEN replace(cs.correto, 'return 0;', 'return 0')
           ELSE cs.errado
       END,
       'CONCLUIDA', t.veredito, r.tempo_ms, r.memoria_kb, t.enviado_em, t.enviado_em + interval '1 second'
FROM tentativa t
JOIN codigo_seed cs ON cs.atividade_id = t.atividade_id
JOIN linguagem cpp ON cpp.nome = 'C++'
LEFT JOIN (
    SELECT solucao_id, max(tempo_ms) AS tempo_ms, max(memoria_kb) AS memoria_kb
    FROM resultado_seed
    GROUP BY solucao_id
) r ON r.solucao_id = t.id
ON CONFLICT (id) DO NOTHING;

INSERT INTO resultado_caso_de_teste (id, solucao_id, caso_de_teste_id, veredito, tempo_ms, memoria_kb)
SELECT gen_random_uuid(), r.solucao_id, r.caso_id, r.veredito, r.tempo_ms, r.memoria_kb
FROM resultado_seed r
WHERE NOT EXISTS (
    SELECT 1
    FROM resultado_caso_de_teste x
    WHERE x.solucao_id = r.solucao_id
      AND x.caso_de_teste_id = r.caso_id
);

COMMIT;
