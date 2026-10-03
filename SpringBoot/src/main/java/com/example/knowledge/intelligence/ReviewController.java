package com.example.knowledge.intelligence;
import com.example.knowledge.common.*;
import com.example.knowledge.security.CurrentUser;
import com.example.knowledge.service.PermissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.List;
@RestController @RequestMapping("/api")
public class ReviewController {
 private final JdbcTemplate jdbc;private final IntelligenceService study;private final PermissionService permissions;
 public ReviewController(JdbcTemplate j,IntelligenceService s,PermissionService p){jdbc=j;study=s;permissions=p;}
 public enum Rating{AGAIN,GOOD,EASY}
 public record Request(@NotNull Rating rating){}
 public record Card(long id,long studyId,long knowledgeBaseId,String front,String back,int intervalDays,int repetitions,String dueAt){}
 private final org.springframework.jdbc.core.RowMapper<Card> row=(r,i)->new Card(r.getLong("id"),r.getLong("study_id"),r.getLong("knowledge_base_id"),r.getString("front"),r.getString("back"),r.getInt("interval_days"),r.getInt("repetitions"),r.getString("due_at"));
 @Transactional @PostMapping("/study/{id}/review-plan") public Result<Integer> plan(@PathVariable Long id){
  var user=CurrentUser.require();var source=study.study(id,user);
  jdbc.queryForList("SELECT id FROM study_session WHERE id=? AND user_id=? FOR UPDATE",id,user.userId());
  if(jdbc.queryForObject("SELECT COUNT(*) FROM review_card WHERE user_id=?",Integer.class,user.userId())>5000)throw new BusinessException(ErrorCode.CONFLICT,"复习卡片已达到 5000 张限制");
  int added=0;
  for(int i=0;i<source.cards().size();i++){
   if(jdbc.queryForObject("SELECT COUNT(*) FROM review_card WHERE study_id=? AND card_index=? AND user_id=?",Integer.class,id,i,user.userId())>0)continue;
   var c=source.cards().get(i);jdbc.update("INSERT INTO review_card(user_id,study_id,knowledge_base_id,card_index,front,back) VALUES (?,?,?,?,?,?)",user.userId(),id,source.knowledgeBaseId(),i,c.front(),c.back());added++;
  }
  return Result.success(added);
 }
 @GetMapping("/review") public Result<List<Card>> due(@RequestParam(defaultValue="false") boolean all){
  var user=CurrentUser.require();
  var cards=jdbc.query("SELECT * FROM review_card WHERE user_id=?"+(all?"":" AND due_at<=CURRENT_TIMESTAMP")+" ORDER BY due_at,id LIMIT 200",row,user.userId());
  return Result.success(cards.stream().filter(c->{try{permissions.requireKnowledgeBaseAccess(c.knowledgeBaseId(),user,false);return true;}catch(BusinessException e){return false;}}).toList());
 }
 @Transactional @PostMapping("/review/{id}") public Result<Card> review(@PathVariable long id,@Valid @RequestBody Request body){
  var user=CurrentUser.require();var cards=jdbc.query("SELECT * FROM review_card WHERE id=? AND user_id=? FOR UPDATE",row,id,user.userId());
  if(cards.isEmpty())throw new BusinessException(ErrorCode.NOT_FOUND);
  var card=cards.getFirst();permissions.requireKnowledgeBaseAccess(card.knowledgeBaseId(),user,false);
  var due=jdbc.queryForObject("SELECT due_at FROM review_card WHERE id=?",java.sql.Timestamp.class,id).toLocalDateTime();
  if(due.isAfter(LocalDateTime.now()))throw new BusinessException(ErrorCode.CONFLICT,"此卡片尚未到复习时间");
  int days=body.rating()==Rating.AGAIN?0:body.rating()==Rating.EASY?Math.min(60,Math.max(3,card.intervalDays()*3)):Math.min(30,card.intervalDays()==0?1:Math.max(3,card.intervalDays()*2));
  int repetitions=body.rating()==Rating.AGAIN?0:card.repetitions()+1;
  var next=days==0?LocalDateTime.now().plusMinutes(10):LocalDateTime.now().plusDays(days);
  jdbc.update("UPDATE review_card SET interval_days=?,repetitions=?,due_at=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",days,repetitions,next,id);
  return Result.success(jdbc.queryForObject("SELECT * FROM review_card WHERE id=?",row,id));
 }
}
