-- Remove os dados criados pelos testes de carga: usuários "lt-*", as atividades deles e tudo que depende delas.
-- Rode só com os testes parados (soluções em andamento voltariam a ser gravadas pelo runner).
--   docker compose exec -T postgres psql -U postgres -d juizin -f - < loadtest/sql/limpar.sql
BEGIN;

CREATE TEMP TABLE lt_usuario ON COMMIT DROP AS
SELECT id FROM usuario WHERE login LIKE 'lt-%';

CREATE TEMP TABLE lt_atividade ON COMMIT DROP AS
SELECT id FROM atividade WHERE autor_id IN (SELECT id FROM lt_usuario);

CREATE TEMP TABLE lt_solucao ON COMMIT DROP AS
SELECT id FROM solucao
WHERE autor_id IN (SELECT id FROM lt_usuario)
   OR atividade_id IN (SELECT id FROM lt_atividade);

DELETE FROM resultado_caso_de_teste
WHERE solucao_id IN (SELECT id FROM lt_solucao)
   OR caso_de_teste_id IN (SELECT id FROM caso_de_teste WHERE atividade_id IN (SELECT id FROM lt_atividade));
DELETE FROM solucao WHERE id IN (SELECT id FROM lt_solucao);
DELETE FROM caso_de_teste WHERE atividade_id IN (SELECT id FROM lt_atividade);
DELETE FROM grupo_atividade_item WHERE atividade_id IN (SELECT id FROM lt_atividade);
DELETE FROM atividade WHERE id IN (SELECT id FROM lt_atividade);
DELETE FROM usuario WHERE id IN (SELECT id FROM lt_usuario);

COMMIT;
