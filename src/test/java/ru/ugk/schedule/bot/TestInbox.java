package ru.ugk.schedule.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory implementation of the persistence seam for HTTP adapter tests. */
public class TestInbox extends BotUpdateInbox {
    private Long cursor;
    private long sequence;
    private final Map<Long,Update> updates=new LinkedHashMap<>();
    public TestInbox() { super(null,new ObjectMapper()); }
    @Override public Long cursor(String stream) { return cursor; }
    @Override public void capture(String stream,Long cursor,JsonNode values) {
        this.cursor=cursor;
        for(JsonNode value:values) updates.put(++sequence,new Update(sequence,value));
    }
    @Override public List<Update> pending(String stream,int limit) { return updates.values().stream().limit(limit).toList(); }
    @Override public void complete(long id) { updates.remove(id); }
    @Override public void failed(long id,Exception failure) { updates.remove(id); }
}
