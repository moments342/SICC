package com.moments.sicc.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

@Service
public class StorageService {
    private final Path root;

    public StorageService(@Value("${sicc.storage.directory}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    public String armazenar(byte[] content, String prefix) {
        try {
            Files.createDirectories(root);
            String key = prefix + "/" + UUID.randomUUID();
            Path destination = resolve(key);
            Files.createDirectories(destination.getParent());
            Files.write(destination, content);
            return key;
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível armazenar o arquivo.", e);
        }
    }

    public Resource carregar(String key) {
        try {
            Path file = resolve(key);
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("Arquivo não encontrado.");
            }
            return resource;
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível carregar o arquivo.", e);
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Chave de armazenamento inválida.");
        return path;
    }
}
