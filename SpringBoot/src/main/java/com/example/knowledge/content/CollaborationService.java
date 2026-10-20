package com.example.knowledge.content;

import com.example.knowledge.common.*;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.service.PermissionService;
import com.example.knowledge.notifications.NotificationPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class CollaborationService {
    private final JdbcTemplate jdbc;
    private final PermissionService permissions;
    private final ObjectMapper json;
    private final NotificationPublisher notifications;
    public CollaborationService(JdbcTemplate jdbc, PermissionService permissions, ObjectMapper json, NotificationPublisher notifications) {
        this.jdbc=jdbc; this.permissions=permissions; this.json=json; this.notifications=notifications;
    }
    public record Star(boolean starred, long starCount) {}
    public record Proposal(@NotNull Long contentId, @NotNull @Min(1) Integer baseVersion,
                           @NotBlank @Size(max=200) String title, @NotBlank @Size(max=20000) String body,
                           @NotBlank @Size(max=1000) String reason) {}
    public enum Status { OPEN, MERGED, CLOSED }
    public record Suggestion(long id,long contentId,int baseVersion,long authorId,String authorName,
                             String originalTitle,String originalBody,String title,String body,String reason,
                             String status,String reviewerName,String createdAt,String reviewedAt) {}
    public record Page(List<Suggestion> items,boolean hasMore) {}
    public record Revision(int version,ContentController.Entry content,String actorName,String contributorName,String action,String createdAt) {}
    private final RowMapper<Suggestion> suggestionRow=(rs,i)->new Suggestion(rs.getLong("id"),rs.getLong("content_id"),rs.getInt("base_version"),rs.getLong("author_id"),rs.getString("author_name"),rs.getString("original_title"),rs.getString("original_body"),rs.getString("title"),rs.getString("body"),rs.getString("reason"),rs.getString("status"),rs.getString("reviewer_name"),rs.getString("created_at"),rs.getString("reviewed_at"));
    private static final String SUGGESTIONS="SELECT s.*, COALESCE(a.nickname,a.username) author_name, COALESCE(r.nickname,r.username) reviewer_name FROM content_suggestion s LEFT JOIN sys_user a ON a.id=s.author_id LEFT JOIN sys_user r ON r.id=s.reviewer_id ";

    public Star star(long kb,JwtPrincipal user) {
        permissions.requireKnowledgeBaseAccess(kb,user,false);
        return new Star(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_star WHERE knowledge_base_id=? AND user_id=?",Long.class,kb,user.userId())>0,
                jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_star WHERE knowledge_base_id=?",Long.class,kb));
    }
    public Star addStar(long kb,JwtPrincipal user) {
        permissions.requireKnowledgeBaseAccess(kb,user,false);
        try { jdbc.update("INSERT INTO knowledge_star(knowledge_base_id,user_id) VALUES(?,?)",kb,user.userId()); }
        catch(DuplicateKeyException alreadyStarred) { /* Idempotent retry. */ }
        return star(kb,user);
    }
    public void removeStar(long kb,JwtPrincipal user) { jdbc.update("DELETE FROM knowledge_star WHERE knowledge_base_id=? AND user_id=?",kb,user.userId()); }
    public Page list(long kb,Status status,int offset,JwtPrincipal user) {
        permissions.requireKnowledgeBaseAccess(kb,user,false);
        if(offset<0 || offset>100000) throw new BusinessException(ErrorCode.BAD_REQUEST);
        var rows=jdbc.query(SUGGESTIONS+"WHERE s.knowledge_base_id=? AND s.status=? ORDER BY s.id DESC LIMIT 21 OFFSET ?",suggestionRow,kb,status.name(),offset);
        return new Page(rows.stream().limit(20).toList(),rows.size()>20);
    }
    private Suggestion find(long kb,long id) {
        return jdbc.query(SUGGESTIONS+"WHERE s.knowledge_base_id=? AND s.id=?",suggestionRow,kb,id).stream().findFirst().orElseThrow(()->new BusinessException(ErrorCode.NOT_FOUND));
    }
    // All content writers acquire the same row lock before incrementing the revision.
    public ContentController.Entry content(long kb,long id,boolean lock) {
        return jdbc.query("SELECT * FROM knowledge_content WHERE knowledge_base_id=? AND id=?"+(lock?" FOR UPDATE":""),ContentController.ROW,kb,id)
                .stream().findFirst().orElseThrow(()->new BusinessException(ErrorCode.NOT_FOUND));
    }
    @Transactional
    public Suggestion propose(long kb,Proposal proposal,JwtPrincipal user) {
        permissions.requireKnowledgeBaseAccess(kb,user,false);
        var current=content(kb,proposal.contentId(),true);
        if(current.version()!=proposal.baseVersion()) throw stale();
        if(current.title().equals(proposal.title().trim()) && current.body().equals(proposal.body()))
            throw new BusinessException(ErrorCode.BAD_REQUEST,"请修改标题或正文后再提交建议");
        baseline(kb,current);
        var key=new GeneratedKeyHolder();
        jdbc.update(c->{var p=c.prepareStatement("INSERT INTO content_suggestion(knowledge_base_id,content_id,base_version,author_id,original_title,original_body,title,body,reason) VALUES(?,?,?,?,?,?,?,?,?)",new String[]{"id"});
            p.setLong(1,kb);p.setLong(2,current.id());p.setInt(3,current.version());p.setLong(4,user.userId());p.setString(5,current.title());p.setString(6,current.body());p.setString(7,proposal.title().trim());p.setString(8,proposal.body());p.setString(9,proposal.reason().trim());return p;},key);
        return find(kb,key.getKey().longValue());
    }
    @Transactional
    public Suggestion merge(long kb,long id,JwtPrincipal user) {
        permissions.requireKnowledgeBaseManagement(kb,user);
        var suggestion=find(kb,id);
        var current=content(kb,suggestion.contentId(),true);
        if(!"OPEN".equals(lockSuggestion(kb,id))) throw new BusinessException(ErrorCode.CONFLICT,"这条建议已经处理");
        if(current.version()!=suggestion.baseVersion()) throw stale();
        baseline(kb,current);
        jdbc.update("UPDATE knowledge_content SET title=?,body=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE knowledge_base_id=? AND id=?",suggestion.title(),suggestion.body(),kb,current.id());
        jdbc.update("UPDATE content_suggestion SET status='MERGED',reviewer_id=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=?",user.userId(),id);
        record(kb,content(kb,current.id(),false),user.userId(),suggestion.authorId(),"MERGED");
        notifications.contentChanged(kb,suggestion.title());
        return find(kb,id);
    }
    @Transactional
    public Suggestion close(long kb,long id,JwtPrincipal user) {
        permissions.requireKnowledgeBaseAccess(kb,user,false);
        String latestStatus=lockSuggestion(kb,id);var suggestion=find(kb,id);
        if(suggestion.authorId()!=user.userId()) permissions.requireKnowledgeBaseManagement(kb,user);
        if(!"OPEN".equals(latestStatus)) throw new BusinessException(ErrorCode.CONFLICT,"这条建议已经处理");
        jdbc.update("UPDATE content_suggestion SET status='CLOSED',reviewer_id=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=?",user.userId(),id);
        return find(kb,id);
    }
    // A locking read sees the latest committed status even under MySQL REPEATABLE READ.
    // Proposal content and author are immutable; only the lifecycle status needs a current read.
    private String lockSuggestion(long kb,long id) {
        var rows=jdbc.queryForList("SELECT status FROM content_suggestion WHERE knowledge_base_id=? AND id=? FOR UPDATE",kb,id);
        if(rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND);
        return (String)rows.getFirst().get("status");
    }
    private BusinessException stale() { return new BusinessException(ErrorCode.CONFLICT,"原内容已有新版本，请重新查看最新内容并提交建议"); }
    public void baseline(long kb,ContentController.Entry entry) {
        if(jdbc.queryForList("SELECT id FROM content_revision WHERE content_id=? AND version=? FOR UPDATE",entry.id(),entry.version()).isEmpty())
            record(kb,entry,null,null,"BASELINE");
    }
    public void record(long kb,ContentController.Entry entry,Long actor,Long contributor,String action) {
        try { jdbc.update("INSERT INTO content_revision(knowledge_base_id,content_id,version,snapshot_json,actor_id,contributor_id,action) VALUES(?,?,?,?,?,?,?)",kb,entry.id(),entry.version(),json.writeValueAsString(entry),actor,contributor,action); }
        catch(JsonProcessingException e) { throw new IllegalStateException("Cannot serialize content revision",e); }
    }
    private ContentController.Entry decode(String snapshot) {
        try { return json.readValue(snapshot,ContentController.Entry.class); }
        catch(JsonProcessingException e) { throw new IllegalStateException("Cannot read content revision",e); }
    }
    public List<Revision> history(long kb,long id,JwtPrincipal user) {
        permissions.requireKnowledgeBaseAccess(kb,user,false);var current=content(kb,id,false);
        var revisions=jdbc.query("SELECT v.*,COALESCE(a.nickname,a.username) actor_name,COALESCE(c.nickname,c.username) contributor_name FROM content_revision v LEFT JOIN sys_user a ON a.id=v.actor_id LEFT JOIN sys_user c ON c.id=v.contributor_id WHERE v.knowledge_base_id=? AND v.content_id=? ORDER BY v.version DESC LIMIT 100",
                (rs,i)->new Revision(rs.getInt("version"),decode(rs.getString("snapshot_json")),rs.getString("actor_name"),rs.getString("contributor_name"),rs.getString("action"),rs.getString("created_at")),kb,id);
        // Existing entries predate revision tracking; display the current baseline without a GET mutation.
        return revisions.isEmpty()?List.of(new Revision(current.version(),current,null,null,"BASELINE",current.updatedAt())):revisions;
    }
    @Transactional
    public ContentController.Entry restore(long kb,long id,int version,JwtPrincipal user) {
        permissions.requireKnowledgeBaseManagement(kb,user);var current=content(kb,id,true);
        var snapshot=jdbc.query("SELECT snapshot_json FROM content_revision WHERE knowledge_base_id=? AND content_id=? AND version=?",(rs,i)->rs.getString(1),kb,id,version)
                .stream().findFirst().orElseThrow(()->new BusinessException(ErrorCode.NOT_FOUND));
        var old=decode(snapshot);
        if(old.sourceDocumentId()!=null && jdbc.queryForObject("SELECT COUNT(*) FROM document WHERE id=? AND knowledge_base_id=? AND status IN ('SUCCESS','ARCHIVED')",Long.class,old.sourceDocumentId(),kb)==0)
            throw new BusinessException(ErrorCode.CONFLICT,"该版本的来源文档已不可用，请手动编辑内容");
        baseline(kb,current);
        jdbc.update("UPDATE knowledge_content SET kind=?,title=?,body=?,aliases=?,source_document_id=?,sort_order=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE knowledge_base_id=? AND id=?",old.kind(),old.title(),old.body(),old.aliases(),old.sourceDocumentId(),old.sortOrder(),kb,id);
        var restored=content(kb,id,false);record(kb,restored,user.userId(),null,"RESTORED");notifications.contentChanged(kb,restored.title());return restored;
    }
}
