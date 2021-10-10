package com.clay.spring;

import io.prometheus.client.hotspot.DefaultExports;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Demo implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(Demo.class, args);
    }

    @Override
    public void run(String... strings) throws Exception {
        DefaultExports.initialize();
    }
}