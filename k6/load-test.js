import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_ID = __ENV.PRODUCT_ID || '1';

export const options = {
  scenarios: {
    burst: {
      executor: 'shared-iterations',
      vus: 100,
      iterations: 100,
      maxDuration: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<2000'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

const successCount = new Counter('reservation_success');
const outOfStockCount = new Counter('reservation_out_of_stock');

export default function () {
  const idempotencyKey = `k6-${__VU}-${__ITER}-${Date.now()}`;
  const payload = JSON.stringify({ productId: Number(PRODUCT_ID), userId: `k6-user-${__VU}`, qty: 1 });

  const res = http.post(`${BASE_URL}/reservations`, payload, {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
    },
  });

  check(res, {
    'status is 201 or 409': (r) => r.status === 201 || r.status === 409,
  });

  if (res.status === 201) successCount.add(1);
  if (res.status === 409) outOfStockCount.add(1);
}

export function handleSummary(data) {
  console.log(`\nSuccess: ${data.metrics.reservation_success ? data.metrics.reservation_success.values.count : 0}`);
  console.log(`Out of stock: ${data.metrics.reservation_out_of_stock ? data.metrics.reservation_out_of_stock.values.count : 0}`);
  console.log(`p95 latency: ${data.metrics.http_req_duration.values['p(95)']}ms`);
  console.log(`p99 latency: ${data.metrics.http_req_duration.values['p(99)']}ms`);
  return { stdout: '' }; // suppress default summary duplication
}