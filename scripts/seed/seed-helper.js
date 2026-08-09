/**
 * seed-helper.js
 * Reads mock-applications.json, checks for existing records,
 * and POSTs new ones to JobTrackr's API.
 *
 * Called by seed-applications.sh — do not run directly unless
 * JWT_TOKEN and optionally API_BASE are set in your environment.
 */

const http  = require('http');
const https = require('https');
const fs    = require('fs');
const path  = require('path');

const API_BASE  = process.env.API_BASE  || 'http://localhost:8080';
const JWT_TOKEN = process.env.JWT_TOKEN || '';
const MOCK_FILE = path.join(__dirname, 'mock-applications.json');

if (!JWT_TOKEN) {
  console.error('ERROR: JWT_TOKEN is not set.');
  process.exit(1);
}

// ── HTTP helper ───────────────────────────────────────────────────────────────
function request(method, urlStr, body) {
  return new Promise((resolve, reject) => {
    const url     = new URL(urlStr);
    const lib     = url.protocol === 'https:' ? https : http;
    const payload = body ? JSON.stringify(body) : null;

    const options = {
      hostname: url.hostname,
      port:     url.port || (url.protocol === 'https:' ? 443 : 80),
      path:     url.pathname + url.search,
      method,
      headers: {
        'Authorization': `Bearer ${JWT_TOKEN}`,
        'Content-Type':  'application/json',
        ...(payload ? { 'Content-Length': Buffer.byteLength(payload) } : {}),
      },
    };

    const req = lib.request(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try { resolve({ status: res.statusCode, body: JSON.parse(data) }); }
        catch { resolve({ status: res.statusCode, body: data }); }
      });
    });

    req.on('error', reject);
    if (payload) req.write(payload);
    req.end();
  });
}

// ── Main ──────────────────────────────────────────────────────────────────────
async function main() {
  const records = JSON.parse(fs.readFileSync(MOCK_FILE, 'utf8'));

  // Fetch existing applications for duplicate check
  console.log('Fetching existing applications for duplicate check ...');
  let existingKeys = new Set();

  try {
    const res = await request('GET', `${API_BASE}/api/applications?page=0&size=500`);
    if (res.status !== 200) {
      console.error(`ERROR: Could not fetch existing applications (HTTP ${res.status}).`);
      console.error('  Cannot verify duplicates - aborting to prevent duplicate records.');
      console.error('  If the server is cold-starting, wait a moment and try again.');
      process.exit(1);
    }
    const apps = res.body.content || res.body.data || res.body || [];
    apps.forEach(a => existingKeys.add(`${a.companyName}|||${a.role}`));
    console.log(`  Found ${existingKeys.size} existing record(s).\n`);
  } catch (err) {
    console.error(`ERROR: Could not reach server - ${err.message}`);
    console.error('  Cannot verify duplicates - aborting to prevent duplicate records.');
    console.error('  Make sure the server is running and try again.');
    process.exit(1);
  }

  console.log(`Seeding ${records.length} mock applications to ${API_BASE}/api/applications ...`);
  console.log('');

  let success = 0, failed = 0, skipped = 0;

  for (const record of records) {
    const label = `${record.companyName} — ${record.role}`;
    const key   = `${record.companyName}|||${record.role}`;

    if (existingKeys.has(key)) {
      console.log(`  [SKIP] ${label} (already exists)`);
      skipped++;
      continue;
    }

    try {
      const res = await request('POST', `${API_BASE}/api/applications`, record);
      if (res.status === 201) {
        console.log(`  [OK]   ${label}`);
        success++;
      } else {
        console.log(`  [FAIL] ${label} (HTTP ${res.status})`);
        console.log(`         ${JSON.stringify(res.body)}`);
        failed++;
      }
    } catch (err) {
      console.log(`  [ERR]  ${label} - ${err.message}`);
      failed++;
    }
  }

  console.log('');
  console.log(`Done. ${success} inserted, ${skipped} skipped (duplicates), ${failed} failed.`);
  if (failed > 0) process.exit(1);
}

main();
