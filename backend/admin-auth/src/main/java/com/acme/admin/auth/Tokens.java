package com.acme.admin.auth;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
@Component
public class Tokens {
 private final byte[] key;
 public Tokens(@Value("${app.jwt-secret}") String secret){key=secret.getBytes(StandardCharsets.UTF_8);if(key.length<32)throw new IllegalArgumentException("JWT secret requires 32+ bytes");}
 public String sign(long userId,String sid,Instant expiry){try{
 var jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),new JWTClaimsSet.Builder().subject(Long.toString(userId)).jwtID(sid).issuer("shop-admin").issueTime(new Date()).expirationTime(Date.from(expiry)).build());jwt.sign(new MACSigner(key));return jwt.serialize();
 }catch(Exception e){throw new IllegalStateException(e);}}
 public JWTClaimsSet verify(String token){try{var jwt=SignedJWT.parse(token);if(!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(key)))return null;var c=jwt.getJWTClaimsSet();return "shop-admin".equals(c.getIssuer()) && c.getExpirationTime()!=null && c.getExpirationTime().after(new Date()) ? c:null;}catch(Exception e){return null;}}
}
