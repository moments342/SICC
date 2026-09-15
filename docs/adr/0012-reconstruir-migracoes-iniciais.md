# Reconstruir as migrações iniciais

As migrações e o esquema atuais são protótipos sem dados reais a preservar. Antes do primeiro lançamento, a implementação poderá substituir a linha de base Flyway existente por um esquema coerente com o domínio final, em vez de acumular migrações corretivas. Depois que essa nova linha de base for usada com dados reais, suas migrações passarão a ser imutáveis.

Esta decisão registra a preparação da linha de base do protótipo. Não autoriza apagar bancos existentes nem reescrever migrações aplicadas em instalações com dados reais. A certificação atual valida a sequência Flyway versionada em um banco isolado.
