# Deploy do Condo+ (Neon + Render + Vercel)

| Parte | Onde | Repositório |
|---|---|---|
| Banco PostgreSQL | [Neon](https://neon.tech) | — |
| API (este repo) | [Render](https://render.com), via `Dockerfile` | `ArthurFloro/condomais-backend` |
| Front | [Vercel](https://vercel.com) | `haveso27/condo_mais` |

Ordem: **Neon → Render → Vercel → voltar no Render para liberar o CORS**.

---

## 1. Banco no Neon

1. Crie um projeto. **Região: a mesma do Render**, por exemplo `AWS US East 2 (Ohio)` no Neon e `Ohio` no Render. Banco e API longe um do outro deixam toda requisição lenta.
2. Em **Connect**, escolha a conexão **direta**: desligue "Connection pooling". O host **não** pode ter `-pooler`. Anote o host, o banco (`neondb`), o usuário e a senha.
3. Em **SQL Editor**, rode nesta ordem:
   1. `docs/sql/deploy/01-schema.sql`: cria as 14 tabelas;
   2. `docs/sql/deploy/02-dados-iniciais.sql`, **trocando os valores entre `<...>`**: cria o condomínio e o primeiro administrador, **sem senha**. O CPF vai no formato `000.000.000-00`.

> Os scripts `docs/sql/2026-*.sql` são migrações para bancos **antigos**. Num banco novo, o `01-schema.sql` já contém tudo.

## 2. API no Render

1. **New → Web Service**. Como o repositório é público, dá para usar **Public Git Repository** com a URL `https://github.com/ArthurFloro/condomais-backend`. Esse modo não faz deploy automático a cada push: use **Manual Deploy** ou um *deploy hook*. Para ter deploy automático, o dono do repositório precisa conectar a conta do GitHub dele no Render.
2. Configuração:
   - **Language:** Docker (o Render detecta o `Dockerfile`);
   - **Branch:** `main`;
   - **Region:** a mesma do Neon;
   - **Instance:** Free, ou Starter para não dormir (veja abaixo).
3. **Environment variables:**

   | Variável | Valor |
   |---|---|
   | `DATABASE_URL` | `jdbc:postgresql://<host-do-neon>/neondb?sslmode=require` (começa com `jdbc:`; sem usuário e senha na URL) |
   | `DATABASE_USERNAME` | usuário do Neon |
   | `DATABASE_PASSWORD` | senha do Neon |
   | `JWT_SECRET` | texto longo e aleatório (**gere um novo**; veja abaixo) |
   | `CORS_ALLOWED_ORIGINS` | por enquanto `https://example.com`; troque pela URL da Vercel no passo 4 |
   | `ANTHROPIC_API_KEY` | *(opcional)* chave da Claude API (console.anthropic.com) para ligar o assistente virtual do morador. Sem ela, `POST /assistente/mensagens` responde 503 e o resto funciona normalmente |

   O `Dockerfile` já define `SPRING_PROFILES_ACTIVE=prod`, e o Render informa a `PORT` sozinho.

   Para gerar o `JWT_SECRET`:
   - PowerShell: `[Convert]::ToBase64String((1..48 | % { Get-Random -Maximum 256 }) -as [byte[]])`
   - Git Bash: `openssl rand -base64 48`
4. **Health Check Path:** `/v3/api-docs`.
5. Crie o serviço. O primeiro build leva alguns minutos. Quando terminar, abra `https://<seu-servico>.onrender.com/swagger-ui.html`.

Se faltar alguma variável, a aplicação **não sobe**, e o log mostra `Could not resolve placeholder '<VARIÁVEL>'`. Isso é de propósito.

**Plano Free:** o serviço dorme depois de 15 minutos sem requisições, e a primeira chamada depois disso leva de 30 a 60 segundos (o Spring Boot iniciando). Para demonstrações, abra o Swagger um minuto antes. Para uso real, o plano Starter não dorme.

## 3. Front na Vercel

1. **Add New → Project**. Para importar o repositório, a Vercel precisa de acesso a ele pelo GitHub. Há três caminhos:
   - o dono (`haveso27`) importa o projeto na conta dele; ou
   - você faz um **fork** para a sua conta e importa o fork. Depois de cada merge, use "Sync fork" no GitHub para atualizar; ou
   - você publica pela CLI, sem GitHub: `npx vercel` e depois `npx vercel --prod`, dentro da pasta do front.
2. **Framework:** Vite, detectado sozinho. O `vercel.json` do repositório já define o build e o redirecionamento das rotas.
3. **Environment Variables:**

   | Variável | Valor |
   |---|---|
   | `VITE_API_URL` | `https://<seu-servico>.onrender.com` (sem barra no final) |
   | `ENABLE_EXPERIMENTAL_COREPACK` | `1` (usa a versão exata do pnpm do `package.json`) |

   O `VITE_API_URL` entra no build: se mudar, faça **Redeploy**.
4. Faça o deploy e anote a URL de produção, por exemplo `https://condomais.vercel.app`.

## 4. Liberar o front na API

No Render, troque `CORS_ALLOWED_ORIGINS` pela URL de produção da Vercel, **sem barra no final**. Para mais de uma URL, separe por vírgula. Salve; o Render reinicia a API.

> As URLs de *preview* da Vercel mudam a cada deploy e não entram no CORS. Teste sempre na URL de produção.

## 5. Primeiro uso

1. Abra o front e clique em **"Ativar minha conta"**. Informe o CPF do administrador cadastrado no passo 1 e crie a senha.
2. Em **Unidades**, cadastre as torres ("+ Nova torre") e as unidades.
3. Em **Moradores**, cadastre os moradores. Cada um ativa a própria conta pelo mesmo "Ativar minha conta".

## Problemas comuns

| Sintoma | Causa provável |
|---|---|
| O deploy no Render falha com `Could not resolve placeholder` | falta variável de ambiente |
| `Schema-validation: missing table` | o `01-schema.sql` não foi rodado no Neon |
| O front mostra "Não foi possível conectar ao servidor" | a API está dormindo (espere e tente de novo), `VITE_API_URL` está errada ou falta o Redeploy do front |
| Erro de CORS no console do navegador | `CORS_ALLOWED_ORIGINS` diferente da URL do front (com barra no final, http/https, URL de preview) |
| "Primeiro acesso indisponível" para o admin | CPF digitado diferente do gravado no `02-dados-iniciais.sql` |
