package com.moments.sicc.service;

import com.moments.sicc.shared.exception.ArmazenamentoException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

@Service
public class ArmazenamentoLocal implements ArmazenamentoArquivo {
    private final Path root;

    public ArmazenamentoLocal(@Value("${sicc.storage.directory}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public String armazenar(byte[] content, String prefix) {
        Path temporary = null;
        try {
            Files.createDirectories(root);
            String key = prefix + "/" + UUID.randomUUID();
            Path destination = resolve(key);
            Files.createDirectories(destination.getParent());
            temporary = Files.createTempFile(destination.getParent(), ".upload-", ".tmp");
            Files.write(temporary, content);
            moverAtomico(temporary, destination);
            return key;
        } catch (IOException e) {
            apagarTemporario(temporary);
            throw new ArmazenamentoException("Não foi possível armazenar o arquivo.", e);
        }
    }

    @Override
    public Resource carregar(String key) {
        try {
            Path file = resolve(key);
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ArmazenamentoException("Arquivo não encontrado.");
            }
            return resource;
        } catch (IOException e) {
            throw new ArmazenamentoException("Não foi possível carregar o arquivo.", e);
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new ArmazenamentoException("Chave de armazenamento inválida.");
        }
        return path;
    }

    private void moverAtomico(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(source, destination);
        }
    }

    private void apagarTemporario(Path temporary) {
        if (temporary == null) return;
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // A falha principal de armazenamento continua sendo a causa reportada.
        }
    }
}
