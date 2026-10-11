package com.campuscard;
import com.campuscard.api.ApplicationServices;
import com.campuscard.config.*;
import com.campuscard.dao.jdbc.*;
import com.campuscard.service.ServiceFactory;
public final class Bootstrap {
    private Bootstrap() {}
    public static ApplicationServices create() {
        var provider = new JdbcConnectionProvider(DatabaseConfig.load());
        return ServiceFactory.create(new JdbcUnitOfWork(provider), new JdbcUserDao(), new JdbcCardDao(), new JdbcTransactionDao(), new JdbcStatisticsDao());
    }
    public static String checkConnection() throws Exception {
        try (var c = new JdbcConnectionProvider(DatabaseConfig.load()).open(); var s = c.createStatement(); var r = s.executeQuery("SELECT VERSION()")) {
            r.next(); return r.getString(1);
        }
    }
}