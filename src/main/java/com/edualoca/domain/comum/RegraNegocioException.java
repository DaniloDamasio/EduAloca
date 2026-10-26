package com.edualoca.domain.comum;

public class RegraNegocioException extends RuntimeException {

    private final int statusHttp;

    public RegraNegocioException(int statusHttp, String mensagem) {
        super(mensagem);
        this.statusHttp = statusHttp;
    }

    public int getStatusHttp() {
        return statusHttp;
    }
}
