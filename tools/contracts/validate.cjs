const { Parser } = require('@asyncapi/parser');
const fs = require('fs');
(async () => {
  const root = '../../documentation/contracts/';
  const files = [
    root + 'asyncapi.json',
    ...fs
      .readdirSync(root + 'asyncapi')
      .filter((f) => f.endsWith('.json'))
      .map((f) => root + 'asyncapi/' + f),
  ];
  for (const file of files) {
    const { document, diagnostics } = await new Parser().parse(fs.readFileSync(file, 'utf8'));
    const errors = diagnostics.filter((d) => d.severity === 0);
    for (const d of errors) console.error(file, d.message, JSON.stringify(d.path));
    if (!document || errors.length) process.exit(1);
  }
  console.log(`${files.length} AsyncAPI contracts validated`);
})().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
