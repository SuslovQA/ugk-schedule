const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const code = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/miniapp.js'), 'utf8');

async function open({search = '', hash = '', bridge = {}, telegram, savedGroup = 42, groupStatus = 200}) {
    const out = {};
    const requests = [];
    await vm.runInNewContext(code, {
        location: {search, hash}, window: {WebApp: bridge, Telegram: telegram}, URLSearchParams, AbortSignal,
        document: {getElementById: () => out},
        fetch: async (url, options) => {
            requests.push(url);
            if (url === '/api/public/max/group' || url === '/api/public/telegram/group') {
                assert.equal(options.method, 'POST');
                assert.ok(JSON.parse(options.body).initData);
                return {ok: groupStatus === 200, status: groupStatus, json: async () => ({groupId: savedGroup})};
            }
            return {ok: true, headers: {get: () => 'application/json'}, json: async () => []};
        }
    });
    return {out, requests};
}

for (const [name, input] of Object.entries({
    'Telegram query': {search: '?groupId=42'},
    'Telegram start parameter': {hash: '#tgWebAppStartParam=g42'},
    'Telegram nested start parameter': {hash: '#tgWebAppData=' + encodeURIComponent('start_param=g42')},
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

test('built-in MAX button loads saved group without start_param', async () => {
    for (const input of [
        {bridge: {initData: 'auth_date=123&user=%7B%22id%22%3A7%7D&hash=test'}},
        {hash: '#WebAppData=' + encodeURIComponent('auth_date=123&user={"id":7}&hash=test')}
    ]) {
        const result = await open(input);
        assert.deepEqual(result.requests, ['/api/public/max/group', '/api/public/groups/42/schedule']);
    }
});

test('built-in button explains how to select a group', async () => {
    const result = await open({bridge: {initData: 'signed-data'}, savedGroup: null});
    assert.deepEqual(result.requests, ['/api/public/max/group']);
    assert.match(result.out.textContent, /Сначала выберите группу/);
});

test('invalid MAX session never loads schedule', async () => {
    const result = await open({bridge: {initData: 'invalid-data'}, groupStatus: 401});
    assert.deepEqual(result.requests, ['/api/public/max/group']);
    assert.match(result.out.textContent, /Сессия MAX/);
});

test('Telegram built-in menu loads Telegram preferences without loading MAX Bridge', async () => {
    for (const input of [
        {hash: '#tgWebAppData=' + encodeURIComponent('user={"id":7}&hash=test')},
        {search: '?tgWebAppData=' + encodeURIComponent('user={"id":7}&hash=test')},
        {telegram: {WebApp: {initData: 'user={"id":7}&hash=test'}}}
    ]) {
        const result = await open({...input, bridge: undefined});
        assert.deepEqual(result.requests, ['/api/public/telegram/group', '/api/public/groups/42/schedule']);
    }
});

test('Telegram missing selection and rejected session never load a schedule', async () => {
    for (const [extra, message] of [
        [{savedGroup: null}, /Сначала выберите группу/],
        [{groupStatus: 401}, /Сессия Telegram/],
        [{groupStatus: 500}, /Не удалось получить выбранную группу/]
    ]) {
        const result = await open({telegram: {WebApp: {initData: 'signed-data'}}, ...extra});
        assert.deepEqual(result.requests, ['/api/public/telegram/group']);
        assert.match(result.out.textContent, message);
    }
});

test('Telegram without initData does not attempt MAX authentication', async () => {
    const result = await open({hash: '#tgWebAppPlatform=android'});
    assert.deepEqual(result.requests, []);
    assert.match(result.out.innerHTML, /Не удалось определить группу/);
});
