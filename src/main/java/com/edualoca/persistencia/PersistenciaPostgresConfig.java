package com.edualoca.persistencia;

import com.edualoca.domain.reserva.HistoricoReservaRepositorio;
import com.edualoca.domain.reserva.ReservaRepositorio;
import com.edualoca.domain.reserva.Transacao;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.ZoneId;

/**
 * Liga a persistencia de reservas ao PostgreSQL quando {@code edualoca.persistencia.url} esta definido.
 * Sem essa propriedade a aplicacao segue com o repositorio em memoria. Credenciais vem de configuracao
 * externa (variaveis de ambiente / propriedades), nunca do codigo.
 */
@Configuration
@ConditionalOnProperty(prefix = "edualoca.persistencia", name = "url")
public class PersistenciaPostgresConfig {

    @Bean(destroyMethod = "close")
    public HikariDataSource dataSource(@Value("${edualoca.persistencia.url}") String url,
                                       @Value("${edualoca.persistencia.usuario}") String usuario,
                                       @Value("${edualoca.persistencia.senha}") String senha,
                                       @Value("${edualoca.persistencia.pool-maximo:10}") int poolMaximo) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(usuario);
        config.setPassword(senha);
        config.setMaximumPoolSize(poolMaximo);
        config.setPoolName("edualoca");
        return new HikariDataSource(config);
    }

    @Bean
    public DataSourceInitializer esquemaReservas(DataSource dataSource) {
        DataSourceInitializer inicializador = new DataSourceInitializer();
        inicializador.setDataSource(dataSource);
        inicializador.setDatabasePopulator(new ResourceDatabasePopulator(new ClassPathResource("db/postgres/schema.sql")));
        return inicializador;
    }

    @Bean
    public TransactionTemplate transacaoReservas(DataSource dataSource) {
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Bean
    public ReservaRepositorio reservaRepositorioJdbc(DataSource dataSource, DataSourceInitializer esquemaReservas,
                                                     TransactionTemplate transacaoReservas,
                                                     @Value("${edualoca.persistencia.fuso-horario:America/Sao_Paulo}") String fusoHorario) {
        return new ReservaRepositorioJdbc(new JdbcTemplate(dataSource), transacaoReservas, ZoneId.of(fusoHorario));
    }

    @Bean
    public HistoricoReservaRepositorio historicoReservaRepositorioJdbc(DataSource dataSource,
                                                                       DataSourceInitializer esquemaReservas) {
        return new HistoricoReservaRepositorioJdbc(new JdbcTemplate(dataSource));
    }

    /** Mesmo DataSource dos dois repositorios: reserva e historico entram na mesma transacao. */
    @Bean
    public Transacao transacaoJdbc(TransactionTemplate transacaoReservas) {
        return new TransacaoJdbc(transacaoReservas);
    }
}
