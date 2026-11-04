const {Parser} = require('@asyncapi/parser');
const fs = require('fs');
(async()=>{const {document,diagnostics}=await new Parser().parse(fs.readFileSync('../../documentation/contracts/asyncapi.json','utf8'));
const errors=diagnostics.filter(d=>d.severity===0);for(const d of errors) console.error(d.message,JSON.stringify(d.path));if(!document || errors.length)process.exit(1);console.log('AsyncAPI validated');})();
