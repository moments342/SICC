package com.moments.sicc.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

final class ApresentacaoRelatorio {
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final Map<String, String> ROTULOS = Map.ofEntries(
            Map.entry("numero_processo", "Processo Administrativo"),
            Map.entry("origem", "Origem"), Map.entry("status", "Status do processo"),
            Map.entry("tipo_instrumento", "Tipo de instrumento"),
            Map.entry("coordenador", "Coordenador"),
            Map.entry("vigencia_contratual", "Vigência contratual"),
            Map.entry("vigencia_ted", "Vigência do TED"),
            Map.entry("valor_atual", "Valor atual"),
            Map.entry("numero_instrumento", "Instrumento Contratual"),
            Map.entry("objeto", "Objeto"), Map.entry("descricao", "Descrição"),
            Map.entry("natureza", "Natureza"), Map.entry("participes", "Partícipes"),
            Map.entry("data_cadastro", "Data de cadastro"), Map.entry("numero_projeto", "Número do projeto"),
            Map.entry("responsavel_dipac", "Responsável DIPAC"),
            Map.entry("data_formalizacao", "Data de formalização"),
            Map.entry("ano", "Ano de cadastro"), Map.entry("total", "Total de processos"),
            Map.entry("em_formalizacao", "Em formalização"), Map.entry("em_vigencia", "Em vigência"),
            Map.entry("concluido", "Concluídos"), Map.entry("quantidade", "Quantidade"),
            Map.entry("valor_total_atual", "Valor total atual"),
            Map.entry("contexto", "Contexto da tramitação"), Map.entry("contexto_id", "Identificador do contexto"),
            Map.entry("data", "Data da movimentação"), Map.entry("sequencia", "Sequência no dia"),
            Map.entry("setor_destino", "Setor de destino"), Map.entry("autor", "Autor"),
            Map.entry("observacao", "Observação"), Map.entry("permanencia_inicio", "Chegada ao setor"),
            Map.entry("permanencia_fim", "Saída do setor"), Map.entry("permanencia_dias", "Permanência (dias)"),
            Map.entry("permanencia_aberta", "Permanência em aberto"),
            Map.entry("situacao_contratual", "Situação da vigência contratual"),
            Map.entry("situacao_ted", "Situação da vigência do TED"),
            Map.entry("dias_ate_vencimento_contratual", "Dias até o vencimento contratual"),
            Map.entry("dias_ate_vencimento_ted", "Dias até o vencimento do TED"),
            Map.entry("no_horizonte_120_contratual", "Contrato vence em até 120 dias"),
            Map.entry("no_horizonte_120_ted", "TED vence em até 120 dias"),
            Map.entry("filtros", "Filtros"), Map.entry("gerado_em", "Gerado em"),
            Map.entry("indicador", "Indicador"), Map.entry("resultado", "Resultado"), Map.entry("unidade", "Unidade"),
            Map.entry("total_processos", "Total de processos"), Map.entry("percentual_concluidos", "Percentual concluído"),
            Map.entry("valor_total_vigente", "Valor total vigente"), Map.entry("alertas_contratuais", "Vencimentos contratuais em até 120 dias"),
            Map.entry("alertas_ted", "Vencimentos do TED em até 120 dias"),
            Map.entry("tempo_medio_tramitacao_inicial", "Tempo médio de tramitação inicial"), Map.entry("maior_gargalo", "Maior gargalo"),
            Map.entry("cadastros_ano", "Cadastros no ano"), Map.entry("formalizacoes_ano", "Formalizações no ano"),
            Map.entry("conclusoes_ano", "Conclusões no ano"), Map.entry("tempo_medio_formalizacoes_ano", "Tempo médio até as formalizações do ano"),
            Map.entry("setor", "Setor"), Map.entry("setor_atual", "Setor atual"), Map.entry("periodos", "Períodos de permanência"),
            Map.entry("permanencia_media_dias", "Permanência média (dias)"), Map.entry("tempo_tramitacao_inicial_dias", "Tramitação inicial (dias)"),
            Map.entry("mes", "Mês"), Map.entry("cadastros", "Cadastros"), Map.entry("formalizacoes", "Formalizações"), Map.entry("conclusoes", "Conclusões"));

    private ApresentacaoRelatorio() {}

    static String rotulo(String codigo) {
        return ROTULOS.getOrDefault(codigo, codigo.replace('_', ' '));
    }

    static String valor(ConteudoRelatorio.Celula celula, String coluna) {
        if (celula.valor().isEmpty()) return "Não informado";
        return switch (celula.natureza()) {
            case MOEDA -> NumberFormat.getCurrencyInstance(PT_BR).format(new BigDecimal(celula.valor()));
            case PERCENTUAL -> NumberFormat.getNumberInstance(PT_BR).format(new BigDecimal(celula.valor())) + "%";
            case DATA -> LocalDate.parse(celula.valor()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            case DATA_HORA -> LocalDateTime.parse(celula.valor())
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            case LOGICO -> Boolean.parseBoolean(celula.valor()) ? "Sim" : "Não";
            case ENUMERACAO -> enumeracao(celula.valor());
            case NUMERO -> coluna.startsWith("valor")
                    ? NumberFormat.getCurrencyInstance(PT_BR).format(new BigDecimal(celula.valor()))
                    : celula.valor();
            default -> coluna.equals("indicador") ? rotulo(celula.valor()) : TextoCelulaRelatorio.emUmaCelula(celula.valor());
        };
    }

    private static String enumeracao(String codigo) {
        return switch (codigo) {
            case "EM_FORMALIZACAO" -> "Em formalização";
            case "EM_VIGENCIA" -> "Em vigência";
            case "CONCLUIDO" -> "Concluído";
            case "CONTRATO_GESTAO" -> "Contrato de gestão";
            case "CONVENIO" -> "Convênio";
            case "ACORDO_PARCERIA" -> "Acordo de parceria";
            case "ACORDO_COOPERACAO_TECNICA" -> "Acordo de cooperação técnica";
            case "VALIDA" -> "Válida";
            case "PROXIMA_VENCIMENTO" -> "Próxima do vencimento";
            case "VENCIDA" -> "Vencida";
            case "NAO_INFORMADA" -> "Não informada";
            case "FORMALIZACAO" -> "Formalização";
            case "TERMO_ADITIVO" -> "Termo Aditivo";
            case "APOSTILAMENTO" -> "Apostilamento";
            default -> codigo;
        };
    }
}
