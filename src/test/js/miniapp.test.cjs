const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../../main/resources/static/js/miniapp.js'),'utf8');
async function run({search='',start='g4',ok=true,contentType='application/json',bridgeFails=false}={}){
 const output={},requests=[],scripts=[];
 const context={URLSearchParams,AbortSignal,setTimeout,clearTimeout,location:{search,hash:''},window:{},
  document:{getElementById:()=>output,createElement:()=>({remove(){}}),head:{appendChild(script){scripts.push(script.src);if(bridgeFails)script.onerror();else{context.window.WebApp={initDataUnsafe:{start_param:start}};script.onload();}}}},
  fetch:async url=>{requests.push(url);return {ok,status:ok?200:503,headers:{get:()=>contentType},json:async()=>[]};}};
 await vm.runInNewContext(source,context);
 return {output,requests,scripts};
}
test('Telegram group link loads schedule without MAX SDK',async()=>{
 const result=await run({search:'?groupId=4'});
 assert.deepEqual(result.requests,['/api/public/groups/4/schedule']);
 assert.equal(result.scripts.length,0);
 assert.match(result.output.innerHTML,/пока не заполнено/);
});
test('MAX loads bridge and uses its launch payload',async()=>{
 const result=await run();
 assert.deepEqual(result.scripts,['https://st.max.ru/js/max-web-app.js']);
 assert.deepEqual(result.requests,['/api/public/groups/4/schedule']);
});
test('API failure is displayed, not treated as an empty schedule',async()=>{
 const result=await run({search:'?groupId=4',ok:false});
 assert.match(result.output.textContent,/HTTP 503/);
});
test('HTML from proxy produces an explicit tunnel error',async()=>{
 const result=await run({search:'?groupId=4',contentType:'text/html'});
 assert.match(result.output.textContent,/ngrok/);
});
test('SDK failure produces a visible message',async()=>{
 const result=await run({bridgeFails:true});
 assert.match(result.output.textContent,/MAX Bridge/);
 assert.equal(result.requests.length,0);
});
