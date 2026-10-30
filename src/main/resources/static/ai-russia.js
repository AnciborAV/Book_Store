// The dated analysis remains fully readable without JavaScript.
const checkedAt=Date.parse('2026-10-30T00:00:00Z');
if(Date.now()-checkedAt>90*24*60*60*1000)document.querySelector('#ru-stale-note').hidden=false;
