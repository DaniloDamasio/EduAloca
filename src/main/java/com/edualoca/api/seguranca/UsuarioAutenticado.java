package com.edualoca.api.seguranca;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marca o parametro de controller que recebe o usuario resolvido a partir do token Bearer. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface UsuarioAutenticado {
}
