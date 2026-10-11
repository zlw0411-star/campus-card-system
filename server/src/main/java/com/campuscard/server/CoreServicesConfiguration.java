package com.campuscard.server;
import com.campuscard.api.ApplicationServices;
import com.campuscard.dao.*;
import com.campuscard.service.ServiceFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class CoreServicesConfiguration {
 @Bean ApplicationServices applicationServices(UnitOfWork work,UserDao users,CardDao cards,TransactionDao transactions,StatisticsDao statistics) { return ServiceFactory.create(work,users,cards,transactions,statistics); }
}
