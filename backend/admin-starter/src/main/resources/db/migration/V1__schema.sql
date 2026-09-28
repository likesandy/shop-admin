CREATE TABLE sys_dept(id BIGINT AUTO_INCREMENT PRIMARY KEY,parent_id BIGINT NULL,name VARCHAR(64) NOT NULL,CONSTRAINT fk_dept_parent FOREIGN KEY(parent_id) REFERENCES sys_dept(id));
CREATE TABLE sys_user(id BIGINT AUTO_INCREMENT PRIMARY KEY,username VARCHAR(64) NOT NULL UNIQUE,password VARCHAR(100) NOT NULL,display_name VARCHAR(64) NOT NULL,dept_id BIGINT NOT NULL,enabled BOOLEAN NOT NULL DEFAULT TRUE,deleted BOOLEAN NOT NULL DEFAULT FALSE,failed_logins INT NOT NULL DEFAULT 0,locked_until TIMESTAMP NULL,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,FOREIGN KEY(dept_id) REFERENCES sys_dept(id));
CREATE INDEX idx_user_dept ON sys_user(dept_id,deleted);
CREATE TABLE sys_role(id BIGINT AUTO_INCREMENT PRIMARY KEY,code VARCHAR(64) NOT NULL UNIQUE,name VARCHAR(64) NOT NULL,data_scope VARCHAR(16) NOT NULL,enabled BOOLEAN NOT NULL DEFAULT TRUE);
CREATE TABLE sys_perm(id BIGINT AUTO_INCREMENT PRIMARY KEY,code VARCHAR(128) NOT NULL UNIQUE,name VARCHAR(64) NOT NULL,type VARCHAR(16) NOT NULL,path VARCHAR(128),parent_id BIGINT NULL,FOREIGN KEY(parent_id) REFERENCES sys_perm(id));
CREATE TABLE sys_user_role(user_id BIGINT NOT NULL,role_id BIGINT NOT NULL,PRIMARY KEY(user_id,role_id),FOREIGN KEY(user_id) REFERENCES sys_user(id),FOREIGN KEY(role_id) REFERENCES sys_role(id));
CREATE TABLE sys_role_perm(role_id BIGINT NOT NULL,perm_id BIGINT NOT NULL,PRIMARY KEY(role_id,perm_id),FOREIGN KEY(role_id) REFERENCES sys_role(id),FOREIGN KEY(perm_id) REFERENCES sys_perm(id));
CREATE TABLE auth_session(id VARCHAR(36) PRIMARY KEY,user_id BIGINT NOT NULL,expires_at TIMESTAMP NOT NULL,FOREIGN KEY(user_id) REFERENCES sys_user(id));
CREATE INDEX idx_session_expiry ON auth_session(expires_at);
CREATE TABLE sys_dict(id BIGINT AUTO_INCREMENT PRIMARY KEY,type VARCHAR(64) NOT NULL,label VARCHAR(64) NOT NULL,value VARCHAR(64) NOT NULL,sort INT NOT NULL DEFAULT 0,enabled BOOLEAN NOT NULL DEFAULT TRUE,UNIQUE(type,value));
CREATE TABLE op_log(id BIGINT AUTO_INCREMENT PRIMARY KEY,username VARCHAR(64) NOT NULL,action VARCHAR(128) NOT NULL,method VARCHAR(255) NOT NULL,outcome VARCHAR(16) NOT NULL,duration_ms BIGINT NOT NULL,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE INDEX idx_log_time ON op_log(created_at);
CREATE TABLE cache_invalidation(id BIGINT AUTO_INCREMENT PRIMARY KEY,dict_type VARCHAR(64) NOT NULL,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
INSERT INTO sys_dept(id,parent_id,name) VALUES(1,NULL,'总部'),(2,1,'产品研发部'),(3,2,'应用开发组'),(4,1,'财务部');
INSERT INTO sys_role(id,code,name,data_scope) VALUES(1,'admin','超级管理员','ALL'),(2,'manager','部门主管','DEPT_TREE'),(3,'viewer','普通员工','SELF');
INSERT INTO sys_perm(id,code,name,type,path) VALUES
(1,'*','超级管理权限','API',NULL),
(10,'menu:users','用户管理','MENU','/users'),(11,'menu:roles','角色管理','MENU','/roles'),(12,'menu:departments','组织架构','MENU','/departments'),(13,'menu:permissions','菜单权限','MENU','/permissions'),(14,'menu:dictionaries','数据字典','MENU','/dictionaries'),(15,'menu:audit','操作审计','MENU','/audit-logs'),
(20,'user:read','查看用户','API',NULL),(21,'user:write','管理用户','BUTTON',NULL),(22,'dict:read','查看字典','API',NULL),(23,'dict:write','管理字典','BUTTON',NULL),(24,'audit:read','查看操作日志','API',NULL);
INSERT INTO sys_role_perm(role_id,perm_id) VALUES(1,1),(2,10),(2,12),(2,14),(2,20),(2,21),(2,22),(3,10),(3,20),(3,14),(3,22);
-- Reserve built-in permission IDs below 1000, including future migrations.
INSERT INTO sys_perm(id,code,name,type) VALUES(999,'reserved','系统保留','API');
DELETE FROM sys_perm WHERE id=999;
INSERT INTO sys_dict(type,label,value,sort) VALUES('user_status','启用','enabled',1),('user_status','停用','disabled',2),('order_status','待处理','pending',1),('order_status','已完成','completed',2);
