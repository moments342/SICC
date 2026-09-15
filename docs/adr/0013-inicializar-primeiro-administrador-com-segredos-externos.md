# Inicializar o primeiro administrador com segredos externos

Quando o banco estiver vazio, o primeiro administrador DIPAC será criado a partir de variáveis de ambiente, sem credenciais padrão no código ou nas migrações. A senha recebida será temporária e deverá ser substituída no primeiro login. Depois do bootstrap, novos usuários serão criados apenas por um administrador autenticado.
