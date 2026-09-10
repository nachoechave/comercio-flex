package com.comercioflex.tenant.application;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
class TenantPublicPathsTests {
 @ParameterizedTest @ValueSource(strings={"admin","api","superadmin","tiendas","stores","assets","actuator","favicon.ico","robots.txt","sitemap.xml","Radio","../radio","%2e%2e","-radio","radio-","radio--a"})
 void rejectsUnsafeOrReservedSlugs(String slug) { assertThat(TenantPublicPaths.validSlug(slug)).isFalse(); }
 @ParameterizedTest @ValueSource(strings={"/api/v1/stores/radio-a/settings","/admin","/superadmin","/tiendas","/assets/a.js","/main.js","/radio-a/admin","/radio-a/missing","/%2e%2e/programas"})
 void excludesNonRadioResources(String path) {assertThat(TenantPublicPaths.cleanPath(path)).isFalse();}
 @ParameterizedTest @ValueSource(strings={"atodoboca","radio-a","radio-b"})
 void buildsGenericPublicUrls(String slug) { assertThat(TenantPublicPaths.radio(slug)).isEqualTo("/"+slug); assertThat(TenantPublicPaths.cleanPath("/"+slug+"/mi-cuenta/pago-retorno")).isTrue(); }
}
