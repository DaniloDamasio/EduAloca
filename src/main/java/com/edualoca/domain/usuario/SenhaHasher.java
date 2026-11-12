package com.edualoca.domain.usuario;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Hash de senha com sal (RNF-05, DEF-013): PBKDF2-HMAC-SHA256 do proprio JDK, 600.000 iteracoes (recomendacao
 * OWASP para esse algoritmo), sal aleatorio de 16 bytes por senha e chave de 256 bits. O valor armazenado leva os
 * parametros, no formato {@code pbkdf2-sha256$iteracoes$sal$hash} (sal e hash em Base64), para que o custo possa
 * subir sem invalidar as senhas ja gravadas.
 */
public final class SenhaHasher {

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final String PREFIXO = "pbkdf2-sha256";
    static final int ITERACOES = 600_000;
    private static final int MAX_ITERACOES = 10_000_000;
    private static final int BYTES_SAL = 16;
    private static final int BITS_CHAVE = 256;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    /**
     * Valor de referencia para conta inexistente ou senha vazia: a conferencia gasta o mesmo tempo e falha, e assim o
     * tempo de resposta nao revela se o e-mail esta cadastrado.
     */
    private static final String HASH_FICTICIO = gerar(UUID.randomUUID().toString());

    private SenhaHasher() {
    }

    /** Gera o valor a armazenar. Nunca devolve nem registra a senha em claro. */
    public static String gerar(String senhaEmTextoPlano) {
        if (senhaEmTextoPlano == null || senhaEmTextoPlano.isEmpty()) {
            throw new IllegalArgumentException("Senha vazia nao pode ser cadastrada");
        }
        byte[] sal = new byte[BYTES_SAL];
        ALEATORIO.nextBytes(sal);
        byte[] chave = derivar(senhaEmTextoPlano, sal, ITERACOES);
        Base64.Encoder b64 = Base64.getEncoder().withoutPadding();
        return PREFIXO + "$" + ITERACOES + "$" + b64.encodeToString(sal) + "$" + b64.encodeToString(chave);
    }

    /**
     * Confere a senha com o valor armazenado, em tempo constante na comparacao. Valor ausente, mal formado ou senha
     * vazia sempre resultam em {@code false}, depois de gastar o custo de uma derivacao.
     */
    public static boolean confere(String senhaEmTextoPlano, String armazenado) {
        boolean entradaValida = senhaEmTextoPlano != null && !senhaEmTextoPlano.isEmpty() && armazenado != null;
        String[] partes = (entradaValida ? armazenado : HASH_FICTICIO).split("\\$");
        int iteracoes;
        byte[] sal;
        byte[] esperado;
        try {
            if (partes.length != 4 || !PREFIXO.equals(partes[0])) {
                return falhaComCusto();
            }
            iteracoes = Integer.parseInt(partes[1]);
            sal = Base64.getDecoder().decode(partes[2]);
            esperado = Base64.getDecoder().decode(partes[3]);
        } catch (IllegalArgumentException e) {
            return falhaComCusto();
        }
        if (iteracoes < 1 || iteracoes > MAX_ITERACOES || sal.length == 0 || esperado.length == 0) {
            return falhaComCusto();
        }
        String candidata = entradaValida ? senhaEmTextoPlano : "senha-ficticia";
        boolean iguais = MessageDigest.isEqual(derivar(candidata, sal, iteracoes), esperado);
        return entradaValida && iguais;
    }

    private static boolean falhaComCusto() {
        confere("senha-ficticia", HASH_FICTICIO);
        return false;
    }

    private static byte[] derivar(String senha, byte[] sal, int iteracoes) {
        PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), sal, iteracoes, BITS_CHAVE);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 indisponivel na JVM", e);
        } finally {
            spec.clearPassword();
        }
    }
}
