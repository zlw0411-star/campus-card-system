package com.campuscard.server.persistence;

import com.campuscard.config.DatabaseConfig;
import com.campuscard.config.JdbcUnitOfWork;
import com.campuscard.dao.*;
import com.campuscard.dao.jdbc.JdbcCardDao;
import com.campuscard.dao.jdbc.JdbcStatisticsDao;
import com.campuscard.dao.jdbc.JdbcTransactionDao;
import com.campuscard.dao.jdbc.JdbcUserDao;
import com.campuscard.server.contract.DashboardReader;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PersistenceConfiguration {
    @Bean(destroyMethod = "close")
    HikariDataSource campusDataSource() {
        DatabaseConfig database = DatabaseConfig.load();
        HikariConfig pool = new HikariConfig();
        pool.setJdbcUrl(database.url());
        pool.setUsername(database.user());
        pool.setPassword(database.password());
        pool.setMaximumPoolSize(10);
        pool.setMinimumIdle(2);
        pool.setConnectionTimeout(5_000);
        pool.setInitializationFailTimeout(-1);
        pool.setPoolName("campus-card-pool");
        return new HikariDataSource(pool);
    }

    @Bean
    ConnectionProvider connectionProvider(HikariDataSource campusDataSource) {
        return new PooledConnectionProvider(campusDataSource);
    }

    @Bean UnitOfWork unitOfWork(ConnectionProvider provider) { return new JdbcUnitOfWork(provider); }
    @Bean UserDao userDao() { return new JdbcUserDao(); }
    @Bean CardDao cardDao() { return new JdbcCardDao(); }
    @Bean TransactionDao transactionDao() { return new JdbcTransactionDao(); }
    @Bean StatisticsDao statisticsDao() { return new JdbcStatisticsDao(); }
    @Bean DashboardReader dashboardReader() { return new JdbcDashboardReader(); }
}
