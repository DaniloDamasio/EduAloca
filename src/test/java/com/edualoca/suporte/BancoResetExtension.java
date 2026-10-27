package com.edualoca.suporte;

import com.edualoca.domain.comum.Banco;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class BancoResetExtension implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        Banco.resetParaTeste();
    }
}
