package com.moments.sicc.service;

import com.moments.sicc.shared.exception.ArmazenamentoException;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;

final class ExclusaoSeguraArquivo {
    private ExclusaoSeguraArquivo() {
    }

    static void remover(Path raiz, Path arquivo) throws IOException {
        Path raizNormalizada = raiz.toAbsolutePath().normalize();
        Path arquivoNormalizado = arquivo.toAbsolutePath().normalize();
        if (arquivoNormalizado.equals(raizNormalizada)
                || !arquivoNormalizado.startsWith(raizNormalizada)) throw chaveInvalida();
        Path relativo = raizNormalizada.relativize(arquivoNormalizado);
        if (relativo.getNameCount() == 0) throw chaveInvalida();

        if (ehWindowsPadrao(raizNormalizada)) {
            try {
                ExclusaoSeguraWindows.remover(raizNormalizada, arquivoNormalizado);
            } catch (LinkageError e) {
                throw new IOException("A exclusão segura do Windows está indisponível.", e);
            }
            return;
        }
        removerComDiretoriosSeguros(raizNormalizada, relativo);
    }

    private static boolean ehWindowsPadrao(Path raiz) {
        return raiz.getFileSystem().equals(FileSystems.getDefault())
                && "\\".equals(raiz.getFileSystem().getSeparator());
    }

    private static void removerComDiretoriosSeguros(Path raiz, Path relativo) throws IOException {
        Path raizDoSistema = raiz.getRoot();
        if (raizDoSistema == null) throw chaveInvalida();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(raizDoSistema)) {
            if (!(stream instanceof SecureDirectoryStream<?>)) {
                throw new IOException(
                        "O sistema de arquivos não oferece exclusão segura por diretório.");
            }
            @SuppressWarnings("unchecked")
            SecureDirectoryStream<Path> diretorio = (SecureDirectoryStream<Path>) stream;
            percorrerEDesvincular(diretorio, raiz, relativo, 0);
        }
    }

    private static void percorrerEDesvincular(
            SecureDirectoryStream<Path> diretorio,
            Path raiz,
            Path relativo,
            int indice) throws IOException {
        int quantidadeDiretoriosRaiz = raiz.getNameCount();
        int quantidadePaisRelativos = relativo.getNameCount() - 1;
        int quantidadeDiretorios = quantidadeDiretoriosRaiz + quantidadePaisRelativos;
        if (indice == quantidadeDiretorios) {
            diretorio.deleteFile(relativo.getFileName());
            return;
        }

        Path nome = indice < quantidadeDiretoriosRaiz
                ? raiz.getName(indice)
                : relativo.getName(indice - quantidadeDiretoriosRaiz);
        validarDiretorioSemSeguirLink(diretorio, nome);
        try (SecureDirectoryStream<Path> filho = diretorio.newDirectoryStream(
                nome, LinkOption.NOFOLLOW_LINKS)) {
            percorrerEDesvincular(filho, raiz, relativo, indice + 1);
        }
    }

    private static void validarDiretorioSemSeguirLink(
            SecureDirectoryStream<Path> diretorio,
            Path nome) throws IOException {
        BasicFileAttributeView view = diretorio.getFileAttributeView(
                nome, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            throw new IOException("O sistema de arquivos não expõe atributos seguros.");
        }
        BasicFileAttributes atributos = view.readAttributes();
        if (!atributos.isDirectory()) throw chaveInvalida();
    }

    private static ArmazenamentoException chaveInvalida() {
        return new ArmazenamentoException("Chave de armazenamento inválida.");
    }
}
