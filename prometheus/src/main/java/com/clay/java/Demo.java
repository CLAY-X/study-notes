package com.clay.java;

import com.sun.net.httpserver.HttpServer;
import io.github.mweirauch.micrometer.jvm.extras.ProcessMemoryMetrics;
import io.github.mweirauch.micrometer.jvm.extras.ProcessThreadMetrics;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.binder.jvm.*;
import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Random;

/**
 * @author claysunyu
 * @date 2021/05/27
 */
public class Demo {
    private static final Logger LOGGER = LoggerFactory.getLogger(Demo.class);

    public static void main(String[] args) throws InterruptedException {
        PrometheusMeterRegistry prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        prometheusRegistry.config().commonTags("application", "MYAPPNAME");

        new JvmGcMetrics().bindTo(prometheusRegistry);
        new JvmMemoryMetrics().bindTo(prometheusRegistry);
        new JvmThreadMetrics().bindTo(prometheusRegistry);
        new ClassLoaderMetrics().bindTo(prometheusRegistry);
        new DiskSpaceMetrics(new File(".")).bindTo(prometheusRegistry);
        new LogbackMetrics().bindTo(prometheusRegistry);
        new FileDescriptorMetrics().bindTo(prometheusRegistry);
        new ProcessorMetrics().bindTo(prometheusRegistry);
        new UptimeMetrics().bindTo(prometheusRegistry);

        new ProcessMemoryMetrics().bindTo(prometheusRegistry);
        new ProcessThreadMetrics().bindTo(prometheusRegistry);

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
            server.createContext("/prometheus", httpExchange -> {
                String response = prometheusRegistry.scrape(TextFormat.CONTENT_TYPE_OPENMETRICS_100);
                httpExchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = httpExchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            });

            new Thread(server::start).start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        DistributionSummary summary = prometheusRegistry.summary("http_server_requests_seconds", "status", "200", "hah", "xix");

        Random random = new Random(1);
        for (int i = 0; i < 100000*100000; i++) {
            long begintime = System.nanoTime();

            Thread.sleep(random.nextInt(5) * 100);

            long endtime = System.nanoTime();
            double costTime = (double) (endtime - begintime) / 1000000000;
            LOGGER.info(costTime + "");
            summary.record(costTime);
            if (i % 10 == 0) {
                System.gc();
            }
        }
    }
}

