const level = document.getElementById('level'), course = document.getElementById('course'),
    group = document.getElementById('group');
const wrap = document.getElementById('scheduleWrap'), dialog = document.getElementById('entryDialog'),
    form = document.getElementById('entryForm');
const days = [['MONDAY', 'Понедельник'], ['TUESDAY', 'Вторник'], ['WEDNESDAY', 'Среда'], ['THURSDAY', 'Четверг'], ['FRIDAY', 'Пятница'], ['SATURDAY', 'Суббота']];
const lessonTimes = new Map([
    ['09:00', '09:45'], ['09:50', '10:35'], ['10:45', '11:30'], ['11:35', '12:20'],
    ['12:40', '13:25'], ['13:30', '14:15'], ['14:45', '15:30'], ['15:35', '16:20'],
    ['16:30', '17:15'], ['17:20', '18:05'], ['18:15', '19:00'], ['19:05', '19:50']
]);
const defaultTimes = [...lessonTimes.keys()];
let entries = [];

function formatTime(time) {
    const [hour, minute] = time.substring(0, 5).split(':');
    return `${Number(hour)}:${minute}`;
}

function timeLabel(time) {
    const end = lessonTimes.get(time);
    return formatTime(time) + (end ? ' - ' + formatTime(end) : '');
}

function defaultEndTime(time) {
    const index = defaultTimes.indexOf(time);
    if (index < 0) return '';
    const endSlot = index % 2 === 0 ? defaultTimes[index + 1] : time;
    return lessonTimes.get(endSlot) || lessonTimes.get(time) || '';
}

async function json(url, opts) {
    const r = await fetch(url, opts);
    if (!r.ok) throw new Error(await r.text());
    return r.status === 204 ? null : r.json();
}

level.onchange = async () => {
    course.innerHTML = '<option value="">Курс</option>';
    group.innerHTML = '<option value="">Группа / направление</option>';
    if (!level.value) return;
    for (const x of await json(`/api/public/levels/${level.value}/courses`)) course.add(new Option(x.name, x.id));
};
course.onchange = async () => {
    group.innerHTML = '<option value="">Группа / направление</option>';
    if (!course.value) return;
    for (const x of await json(`/api/public/courses/${course.value}/groups`)) group.add(new Option(x.name, x.id));
};
group.onchange = load;

async function load() {
    if (!group.value) return;
    entries = await json(`/api/admin/schedule?groupId=${group.value}`);
    render();
}

function render() {
    const allTimes = [...new Set([...defaultTimes, ...entries.map(e => e.startTime.substring(0, 5))])].sort();
    let h = '<table class="schedule-grid"><thead><tr><th>Время</th>' + days.map(d => `<th>${d[1]}</th>`).join('') + '</tr></thead><tbody>';
    for (const t of allTimes) {
        h += `<tr><th>${timeLabel(t)}</th>`;
        for (const [key] of days) {
            const list = entries.filter(e => e.dayOfWeek === key && e.startTime.startsWith(t));
            h += `<td class="schedule-cell" data-day="${key}" data-time="${t}">${list.map(e => `<button class="entry" data-id="${e.id}"><b>${esc(e.subject)}</b><span>${esc(e.room || '')} ${esc(e.teacherName || '')}</span></button>`).join('')}<button class="add-entry">＋</button></td>`;
        }
        h += '</tr>';
    }
    wrap.innerHTML = h + '</tbody></table>';
    wrap.querySelectorAll('.schedule-cell').forEach(td => td.addEventListener('click', e => {
        const b = e.target.closest('.entry');
        openEntry(td.dataset.day, td.dataset.time, b ? Number(b.dataset.id) : null);
    }));
}

function openEntry(day, time, id) {
    const e = id ? entries.find(x => x.id === id) : null;
    entryId.value = e?.id || '';
    entryDay.value = day;
    subject.value = e?.subject || '';
    startTime.value = e?.startTime?.substring(0, 5) || time;
    endTime.value = e ? (e.endTime?.substring(0, 5) || '') : defaultEndTime(time);
    room.value = e?.room || '';
    teacherName.value = e?.teacherName || '';
    note.value = e?.note || '';
    deleteBtn.style.visibility = id ? 'visible' : 'hidden';
    validateEndTime();
    dialog.showModal();
}

function validateEndTime() {
    const invalid = startTime.value && endTime.value && endTime.value < startTime.value;
    endTime.setCustomValidity(invalid ? 'Время окончания не должно быть раньше времени начала' : '');
}

startTime.addEventListener('input', validateEndTime);
endTime.addEventListener('input', validateEndTime);

form.onsubmit = async ev => {
    ev.preventDefault();
    validateEndTime();
    if (!form.reportValidity()) return;
    const payload = {
        groupId: Number(group.value),
        dayOfWeek: entryDay.value,
        startTime: startTime.value,
        endTime: endTime.value || null,
        subject: subject.value,
        room: room.value,
        teacherName: teacherName.value,
        note: note.value
    };
    const id = entryId.value;
    await json(id ? `/api/admin/schedule/${id}` : '/api/admin/schedule', {
        method: id ? 'PUT' : 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(payload)
    });
    dialog.close();
    load();
};
deleteBtn.onclick = async () => {
    if (entryId.value && confirm('Удалить запись?')) {
        await json(`/api/admin/schedule/${entryId.value}`, {method: 'DELETE'});
        dialog.close();
        load();
    }
};
cancelBtn.onclick = () => dialog.close();

function esc(s) {
    return String(s).replace(/[&<>"']/g, c => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    }[c]));
}
