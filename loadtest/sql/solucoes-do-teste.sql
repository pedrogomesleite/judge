-- Uma linha por solução enviada numa execução de teste de carga, com a duração de cada fase (ms).
-- O rodar.py exporta o resultado em solucoes.csv e calcula os percentis; também serve para conferir à mão:
--   docker compose exec -T postgres psql -U postgres -d juizin --csv -v login=lt-<RUN_ID> -f - < loadtest/sql/solucoes-do-teste.sql
--
-- Fases (relógio da API, ver docs/architecture.md, 3.3):
--   publicacao_ms  enviado_em   → publicada_em   commit + publicação no RabbitMQ com publisher confirm
--   fila_ms        publicada_em → iniciada_em    espera na fila submissions.cpp até um runner consumir
--   avaliacao_ms   iniciada_em  → finalizada_em  casos de teste, compilação e execução no isolate, resultado gravado
--   total_ms       enviado_em   → finalizada_em  tempo de resposta do envio (sem a notificação SSE ao cliente)
-- iniciada_em é gravado quando a API processa o EXECUCAO_INICIADA, então fila_ms inclui o trânsito em submissions.results.
SELECT s.id,
       substring(s.codigo FROM 'juizin-loadtest tipo=([a-z]+)')                     AS tipo,
       s.status,
       s.veredito,
       s.tempo_ms,
       s.memoria_kb,
       extract(EPOCH FROM s.enviado_em)                                            AS enviado_em,
       extract(EPOCH FROM s.publicada_em)                                          AS publicada_em,
       extract(EPOCH FROM s.iniciada_em)                                           AS iniciada_em,
       extract(EPOCH FROM s.finalizada_em)                                         AS finalizada_em,
       round(extract(EPOCH FROM (s.publicada_em - s.enviado_em)) * 1000, 3)        AS publicacao_ms,
       round(extract(EPOCH FROM (s.iniciada_em - s.publicada_em)) * 1000, 3)       AS fila_ms,
       round(extract(EPOCH FROM (s.finalizada_em - s.iniciada_em)) * 1000, 3)      AS avaliacao_ms,
       round(extract(EPOCH FROM (s.finalizada_em - s.enviado_em)) * 1000, 3)       AS total_ms
FROM solucao s
         JOIN usuario u ON u.id = s.autor_id
WHERE u.login = :'login'
ORDER BY s.enviado_em;
