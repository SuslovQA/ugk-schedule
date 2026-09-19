const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const code = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/miniapp.js'), 'utf8');

async function open({search = '', hash = '', bridge = {}}) {
    const out = {};
    const requests = [];
    await vm.runInNewContext(code, {
        location: {search, hash}, window: {WebApp: bridge}, URLSearchParams, AbortSignal,
        document: {getElementById: () => out},
        fetch: async url => {
            requests.push(url);
            return {ok: true, headers: {get: () => 'application/json'}, json: async () => []};
        }
    });
    return {out, requests};
}

for (const [name, input] of Object.entries({
    'Telegram query': {search: '?groupId=42'},
    'MAX deep link query': {search: '?WebAppStartParam=g42'},
    'MAX deep link fragment': {hash: '#WebAppStartParam=g42'},
    'MAX nested fragment': {hash: '#WebAppData=' + encodeURIComponent('user={}&start_param=g42')},
    'MAX nested query': {search: '?WebAppData=' + encodeURIComponent('start_param=g42')},
    'MAX Bridge parsed data': {bridge: {initDataUnsafe: {start_param: 'g42'}}},
    'MAX Bridge raw data': {bridge: {initData: 'start_param=g42'}},
    'invalid query falls back to MAX': {search: '?groupId=bad&WebAppStartParam=g42'}
})) {
    test(name, async () => {
        const result = await open(input);
        assert.deepEqual(result.requests, ['/api/public/groups/42/schedule']);
        assert.match(result.out.innerHTML, /Расписание пока не заполнено/);
    });
}

test('missing or malformed group does not request a schedule', async () => {
    for (const start of ['', 'g0', 'g-1', 'g42/other', 'g<script>']) {
        const result = await open({bridge: {initDataUnsafe: {start_param: start}}});
        assert.deepEqual(result.requests, []);
        assert.match(result.out.innerHTML, /Не удалось определить группу/);
    }
});
