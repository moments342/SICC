package com.moments.sicc.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class RelogioControlavel extends Clock {
    private final AtomicReference<Instant> instante;
    private final AtomicReference<Instant> proximoInstante = new AtomicReference<>();
    private final AtomicInteger leiturasAteAvanco = new AtomicInteger();

    public RelogioControlavel(LocalDate data) {
        instante = new AtomicReference<>(instanteEm(data));
    }

    public void redefinir(LocalDate data) {
        instante.set(instanteEm(data));
        proximoInstante.set(null);
        leiturasAteAvanco.set(0);
    }

    public void avancarAposProximaLeitura(LocalDate data) {
        avancarAposLeituras(1, data);
    }

    public void avancarAposLeituras(int quantidade, LocalDate data) {
        if (quantidade < 1) {
            throw new IllegalArgumentException("A quantidade de leituras deve ser positiva.");
        }
        proximoInstante.set(instanteEm(data));
        leiturasAteAvanco.set(quantidade);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        if (!ZoneOffset.UTC.equals(zone)) {
            throw new IllegalArgumentException("O relógio de teste usa UTC.");
        }
        return this;
    }

    @Override
    public Instant instant() {
        Instant atual = instante.get();
        Instant seguinte = proximoInstante.get();
        if (seguinte != null && leiturasAteAvanco.decrementAndGet() == 0) {
            instante.set(seguinte);
            proximoInstante.compareAndSet(seguinte, null);
        }
        return atual;
    }

    private Instant instanteEm(LocalDate data) {
        return data.atTime(12, 0).toInstant(ZoneOffset.UTC);
    }
}
