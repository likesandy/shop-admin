package com.acme.admin.auth;
import com.acme.admin.common.Problem;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
@Component("access")
public class Access {
 public Actor actor(){var a=SecurityContextHolder.getContext().getAuthentication();if(a==null || !(a.getPrincipal() instanceof Actor actor))throw Problem.forbidden();return actor;}
 public boolean has(String code){return actor().admin() || actor().permissions().contains(code);}
 public void require(String code){if(!has(code))throw Problem.forbidden();}
}
