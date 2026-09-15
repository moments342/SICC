package com.moments.sicc.service;

import com.moments.sicc.service.ArmazenamentoSeguroUnix.AtributosDescritor;
import com.moments.sicc.service.ConfiguracaoUnix.Plataforma;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import java.io.IOException;

final class ChamadasNativasUnix implements ArmazenamentoSeguroUnix.ChamadasUnix {
    private static final int RENAME_NOREPLACE_LINUX = 1;
    private static final int RENAME_EXCL_MAC = 0x00000004;
    private static final int AT_SYMLINK_NOFOLLOW = 0x0100;
    private static final int AT_EMPTY_PATH = 0x1000;
    private static final int STATX_BASIC_STATS = 0x000007ff;
    private static final int TAMANHO_STATX_LINUX = 256;
    private static final int TAMANHO_STAT_MAC_LP64 = 144;

    static AtributosDescritor interpretarAtributosLinux(Pointer memoria) {
        // Layout estável de struct statx definido pela UAPI Linux.
        long dispositivo = (Integer.toUnsignedLong(memoria.getInt(136)) << 32)
                | Integer.toUnsignedLong(memoria.getInt(140));
        return new AtributosDescritor(
                dispositivo,
                memoria.getLong(32),
                memoria.getLong(40));
    }

    static AtributosDescritor interpretarAtributosMac(Pointer memoria) {
        // Layout LP64 de struct stat para amd64/arm64 definido pelo Darwin.
        return new AtributosDescritor(
                Integer.toUnsignedLong(memoria.getInt(0)),
                memoria.getLong(8),
                memoria.getLong(96));
    }

    @Override
    public int open(String caminho, int flags, int modo) {
        return LibC.INSTANCE.open(caminho, flags, Integer.valueOf(modo));
    }

    @Override
    public int openat(int diretorio, String nome, int flags, int modo) {
        return LibC.INSTANCE.openat(
                diretorio, nome, flags, Integer.valueOf(modo));
    }

    @Override
    public int mkdirat(int diretorio, String nome, int modo) {
        return LibC.INSTANCE.mkdirat(diretorio, nome, modo);
    }

    @Override
    public int publicarSemSubstituir(
            int diretorioOrigem,
            String origem,
            int diretorioDestino,
            String destino,
            ConfiguracaoUnix configuracao) {
        try {
            if (configuracao.plataforma() == Plataforma.LINUX) {
                return LibC.INSTANCE.renameat2(
                        diretorioOrigem,
                        origem,
                        diretorioDestino,
                        destino,
                        RENAME_NOREPLACE_LINUX);
            }
            return LibC.INSTANCE.renameatx_np(
                    diretorioOrigem,
                    origem,
                    diretorioDestino,
                    destino,
                    RENAME_EXCL_MAC);
        } catch (LinkageError e) {
            throw new AdaptadorNativoIndisponivel(e);
        }
    }

    @Override
    public int unlinkat(int diretorio, String nome, int flags) {
        return LibC.INSTANCE.unlinkat(diretorio, nome, flags);
    }

    @Override
    public int duplicarCloexec(int descritor, int comando) {
        return LibC.INSTANCE.fcntl(
                descritor, comando, Integer.valueOf(0));
    }

    @Override
    public long read(int descritor, Pointer destino, long quantidade) {
        return LibC.INSTANCE.read(
                descritor, destino, new NativeLong(quantidade)).longValue();
    }

    @Override
    public long write(int descritor, Pointer origem, long quantidade) {
        return LibC.INSTANCE.write(
                descritor, origem, new NativeLong(quantidade)).longValue();
    }

    @Override
    public int close(int descritor) {
        return LibC.INSTANCE.close(descritor);
    }

    @Override
    public AtributosDescritor atributos(
            int descritor,
            ConfiguracaoUnix configuracao) throws IOException {
        try {
            if (configuracao.plataforma() == Plataforma.LINUX) {
                return atributosLinux(descritor);
            }
            return atributosMac(descritor);
        } catch (LinkageError e) {
            throw new IOException(
                    "O sistema Unix não expõe atributos seguros por descritor.", e);
        }
    }

    private AtributosDescritor atributosLinux(int descritor) throws IOException {
        try (Memory memoria = new Memory(TAMANHO_STATX_LINUX)) {
            memoria.clear();
            int resultado = LibC.INSTANCE.statx(
                    descritor,
                    "",
                    AT_EMPTY_PATH | AT_SYMLINK_NOFOLLOW,
                    STATX_BASIC_STATS,
                    memoria);
            if (resultado != 0) {
                throw ArmazenamentoSeguroUnix.erro(
                        "consultar atributos", erro(), null);
            }
            return interpretarAtributosLinux(memoria);
        }
    }

    private AtributosDescritor atributosMac(int descritor) throws IOException {
        try (Memory memoria = new Memory(TAMANHO_STAT_MAC_LP64)) {
            memoria.clear();
            if (LibC.INSTANCE.fstat(descritor, memoria) != 0) {
                throw ArmazenamentoSeguroUnix.erro(
                        "consultar atributos", erro(), null);
            }
            return interpretarAtributosMac(memoria);
        }
    }

    @Override
    public int erro() {
        return Native.getLastError();
    }

    private interface LibC extends Library {
        LibC INSTANCE = Native.load("c", LibC.class);

        int open(String caminho, int flags, Object... argumentos);

        int openat(int diretorio, String nome, int flags, Object... argumentos);

        int mkdirat(int diretorio, String nome, int modo);

        int renameat2(
                int diretorioOrigem,
                String origem,
                int diretorioDestino,
                String destino,
                int flags);

        int renameatx_np(
                int diretorioOrigem,
                String origem,
                int diretorioDestino,
                String destino,
                int flags);

        int unlinkat(int diretorio, String nome, int flags);

        int fcntl(int descritor, int comando, Object... argumentos);

        NativeLong read(int descritor, Pointer destino, NativeLong quantidade);

        NativeLong write(int descritor, Pointer origem, NativeLong quantidade);

        int statx(int descritor, String caminho, int flags, int mascara, Pointer atributos);

        int fstat(int descritor, Pointer atributos);

        int close(int descritor);
    }

    private static final class AdaptadorNativoIndisponivel extends LinkageError {
        private AdaptadorNativoIndisponivel(LinkageError causa) {
            super("A publicação atômica sem substituição está indisponível.", causa);
        }
    }

}
