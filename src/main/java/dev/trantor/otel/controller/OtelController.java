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

    private void simulateWork() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

