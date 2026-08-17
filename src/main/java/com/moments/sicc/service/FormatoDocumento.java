package com.moments.sicc.service;

import com.moments.sicc.domain.Enums.CategoriaDocumento;

public enum FormatoDocumento {
    PDF("application/pdf", "pdf"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
    CSV("text/csv", "csv");

    private final String mime;
    private final String extensao;

    FormatoDocumento(String mime, String extensao) {
        this.mime = mime;
        this.extensao = extensao;
    }

    public String mime() {
        return mime;
    }

    public String extensao() {
        return extensao;
    }

    public boolean permitidoPara(CategoriaDocumento categoria) {
        return categoria != CategoriaDocumento.ASSINADO || this == PDF;
    }
}
