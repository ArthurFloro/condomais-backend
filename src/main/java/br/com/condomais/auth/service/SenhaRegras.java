package br.com.condomais.auth.service;

import java.nio.charset.StandardCharsets;

/** Regras da senha escolhida pelo usuário, iguais no primeiro acesso, na recuperação e no front. */
public final class SenhaRegras {

    public static final int MINIMA = 8;
    // Acima de 72 bytes o bcrypt ignora o resto, então a senha seria só um prefixo
    public static final int MAXIMA_BYTES = 72;

    private SenhaRegras() {}

    /** @return a mensagem do problema, ou null se a senha serve */
    public static String problema(String senha) {
        if (senha == null || senha.isBlank()) return "Informe a nova senha.";
        if (senha.length() < MINIMA) return "A senha deve ter no mínimo " + MINIMA + " caracteres.";
        if (senha.getBytes(StandardCharsets.UTF_8).length > MAXIMA_BYTES) return "A senha deve ter no máximo " + MAXIMA_BYTES + " caracteres.";
        return null;
    }
}
