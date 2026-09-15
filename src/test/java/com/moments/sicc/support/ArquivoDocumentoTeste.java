package com.moments.sicc.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

public final class ArquivoDocumentoTeste {
    private ArquivoDocumentoTeste() {}

    public static byte[] pdfValido() {
        return pdfValido("fixture-sicc");
    }

    public static byte[] pdfValido(String marcador) {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.getDocumentInformation().setTitle(marcador);
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel criar o PDF de teste.", e);
        }
    }
}
