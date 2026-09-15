package com.moments.sicc.service;

import java.io.IOException;
import java.util.Locale;

record ConfiguracaoUnix(
        int oRdonly,
        int oWronly,
        int oCreat,
        int oExcl,
        int oDirectory,
        int oNofollow,
        int oCloexec,
        int fDupfdCloexec,
        int enoent,
        int eexist,
        int enotdir,
        int eloop,
        Plataforma plataforma) {

    ConfiguracaoUnix(
            int oRdonly,
            int oWronly,
            int oCreat,
            int oExcl,
            int oDirectory,
            int oNofollow,
            int oCloexec,
            int fDupfdCloexec,
            int enoent,
            int eexist,
            int enotdir,
            int eloop) {
        this(
                oRdonly, oWronly, oCreat, oExcl, oDirectory, oNofollow,
                oCloexec, fDupfdCloexec, enoent, eexist, enotdir, eloop,
                Plataforma.LINUX);
    }

    static ConfiguracaoUnix atual() throws IOException {
        return para(
                System.getProperty("os.name", ""),
                System.getProperty("os.arch", ""));
    }

    static ConfiguracaoUnix para(String nomeSistema, String nomeArquitetura)
            throws IOException {
        String sistema = nomeSistema.toLowerCase(Locale.ROOT);
        String arquitetura = nomeArquitetura.toLowerCase(Locale.ROOT);
        boolean arquiteturaConhecida = arquitetura.equals("amd64")
                || arquitetura.equals("x86_64")
                || arquitetura.equals("aarch64")
                || arquitetura.equals("arm64");
        if (sistema.contains("linux") && arquiteturaConhecida) {
            return new ConfiguracaoUnix(
                    0, 1, 0x40, 0x80, 0x10000, 0x20000, 0x80000,
                    1030, 2, 17, 20, 40, Plataforma.LINUX);
        }
        if ((sistema.contains("mac") || sistema.contains("darwin"))
                && arquiteturaConhecida) {
            return new ConfiguracaoUnix(
                    0, 1, 0x0200, 0x0800, 0x100000, 0x0100, 0x1000000,
                    67, 2, 17, 20, 62, Plataforma.MACOS);
        }
        throw new IOException("O sistema Unix não possui adaptador seguro de armazenamento.");
    }

    int flagsDiretorio() {
        return oRdonly | oDirectory | oNofollow | oCloexec;
    }

    int flagsCriacaoArquivo() {
        return oWronly | oCreat | oExcl | oNofollow | oCloexec;
    }

    int flagsLeituraArquivo() {
        return oRdonly | oNofollow | oCloexec;
    }

    enum Plataforma {
        LINUX,
        MACOS
    }
}
