package com.moments.sicc.service;

import com.moments.sicc.shared.exception.ArmazenamentoException;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.win32.StdCallLibrary;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ExclusaoSeguraWindows {
    private static final int DELETE = 0x00010000;
    private static final int FILE_READ_ATTRIBUTES = 0x00000080;
    private static final int FILE_SHARE_READ = 0x00000001;
    private static final int FILE_SHARE_WRITE = 0x00000002;
    private static final int FILE_SHARE_DELETE = 0x00000004;
    private static final int FILE_ATTRIBUTE_DIRECTORY = 0x00000010;
    private static final int FILE_ATTRIBUTE_REPARSE_POINT = 0x00000400;
    private static final int OPEN_EXISTING = 3;
    private static final int FILE_FLAG_BACKUP_SEMANTICS = 0x02000000;
    private static final int FILE_FLAG_OPEN_REPARSE_POINT = 0x00200000;
    private static final int FILE_DISPOSITION_INFO = 4;
    private static final int FILE_ATTRIBUTE_TAG_INFO = 9;
    private static final int ERROR_FILE_NOT_FOUND = 2;
    private static final int ERROR_PATH_NOT_FOUND = 3;
    private static final long INVALID_HANDLE_VALUE = -1L;

    private ExclusaoSeguraWindows() {
    }

    static Closeable fixarRaiz(Path raiz, boolean criarSeAusente) throws IOException {
        Path raizDoSistema = raiz.getRoot();
        if (raizDoSistema == null) {
            throw new ArmazenamentoException("Raiz de armazenamento inválida.");
        }
        List<Handle> handles = new ArrayList<>();
        try {
            Path atual = raizDoSistema;
            if (raiz.getNameCount() == 0) {
                abrirValidarEFixar(atual, criarSeAusente, handles);
            } else {
                for (Path nome : raiz) {
                    atual = atual.resolve(nome);
                    abrirValidarEFixar(atual, criarSeAusente, handles);
                }
            }
            return new HandlesFixados(handles);
        } catch (IOException | RuntimeException e) {
            fecharReverso(handles, e);
            throw e;
        }
    }

    private static void abrirValidarEFixar(
            Path caminho,
            boolean criarSeAusente,
            List<Handle> handles) throws IOException {
        Handle handle = abrir(
                caminho,
                FILE_READ_ATTRIBUTES,
                FILE_SHARE_READ);
        if (handle == null && criarSeAusente) {
            try {
                java.nio.file.Files.createDirectory(caminho);
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                // A entrada que venceu a corrida será validada pelo handle sem seguir reparse.
            }
            handle = abrir(
                    caminho,
                    FILE_READ_ATTRIBUTES,
                    FILE_SHARE_READ);
        }
        if (handle == null) throw new java.nio.file.NoSuchFileException(caminho.toString());
        try {
            validarRaiz(handle);
            handles.add(handle);
        } catch (IOException | RuntimeException e) {
            try {
                handle.close();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    private static void fecharReverso(List<Handle> handles, Throwable falha) {
        for (int indice = handles.size() - 1; indice >= 0; indice--) {
            try {
                handles.get(indice).close();
            } catch (IOException e) {
                falha.addSuppressed(e);
            }
        }
    }

    static void remover(Path raiz, Path arquivo) throws IOException {
        try (Handle raizAberta = abrir(raiz, FILE_READ_ATTRIBUTES)) {
            if (raizAberta == null) return;
            try (Handle arquivoAberto = abrir(arquivo, DELETE | FILE_READ_ATTRIBUTES)) {
                if (arquivoAberto == null) return;
                Path raizReal = caminhoFinal(raizAberta);
                Path arquivoReal = caminhoFinal(arquivoAberto);
                if (arquivoReal.equals(raizReal) || !arquivoReal.startsWith(raizReal)) {
                    throw chaveInvalida();
                }
                marcarParaExclusao(arquivoAberto);
            }
        }
    }

    static ArquivoFixado fixarArquivo(Path raiz, Path arquivo) throws IOException {
        Handle handle = abrir(arquivo, DELETE | FILE_READ_ATTRIBUTES, FILE_SHARE_READ);
        if (handle == null) throw new java.nio.file.NoSuchFileException(arquivo.toString());
        try {
            Path real = caminhoFinal(handle);
            if (!real.equals(arquivo.toAbsolutePath().normalize()) || !real.startsWith(raiz)) {
                throw chaveInvalida();
            }
            try (Memory informacao = new Memory(8)) {
                if (!Kernel32.INSTANCE.GetFileInformationByHandleEx(
                        handle.valor(), FILE_ATTRIBUTE_TAG_INFO, informacao, 8)) {
                    throw erroNativo("validar arquivo", arquivo, Kernel32.INSTANCE.GetLastError());
                }
                if ((informacao.getInt(0) & (FILE_ATTRIBUTE_DIRECTORY | FILE_ATTRIBUTE_REPARSE_POINT)) != 0) {
                    throw chaveInvalida();
                }
            }
            return new ArquivoFixado(handle);
        } catch (IOException | RuntimeException e) {
            try { handle.close(); } catch (IOException falha) { e.addSuppressed(falha); }
            throw e;
        }
    }

    static final class ArquivoFixado implements Closeable {
        private final Handle handle;

        private ArquivoFixado(Handle handle) { this.handle = handle; }

        void remover() throws IOException { marcarParaExclusao(handle); }

        @Override
        public void close() throws IOException { handle.close(); }
    }

    private static Handle abrir(Path caminho, int acesso) throws IOException {
        return abrir(
                caminho,
                acesso,
                FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE);
    }

    private static Handle abrir(Path caminho, int acesso, int compartilhamento)
            throws IOException {
        Pointer valor = Kernel32.INSTANCE.CreateFileW(
                new WString(caminho.toString()),
                acesso,
                compartilhamento,
                Pointer.NULL,
                OPEN_EXISTING,
                FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OPEN_REPARSE_POINT,
                Pointer.NULL);
        if (valor != null && Pointer.nativeValue(valor) != INVALID_HANDLE_VALUE) {
            return new Handle(valor);
        }
        int erro = Kernel32.INSTANCE.GetLastError();
        if (erro == ERROR_FILE_NOT_FOUND || erro == ERROR_PATH_NOT_FOUND) return null;
        throw erroNativo("abrir", caminho, erro);
    }

    private static void validarRaiz(Handle handle) throws IOException {
        try (Memory informacao = new Memory(8)) {
            if (!Kernel32.INSTANCE.GetFileInformationByHandleEx(
                    handle.valor(), FILE_ATTRIBUTE_TAG_INFO, informacao, 8)) {
                throw erroNativo("validar raiz", null, Kernel32.INSTANCE.GetLastError());
            }
            int atributos = informacao.getInt(0);
            if ((atributos & FILE_ATTRIBUTE_DIRECTORY) == 0
                    || (atributos & FILE_ATTRIBUTE_REPARSE_POINT) != 0) {
                throw new ArmazenamentoException("Raiz de armazenamento inválida.");
            }
        }
    }

    private static Path caminhoFinal(Handle handle) throws IOException {
        char[] buffer = new char[512];
        int tamanho = Kernel32.INSTANCE.GetFinalPathNameByHandleW(
                handle.valor(), buffer, buffer.length, 0);
        if (tamanho == 0) {
            throw erroNativo("resolver", null, Kernel32.INSTANCE.GetLastError());
        }
        if (tamanho >= buffer.length) {
            buffer = new char[tamanho + 1];
            tamanho = Kernel32.INSTANCE.GetFinalPathNameByHandleW(
                    handle.valor(), buffer, buffer.length, 0);
            if (tamanho == 0 || tamanho >= buffer.length) {
                throw erroNativo("resolver", null, Kernel32.INSTANCE.GetLastError());
            }
        }
        return Path.of(normalizarPrefixoWindows(new String(buffer, 0, tamanho)))
                .toAbsolutePath()
                .normalize();
    }

    private static String normalizarPrefixoWindows(String caminho) {
        if (caminho.startsWith("\\\\?\\UNC\\")) {
            return "\\\\" + caminho.substring("\\\\?\\UNC\\".length());
        }
        if (caminho.startsWith("\\\\?\\")) {
            return caminho.substring("\\\\?\\".length());
        }
        return caminho;
    }

    private static void marcarParaExclusao(Handle handle) throws IOException {
        try (Memory disposicao = new Memory(1)) {
            disposicao.setByte(0, (byte) 1);
            if (!Kernel32.INSTANCE.SetFileInformationByHandle(
                    handle.valor(), FILE_DISPOSITION_INFO, disposicao, 1)) {
                throw erroNativo("remover", null, Kernel32.INSTANCE.GetLastError());
            }
        }
    }

    private static IOException erroNativo(String operacao, Path caminho, int codigo) {
        String alvo = caminho == null ? "" : " " + caminho;
        return new IOException(
                "Falha nativa ao " + operacao + alvo + " (código " + codigo + ").");
    }

    private static ArmazenamentoException chaveInvalida() {
        return new ArmazenamentoException("Chave de armazenamento inválida.");
    }

    private interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        Pointer CreateFileW(
                WString nome,
                int acesso,
                int compartilhamento,
                Pointer atributosSeguranca,
                int disposicao,
                int atributos,
                Pointer modelo);

        int GetFinalPathNameByHandleW(
                Pointer arquivo,
                char[] caminho,
                int capacidade,
                int flags);

        boolean SetFileInformationByHandle(
                Pointer arquivo,
                int classe,
                Pointer informacao,
                int tamanho);

        boolean GetFileInformationByHandleEx(
                Pointer arquivo,
                int classe,
                Pointer informacao,
                int tamanho);

        boolean CloseHandle(Pointer handle);

        int GetLastError();
    }

    private record Handle(Pointer valor) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            if (!Kernel32.INSTANCE.CloseHandle(valor)) {
                throw erroNativo("fechar handle", null, Kernel32.INSTANCE.GetLastError());
            }
        }
    }

    private static final class HandlesFixados implements Closeable {
        private final List<Handle> handles;

        private HandlesFixados(List<Handle> handles) {
            this.handles = List.copyOf(handles);
        }

        @Override
        public void close() throws IOException {
            IOException falha = null;
            for (int indice = handles.size() - 1; indice >= 0; indice--) {
                try {
                    handles.get(indice).close();
                } catch (IOException e) {
                    if (falha == null) falha = e;
                    else falha.addSuppressed(e);
                }
            }
            if (falha != null) throw falha;
        }
    }
}
