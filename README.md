# SICC

Sistema Integrado de Controle de Contratos da DIPAC, voltado ao acompanhamento
de processos administrativos, instrumentos contratuais e suas alterações.

A documentação principal do projeto é o
[Trabalho de Conclusão de Curso](TCC_2_CORRIGIDO%20-%20GABRIEL%20LEMOS%20FRANCISCONI_HUGO%20ARAUJO%20TEIXEIRA.pdf).

## Funcionalidades

- Cadastro de processos administrativos e formalização de instrumentos contratuais.
- Termos aditivos e apostilamentos, com efetivação, retificação e cancelamento
  sem apagar o histórico.
- Tramitação livre entre setores, histórico de movimentações e cálculo de permanência.
- Documentos anexos com versões preservadas e notificações internas.
- Dashboard gerencial e relatórios em PDF, XLSX e CSV, com filtros por setor e responsável.
- Consulta pública simplificada, controle de acesso por perfil, administração
  de usuários e setores e registros de auditoria.

O status dos processos e a situação das vigências contratual e do TED são
calculados automaticamente. A consulta pública apresenta somente os dados
permitidos para acesso sem autenticação.

## Tecnologias

- **Back-end:** Java 25, Spring Boot 3.5, Spring Security com JWT, JPA e Flyway.
- **Front-end:** React 19, TypeScript e Vite.
- **Banco de dados:** PostgreSQL 18; H2 nos testes.
- **API:** `/api/v1`. Os documentos são armazenados em diretório local configurável.

## Execução local

Requisitos: Java 25, Maven, Node.js com npm e PostgreSQL 18.

Crie o banco `sicc` e configure as variáveis no terminal do back-end. Os valores
entre `<...>` devem ser substituídos pelos dados do ambiente:

```powershell
$env:SICC_DB_URL = "jdbc:postgresql://localhost:5432/sicc"
$env:SICC_DB_USERNAME = "postgres"
$env:SICC_DB_PASSWORD = "<senha-do-banco>"
$env:SICC_JWT_SECRET = "<chave-aleatoria-com-pelo-menos-32-bytes>"
$env:SICC_BOOTSTRAP_LOGIN = "admin"
$env:SICC_BOOTSTRAP_PASSWORD = "<senha-temporaria>"
$env:SICC_BOOTSTRAP_EMAIL = "<email-do-administrador>"

mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

As credenciais de inicialização criam o primeiro administrador quando não há
usuários cadastrados. A senha deve ter pelo menos dez caracteres, com maiúscula,
minúscula e número, e será substituída no primeiro acesso. O Flyway aplica as
migrações pendentes ao iniciar a aplicação.

Em outro terminal, na raiz do projeto:

```powershell
cd frontend
npm ci
npm run dev
```

Acesse `http://localhost:5173`. O front-end encaminha as chamadas `/api` ao
back-end em `http://localhost:8080`.

Como alternativa ao PostgreSQL instalado localmente, execute
`docker compose up -d postgres` com `SICC_DB_PASSWORD` definida. Para alterar o
diretório dos documentos, configure `SICC_STORAGE_DIRECTORY`; o padrão é
`./data/storage`.

## Verificação

Na raiz do projeto:

```powershell
mvn test
cd frontend
npm run build
npm run test:e2e
```

Os testes de interface utilizam Google Chrome instalado e iniciam seu próprio
servidor de desenvolvimento.
