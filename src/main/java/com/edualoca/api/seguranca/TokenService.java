package com.edualoca.api.seguranca;

import com.edualoca.domain.usuario.Usuario;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TokenService {

    public String emitir(Usuario usuario) {
        return UUID.randomUUID().toString();
    }
}
