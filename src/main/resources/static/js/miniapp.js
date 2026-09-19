(async () => {
    const out = document.getElementById('miniSchedule');
    const qs = new URLSearchParams(location.search), fragment = new URLSearchParams(location.hash.substring(1));
    const fromStart = p => typeof p === 'string' && /^g[1-9]\d*$/.test(p) ? p.substring(1) : null;
    let groupId = qs.get('groupId');
    if (!/^[1-9]\d*$/.test(groupId || '')) groupId = null;
    groupId ||= fromStart(qs.get('WebAppStartParam')) || fromStart(fragment.get('WebAppStartParam'));
    // MAX can include start_param inside the URL-encoded WebAppData envelope.
    const fromInitData = value => fromStart(new URLSearchParams(value || '').get('start_param'));
    groupId ||= fromInitData(qs.get('WebAppData')) || fromInitData(fragment.get('WebAppData'));
    let maxInitData = qs.get('WebAppData') || fragment.get('WebAppData');
    const telegramInitData = window.Telegram?.WebApp?.initData
        || qs.get('tgWebAppData') || fragment.get('tgWebAppData');
    const isTelegram = Boolean(telegramInitData || window.Telegram?.WebApp
        || qs.has('tgWebAppPlatform') || fragment.has('tgWebAppPlatform'));
    groupId ||= fromStart(qs.get('tgWebAppStartParam')) || fromStart(fragment.get('tgWebAppStartParam'))
        || fromInitData(telegramInitData);
    if (!groupId && !maxInitData && !isTelegram) {
        try {
            if (!window.WebApp) await loadMaxBridge();
            groupId = fromStart(window.WebApp?.initDataUnsafe?.start_param)
                || fromInitData(window.WebApp?.initData);
            maxInitData = window.WebApp?.initData;
        } catch (e) {
            out.textContent = 'Не удалось загрузить MAX Bridge. Проверьте подключение и откройте мини-приложение заново.';
            return;
        }
    }
    const initData = isTelegram ? telegramInitData : maxInitData;
    const messenger = isTelegram ? 'telegram' : 'max';
    const messengerName = isTelegram ? 'Telegram' : 'MAX';
    if (!groupId && initData) {
        try {
            const response = await fetch(`/api/public/${messenger}/group`, {
                method: 'POST',
                headers: {'Content-Type': 'application/json', Accept: 'application/json'},
                body: JSON.stringify({initData}),
                signal: AbortSignal.timeout(15000)
            });
            if (response.status === 401) {
                out.textContent = `Сессия ${messengerName} истекла или не подтверждена. Закройте мини-приложение и откройте его заново.`;
                return;
            }
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const saved = await response.json();
            if (saved.groupId == null) {
                out.textContent = 'Сначала выберите группу в боте: отправьте /start и пройдите настройку. Затем откройте мини-приложение заново.';
                return;
            }
            if (!/^[1-9]\d*$/.test(String(saved.groupId))) throw new Error('Неверный формат группы');
            groupId = String(saved.groupId);
        } catch (e) {
            out.textContent = 'Не удалось получить выбранную группу. Попробуйте открыть мини-приложение заново.';
            return;
        }
    }
    if (!groupId) {
        out.innerHTML = '<div class="error">Не удалось определить группу. Откройте расписание из бота.</div>';
        return;
    }
    try {
        const r = await fetch(`/api/public/groups/${groupId}/schedule`, {
            headers: {Accept: 'application/json'},
            signal: AbortSignal.timeout(15000)
        });
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        if (!r.headers.get('content-type')?.includes('application/json')) throw new Error('Сервер вернул страницу вместо расписания. Проверьте туннель ngrok.');
        const data = await r.json();
        if (!Array.isArray(data)) throw new Error('Неверный формат расписания');
        const names = {
            MONDAY: 'Понедельник',
            TUESDAY: 'Вторник',
            WEDNESDAY: 'Среда',
            THURSDAY: 'Четверг',
            FRIDAY: 'Пятница',
            SATURDAY: 'Суббота',
            SUNDAY: 'Воскресенье'
        };
        if (!data.length) {
            out.innerHTML = '<p>Расписание пока не заполнено.</p>';
            return;
        }
        let html = '';
        for (const day of Object.keys(names)) {
            const a = data.filter(x => x.dayOfWeek === day);
            if (!a.length) continue;
            html += `<section class="day-card"><h2>${names[day]}</h2>` + a.map(x => `<article class="lesson"><time>${formatTime(x.startTime)}${x.endTime ? ' - ' + formatTime(x.endTime) : ''}</time><div><b>${safe(x.subject)}</b><p>${safe(x.room || '')}${x.teacherName ? ' · ' + safe(x.teacherName) : ''}</p></div></article>`).join('') + '</section>';
        }
        out.innerHTML = html;
    } catch (e) {
        out.textContent = 'Не удалось загрузить расписание. ' + (e.name === 'TimeoutError' ? 'Сервер не ответил за 15 секунд.' : e.message);
    }

    function loadMaxBridge() {
        return new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = 'https://st.max.ru/js/max-web-app.js';
            const timeout = setTimeout(() => {
                script.remove();
                reject(new Error('MAX Bridge timeout'));
            }, 5000);
            script.onload = () => {
                clearTimeout(timeout);
                resolve();
            };
            script.onerror = () => {
                clearTimeout(timeout);
                reject(new Error('MAX Bridge unavailable'));
            };
            document.head.appendChild(script);
        });
    }

    function formatTime(time) {
        const [hour, minute] = time.substring(0, 5).split(':');
        return `${Number(hour)}:${minute}`;
    }

    function safe(s) {
        return String(s).replace(/[&<>"']/g, c => ({
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#39;'
        }[c]));
    }
})();
