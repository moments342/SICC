# SICC

Sistema Integrado de Controle de Contratos da DIPAC. A aplicação separa Processo
Administrativo de Instrumento Contratual, mantém tramitações e documentos
imutáveis, oferece uma consulta pública controlada e calcula status e vigências
automaticamente.

## Arquitetura

- Back-end: Java 25, Spring Boot 3.5, Spring Security/JWT, JPA e Flyway.
- Front-end: React 19, TypeScript e Vite em `frontend/`.
- Banco: PostgreSQL 18 em desenvolvimento/produção e H2 em testes.
- Arquivos: diretório local configurável por `SICC_STORAGE_DIRECTORY`.
- API: contratos versionados em `/api/v1`.

## Desenvolvimento

1. Confirme que o serviço local `postgresql-x64-18` está em execução e que a
   database `sicc` existe na porta `5432`:

   ```powershell
   Get-Service postgresql-x64-18
   & "C:\Program Files\PostgreSQL\18\bin\psql.exe" `
     -h localhost -p 5432 -U postgres -d sicc
   ```

2. Configure a conexão sem gravar a senha no repositório:

   ```powershell
   $env:SICC_DB_URL = "jdbc:postgresql://localhost:5432/sicc"
   $env:SICC_DB_USERNAME = "postgres"
   $env:SICC_DB_PASSWORD = "<senha-do-postgres>"
   ```

3. Defina as credenciais do primeiro administrador. Elas só são usadas quando a
   tabela de usuários está vazia e a senha deve ser trocada no primeiro acesso:

   ```powershell
   $env:SICC_BOOTSTRAP_LOGIN = "admin"
   $env:SICC_BOOTSTRAP_PASSWORD = "Temporaria123!"
   $env:SICC_BOOTSTRAP_EMAIL = "admin@sicc.local"
   $env:SICC_JWT_SECRET = "substitua-por-uma-chave-secreta-com-32-bytes"
   ```

4. Inicie o back-end com o perfil `dev`. O Flyway aplicará apenas as migrations
   ainda pendentes:

   ```powershell
   mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
   ```

5. Em outro terminal, inicie o front-end:

   ```powershell
   cd frontend
   npm install
   npm run dev
   ```

O Vite encaminha `/api` para `http://localhost:8080`.

## Verificação

```powershell
mvn test
cd frontend
npm run build
npm run test:e2e
```

As propriedades `SICC_BOOTSTRAP_*`, `SICC_JWT_SECRET`, `SICC_DB_URL`,
`SICC_DB_USERNAME`, `SICC_DB_PASSWORD` e `SICC_STORAGE_DIRECTORY` devem ser
fornecidas pelo ambiente de implantação. O `compose.yaml` permanece disponível
como alternativa isolada, usando PostgreSQL 18, mas não é necessário quando o
serviço local está ativo.
