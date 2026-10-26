package com.edualoca.domain.usuario;

import java.util.UUID;

public class Usuario {

    private final UUID id;
    private final String email;
    private final String senhaHash;
    private final Perfil perfil;

    public Usuario(UUID id, String email, String senhaHash, Perfil perfil) {
        this.id = id;
        this.email = email;
        this.senhaHash = senhaHash;
        this.perfil = perfil;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Perfil getPerfil() {
        return perfil;
    }
}
