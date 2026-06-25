import { execSync } from 'child_process';
import { writeFileSync } from 'fs';
import { join } from 'path';

const repoRoot = process.cwd();
const { markdownToAdf } = await import(join(repoRoot, 'node_modules/markdown-to-adf/dist/index.js'));

const number = process.env.ISSUE_NUMBER;
const title = process.env.ISSUE_TITLE;
const url = process.env.ISSUE_URL;
const body = process.env.ISSUE_BODY || '';

const fullMarkdown = `GitHub Issue: ${url}\n\n${body}`;
const adf = markdownToAdf(fullMarkdown);

const payload = JSON.stringify({
  fields: {
    project: { key: 'JD' },
    summary: `[GitHub Issue #${number}] ${title}`,
    description: adf,
    issuetype: { name: 'Bug' },
    labels: ['github-sync'],
    customfield_10109: parseInt(number)
  }
});

writeFileSync('/tmp/jira-payload.json', payload);

const response = execSync(
  `curl -s -X POST "${process.env.JIRA_BASE_URL}/rest/api/3/issue" \
  -H "Authorization: Basic ${process.env.JIRA_AUTH_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @/tmp/jira-payload.json`
).toString();

console.log('Jira response:', response);

let key;
try {
  key = JSON.parse(response).key;
} catch(e) {
  console.error('Failed to parse Jira response:', response);
  process.exit(1);
}

if (!key) {
  console.error('No key returned. Full response:', response);
  process.exit(1);
}

execSync(`echo "JIRA_KEY=${key}" >> ${process.env.GITHUB_OUTPUT}`);
console.log(`Created Jira issue: ${key}`);