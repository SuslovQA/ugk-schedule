(async()=>{
 const out=document.getElementById('miniSchedule');const qs=new URLSearchParams(location.search);let groupId=qs.get('groupId');if(!groupId){const p=qs.get('WebAppStartParam');if(p&&/^g\d+$/.test(p))groupId=p.substring(1);}
 if(!groupId && window.WebApp?.initDataUnsafe?.start_param){const p=window.WebApp.initDataUnsafe.start_param;if(/^g\d+$/.test(p))groupId=p.substring(1);}
 if(!groupId){out.innerHTML='<div class="error">Не удалось определить группу. Откройте расписание из бота.</div>';return;}
 try{const r=await fetch(`/api/public/groups/${groupId}/schedule`);const data=await r.json();const names={MONDAY:'Понедельник',TUESDAY:'Вторник',WEDNESDAY:'Среда',THURSDAY:'Четверг',FRIDAY:'Пятница',SATURDAY:'Суббота',SUNDAY:'Воскресенье'};
  if(!data.length){out.innerHTML='<p>Расписание пока не заполнено.</p>';return;} let html='';for(const day of Object.keys(names)){const a=data.filter(x=>x.dayOfWeek===day);if(!a.length)continue;html+=`<section class="day-card"><h2>${names[day]}</h2>`+a.map(x=>`<article class="lesson"><time>${x.startTime.substring(0,5)}${x.endTime?'–'+x.endTime.substring(0,5):''}</time><div><b>${safe(x.subject)}</b><p>${safe(x.room||'')}${x.teacherName?' · '+safe(x.teacherName):''}</p></div></article>`).join('')+'</section>'; } out.innerHTML=html;
 }catch(e){out.innerHTML='<div class="error">Не удалось загрузить расписание.</div>';}
 function safe(s){return String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
})();
