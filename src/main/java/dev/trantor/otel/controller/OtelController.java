package dev.trantor.otel.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ThreadLocalRandom;
import java.util.Locale;

@RestController
public class OtelController {

    private static final Logger log = LoggerFactory.getLogger(OtelController.class);

    @GetMapping("/")
    public String home() {
        log.info("Home endpoint called");
        return "Hello World!";
    }

    @GetMapping("/greet/{name}")
    public String greet(@PathVariable String name) {
        log.info("Greeting user: {}", name);
        simulateWork();
        return "Hello, " + name + "!";
    }

    @GetMapping("/slow")
    public String slow() throws InterruptedException {
        log.info("Starting slow operation");
        Thread.sleep(500);
        log.info("Slow operation completed");
        return "Done!";
    }

    @GetMapping("/unstable")
    public String unstable(@RequestParam(defaultValue = "25") int failPercent) throws InterruptedException {
        int boundedFailPercent = Math.max(0, Math.min(failPercent, 100));
        long delayMs = ThreadLocalRandom.current().nextLong(30, 1200);
        Thread.sleep(delayMs);

        int random = ThreadLocalRandom.current().nextInt(100);
        if (random < boundedFailPercent) {
            log.error("Unstable endpoint failed after {} ms (failPercent={})", delayMs, boundedFailPercent);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Synthetic demo failure");
        }

        log.info("Unstable endpoint succeeded in {} ms (failPercent={})", delayMs, boundedFailPercent);
        return "Unstable success in " + delayMs + " ms";
    }

    @GetMapping("/chatter/{count}")
    public String chatter(@PathVariable int count) {
        int safeCount = Math.max(1, Math.min(count, 200));
        for (int i = 1; i <= safeCount; i++) {
            log.info("Chatter log line {}/{}", i, safeCount);
        }
        return "Emitted " + safeCount + " log lines";
    }

    @GetMapping("/checkout/{tenant}")
    public String checkout(
            @PathVariable String tenant,
            @RequestParam(defaultValue = "0.15") double failRate,
            @RequestParam(defaultValue = "3") int items
    ) throws InterruptedException {
        double boundedFailRate = Math.max(0.0, Math.min(failRate, 1.0));
        int safeItems = Math.max(1, Math.min(items, 12));
        long latencyMs = ThreadLocalRandom.current().nextLong(40, 1400);
        Thread.sleep(latencyMs);

        String[] regions = {"us-east", "us-west", "eu-central", "ap-south"};
        String[] paymentMethods = {"card", "upi", "wallet", "bank"};
        String region = regions[ThreadLocalRandom.current().nextInt(regions.length)];
        String paymentMethod = paymentMethods[ThreadLocalRandom.current().nextInt(paymentMethods.length)];
        double amount = 9.99 + (ThreadLocalRandom.current().nextDouble() * (safeItems * 40.0));
        int random = ThreadLocalRandom.current().nextInt(10000);
        boolean failed = random < (boundedFailRate * 10000.0);
        String amountText = String.format(Locale.US, "%.2f", amount);

        if (failed) {
            log.error(
                    "event=checkout tenant={} region={} status=FAILED amount={} items={} latency_ms={} payment_method={} error_code=PAYMENT_DECLINED",
                    tenant, region, amountText, safeItems, latencyMs, paymentMethod
            );
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Payment declined");
        }

        log.info(
                "event=checkout tenant={} region={} status=SUCCESS amount={} items={} latency_ms={} payment_method={}",
                tenant, region, amountText, safeItems, latencyMs, paymentMethod
        );
        return "Checkout accepted for tenant " + tenant;
    }

    private void simulateWork() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

