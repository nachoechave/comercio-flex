package com.comercioflex;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.sql.DataSource;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.lifecycle.Startables;
import com.fasterxml.jackson.databind.*;
import com.comercioflex.tenant.application.TenantContext;
@Testcontainers @SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT) @AutoConfigureMockMvc
class RadioMembershipIntegrationTests {
 @Container static final MySQLContainer<?> CONTROL=new MySQLContainer<>("mysql:8.4.10");
 @Container static final MySQLContainer<?> A=new MySQLContainer<>("mysql:8.4.10");
 @Container static final MySQLContainer<?> B=new MySQLContainer<>("mysql:8.4.10");
 static { Startables.deepStart(Stream.of(CONTROL,A,B)).join(); }
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
  r.add("spring.datasource.url",CONTROL::getJdbcUrl);r.add("spring.datasource.username",CONTROL::getUsername);r.add("spring.datasource.password",CONTROL::getPassword);
  r.add("spring.flyway.user",CONTROL::getUsername);r.add("spring.flyway.password",CONTROL::getPassword);
  r.add("app.database.tenant-migration-enabled",()->"true");r.add("app.database.migration-username",A::getUsername);r.add("app.database.migration-password",A::getPassword);
  r.add("app.email.outbox-worker-enabled",()->"false");
  for(var entry:Map.of("tenant-a",A,"tenant-b",B,"tenant-shop",B).entrySet()) {String p="app.database.tenant-connections."+entry.getKey();var db=entry.getValue();r.add(p+".url",db::getJdbcUrl);r.add(p+".username",db::getUsername);r.add(p+".password",db::getPassword);}
 }
 @org.springframework.boot.test.web.server.LocalServerPort int port;
 @Autowired MockMvc mvc; @Autowired ObjectMapper json;
 @Autowired @Qualifier("controlJdbcTemplate") JdbcTemplate control;
 @Autowired @Qualifier("tenantJdbcTemplate") JdbcTemplate tenant;
 @Autowired TenantContext context;
 @MockitoBean(name="membershipClock") Clock clock;
 final AtomicReference<Instant> now=new AtomicReference<>();
 Session owner,admin,member,other,staff;
 static final String PASSWORD="radio-membership-password";
 static final String HASH="{bcrypt}"+new BCryptPasswordEncoder(4).encode(PASSWORD);
 @BeforeEach void setup() throws Exception {
  now.set(Instant.parse("2026-09-15T12:00:00Z"));when(clock.instant()).thenAnswer(i->now.get());when(clock.withZone(any())).thenAnswer(i->Clock.fixed(now.get(),i.getArgument(0)));
  control.update("DELETE FROM SPRING_SESSION");control.update("DELETE FROM memberships");control.update("DELETE FROM platform_users");control.update("DELETE FROM tenants");
  control.update("INSERT INTO tenants(public_id,slug,display_name,status,database_key,tenant_type) VALUES(UUID_TO_BIN(UUID()),'radio-a','Radio A','ACTIVE','tenant-a','RADIO'),(UUID_TO_BIN(UUID()),'radio-b','Radio B','ACTIVE','tenant-b','RADIO'),(UUID_TO_BIN(UUID()),'shop','Shop','ACTIVE','tenant-shop','ECOMMERCE')");
  for(String user:List.of("owner","admin","member","other","staff"))control.update("INSERT INTO platform_users(public_id,email_normalized,display_name,first_name,last_name,password_hash,status,platform_role) VALUES(UUID_TO_BIN(UUID()),?,?,?,'Prueba',?,'ACTIVE','USER')",user+"@example.com",user,user,HASH);
  for(String role:List.of("OWNER","ADMIN","STAFF"))control.update("INSERT INTO memberships(user_id,tenant_id,role,status) SELECT u.id,t.id,?,'ACTIVE' FROM platform_users u,tenants t WHERE u.email_normalized=? AND t.slug='radio-a'",role,role.toLowerCase()+"@example.com");
  for(String db:List.of("tenant-a","tenant-b"))try(var ignored=context.open(db)) {tenant.update("DELETE FROM membership_payments");tenant.update("DELETE FROM membership_payment_attempts");tenant.update("DELETE FROM membership_periods");tenant.update("DELETE FROM paid_memberships");tenant.update("DELETE FROM membership_plans");tenant.update("DELETE FROM radio_programs");tenant.update("DELETE FROM radio_team_members");tenant.update("DELETE FROM radio_sponsors");tenant.update("UPDATE radio_site_settings SET hero_title=NULL,hero_subtitle=NULL,description=NULL,youtube_url=NULL,instagram_url=NULL,x_url=NULL,whatsapp_url=NULL WHERE id=1");tenant.update("DELETE FROM store_settings");tenant.update("INSERT INTO store_settings(store_name,currency_code,timezone) VALUES('Radio','ARS','America/Argentina/Buenos_Aires')");}
  owner=login("owner");admin=login("admin");member=login("member");other=login("other");staff=login("staff");
 }
 @Test void publicPlansAreRadioOnlyActiveOrderedAndWithoutInternalIds() throws Exception {
  String first=plan("PLUS",6000,true,2),second=plan("BASICO",3000,true,1);plan("HIDDEN",500,false,0);
  mvc.perform(get(base("radio-a")+"/membership-plans")).andExpect(status().isOk()).andExpect(jsonPath("$[0].publicId").value(second)).andExpect(jsonPath("$[1].publicId").value(first)).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].id").doesNotExist());
  mvc.perform(get(base("shop")+"/membership-plans")).andExpect(status().isNotFound());
  mvc.perform(get(base("missing")+"/membership-plans")).andExpect(status().isNotFound());
  assertThat(context.currentDatabaseKey()).isEmpty();
 }
 @Test void administratorsCreateEditDisableAndValidatePlansButEndUsersCannot() throws Exception {
  String id=body(send(post(base("radio-a")+"/admin/membership-plans"),admin,planInput("PLUS",6000,true,0)).andExpect(status().isOk())).get("publicId").asText();
  send(put(base("radio-a")+"/admin/membership-plans/"+id),owner,planInput("ORO",8000,false,3)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("ORO"));
  for(Session denied:List.of(member,other,staff))send(post(base("radio-a")+"/admin/membership-plans"),denied,planInput("X",1,true,0)).andExpect(status().isForbidden());
  for(Map<String,Object> invalid:List.of(planInput(" ",1,true,0),planInput("X",-1,true,0)))send(post(base("radio-a")+"/admin/membership-plans"),owner,invalid).andExpect(status().isBadRequest());
  var input=planInput("X",1,true,0);input.put("currency","USD");send(post(base("radio-a")+"/admin/membership-plans"),owner,input).andExpect(status().isBadRequest());
 }
 @Test void enrollmentUsesSessionAndDatabasePriceAndRejectsMassAssignment() throws Exception {
  String id=plan("PLUS",6000,true,0);
  for(String field:List.of("platformUserId","userId","role","databaseKey","tenantId","price","currency","status","coverageStart")) {
   var forged=new HashMap<String,Object>();forged.put("planPublicId",id);forged.put(field,"forged");send(post(me()),member,forged).andExpect(status().isBadRequest());
  }
  send(post(me()),member,Map.of("planPublicId",id)).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("PENDING")).andExpect(jsonPath("$.currentPeriod.amount").value(6000)).andExpect(jsonPath("$.currentPeriod.currency").value("ARS")).andExpect(jsonPath("$.currentPeriod.coverageEndExclusive").value("2026-10-01")).andExpect(jsonPath("$.platformUserId").doesNotExist());
  assertThat(count("paid_memberships")).isEqualTo(1);assertThat(count("membership_periods")).isEqualTo(1);
  assertThat(control.queryForObject("SELECT COUNT(*) FROM memberships WHERE user_id=(SELECT id FROM platform_users WHERE email_normalized='member@example.com')",Integer.class)).isZero();
  assertThat(control.queryForObject("SELECT platform_role FROM platform_users WHERE email_normalized='member@example.com'",String.class)).isEqualTo("USER");
  send(post(base("radio-a")+"/admin/membership-plans"),member,planInput("FORBIDDEN",1,true,0)).andExpect(status().isForbidden());
 }
 @Test void duplicateAndConcurrentEnrollmentAndPeriodCreationAreIdempotent() throws Exception {
  String id=plan("PLUS",6000,true,0);
  concurrently(()->send(post(me()),member,Map.of("planPublicId",id)).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("PENDING")));
  assertThat(count("paid_memberships")).isEqualTo(1);assertThat(count("membership_periods")).isEqualTo(1);
  now.set(Instant.parse("2026-10-15T12:00:00Z"));
  concurrently(()->send(post(me()+"/current-period"),member,Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.currentPeriod.periodMonth").value(10)));
  assertThat(count("membership_periods")).isEqualTo(2);
 }
 @Test void snapshotsSurvivePriceAndNameChangesAndNextMonthUsesNewValues() throws Exception {
  String id=plan("PLUS",6000,true,0);join(id);
  send(put(base("radio-a")+"/admin/membership-plans/"+id),owner,planInput("SOCIO ORO",8000,true,0)).andExpect(status().isOk());
  mine(member).andExpect(jsonPath("$.currentPeriod.amount").value(6000)).andExpect(jsonPath("$.currentPeriod.planNameSnapshot").value("PLUS"));
  try(var ignored=context.open("tenant-a")){tenant.update("UPDATE store_settings SET currency_code='USD'");}
  var changedCurrency=planInput("SOCIO ORO",8000,true,0);changedCurrency.put("currency","USD");
  send(put(base("radio-a")+"/admin/membership-plans/"+id),owner,changedCurrency).andExpect(status().isOk());
  mine(member).andExpect(jsonPath("$.currentPeriod.currency").value("ARS"));
  now.set(Instant.parse("2026-10-01T04:00:00Z"));mine(member).andExpect(jsonPath("$.state").value("EXPIRED"));
  send(post(me()+"/current-period"),member,Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.currentPeriod.amount").value(8000)).andExpect(jsonPath("$.currentPeriod.planNameSnapshot").value("SOCIO ORO")).andExpect(jsonPath("$.currentPeriod.currency").value("USD"));
  mvc.perform(get(me()+"/periods").cookie(member.cookie())).andExpect(jsonPath("$[1].amount").value(6000)).andExpect(jsonPath("$[1].currency").value("ARS"));
 }
 @Test void changingPlanReplacesPendingSnapshotButNeverAccreditedHistory() throws Exception {
  String first=plan("PLUS",6000,true,0),second=plan("ORO",8000,true,0);join(first);
  send(put(me()+"/plan"),member,Map.of("planPublicId",second)).andExpect(status().isOk()).andExpect(jsonPath("$.currentPeriod.amount").value(8000));
  accredit();mine(member).andExpect(jsonPath("$.state").value("ACTIVE"));
  send(put(me()+"/plan"),member,Map.of("planPublicId",first)).andExpect(status().isOk()).andExpect(jsonPath("$.plan.name").value("PLUS")).andExpect(jsonPath("$.currentPeriod.planNameSnapshot").value("ORO"));
  now.set(Instant.parse("2026-10-10T12:00:00Z"));send(post(me()+"/current-period"),member,Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.currentPeriod.amount").value(6000));
 }
 @Test void disabledPlanCannotBeChosenAndRetainsHistory() throws Exception {
  String id=plan("PLUS",6000,true,0);join(id);
  send(put(base("radio-a")+"/admin/membership-plans/"+id),owner,planInput("PLUS",6000,false,0)).andExpect(status().isOk());
  send(post(me()),other,Map.of("planPublicId",id)).andExpect(status().isConflict());assertThat(count("paid_memberships")).isEqualTo(1);
  mine(member).andExpect(jsonPath("$.currentPeriod.amount").value(6000));
  now.set(Instant.parse("2026-10-10T12:00:00Z"));send(post(me()+"/current-period"),member,Map.of()).andExpect(status().isConflict());assertThat(count("membership_periods")).isEqualTo(1);
 }
 @Test void cancellationIsLogicalTerminalAndHasPrecedenceOverCoverage() throws Exception {
  String id=plan("PLUS",6000,true,0);join(id);accredit();
  send(post(me()+"/cancel"),member,Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CANCELLED"));
  send(post(me()+"/current-period"),member,Map.of()).andExpect(status().isConflict());send(post(me()),member,Map.of("planPublicId",id)).andExpect(status().isConflict());
  assertThat(count("membership_periods")).isEqualTo(1);assertThat(count("paid_memberships")).isEqualTo(1);
 }
 @Test void tenantAndOwnerIsolationApplyToEveryLookupAndAdminHistory() throws Exception {
  String id=plan("PLUS",6000,true,0);String membershipId=body(join(id)).get("publicId").asText();
  mine(other).andExpect(jsonPath("$.state").value("NONE"));
  mvc.perform(get(base("radio-b")+"/me/membership").cookie(member.cookie()).header("X-Database-Key","tenant-a")).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("NONE"));
  send(post(base("radio-b")+"/me/membership"),member,Map.of("planPublicId",id)).andExpect(status().isNotFound());
  mvc.perform(get(base("radio-b")+"/membership-plans")).andExpect(jsonPath("$.length()").value(0));
  for(String suffix:List.of("/admin/paid-memberships","/admin/paid-memberships/"+membershipId+"/periods","/admin/membership-plans"))mvc.perform(get(base("radio-a")+suffix).cookie(member.cookie())).andExpect(status().isForbidden());
  mvc.perform(get(base("radio-b")+"/admin/paid-memberships/"+membershipId+"/periods").cookie(owner.cookie())).andExpect(status().isForbidden());
  control.update("INSERT INTO memberships(user_id,tenant_id,role,status) SELECT u.id,t.id,'OWNER','ACTIVE' FROM platform_users u,tenants t WHERE u.email_normalized='owner@example.com' AND t.slug='radio-b'");
  mvc.perform(get(base("radio-b")+"/admin/paid-memberships/"+membershipId+"/periods").cookie(owner.cookie())).andExpect(status().isNotFound());
  mvc.perform(get(me()+"/"+membershipId).cookie(other.cookie())).andExpect(status().isNotFound());
  assertThat(context.currentDatabaseKey()).isEmpty();
 }
 @Test void adminListsIdentitiesAndFiltersDerivedStatesAndPlans() throws Exception {
  String id=plan("PLUS",6000,true,0);String membershipId=body(join(id)).get("publicId").asText();
  mvc.perform(get(base("radio-a")+"/admin/paid-memberships").param("state","PENDING").param("planPublicId",id).cookie(admin.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$[0].identity.email").value("member@example.com")).andExpect(jsonPath("$[0].membership.currentPeriod.amount").value(6000));
  mvc.perform(get(base("radio-a")+"/admin/paid-memberships").param("state","ACTIVE").cookie(owner.cookie())).andExpect(jsonPath("$.length()").value(0));
  accredit();mvc.perform(get(base("radio-a")+"/admin/paid-memberships").param("state","ACTIVE").cookie(owner.cookie())).andExpect(jsonPath("$.length()").value(1));
  mvc.perform(get(base("radio-a")+"/admin/paid-memberships/"+membershipId+"/periods").cookie(admin.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$[0].accreditationStatus").value("ACCREDITED"));
  now.set(Instant.parse("2026-10-15T12:00:00Z"));
  mvc.perform(get(base("radio-a")+"/admin/paid-memberships").param("state","EXPIRED").cookie(admin.cookie())).andExpect(jsonPath("$.length()").value(1));
  send(post(me()+"/cancel"),member,Map.of()).andExpect(status().isOk());
  mvc.perform(get(base("radio-a")+"/admin/paid-memberships").param("state","CANCELLED").cookie(admin.cookie())).andExpect(jsonPath("$.length()").value(1));
 }
 @ParameterizedTest @CsvSource({"2026-01-31T23:00:00Z,2026-01-01,2026-02-01","2026-02-28T12:00:00Z,2026-02-01,2026-03-01","2028-02-29T12:00:00Z,2028-02-01,2028-03-01","2026-03-31T12:00:00Z,2026-03-01,2026-04-01","2026-12-31T23:00:00Z,2026-12-01,2027-01-01","2027-01-01T02:59:59Z,2026-12-01,2027-01-01","2027-01-01T03:00:00Z,2027-01-01,2027-02-01"})
 void calendarCoverageUsesTenantTimezone(String instant,String start,String end) throws Exception {
  now.set(Instant.parse(instant));String id=plan("PLUS",6000,true,0);join(id).andExpect(jsonPath("$.currentPeriod.coverageStart").value(start)).andExpect(jsonPath("$.currentPeriod.coverageEndExclusive").value(end));
 }
 @Test void tenantTimezoneIsConfigurable() throws Exception {
  try(var ignored=context.open("tenant-a")){tenant.update("UPDATE store_settings SET timezone='UTC'");}
  now.set(Instant.parse("2027-01-01T01:00:00Z"));join(plan("PLUS",6000,true,0)).andExpect(jsonPath("$.currentPeriod.periodYear").value(2027)).andExpect(jsonPath("$.currentPeriod.periodMonth").value(1));
 }
 @Test void databaseConstraintsEnforceUniquenessAndValidCoverage() throws Exception {
  join(plan("PLUS",6000,true,0));
  try(var ignored=context.open("tenant-a")) {
   assertThatThrownBy(()->tenant.update("UPDATE membership_periods SET period_month=13")).hasRootCauseInstanceOf(java.sql.SQLException.class).rootCause().extracting("errorCode").isEqualTo(3819);
   assertThatThrownBy(()->tenant.update("UPDATE membership_periods SET amount_snapshot=-1")).hasRootCauseInstanceOf(java.sql.SQLException.class).rootCause().extracting("errorCode").isEqualTo(3819);
   assertThatThrownBy(()->tenant.update("UPDATE membership_periods SET coverage_end_exclusive=coverage_start")).hasRootCauseInstanceOf(java.sql.SQLException.class).rootCause().extracting("errorCode").isEqualTo(3819);
   assertThatThrownBy(()->tenant.update("INSERT INTO membership_periods(public_id,membership_id,period_year,period_month,coverage_start,coverage_end_exclusive,plan_id,plan_name_snapshot,amount_snapshot,currency_snapshot) SELECT UUID_TO_BIN(UUID()),membership_id,period_year,period_month,coverage_start,coverage_end_exclusive,plan_id,plan_name_snapshot,amount_snapshot,currency_snapshot FROM membership_periods")).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
  }
 }
 @Test void authenticationCsrfAndDisabledUserRemainEnforced() throws Exception {
  mvc.perform(get(me())).andExpect(status().isUnauthorized());
  for(String path:List.of(me(),me()+"/current-period",me()+"/cancel",base("radio-a")+"/admin/membership-plans"))mvc.perform(post(path).cookie(member.cookie()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
  control.update("UPDATE platform_users SET status='DISABLED' WHERE email_normalized='member@example.com'");mine(member).andExpect(status().isForbidden());
 }
 @Test void cleanRadioPathsAndLegacyRedirectsAreTenantScoped() throws Exception {
  for (String slug : List.of("radio-a", "radio-b")) for (String suffix : List.of("", "/programas", "/nosotros", "/socios", "/login", "/registro", "/mi-cuenta", "/mi-cuenta/pago-retorno")) {
   mvc.perform(get("/" + slug + suffix)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
  }
  mvc.perform(get("/tiendas/radio-a/ingresar?next=socios")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/radio-a/login?next=socios"));
  mvc.perform(get("/tiendas/shop")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
  for(String slug : List.of("shop", "missing")) mvc.perform(get("/" + slug)).andExpect(status().isNotFound());
  control.update("UPDATE tenants SET status='INACTIVE' WHERE slug='radio-a'");
  mvc.perform(get("/radio-a/programas")).andExpect(status().isNotFound());
  assertThat(context.currentDatabaseKey()).isEmpty();
 }

 @Test void membershipCheckoutNeverAcceptsClientPriceOrIdentityAndNeedsCsrf() throws Exception {
  mvc.perform(get(me()+"/current-period/payment").cookie(member.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
  mvc.perform(post(me()+"/current-period/checkout-pro").cookie(member.cookie()).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1,\"currency\":\"USD\",\"userId\":\"forged\"}"))
   .andExpect(status().isForbidden());
  send(post(me()+"/current-period/checkout-pro"),member,Map.of("amount",1,"currency","USD","userId","forged"))
   .andExpect(status().isBadRequest());
 }

 @Test void radioContentIsTenantScopedAndAdminOnly() throws Exception {
  mvc.perform(get(base("radio-a")+"/radio-site")).andExpect(status().isOk()).andExpect(jsonPath("$.programs.length()").value(0));
  send(put(base("radio-a")+"/admin/radio-site"),member,Map.of("heroTitle","No autorizado")).andExpect(status().isForbidden());
  send(put(base("radio-a")+"/admin/radio-site"),admin,Map.of("heroTitle","Radio A en vivo","heroSubtitle","Comunidad","description","La voz del barrio","youtubeUrl","https://youtube.com/radio-a")).andExpect(status().isOk()).andExpect(jsonPath("$.settings.heroTitle").value("Radio A en vivo"));
  send(post(base("radio-a")+"/admin/radio-site/programs"),admin,Map.of("name","Programa A","description","Historias","days","Lunes","schedule","18:00","active",true,"displayOrder",0)).andExpect(status().isOk());
  mvc.perform(get(base("radio-a")+"/radio-site")).andExpect(jsonPath("$.programs[0].name").value("Programa A"));
  mvc.perform(get(base("radio-b")+"/radio-site")).andExpect(jsonPath("$.settings.heroTitle").doesNotExist()).andExpect(jsonPath("$.programs.length()").value(0));
  mvc.perform(get(base("shop")+"/radio-site")).andExpect(status().isNotFound());
 }

 @Test @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="radio.browser",matches="true")
 void browserEndToEnd() throws Exception {
  ProcessBuilder process=new ProcessBuilder("node", "e2e/radio-membership.cjs", Integer.toString(port));
  process.directory(new java.io.File("../frontend"));
  java.io.File log=new java.io.File("target/radio-membership-browser.log");
  process.redirectErrorStream(true).redirectOutput(log);
  Process running=process.start();
  boolean finished=running.waitFor(120,TimeUnit.SECONDS);
  if(!finished)running.destroyForcibly();
  assertThat(finished).as("Browser E2E completes within two minutes").isTrue();
  String output=java.nio.file.Files.readString(log.toPath());
  System.out.println(output);
  assertThat(running.exitValue()).as(output).isZero();
 }
 private ResultActions join(String id) throws Exception { return send(post(me()),member,Map.of("planPublicId",id)).andExpect(status().isOk()); }
 private String plan(String name,int price,boolean active,int order) throws Exception { return body(send(post(base("radio-a")+"/admin/membership-plans"),owner,planInput(name,price,active,order)).andExpect(status().isOk())).get("publicId").asText(); }
 private Map<String,Object> planInput(String name,int price,boolean active,int order) { return new HashMap<>(Map.of("name",name,"description","Descripción","price",price,"currency","ARS","benefits",List.of("Comunidad"),"active",active,"displayOrder",order)); }
 private ResultActions mine(Session user) throws Exception { return mvc.perform(get(me()).cookie(user.cookie())); }
 private int count(String table) { try(var ignored=context.open("tenant-a")){return tenant.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);} }
 private void accredit() { try(var ignored=context.open("tenant-a")){tenant.update("UPDATE membership_periods SET accreditation_status='ACCREDITED',accredited_at=CURRENT_TIMESTAMP(6)");} }
 private void concurrently(Callable<ResultActions> action) throws Exception {try(var executor=Executors.newFixedThreadPool(2)){var gate=new CountDownLatch(1);Callable<ResultActions> task=()->{gate.await();return action.call();};var one=executor.submit(task);var two=executor.submit(task);gate.countDown();one.get(20,TimeUnit.SECONDS);two.get(20,TimeUnit.SECONDS);} }
 private JsonNode body(ResultActions action) throws Exception { return json.readTree(action.andReturn().getResponse().getContentAsString()); }
 private ResultActions send(MockHttpServletRequestBuilder request,Session session,Object body) throws Exception {return mvc.perform(request.cookie(session.cookie(),session.csrf()).header("X-XSRF-TOKEN",session.csrf().getValue()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));}
 private Session login(String user) throws Exception {Cookie csrf=mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");var response=mvc.perform(post("/api/v1/auth/login").cookie(csrf).header("X-XSRF-TOKEN",csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email",user+"@example.com","password",PASSWORD)))).andExpect(status().isOk()).andReturn().getResponse();return new Session(response.getCookie("CFSESSION"),response.getCookie("XSRF-TOKEN"));}
 private String base(String slug){return "/api/v1/stores/"+slug;}private String me(){return base("radio-a")+"/me/membership";}
 private record Session(Cookie cookie,Cookie csrf){}
}
