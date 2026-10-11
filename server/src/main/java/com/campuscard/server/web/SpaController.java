package com.campuscard.server.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {
    @GetMapping({"/", "/login", "/dashboard", "/wallet", "/transactions", "/users", "/statistics", "/settings"})
    public String application() { return "forward:/index.html"; }
}
