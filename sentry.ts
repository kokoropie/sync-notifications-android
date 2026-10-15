import * as Sentry from '@sentry/react-native';
import { SENTRY_DSN } from './sentry.dsn';

// DSN lấy từ sentry.dsn.ts (tự sinh từ env SENTRY_DSN, xem scripts/gen-sentry-config.js). Rỗng = tắt Sentry.
// DSN của app client vốn công khai (nằm trong APK). KHÔNG để SENTRY_AUTH_TOKEN trong code.

const SENSITIVE = /authorization|cookie|secret|token|password|key|x-device-id/i;

Sentry.init({
  dsn: SENTRY_DSN || undefined,
  enabled: !__DEV__ && !!SENTRY_DSN,
  sendDefaultPii: false,
  attachScreenshot: false,
  attachViewHierarchy: false,
  tracesSampleRate: 0,
  enableAutoPerformanceTracing: false,
  beforeSend(event) {
    if (event.request) {
      delete event.request.data;
      delete event.request.cookies;
      delete event.request.query_string;
      if (event.request.url) event.request.url = event.request.url.split('?')[0];
      for (const k of Object.keys(event.request.headers ?? {})) {
        if (SENSITIVE.test(k)) event.request.headers![k] = '[Filtered]';
      }
    }
    delete event.user;
    return event;
  },
  beforeBreadcrumb(b) {
    // console log và request URL có thể chứa nội dung thông báo / account key
    if (b.category === 'console') return null;
    if (b.data) {
      delete b.data.url;
      delete b.data.body;
    }
    return b;
  },
});

export default Sentry;
