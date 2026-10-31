const keys=['bold','italic','underline','color','size'];
export function cleanStyle(value={}) {
 const out={};for(const key of ['bold','italic','underline'])if(value[key]===true)out[key]=true;
 if(/^#[0-9a-f]{6}$/i.test(value.color||''))out.color=value.color;
 if(Number.isInteger(value.size)&&value.size>=12&&value.size<=32)out.size=value.size;
 return out;
}
const same=(a,b)=>keys.every(k=>a[k]===b[k]);
function merge(runs) {
 const out=[];for(const run of runs){const style=cleanStyle(run);if(run.end<=run.start||!Object.keys(style).length)continue;const last=out.at(-1);if(last&&last.end===run.start&&same(last,style))last.end=run.end;else out.push({start:run.start,end:run.end,...style});}return out;
}
export function validRuns(text,runs=[]) {
 let end=0;const out=[];for(const r of Array.isArray(runs)?runs:[]){if(!Number.isInteger(r.start)||!Number.isInteger(r.end)||r.start<end||r.end<=r.start||r.end>text.length)continue;out.push({...cleanStyle(r),start:r.start,end:r.end});end=r.end;}return merge(out);
}
export function applyStyle(text,runs,start,end,patch) {
 const current=validRuns(text,runs);if(start>=end||start<0||end>text.length)return current;
 const points=[...new Set([0,text.length,start,end,...current.flatMap(r=>[r.start,r.end])])].sort((a,b)=>a-b),out=[];
 for(let i=0;i<points.length-1;i++){const a=points[i],b=points[i+1];const style={...cleanStyle(current.find(r=>r.start<=a&&r.end>=b))};if(a>=start&&b<=end)Object.assign(style,patch);out.push({start:a,end:b,...cleanStyle(style)});}return merge(out);
}
export function selectionHas(text,runs,start,end,key) {
 if(start>=end)return false;let at=start;
 for(const r of validRuns(text,runs)){if(r.end<=at)continue;if(r.start>at||!r[key])return false;at=Math.min(end,r.end);if(at===end)return true;}return false;
}
export function remapStyles(before,after,runs,edit) {
 const current=validRuns(before,runs);let a=0,b=before.length;
 if(edit&&after.startsWith(before.slice(0,edit.start))&&after.endsWith(before.slice(edit.end))&&after.length>=before.length-(edit.end-edit.start)){a=edit.start;b=edit.end;}
 else {while(a<before.length&&a<after.length&&before[a]===after[a])a++;while(b>a&&after.length-(before.length-b)>a&&before[b-1]===after[after.length-1-(before.length-b)])b--;}
 const inserted=after.length-(before.length-(b-a)),delta=after.length-before.length,out=[];
 for(const r of current){if(r.start<a)out.push({...r,end:Math.min(r.end,a)});if(r.end>b)out.push({...r,start:Math.max(r.start,b)+delta,end:r.end+delta});}
 const inherited=current.find(r=>r.start<a&&r.end>=a)||current.find(r=>r.start===a);
 if(inserted>0&&inherited)out.push({...inherited,start:a,end:a+inserted});
 return merge(out.sort((x,y)=>x.start-y.start));
}
