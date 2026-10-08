const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
function page(meta) {
    let sent;
    const element = () => ({addEventListener(){}});
    const context = vm.createContext({
        document: {getElementById: element, querySelector: selector => meta ? {content: selector.includes('_csrf_header') ? 'X-CSRF-TOKEN' : 'masked-token'} : null},
        startTime: element(), endTime: element(), deleteBtn: element(), cancelBtn: element(),
        fetch: async (url, options) => {sent = options; return {ok:true,status:204};}
    });
    vm.runInContext(fs.readFileSync('src/main/resources/static/js/admin-schedule.js','utf8'),context);
    return {request: code => vm.runInContext(code,context), sent: () => sent};
}
test('admin POST and DELETE send masked CSRF token without losing JSON header', async () => {
    const p = page(true);
    await p.request("json('/api/admin/schedule', {method:'POST',headers:{'Content-Type':'application/json'},body:'{}'})");
    assert.equal(p.sent().headers['X-CSRF-TOKEN'],'masked-token');
    assert.equal(p.sent().headers['Content-Type'],'application/json');
    await p.request("json('/api/admin/schedule/1', {method:'DELETE'})");
    assert.equal(p.sent().headers['X-CSRF-TOKEN'],'masked-token');
});
test('missing CSRF metadata prevents mutation', async () => {
    const p = page(false);
    await assert.rejects(p.request("json('/api/admin/schedule/1', {method:'DELETE'})"));
    assert.equal(p.sent(),undefined);
});
