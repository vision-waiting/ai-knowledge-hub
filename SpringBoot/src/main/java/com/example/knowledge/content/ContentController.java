package com.example.knowledge.content;

import com.example.knowledge.common.*;
import com.example.knowledge.security.CurrentUser;
import com.example.knowledge.service.PermissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.sql.Statement;
import java.util.List;

@RestController @RequestMapping("/api/knowledge-bases/{kb}/content")
public class ContentController {
    private final JdbcTemplate jdbc; private final PermissionService permissions;
    public ContentController(JdbcTemplate jdbc,PermissionService permissions){this.jdbc=jdbc;this.permissions=permissions;}
    public enum Kind { FAQ, TERM, READING }
    public record Request(@NotNull Kind kind,@NotBlank @Size(max=200) String title,@NotBlank @Size(max=20000) String body,
                          @Size(max=500) String aliases,Long sourceDocumentId,@Min(0) @Max(10000) Integer sortOrder){}
    public record Entry(long id,String kind,String title,String body,String aliases,Long sourceDocumentId,int sortOrder,String updatedAt){}
    public record Page(List<Entry> items,boolean canManage,boolean hasMore){}
    private final org.springframework.jdbc.core.RowMapper<Entry> row=(rs,i)->new Entry(rs.getLong("id"),rs.getString("kind"),rs.getString("title"),rs.getString("body"),rs.getString("aliases"),(Long)rs.getObject("source_document_id"),rs.getInt("sort_order"),rs.getString("updated_at"));
    @GetMapping public Result<Page> list(@PathVariable Long kb,@RequestParam(defaultValue="FAQ") Kind kind,@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="30") int limit){
        var user=CurrentUser.require();var base=permissions.requireKnowledgeBaseAccess(kb,user,false);
        if(offset<0||offset>100000||limit<1||limit>100)throw new BusinessException(ErrorCode.BAD_REQUEST);
        var rows=jdbc.query("SELECT * FROM knowledge_content WHERE knowledge_base_id=? AND kind=? ORDER BY sort_order,id LIMIT ? OFFSET ?",row,kb,kind.name(),limit+1,offset);
        return Result.success(new Page(rows.stream().limit(limit).toList(),permissions.canManage(base,user),rows.size()>limit));
    }
    private void source(Long kb,Request body){
        if(body.sourceDocumentId()!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM document WHERE id=? AND knowledge_base_id=? AND status IN ('SUCCESS','ARCHIVED')",Integer.class,body.sourceDocumentId(),kb)==0)
            throw new BusinessException(ErrorCode.BAD_REQUEST,"来源必须是当前知识库已完成的文档");
    }
    @Transactional @PostMapping public Result<Entry> create(@PathVariable Long kb,@Valid @RequestBody Request body){
        permissions.requireKnowledgeBaseManagement(kb,CurrentUser.require());source(kb,body);
        var key=new GeneratedKeyHolder();
        jdbc.update(connection->{var p=connection.prepareStatement("INSERT INTO knowledge_content(knowledge_base_id,kind,title,body,aliases,source_document_id,sort_order) VALUES (?,?,?,?,?,?,?)",new String[]{"id"});
            p.setLong(1,kb);p.setString(2,body.kind().name());p.setString(3,body.title().trim());p.setString(4,body.body());p.setString(5,body.aliases());p.setObject(6,body.sourceDocumentId());p.setInt(7,body.sortOrder()==null?0:body.sortOrder());return p;},key);
        return Result.success(find(kb,key.getKey().longValue()));
    }
    @Transactional @PutMapping("/{id}") public Result<Entry> update(@PathVariable Long kb,@PathVariable long id,@Valid @RequestBody Request body){
        permissions.requireKnowledgeBaseManagement(kb,CurrentUser.require());find(kb,id);source(kb,body);
        jdbc.update("UPDATE knowledge_content SET kind=?,title=?,body=?,aliases=?,source_document_id=?,sort_order=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND knowledge_base_id=?",body.kind().name(),body.title().trim(),body.body(),body.aliases(),body.sourceDocumentId(),body.sortOrder()==null?0:body.sortOrder(),id,kb);
        return Result.success(find(kb,id));
    }
    @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable Long kb,@PathVariable long id){
        permissions.requireKnowledgeBaseManagement(kb,CurrentUser.require());find(kb,id);jdbc.update("DELETE FROM knowledge_content WHERE id=? AND knowledge_base_id=?",id,kb);return Result.success(null);
    }
    private Entry find(Long kb,long id){return jdbc.query("SELECT * FROM knowledge_content WHERE knowledge_base_id=? AND id=?",row,kb,id).stream().findFirst().orElseThrow(()->new BusinessException(ErrorCode.NOT_FOUND));}
}
