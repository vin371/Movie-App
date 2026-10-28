'use strict';

const express = require('express');
const cors = require('cors');
const routes = require('./src/routes');
const { CONFIG } = require('./src/config');

const app = express();
app.disable('x-powered-by');
app.use(cors({ origin: true }));
const path = require('node:path');
app.get('/hls.min.js', (req, res) => res.sendFile(require.resolve('hls.js/dist/hls.min.js')));
app.get('/favicon.svg', (req, res) => res.sendFile(path.join(__dirname, 'nuvio-plugin/logo.svg')));
app.get('/download/VietStream.apk', (req, res) =>
  res.download(path.join(__dirname, 'downloads/VietStream-v1.3-debug.apk'), 'VietStream.apk'));
app.get('/download/VietStream-v1.2-debug.apk', (req, res) => res.redirect(302, '/download/VietStream.apk'));
app.get('/mobile-app.css', (req, res) => res.sendFile(path.join(__dirname, 'src/mobile-app.css')));
app.get('/mobile-app.js', (req, res) => res.sendFile(path.join(__dirname, 'src/mobile-app.js')));
app.get('/', (req, res) => res.redirect('/app'));
app.get(['/app', '/app/1'], (req, res) => res.sendFile(path.join(__dirname, 'src/mobile-app.html')));
app.use(routes);

// bắt lỗi rẻ nhánh
app.use((err, req, res, next) => {
  console.error('[server]', err.message);
  if (!res.headersSent) res.status(500).json({ err: 'internal error' });
});

app.listen(CONFIG.port, () => {
  console.log(`\n🎬 VietStream addon đang chạy.`);
  console.log(`   Manifest (dán vào Nuvio/Stremio): http://localhost:${CONFIG.port}/manifest.json`);
  console.log(`   Trang chủ: http://localhost:${CONFIG.port}/\n`);
});

