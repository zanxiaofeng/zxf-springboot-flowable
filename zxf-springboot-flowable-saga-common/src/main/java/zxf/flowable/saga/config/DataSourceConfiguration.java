package zxf.flowable.saga.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 双数据源配置（对应 Camunda 版同名类）。
 *
 * <p>与 Camunda 版的差异：Camunda 通过 BOM/starter 把引擎绑定到 {@code camundaBpmDataSource}；
 * Flowable 的 Spring Boot auto-config 默认使用 {@code @Primary} DataSource。因此这里将
 * <b>Flowable 引擎数据源设为 @Primary</b>（auto-config 自动绑定），业务数据源为次级，
 * 供 {@code businessJdbcTemplate} 与 {@code businessTransactionManager} 使用。
 *
 * <p>Flyway 默认也使用 @Primary 数据源；为避免把 TBL_ORDER 建到 Flowable 库，
 * 在 application.yml 中显式设置 {@code spring.flyway.url/user/password} 指向业务库。
 */
@Configuration
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
public class DataSourceConfiguration {

    // ==================== Flowable 引擎数据源（@Primary，供 Flowable auto-config 使用） ====================

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.flowable")
    public DataSourceProperties flowableEngineDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean("flowableEngineDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.flowable.hikari")
    public HikariDataSource flowableEngineDataSource() {
        return flowableEngineDataSourceProperties().initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Primary
    @Bean("flowableEngineTransactionManager")
    public PlatformTransactionManager flowableEngineTransactionManager() {
        return new DataSourceTransactionManager(flowableEngineDataSource());
    }

    // ==================== 业务数据源（次级，供 OrderService / NamedParameterJdbcTemplate 使用） ====================

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.business")
    public DataSourceProperties businessDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("businessDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.business.hikari")
    public HikariDataSource businessDataSource() {
        return businessDataSourceProperties().initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean("businessTransactionManager")
    public PlatformTransactionManager businessTransactionManager() {
        return new JdbcTransactionManager(businessDataSource());
    }

    @Bean("businessJdbcTemplate")
    public NamedParameterJdbcTemplate businessJdbcTemplate() {
        return new NamedParameterJdbcTemplate(businessDataSource());
    }
}
