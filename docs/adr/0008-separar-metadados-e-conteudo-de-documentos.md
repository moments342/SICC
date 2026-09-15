# Separar metadados e conteúdo de documentos

O PostgreSQL armazenará os metadados dos documentos, incluindo vínculo, versão, autor, data e checksum, enquanto o conteúdo dos arquivos ficará em um armazenamento acessado por interface própria. A primeira implementação utilizará uma pasta local configurável, permitindo substituir o mecanismo por S3, MinIO ou serviço institucional sem alterar as regras de domínio.
