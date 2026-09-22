package com.comercioflex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.sql.DataSource;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ProductManagementIntegrationTests {

	private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4.10");
	private static final String PASSWORD = "correct-horse-battery-staple";
	private static final String PASSWORD_HASH =
		"{bcrypt}" + new BCryptPasswordEncoder(4).encode(PASSWORD);
	private static final String CATEGORY_A = "11111111-1111-4111-8111-111111111111";
	private static final String CATEGORY_A_INACTIVE = "22222222-2222-4222-8222-222222222222";
	private static final String CATEGORY_B = "33333333-3333-4333-8333-333333333333";

	@Container
	static final MySQLContainer<?> CONTROL_DATABASE = new MySQLContainer<>(MYSQL_IMAGE);
	@Container
	static final MySQLContainer<?> TENANT_A_DATABASE = new MySQLContainer<>(MYSQL_IMAGE);
	@Container
	static final MySQLContainer<?> TENANT_B_DATABASE = new MySQLContainer<>(MYSQL_IMAGE);

	static {
		Startables.deepStart(Stream.of(
			CONTROL_DATABASE,
			TENANT_A_DATABASE,
			TENANT_B_DATABASE)).join();
	}

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private DataSource controlDataSource;
	@Autowired
	private ObjectMapper objectMapper;

	private JdbcTemplate control;

	@DynamicPropertySource
	static void configureDatabases(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", CONTROL_DATABASE::getJdbcUrl);
		registry.add("spring.datasource.username", CONTROL_DATABASE::getUsername);
		registry.add("spring.datasource.password", CONTROL_DATABASE::getPassword);
		registry.add("spring.flyway.user", CONTROL_DATABASE::getUsername);
		registry.add("spring.flyway.password", CONTROL_DATABASE::getPassword);
		registry.add("app.database.tenant-migration-enabled", () -> "true");
		registry.add("app.database.migration-username", TENANT_A_DATABASE::getUsername);
		registry.add("app.database.migration-password", TENANT_A_DATABASE::getPassword);
		registerTenant(registry, "tenant-a", TENANT_A_DATABASE);
		registerTenant(registry, "tenant-b", TENANT_B_DATABASE);
		registry.add("app.media.local-root", () -> "target/test-media-product-management");
	}

	@BeforeEach
	void seed() throws SQLException {
		control = new JdbcTemplate(controlDataSource);
		control.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
		control.update("DELETE FROM SPRING_SESSION");
		control.update("DELETE FROM memberships");
		control.update("DELETE FROM platform_users");
		control.update("DELETE FROM tenants");
		control.update("""
			INSERT INTO tenants (public_id, slug, display_name, status, database_key)
			VALUES
				(UNHEX(REPLACE(UUID(), '-', '')), 'tienda-a', 'Tienda A', 'ACTIVE', 'tenant-a'),
				(UNHEX(REPLACE(UUID(), '-', '')), 'tienda-b', 'Tienda B', 'ACTIVE', 'tenant-b')
			""");
		insertUser("owner@example.com", "OWNER", true);
		insertUser("admin@example.com", "ADMIN", false);
		insertUser("staff@example.com", "STAFF", false);

		resetTenant(TENANT_A_DATABASE);
		resetTenant(TENANT_B_DATABASE);
		insertCategory(TENANT_A_DATABASE, CATEGORY_A, "Remeras", "remeras", "ACTIVE");
		insertCategory(
			TENANT_A_DATABASE,
			CATEGORY_A_INACTIVE,
			"Archivada",
			"archivada",
			"INACTIVE");
		insertCategory(TENANT_B_DATABASE, CATEGORY_B, "Remeras", "remeras", "ACTIVE");
	}


	@Test
	void gallerySupportsEmptySingleMultipleSixAndRejectsOverflowAtomically() throws Exception {
		Auth owner = login("owner@example.com");
		JsonNode product = createProduct("tienda-a", CATEGORY_A, "Galería", "GALLERY", "10", owner);
		String base = products("tienda-a") + "/" + product.get("id").asText();
		mockMvc.perform(auth(get(base), owner)).andExpect(jsonPath("$.images.length()").value(0));
		JsonNode one = uploadGallery(base, owner, 1, 200);
		assertThat(one.size()).isEqualTo(1);
		assertThat(one.get(0).get("primary").asBoolean()).isTrue();
		assertThat(uploadGallery(base, owner, 4, 200).size()).isEqualTo(5);
		uploadGallery(base, owner, 2, 400);
		mockMvc.perform(auth(get(base), owner)).andExpect(jsonPath("$.images.length()").value(5));
		assertThat(uploadGallery(base, owner, 1, 200).size()).isEqualTo(6);
		uploadGallery(base, owner, 1, 400);
		uploadGallery(base, owner, 7, 400);
		JsonNode detail = json(mockMvc.perform(auth(get(base), owner)).andExpect(status().isOk()).andReturn().getResponse());
		assertThat(detail.get("images").size()).isEqualTo(6);
		assertThat(detail.get("imageUrl").asText()).isEqualTo(detail.at("/image/url").asText());
		mockMvc.perform(auth(get(products("tienda-a")), owner)).andExpect(jsonPath("$.items.length()").value(1));
		String secondBase = products("tienda-a") + "/" + createProduct("tienda-a", CATEGORY_A, "Seis", "SIX", "10", owner).get("id").asText();
		assertThat(uploadGallery(secondBase, owner, 6, 200).size()).isEqualTo(6);
	}

	@Test
	void galleryReordersChangesPrimaryDeletesAndRejectsForeignIds() throws Exception {
		Auth owner = login("owner@example.com");
		JsonNode product = createProduct("tienda-a", CATEGORY_A, "Galería", "GALLERY", "10", owner);
		String base = products("tienda-a") + "/" + product.get("id").asText();
		JsonNode images = uploadGallery(base, owner, 3, 200);
		String first = images.get(0).get("id").asText();
		String second = images.get(1).get("id").asText();
		String third = images.get(2).get("id").asText();
		mockMvc.perform(auth(put(base + "/images/" + second + "/primary"), owner)).andExpect(status().isOk());
		String order = objectMapper.writeValueAsString(List.of(third, first, second));
		JsonNode reordered = json(mockMvc.perform(auth(put(base + "/images/order"), owner)
			.contentType(MediaType.APPLICATION_JSON).content(order)).andExpect(status().isOk()).andReturn().getResponse());
		assertThat(reordered.get(0).get("id").asText()).isEqualTo(third);
		assertThat(reordered.get(2).get("primary").asBoolean()).isTrue();
		assertThat(count(TENANT_A_DATABASE, "SELECT COUNT(*) FROM product_images WHERE is_primary = TRUE")).isEqualTo(1);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> execute(TENANT_A_DATABASE,
			"UPDATE product_images SET is_primary = TRUE WHERE public_id = UUID_TO_BIN('" + first + "')"))
			.isInstanceOf(SQLException.class);
		mockMvc.perform(auth(put(base + "/images/order"), owner).contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(List.of(first, first, third)))).andExpect(status().isBadRequest());
		String otherProduct = createProduct("tienda-a", CATEGORY_A, "Otro", "OTHER", "10", owner).get("id").asText();
		String foreignBase = products("tienda-a") + "/" + otherProduct;
		mockMvc.perform(auth(delete(foreignBase + "/images/" + first), owner)).andExpect(status().isNotFound());
		mockMvc.perform(auth(put(foreignBase + "/images/" + first + "/primary"), owner)).andExpect(status().isNotFound());
		String tenantB = base.replace("tienda-a", "tienda-b");
		mockMvc.perform(auth(delete(tenantB + "/images/" + first), owner)).andExpect(status().isNotFound());
		mockMvc.perform(auth(put(tenantB + "/images/" + first + "/primary"), owner)).andExpect(status().isNotFound());
		mockMvc.perform(auth(put(tenantB + "/images/order"), owner).contentType(MediaType.APPLICATION_JSON).content(order))
			.andExpect(status().isNotFound());
		uploadGallery(tenantB, owner, 1, 404);
		JsonNode afterDelete = json(mockMvc.perform(auth(delete(base + "/images/" + second), owner))
			.andExpect(status().isOk()).andReturn().getResponse());
		assertThat(afterDelete.size()).isEqualTo(2);
		assertThat(afterDelete.get(0).get("id").asText()).isEqualTo(third);
		assertThat(afterDelete.get(0).get("primary").asBoolean()).isTrue();
		assertThat(afterDelete.get(1).get("position").asInt()).isEqualTo(1);
		mockMvc.perform(auth(delete(base + "/images/" + first), owner)).andExpect(status().isOk());
		mockMvc.perform(auth(delete(base + "/images/" + third), owner)).andExpect(status().isOk());
		mockMvc.perform(auth(get(base), owner)).andExpect(jsonPath("$.images.length()").value(0))
			.andExpect(jsonPath("$.image").doesNotExist());
	}

	@Test
	void galleryRollsBackInvalidBatchAndSerializesConcurrentUploads() throws Exception {
		Auth owner = login("owner@example.com");
		String base = products("tienda-a") + "/" + createProduct("tienda-a", CATEGORY_A, "Lotes", "BATCH", "10", owner).get("id").asText();
		var invalid = multipart(base + "/images")
			.file(new MockMultipartFile("images", "valid.png", "image/png", imageFile().getBytes()))
			.file(new MockMultipartFile("images", "fake.png", "image/png", new byte[] {1, 2, 3})).param("altText", "Producto");
		mockMvc.perform(auth(invalid, owner)).andExpect(status().isBadRequest());
		mockMvc.perform(auth(get(base), owner)).andExpect(jsonPath("$.images.length()").value(0));
		uploadGallery(base, owner, 5, 200);
		var executor = Executors.newFixedThreadPool(2);
		try {
			Callable<Integer> upload = () -> mockMvc.perform(auth(multipart(base + "/images")
				.file(new MockMultipartFile("images", "test.png", "image/png", imageFile().getBytes()))
				.param("altText", "Producto"), owner)).andReturn().getResponse().getStatus();
			var results = executor.invokeAll(List.of(upload, upload));
			assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(200, 400);
		} finally { executor.shutdownNow(); }
		assertThat(count(TENANT_A_DATABASE, "SELECT COUNT(*) FROM product_images WHERE is_primary = TRUE")).isEqualTo(1);
		mockMvc.perform(auth(get(base), owner)).andExpect(jsonPath("$.images.length()").value(6));
		// Legacy replacement still replaces just the primary, keeping the gallery at six.
		mockMvc.perform(auth(multipartPut(base + "/image", imageFile()).param("altText", "Reemplazo"), owner))
			.andExpect(status().isOk());
		mockMvc.perform(auth(get(base), owner)).andExpect(jsonPath("$.images.length()").value(6));
	}

	@Test
	void galleryRequiresRoleCsrfAndPublishedProductForPublicAccess() throws Exception {
		Auth owner = login("owner@example.com");
		Auth staff = login("staff@example.com");
		String base = products("tienda-a") + "/" + createProduct("tienda-a", CATEGORY_A, "Privado", "PRIVATE", "10", owner).get("id").asText();
		uploadGallery(base, staff, 1, 403);
		mockMvc.perform(multipart(base + "/images").file(new MockMultipartFile("images", "test.png", "image/png", imageFile().getBytes()))
			.param("altText", "Producto").cookie(owner.session())).andExpect(status().isForbidden());
		JsonNode images = uploadGallery(base, owner, 2, 200);
		String publicUrl = "/api/v1/stores/tienda-a/media/product-images/" + images.get(1).get("id").asText() + "/display";
		mockMvc.perform(get(publicUrl)).andExpect(status().isNotFound());
		mockMvc.perform(auth(patch(base + "/status"), owner).contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"PUBLISHED\",\"version\":0}")).andExpect(status().isOk());
		mockMvc.perform(get(publicUrl)).andExpect(status().isOk());
		mockMvc.perform(get(publicUrl.replace("tienda-a", "tienda-b"))).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/stores/tienda-a/catalog/products/privado"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.images.length()").value(2))
			.andExpect(jsonPath("$.imageUrl").isString());
	}

	@Test
	void galleryFallsBackToFirstPositionAndRepairsMissingPrimaryOnUpload() throws Exception {
		Auth owner = login("owner@example.com");
		String base = products("tienda-a") + "/" + createProduct("tienda-a", CATEGORY_A, "Fallback", "FALLBACK", "10", owner).get("id").asText();
		JsonNode images = uploadGallery(base, owner, 2, 200);
		execute(TENANT_A_DATABASE, "UPDATE product_images SET is_primary = FALSE");
		mockMvc.perform(auth(get(base), owner)).andExpect(status().isOk())
			.andExpect(jsonPath("$.image.id").value(images.get(0).get("id").asText()))
			.andExpect(jsonPath("$.imageUrl").value(images.get(0).get("url").asText()));
		JsonNode repaired = uploadGallery(base, owner, 1, 200);
		assertThat(repaired.get(0).get("primary").asBoolean()).isTrue();
		assertThat(count(TENANT_A_DATABASE, "SELECT COUNT(*) FROM product_images WHERE is_primary = TRUE")).isEqualTo(1);
	}

	private JsonNode uploadGallery(String base, Auth owner, int count, int expected) throws Exception {
		var request = multipart(base + "/images");
		request.param("altText", "Producto");
		for (int i = 0; i < count; i++) request.file(new MockMultipartFile("images", "image.png", "image/png", imageFile().getBytes()));
		return json(mockMvc.perform(auth(request, owner)).andExpect(status().is(expected)).andReturn().getResponse());
	}

	@Test
	void createsAggregateAtomicallyAndReturnsCanonicalPricesAndOpaqueIds() throws Exception {
		Auth owner = login("owner@example.com");
		JsonNode product = createProduct(
			"tienda-a", CATEGORY_A, " Remera   Básica ", "rem-001", "1500.5", owner);

		assertThat(product.get("name").asText()).isEqualTo("Remera Básica");
		assertThat(product.get("slug").asText()).isEqualTo("remera-basica");
		assertThat(product.get("status").asText()).isEqualTo("DRAFT");
		assertThat(product.get("version").asLong()).isZero();
		assertThat(product.at("/variants/0/sku").asText()).isEqualTo("REM-001");
		assertThat(product.at("/variants/0/price").isTextual()).isTrue();
		assertThat(product.at("/variants/0/price").asText()).isEqualTo("1500.50");
		assertThat(product.at("/variants/0/version").asLong()).isZero();
		assertThat(product.has("internalId")).isFalse();
	}

	@Test
	void persistsGenericOptionsAndRejectsTheSameCombinationInAnyOrder() throws Exception {
		Auth owner = login("owner@example.com");
		String body = """
			{"name":"Bolsa configurable","categoryId":"%s","variants":[
				{"sku":"BOLSA-ALG-M","price":"2500","options":[
					{"name":"Material","value":"Algodón"},
					{"name":"Talle","value":"M"}
				]}
			]}
			""".formatted(CATEGORY_A);

		JsonNode product = json(mockMvc.perform(auth(post(products("tienda-a")), owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andReturn().getResponse());

		assertThat(product.at("/variants/0/options/0/name").asText()).isEqualTo("Material");
		assertThat(product.at("/variants/0/options/0/value").asText()).isEqualTo("Algodón");
		assertThat(product.at("/variants/0/options/1/name").asText()).isEqualTo("Talle");
		assertThat(product.at("/variants/0/size").asText()).isEqualTo("M");
		assertThat(count(TENANT_A_DATABASE, "SELECT COUNT(*) FROM product_options"))
			.isEqualTo(2);
		assertThat(count(TENANT_A_DATABASE, "SELECT COUNT(*) FROM product_option_values"))
			.isEqualTo(2);

		mockMvc.perform(auth(post(products("tienda-a") + "/" + product.get("id").asText()
				+ "/variants"), owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"sku":"BOLSA-DUP","price":"2600","options":[
						{"name":"talle","value":"m"},
						{"name":"material","value":"algodón"}
					]}
					"""))
			.andExpect(status().isConflict());
	}

	@Test
	void securesProductImagesByTenantRoleCsrfAndPublicationState() throws Exception {
		Auth owner = login("owner@example.com");
		Auth admin = login("admin@example.com");
		Auth staff = login("staff@example.com");
		JsonNode product = createProduct(
			"tienda-a", CATEGORY_A, "Producto visual", "MEDIA-1", "100", owner);
		String productId = product.get("id").asText();
		String imageUrl = products("tienda-a") + "/" + productId + "/image";
		MockMultipartFile file = imageFile();

		mockMvc.perform(multipartPut(imageUrl, file)
				.param("altText", "Producto visual")
				.cookie(owner.session()))
			.andExpect(status().isForbidden());
		mockMvc.perform(auth(multipartPut(imageUrl, imageFile())
				.param("altText", "Producto visual"), staff))
			.andExpect(status().isForbidden());

		JsonNode uploaded = json(mockMvc.perform(auth(multipartPut(imageUrl, imageFile())
				.param("altText", "Producto visual"), admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.thumbnailUrl").isString())
			.andReturn().getResponse());
		String imageId = uploaded.get("id").asText();
		String publicImage = "/api/v1/stores/tienda-a/media/product-images/"
			+ imageId + "/thumbnail";

		mockMvc.perform(get(publicImage)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/stores/tienda-b/media/product-images/"
				+ imageId + "/thumbnail"))
			.andExpect(status().isNotFound());

		mockMvc.perform(auth(patch(products("tienda-a") + "/" + productId + "/status"), owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"PUBLISHED\",\"version\":0}"))
			.andExpect(status().isOk());
		mockMvc.perform(get(publicImage))
			.andExpect(status().isOk())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
				.header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
				.header().exists("ETag"));

		mockMvc.perform(auth(patch(products("tienda-a") + "/" + productId + "/status"), owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"ARCHIVED\",\"version\":1}"))
			.andExpect(status().isOk());
		mockMvc.perform(get(publicImage)).andExpect(status().isNotFound());
		mockMvc.perform(auth(multipartPut(imageUrl, imageFile())
				.param("altText", "No editable"), owner))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value(
				"https://comercio-flex.local/problems/product-image-conflict"));

		mockMvc.perform(auth(delete(imageUrl), owner))
			.andExpect(status().isConflict());
	}

	@Test
	void paginatesAndFiltersByQStatusAndCategory() throws Exception {
		Auth owner = login("owner@example.com");
		createProduct("tienda-a", CATEGORY_A, "Remera Lisa", "REM-LISA", "100", owner);
		createProduct("tienda-a", CATEGORY_A, "Pantalón", "PAN-ESPECIAL", "200", owner);

		mockMvc.perform(get(products("tienda-a"))
				.param("q", "especial")
				.cookie(owner.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].name").value("Pantalón"))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalItems").value(1));
		mockMvc.perform(get(products("tienda-a"))
				.param("q", "remera")
				.param("status", "DRAFT")
				.param("categoryId", CATEGORY_A)
				.param("size", "1")
				.cookie(owner.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.totalPages").value(1));
	}

	@Test
	void enforcesRolesAndCsrf() throws Exception {
		Auth staff = login("staff@example.com");
		mockMvc.perform(get(products("tienda-a")).cookie(staff.session()))
			.andExpect(status().isOk());
		mockMvc.perform(auth(post(products("tienda-a")), staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody(CATEGORY_A, "Remera", "STAFF-1", "10")))
			.andExpect(status().isForbidden());

		Auth admin = login("admin@example.com");
		mockMvc.perform(post(products("tienda-a"))
				.cookie(admin.session())
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody(CATEGORY_A, "Remera", "ADMIN-1", "10")))
			.andExpect(status().isForbidden());
		mockMvc.perform(auth(post(products("tienda-a")), admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody(CATEGORY_A, "Remera", "ADMIN-1", "10")))
			.andExpect(status().isCreated());
	}

	@Test
	void isolatesTenantDatabasesAndAllowsTheSameSkuInEach() throws Exception {
		Auth owner = login("owner@example.com");
		JsonNode a = createProduct(
			"tienda-a", CATEGORY_A, "Remera A", "SHARED-SKU", "10", owner);
		mockMvc.perform(get(products("tienda-b")).cookie(owner.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty());
		mockMvc.perform(get(products("tienda-b") + "/" + a.get("id").asText())
				.cookie(owner.session()))
			.andExpect(status().isNotFound());
		createProduct("tienda-b", CATEGORY_B, "Remera B", "SHARED-SKU", "10", owner);
	}

	@Test
	void protectsPublicationLastVariantAndArchiveRestoreTransitions() throws Exception {
		Auth owner = login("owner@example.com");
		JsonNode product = createProduct(
			"tienda-a", CATEGORY_A, "Remera", "PUB-1", "10", owner);
		String productId = product.get("id").asText();
		String variantId = product.at("/variants/0/id").asText();

		JsonNode published = json(mockMvc.perform(auth(
				patch(products("tienda-a") + "/" + productId + "/status"), owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"PUBLISHED\",\"version\":0}"))
			.andExpect(status().isOk()).andReturn().getResponse());
		assertThat(published.get("version").asLong()).isEqualTo(1);

		mockMvc.perform(auth(patch(products("tienda-a") + "/" + productId
				+ "/variants/" + variantId + "/status"), owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false,\"version\":0}"))
			.andExpect(status().isConflict());

		JsonNode archived = json(mockMvc.perform(auth(
				patch(products("tienda-a") + "/" + productId + "/status"), owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"ARCHIVED\",\"version\":1}"))
			.andExpect(status().isOk()).andReturn().getResponse());
		assertThat(archived.get("status").asText()).isEqualTo("ARCHIVED");
		mockMvc.perform(auth(patch(products("tienda-a") + "/" + productId + "/status"), owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"PUBLISHED\",\"version\":2}"))
			.andExpect(status().isConflict());
		mockMvc.perform(auth(patch(products("tienda-a") + "/" + productId + "/status"), owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"DRAFT\",\"version\":2}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(3));
	}

	@Test
	void requiresVersionsAndRejectsStaleProductAndVariantWrites() throws Exception {
		Auth owner = login("owner@example.com");
		JsonNode product = createProduct(
			"tienda-a", CATEGORY_A, "Remera", "VER-1", "10", owner);
		String productId = product.get("id").asText();
		String variantId = product.at("/variants/0/id").asText();

		mockMvc.perform(auth(put(products("tienda-a") + "/" + productId), owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content(metadataBody(CATEGORY_A, "Cambio", null)))
			.andExpect(status().isBadRequest());
		mockMvc.perform(auth(patch(products("tienda-a") + "/" + productId + "/status"), owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isBadRequest());
		mockMvc.perform(auth(put(products("tienda-a") + "/" + productId
				+ "/variants/" + variantId), owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"sku\":\"VER-1\",\"price\":\"11\"}"))
			.andExpect(status().isBadRequest());
		mockMvc.perform(auth(patch(products("tienda-a") + "/" + productId
				+ "/variants/" + variantId + "/status"), owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false}"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(auth(put(products("tienda-a") + "/" + productId), owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content(metadataBody(CATEGORY_A, "Cambio", 0L)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(1));
		mockMvc.perform(auth(put(products("tienda-a") + "/" + productId), owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content(metadataBody(CATEGORY_A, "Obsoleto", 0L)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.title").value("Versión desactualizada"));

		mockMvc.perform(auth(put(products("tienda-a") + "/" + productId
				+ "/variants/" + variantId), owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content(variantUpdateBody("VER-1", "11", 0L)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(1));
		mockMvc.perform(auth(put(products("tienda-a") + "/" + productId
				+ "/variants/" + variantId), owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content(variantUpdateBody("VER-1", "12", 0L)))
			.andExpect(status().isConflict());
	}

	@Test
	void rejectsDuplicatesOversizedPayloadExtremePageAndRollsBackOnDbConflict()
			throws Exception {
		Auth owner = login("owner@example.com");
		createProduct("tienda-a", CATEGORY_A, "Primero", "TAKEN", "10", owner);

		String rollbackBody = """
			{"name":"Debe revertirse","categoryId":"%s","variants":[
				{"sku":"NEW-SKU","price":"10","size":"M","color":"Azul"},
				{"sku":"TAKEN","price":"20","size":"L","color":"Rojo"}
			]}
			""".formatted(CATEGORY_A);
		mockMvc.perform(auth(post(products("tienda-a")), owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content(rollbackBody))
			.andExpect(status().isConflict());
		assertThat(count(TENANT_A_DATABASE,
			"SELECT COUNT(*) FROM products WHERE name = 'Debe revertirse'")).isZero();
		assertThat(count(TENANT_A_DATABASE,
			"SELECT COUNT(*) FROM product_variants WHERE sku = 'NEW-SKU'")).isZero();

		String variants = IntStream.range(0, 101)
			.mapToObj(index -> """
				{"sku":"SKU-%03d","price":"1","size":"%d"}
				""".formatted(index, index))
			.collect(java.util.stream.Collectors.joining(","));
		mockMvc.perform(auth(post(products("tienda-a")), owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Demasiadas","categoryId":"%s","variants":[%s]}
					""".formatted(CATEGORY_A, variants)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.variants").exists());

		mockMvc.perform(get(products("tienda-a"))
				.param("page", "2147483647")
				.cookie(owner.session()))
			.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsInactiveCategoryAndRollsBackTheWholeAggregate() throws Exception {
		Auth owner = login("owner@example.com");

		mockMvc.perform(auth(post(products("tienda-a")), owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody(
					CATEGORY_A_INACTIVE,
					"No debe persistir",
					"INACTIVE-CATEGORY",
					"10")))
			.andExpect(status().isConflict());

		assertThat(count(TENANT_A_DATABASE,
			"SELECT COUNT(*) FROM products WHERE name = 'No debe persistir'")).isZero();
		assertThat(count(TENANT_A_DATABASE,
			"SELECT COUNT(*) FROM product_variants WHERE sku = 'INACTIVE-CATEGORY'")).isZero();
	}

	@Test
	void serializesConcurrentVariantDeactivationSoOneActiveVariantRemains()
			throws Exception {
		Auth owner = login("owner@example.com");
		JsonNode product = createProductWithTwoVariants(owner);
		String productId = product.get("id").asText();
		String first = product.at("/variants/0/id").asText();
		String second = product.at("/variants/1/id").asText();
		mockMvc.perform(auth(patch(products("tienda-a") + "/" + productId + "/status"), owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"PUBLISHED\",\"version\":0}"))
			.andExpect(status().isOk());

		var executor = Executors.newFixedThreadPool(2);
		try {
			Callable<Integer> deactivateFirst = () -> deactivate(owner, productId, first);
			Callable<Integer> deactivateSecond = () -> deactivate(owner, productId, second);
			List<Future<Integer>> results =
				executor.invokeAll(List.of(deactivateFirst, deactivateSecond));
			assertThat(results).extracting(Future::get)
				.containsExactlyInAnyOrder(200, 409);
			assertThat(count(TENANT_A_DATABASE, """
				SELECT COUNT(*) FROM product_variants
				WHERE product_id = (
					SELECT id FROM products
					WHERE public_id = UUID_TO_BIN('%s')
				) AND status = 'ACTIVE'
				""".formatted(productId))).isEqualTo(1);
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void serializesConcurrentPublishAndLastVariantDeactivation() throws Exception {
		Auth owner = login("owner@example.com");
		JsonNode product = createProduct(
			"tienda-a", CATEGORY_A, "Carrera publicación", "RACE-PUBLISH", "10", owner);
		String productId = product.get("id").asText();
		String variantId = product.at("/variants/0/id").asText();

		var executor = Executors.newFixedThreadPool(2);
		try {
			Callable<Integer> publish = () -> mockMvc.perform(auth(
					patch(products("tienda-a") + "/" + productId + "/status"), owner)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"PUBLISHED\",\"version\":0}"))
				.andReturn().getResponse().getStatus();
			Callable<Integer> deactivate =
				() -> deactivate(owner, productId, variantId);
			List<Future<Integer>> results = executor.invokeAll(List.of(publish, deactivate));

			assertThat(results).extracting(Future::get)
				.containsExactlyInAnyOrder(200, 409);
			assertThat(count(TENANT_A_DATABASE, """
				SELECT COUNT(*)
				FROM products product
				WHERE product.public_id = UUID_TO_BIN('%s')
					AND product.status = 'PUBLISHED'
					AND NOT EXISTS (
						SELECT 1 FROM product_variants variant
						WHERE variant.product_id = product.id
							AND variant.status = 'ACTIVE'
					)
				""".formatted(productId))).isZero();
		}
		finally {
			executor.shutdownNow();
		}
	}

	private JsonNode createProduct(
			String store,
			String category,
			String name,
			String sku,
			String price,
			Auth auth) throws Exception {
		MockHttpServletResponse response = mockMvc.perform(auth(post(products(store)), auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody(category, name, sku, price)))
			.andExpect(status().isCreated())
			.andReturn().getResponse();
		return json(response);
	}

	private JsonNode createProductWithTwoVariants(Auth owner) throws Exception {
		String body = """
			{"name":"Con dos","categoryId":"%s","variants":[
				{"sku":"TWO-M","price":"10","size":"M","color":"Azul"},
				{"sku":"TWO-L","price":"10","size":"L","color":"Azul"}
			]}
			""".formatted(CATEGORY_A);
		return json(mockMvc.perform(auth(post(products("tienda-a")), owner)
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated()).andReturn().getResponse());
	}

	private int deactivate(Auth auth, String productId, String variantId) throws Exception {
		return mockMvc.perform(auth(patch(products("tienda-a") + "/" + productId
				+ "/variants/" + variantId + "/status"), auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false,\"version\":0}"))
			.andReturn().getResponse().getStatus();
	}

	private Auth login(String email) throws Exception {
		Cookie initial = requiredCookie(mockMvc.perform(get("/api/v1/auth/csrf"))
			.andExpect(status().isOk()).andReturn().getResponse(), "XSRF-TOKEN");
		MockHttpServletResponse response = mockMvc.perform(post("/api/v1/auth/login")
				.cookie(initial)
				.header("X-XSRF-TOKEN", initial.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","password":"%s"}
					""".formatted(email, PASSWORD)))
			.andExpect(status().isOk()).andReturn().getResponse();
		return new Auth(
			requiredCookie(response, "CFSESSION"),
			requiredCookie(response, "XSRF-TOKEN"));
	}

	private MockHttpServletRequestBuilder auth(
			MockHttpServletRequestBuilder request,
			Auth auth) {
		return request.cookie(auth.session(), auth.csrf())
			.header("X-XSRF-TOKEN", auth.csrf().getValue());
	}

	private MockHttpServletRequestBuilder multipartPut(String url, MockMultipartFile file) {
		return multipart(url).file(file).with(request -> {
			request.setMethod("PUT");
			return request;
		});
	}

	private MockMultipartFile imageFile() throws Exception {
		BufferedImage image = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "png", output);
		return new MockMultipartFile("file", "product.png", "image/png", output.toByteArray());
	}

	private String products(String store) {
		return "/api/v1/stores/" + store + "/admin/products";
	}

	private String createBody(String category, String name, String sku, String price) {
		return """
			{"name":"%s","categoryId":"%s","variants":[
				{"sku":"%s","price":"%s","size":"M","color":"Azul"}
			]}
			""".formatted(name, category, sku, price);
	}

	private String metadataBody(String category, String name, Long version) {
		String versionProperty = version == null ? "" : ",\"version\":" + version;
		return """
			{"name":"%s","categoryId":"%s"%s}
			""".formatted(name, category, versionProperty);
	}

	private String variantUpdateBody(String sku, String price, long version) {
		return """
			{"sku":"%s","price":"%s","size":"M","color":"Azul","version":%d}
			""".formatted(sku, price, version);
	}

	private JsonNode json(MockHttpServletResponse response) throws Exception {
		return objectMapper.readTree(response.getContentAsString());
	}

	private void insertUser(String email, String role, boolean bothStores) {
		control.update("""
			INSERT INTO platform_users
				(public_id, email_normalized, display_name, password_hash, status)
			VALUES (UNHEX(REPLACE(UUID(), '-', '')), ?, ?, ?, 'ACTIVE')
			""", email, role, PASSWORD_HASH);
		insertMembership(email, role, "tienda-a");
		if (bothStores) {
			insertMembership(email, role, "tienda-b");
		}
	}

	private void insertMembership(String email, String role, String store) {
		control.update("""
			INSERT INTO memberships (user_id, tenant_id, role, status)
			SELECT user.id, tenant.id, ?, 'ACTIVE'
			FROM platform_users user, tenants tenant
			WHERE user.email_normalized = ? AND tenant.slug = ?
			""", role, email, store);
	}

	private static void resetTenant(MySQLContainer<?> database) throws SQLException {
		execute(database, "DELETE FROM product_images");
		execute(database, "DELETE FROM product_variant_option_values");
		execute(database, "DELETE FROM product_option_values");
		execute(database, "DELETE FROM product_options");
		execute(database, "DELETE FROM product_variants");
		execute(database, "DELETE FROM products");
		execute(database, "DELETE FROM categories");
	}

	private static void insertCategory(
			MySQLContainer<?> database,
			String id,
			String name,
			String slug,
			String status) throws SQLException {
		execute(database, """
			INSERT INTO categories (public_id, name, slug, status)
			VALUES (UUID_TO_BIN('%s'), '%s', '%s', '%s')
			""".formatted(id, name, slug, status));
	}

	private static int count(MySQLContainer<?> database, String sql) throws SQLException {
		try (Connection connection = DriverManager.getConnection(
				database.getJdbcUrl(), database.getUsername(), database.getPassword());
				Statement statement = connection.createStatement();
				var result = statement.executeQuery(sql)) {
			result.next();
			return result.getInt(1);
		}
	}

	private static void execute(MySQLContainer<?> database, String sql) throws SQLException {
		try (Connection connection = DriverManager.getConnection(
				database.getJdbcUrl(), database.getUsername(), database.getPassword());
				Statement statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private static void registerTenant(
			DynamicPropertyRegistry registry,
			String key,
			MySQLContainer<?> database) {
		String prefix = "app.database.tenant-connections." + key;
		registry.add(prefix + ".url", database::getJdbcUrl);
		registry.add(prefix + ".username", database::getUsername);
		registry.add(prefix + ".password", database::getPassword);
	}

	private static Cookie requiredCookie(MockHttpServletResponse response, String name) {
		Cookie cookie = response.getCookie(name);
		if (cookie == null) {
			throw new AssertionError("Missing cookie " + name);
		}
		return cookie;
	}

	private record Auth(Cookie session, Cookie csrf) {
	}
}
