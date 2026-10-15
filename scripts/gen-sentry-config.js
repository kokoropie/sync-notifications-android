// Sinh sentry.dsn.ts (gitignore) từ env SENTRY_DSN hoặc file .sentry-dsn. Không có thì DSN rỗng = tắt Sentry.
// Chạy trước build release: npm run build:release (tự gọi qua prebuild:release) và trong CI.
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
let dsn = process.env.SENTRY_DSN || '';
const file = path.join(root, '.sentry-dsn');
if (!dsn && fs.existsSync(file)) dsn = fs.readFileSync(file, 'utf8').trim();

fs.writeFileSync(
  path.join(root, 'sentry.dsn.ts'),
  `// File tự sinh bởi scripts/gen-sentry-config.js, không sửa tay, không commit.\nexport const SENTRY_DSN = ${JSON.stringify(dsn)};\n`,
);
if (!dsn) console.warn('WARN: thiếu SENTRY_DSN / .sentry-dsn, Sentry sẽ tắt');
