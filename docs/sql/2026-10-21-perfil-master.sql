-- Perfil MASTER (donos do Condo+): o único usuário sem condomínio.
-- Rode no banco ANTES de subir a versão que traz /plataforma (spring.jpa.hibernate.ddl-auto=validate).
--
-- 1) condominio_id passa a aceitar NULL, mas só para o MASTER: o CHECK impede um ADMIN, PORTARIA
--    ou MORADOR sem condomínio e um MASTER preso a um condomínio.
ALTER TABLE usuarios ALTER COLUMN condominio_id DROP NOT NULL;

ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS usuarios_master_sem_condominio;
ALTER TABLE usuarios ADD CONSTRAINT usuarios_master_sem_condominio
    CHECK ((perfil = 'MASTER') = (condominio_id IS NULL));

-- 2) Primeiro MASTER. Troque os valores entre <...> e rode uma vez.
--    Ele nasce SEM senha e a define em "Ativar minha conta" (POST /auth/primeiro-acesso).
--    CPF no formato 000.000.000-00 (o login compara o valor exato).
-- INSERT INTO usuarios (id, nome, cpf, email, telefone, perfil, status, senha, condominio_id)
-- VALUES (gen_random_uuid(), '<Nome>', '<000.000.000-00>', '<email@exemplo.com>', NULL, 'MASTER', 'ATIVO', NULL, NULL);
