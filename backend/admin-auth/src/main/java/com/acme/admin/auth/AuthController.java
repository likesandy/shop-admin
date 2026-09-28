package com.acme.admin.auth;
import com.acme.admin.common.*;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/auth")
public class AuthController {
 private final AuthService service;private final Access access;
 public AuthController(AuthService service,Access access){this.service=service;this.access=access;}
 public record Login(@NotBlank @Size(max=64) String username,@NotBlank @Size(max=72) String password){}
 public record Me(long id,String username,long deptId,java.util.Set<String> permissions){}
 @PostMapping("/login") public Api<AuthService.LoginResult> login(@Valid @RequestBody Login req){return Api.ok(service.login(req.username(),req.password()));}
 @GetMapping("/me") public Api<Me> me(){var a=access.actor();return Api.ok(new Me(a.id(),a.username(),a.deptId(),a.permissions()));}
 @PostMapping("/logout") @Audited("退出登录") public Api<Void> logout(){service.logout(access.actor());return Api.ok(null);}
}
