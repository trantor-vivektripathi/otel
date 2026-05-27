import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const NAMES = ["alex", "sam", "john", "maria", "lee", "olivia", "nora", "vik"];

export const options = {
  scenarios: {
    warmup: {
      executor: "ramping-vus",
      startVUs: 1,
      stages: [
        { duration: "30s", target: 10 },
        { duration: "60s", target: 40 },
        { duration: "90s", target: 90 },
      ],
      gracefulRampDown: "10s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.30"],
    http_req_duration: ["p(95)<1500"],
  },
};

function randomName() {
  return NAMES[Math.floor(Math.random() * NAMES.length)];
}

export default function () {
  const selector = Math.random();
  let response;

  if (selector < 0.30) {
    response = http.get(`${BASE_URL}/`);
  } else if (selector < 0.65) {
    response = http.get(`${BASE_URL}/greet/${randomName()}`);
  } else if (selector < 0.82) {
    response = http.get(`${BASE_URL}/slow`);
  } else if (selector < 0.95) {
    response = http.get(`${BASE_URL}/unstable?failPercent=40`);
  } else {
    response = http.get(`${BASE_URL}/chatter/30`);
  }

  check(response, {
    "response under 2s": (r) => r.timings.duration < 2000,
    "status is expected": (r) => [200, 500].includes(r.status),
  });

  sleep(Math.random() * 0.3);
}
