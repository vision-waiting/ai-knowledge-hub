package com.example.knowledge.websource;
import com.example.knowledge.common.*;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.service.*;
import com.example.knowledge.vo.DocumentVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
@Service public class WebSourceService {
 private final JdbcTemplate jdbc;private final DocumentService documents;private final PermissionService permissions;
 public WebSourceService(JdbcTemplate j,DocumentService d,PermissionService p){jdbc=j;documents=d;permissions=p;}
 public record Source(long id,String url,String title,long documentId,String status,String checkedAt){}
 private final org.springframework.jdbc.core.RowMapper<Source> row=(r,i)->new Source(r.getLong("id"),r.getString("url"),r.getString("title"),r.getLong("document_id"),r.getString("status"),r.getString("checked_at"));
 public List<Source> list(Long kb){return jdbc.query("SELECT w.*,d.status FROM web_source w JOIN document d ON d.id=w.document_id WHERE w.knowledge_base_id=? ORDER BY w.id DESC LIMIT 100",row,kb);}
 public Source find(Long kb,long id){return jdbc.query("SELECT w.*,d.status FROM web_source w JOIN document d ON d.id=w.document_id WHERE w.knowledge_base_id=? AND w.id=?",row,kb,id).stream().findFirst().orElseThrow(()->new BusinessException(ErrorCode.NOT_FOUND));}
 @Transactional public Source save(Long kb,Long id,String url,PublicPageFetcher.Page page,JwtPrincipal user) throws IOException {
  permissions.requireKnowledgeBaseAccess(kb,user,true);jdbc.queryForList("SELECT id FROM knowledge_base WHERE id=? FOR UPDATE",kb);permissions.requireKnowledgeBaseAccess(kb,user,true);
  String text=page.title()+"\n来源："+page.url()+"\n\n"+page.text(),hash=hash(text);
  var existing=jdbc.queryForList("SELECT * FROM web_source WHERE knowledge_base_id=? AND url_hash=?",kb,hash(url));
  if(id!=null&&(existing.isEmpty()||((Number)existing.getFirst().get("id")).longValue()!=id))throw new BusinessException(ErrorCode.CONFLICT);
  DocumentVO document;
  if(!existing.isEmpty()){
   var old=existing.getFirst();id=((Number)old.get("id")).longValue();long docId=((Number)old.get("document_id")).longValue();var current=documents.get(docId,user);
   if(!Set.of("SUCCESS","FAILED").contains(current.status()))throw new BusinessException(ErrorCode.CONFLICT,"关联文档正在处理或已移入历史/回收站，请在文档页处理后再试");
   if(hash.equals(old.get("content_hash"))&&"SUCCESS".equals(current.status())){jdbc.update("UPDATE web_source SET checked_at=CURRENT_TIMESTAMP WHERE id=?",id);return find(kb,id);}
   if("FAILED".equals(current.status())){
    if(!hash.equals(old.get("content_hash")))throw new BusinessException(ErrorCode.CONFLICT,"上次导入失败，请先在文档页重新索引后再检查更新");
    document=documents.reindex(docId,user);
   }else document=documents.uploadVersion(docId,new TextFile(page.title(),text),user);
   jdbc.update("UPDATE web_source SET document_id=?,content_hash=?,title=?,checked_at=CURRENT_TIMESTAMP WHERE id=?",document.id(),hash,title(page.title()),id);
  }else{
   document=documents.upload(kb,new TextFile(page.title(),text),user);var key=new GeneratedKeyHolder();long docId=document.id();
   jdbc.update(c->{var s=c.prepareStatement("INSERT INTO web_source(knowledge_base_id,url,url_hash,title,document_id,content_hash) VALUES (?,?,?,?,?,?)",new String[]{"id"});s.setLong(1,kb);s.setString(2,url);s.setString(3,hash(url));s.setString(4,title(page.title()));s.setLong(5,docId);s.setString(6,hash);return s;},key);id=key.getKey().longValue();
  }
  return find(kb,id);
 }
 private static String title(String value){return value.substring(0,Math.min(180,value.length()));}
 private static String hash(String text){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private static class TextFile implements MultipartFile {
  private final byte[] bytes;private final String name;
  TextFile(String title,String text){bytes=text.getBytes(StandardCharsets.UTF_8);name=title(title).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_")+".txt";}
  public String getName(){return "file";}public String getOriginalFilename(){return name;}public String getContentType(){return "text/plain";}public boolean isEmpty(){return bytes.length==0;}public long getSize(){return bytes.length;}public byte[] getBytes(){return bytes.clone();}public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}public void transferTo(File file)throws IOException{Files.write(file.toPath(),bytes);}
 }
}
