package com.acme.admin.system;
import com.acme.admin.auth.*;import com.acme.admin.common.*;import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.stereotype.Component;import java.util.*;
@Component
public class DataScope {
 private final JdbcClient db;private final Access access;
 public DataScope(JdbcClient db,Access access){this.db=db;this.access=access;}
 public record Filter(String sql,Map<String,Object> params){}
 public Set<Long> departments(){var a=access.actor();var result=new HashSet<Long>();if(a.scopes().contains("DEPT")||a.scopes().contains("DEPT_TREE"))result.add(a.deptId());
 if(a.scopes().contains("DEPT_TREE")){var edges=db.sql("select id,parent_id from sys_dept").query().listOfRows();boolean changed;do{changed=false;for(var e:edges){if(e.get("parent_id")!=null && result.contains(((Number)e.get("parent_id")).longValue()))changed|=result.add(((Number)e.get("id")).longValue());}}while(changed);}
 return result;}
 public boolean all(){return access.actor().admin()||access.actor().scopes().contains("ALL");}
 public Filter users(){if(all())return new Filter("1=1",Map.of());var depts=departments();var a=access.actor();var params=new HashMap<String,Object>();var terms=new ArrayList<String>();if(a.scopes().contains("SELF")){terms.add("u.id=:actorId");params.put("actorId",a.id());}if(!depts.isEmpty()){terms.add("u.dept_id in (:scopeDepts)");params.put("scopeDepts",depts);}return new Filter(terms.isEmpty()?"1=0":"("+String.join(" or ",terms)+")",params);}
 public void requireUser(long id){var f=users();long count=db.sql("select count(*) from sys_user u where u.id=:target and u.deleted=false and "+f.sql()).params(f.params()).param("target",id).query(Long.class).single();if(count==0)throw Problem.forbidden();}
 public void requireDept(long id){if(!all()&&!departments().contains(id))throw Problem.forbidden();if(db.sql("select count(*) from sys_dept where id=?").param(id).query(Long.class).single()==0)throw Problem.bad("部门不存在");}
}
