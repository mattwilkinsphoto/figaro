// Markdown parsing is delegated to maintained marked, not a custom Markdown parser.
const fs = require('node:fs');
const {marked} = require('marked');
process.stdout.write(JSON.stringify(marked.lexer(fs.readFileSync(process.argv[2], 'utf8'))));
