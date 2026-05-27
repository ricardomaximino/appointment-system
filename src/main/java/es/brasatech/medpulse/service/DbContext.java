package es.brasatech.medpulse.service;

import es.brasatech.medpulse.MedpulseApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

public class DbContext {

    private static ApplicationContext context;

    public static synchronized ApplicationContext getContext() {
        if (context == null) {
            context = SpringApplication.run(MedpulseApplication.class);
        }
        return context;
    }
}
