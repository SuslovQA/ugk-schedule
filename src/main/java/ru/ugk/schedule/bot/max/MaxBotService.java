package ru.ugk.schedule.bot.max;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ru.ugk.schedule.domain.*;
import ru.ugk.schedule.service.*;
import java.util.*;

@Service
public class MaxBotService {
    private final RestClient http=RestClient.create(); private final CatalogService catalog; private final UserPreferenceService prefs;
    private final String token; private final String botName; private Long marker;
    public MaxBotService(CatalogService catalog,UserPreferenceService prefs,@Value("${app.max.token:}") String token,@Value("${app.max.bot-name:}") String botName){this.catalog=catalog;this.prefs=prefs;this.token=token;this.botName=botName;}

    @Scheduled(fixedDelayString="${app.max.poll-delay-ms:2000}") public void poll(){
        if(token.isBlank()) return;
        try{
            String url="https://platform-api2.max.ru/updates?timeout=1&limit=100"+(marker==null?"":"&marker="+marker);
            JsonNode root=http.get().uri(url).header("Authorization",token).retrieve().body(JsonNode.class); if(root==null)return;
            if(root.hasNonNull("marker")) marker=root.path("marker").asLong(); for(JsonNode u:root.path("updates"))handle(u);
        }catch(Exception e){System.err.println("MAX polling: "+e.getMessage());}
    }
    private void handle(JsonNode u){
        String type=u.path("update_type").asText();
        if("message_created".equals(type)){
            JsonNode m=u.path("message"); String userId=firstText(m.path("sender").path("user_id"),u.path("user").path("user_id"));
            String text=m.path("body").path("text").asText(""); if(text.equalsIgnoreCase("/start")||text.equalsIgnoreCase("start")||text.equalsIgnoreCase("начать")) showCurrentOrLevels(userId);
        } else if("message_callback".equals(type)){
            String userId=firstText(u.path("user").path("user_id"),u.path("callback").path("user").path("user_id"));
            String payload=u.path("callback").path("payload").asText(); processCallback(userId,payload);
        }
    }
    private String firstText(JsonNode... nodes){for(JsonNode n:nodes)if(n!=null&&!n.isMissingNode()&&!n.isNull()&&!n.asText().isBlank())return n.asText();return "";}
    private void processCallback(String userId,String data){
        if(data.equals("RESET")){prefs.reset(MessengerType.MAX,userId);showLevels(userId);return;}
        if(data.startsWith("L:")){long id=Long.parseLong(data.substring(2));prefs.setLevel(MessengerType.MAX,userId,id);showCourses(userId,id);return;}
        if(data.startsWith("C:")){long id=Long.parseLong(data.substring(2));prefs.setCourse(MessengerType.MAX,userId,id);showGroups(userId,id);return;}
        if(data.startsWith("G:")){prefs.setGroup(MessengerType.MAX,userId,Long.parseLong(data.substring(2)));showMenu(userId);}
    }
    private void showCurrentOrLevels(String userId){var p=prefs.find(MessengerType.MAX,userId);if(p.isPresent()&&p.get().getGroup()!=null)showMenu(userId);else showLevels(userId);}
    private void showLevels(String userId){send(userId,"Выберите уровень образования",buttons(catalog.activeLevels().stream().map(x->new Btn(x.getName(),"L:"+x.getId())).toList()));}
    private void showCourses(String userId,Long levelId){send(userId,"Выберите курс",buttons(catalog.activeCourses(levelId).stream().map(x->new Btn(x.getName(),"C:"+x.getId())).toList()));}
    private void showGroups(String userId,Long courseId){send(userId,"Выберите группу / направление",buttons(catalog.activeGroups(courseId).stream().map(x->new Btn(x.getName(),"G:"+x.getId())).toList()));}
    private void showMenu(String userId){
        UserPreference p=prefs.find(MessengerType.MAX,userId).orElseThrow(); String deepLink="https://max.ru/"+botName+"?startapp=g"+p.getGroup().getId();
        List<List<Map<String,Object>>> rows=new ArrayList<>(); rows.add(List.of(Map.of("type","open_app","text","Показать расписание","web_app",deepLink))); rows.add(List.of(Map.of("type","callback","text","Сброс настроек","payload","RESET")));
        send(userId,"Настройки сохранены: "+p.getEducationLevel().getName()+", "+p.getCourse().getName()+", "+p.getGroup().getName(),rows);
    }
    private record Btn(String text,String data){}
    private List<List<Map<String,Object>>> buttons(List<Btn> bs){return bs.stream().map(b->List.<Map<String,Object>>of(Map.of("type","callback","text",b.text(),"payload",b.data()))).toList();}
    private void send(String userId,String text,List<List<Map<String,Object>>> rows){
        Map<String,Object> body=Map.of("text",text,"attachments",List.of(Map.of("type","inline_keyboard","payload",Map.of("buttons",rows))));
        http.post().uri("https://platform-api2.max.ru/messages?user_id="+userId).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
    }
}
