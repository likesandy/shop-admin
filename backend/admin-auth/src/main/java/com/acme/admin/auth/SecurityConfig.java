package com.acme.admin.auth;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.IOException;import java.util.List;
@Configuration @EnableMethodSecurity
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
 @Bean SecurityFilterChain security(HttpSecurity http,AuthService auth) throws Exception {
 var filter=new OncePerRequestFilter(){@Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{String header=req.getHeader("Authorization");if(header!=null && header.startsWith("Bearer ")){var actor=auth.resolve(header.substring(7));if(actor!=null)SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,List.of()));}chain.doFilter(req,res);}};
 return http.csrf(x->x.disable()).sessionManagement(x->x.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
 .authorizeHttpRequests(x->x.requestMatchers("/api/auth/login","/actuator/health","/actuator/health/**","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll().anyRequest().authenticated())
 .exceptionHandling(x->x.authenticationEntryPoint((req,res,e)->error(res,401,"请先登录")).accessDeniedHandler((req,res,e)->error(res,403,"无权访问该接口")))
 .addFilterBefore(filter,UsernamePasswordAuthenticationFilter.class).build();
 }
 private static void error(HttpServletResponse r,int status,String message)throws IOException{r.setStatus(status);r.setContentType("application/json;charset=UTF-8");r.getWriter().write("{\"data\":null,\"message\":\""+message+"\"}");}
}
