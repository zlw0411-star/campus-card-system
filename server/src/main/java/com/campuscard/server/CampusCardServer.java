package com.campuscard.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class CampusCardServer {
    public static void main(String[] args) {
        SpringApplication.run(CampusCardServer.class, args);
    }
}
