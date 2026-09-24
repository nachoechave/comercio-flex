package com.comercioflex.shipping.infrastructure;

import com.comercioflex.shipping.application.CarrierGateway;
import com.comercioflex.shipping.application.CarrierUnavailableException;
import com.comercioflex.shipping.domain.CarrierModels.*;
import com.comercioflex.shipping.domain.ShippingModels.Status;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AndreaniCarrierGateway implements CarrierGateway {
  private static final String QA = "https://apisqa.andreani.com";
  private static final String PROD = "https://apis.andreani.com";
  private final ObjectMapper json;

  public AndreaniCarrierGateway(ObjectMapper json) {
    this.json = json;
  }

  @Override
  public Provider provider() {
    return Provider.ANDREANI;
  }

  @Override
  public QuoteResult quote(Account account, String postalCode, Parcel parcel) {
    requireAccount(account, false);
    if (postalCode == null || postalCode.isBlank()) {
      throw new CarrierUnavailableException("Ingresá un código postal para cotizar Andreani.");
    }
    try {
      JsonNode response =
          client(account)
              .get()
              .uri(
                  builder ->
                      builder
                          .path("/v1/tarifas")
                          .queryParam("cpDestino", postalCode.trim())
                          .queryParam("contrato", account.contractCode())
                          .queryParam("cliente", account.clientCode())
                          .queryParam("bultos[0][volumen]", decimal(parcel.volumeCm3()))
                          .queryParam("bultos[0][kilos]", decimal(parcel.kilos()))
                          .queryParam("bultos[0][altoCm]", decimal(parcel.heightCm()))
                          .queryParam("bultos[0][largoCm]", decimal(parcel.lengthCm()))
                          .queryParam("bultos[0][anchoCm]", decimal(parcel.widthCm()))
                          .build())
              .retrieve()
              .body(JsonNode.class);
      BigDecimal total = money(response, "tarifaConIva", "total");
      if (total == null) total = money(response, "tarifaSinIva", "total");
      if (total == null || total.signum() < 0) {
        throw new CarrierUnavailableException("Andreani no devolvió una tarifa válida.");
      }
      return new QuoteResult("ANDREANI_DOMICILIO", total.setScale(2), null, "Entrega Andreani");
    } catch (CarrierUnavailableException e) {
      throw e;
    } catch (RestClientException | IllegalArgumentException e) {
      throw unavailable("No pudimos cotizar Andreani en este momento.", e);
    }
  }

  @Override
  public CreatedShipment create(Account account, CreateShipmentRequest request) {
    requireAccount(account, true);
    if (request.destination() == null || request.customerDocumentType() == null) {
      throw new CarrierUnavailableException("Faltan datos del destinatario para generar el envío.");
    }
    try {
      String token = login(account);
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("contrato", account.contractCode());
      payload.put("origen", Map.of("postal", postal(account.origin())));
      payload.put("destino", Map.of("postal", postal(request.destination())));
      payload.put(
          "remitente",
          person(
              account.origin().senderName(),
              account.origin().senderEmail(),
              account.origin().senderDocumentType().name(),
              account.origin().senderDocumentNumber(),
              account.origin().senderPhone(),
              1));
      payload.put(
          "destinatario",
          List.of(
              person(
                  request.customerName(),
                  request.customerEmail(),
                  request.customerDocumentType().name(),
                  request.customerDocumentNumber(),
                  request.customerPhone(),
                  2)));
      payload.put(
          "bultos",
          List.of(
              Map.of(
                  "kilos",
                  request.parcel().kilos(),
                  "largoCm",
                  request.parcel().lengthCm(),
                  "altoCm",
                  request.parcel().heightCm(),
                  "anchoCm",
                  request.parcel().widthCm(),
                  "valorDeclaradoSinImpuestos",
                  request.declaredValue(),
                  "valorDeclaradoConImpuestos",
                  request.declaredValue(),
                  "descripcion",
                  "Pedido Comercio Flex " + request.orderReference())));

      JsonNode response =
          client(account)
              .post()
              .uri("/v2/ordenes-de-envio")
              .contentType(MediaType.APPLICATION_JSON)
              .header("x-authorization-token", token)
              .body(payload)
              .retrieve()
              .body(JsonNode.class);
      String external = text(response.path("bultos").path(0), "numeroDeEnvio", "numeroAndreani");
      String labelRef = text(response, "agrupadorDeBultos");
      String state = text(response, "estado", "state");
      if (blank(external) || blank(labelRef)) {
        throw new CarrierUnavailableException("Andreani no devolvió el número de envío o la etiqueta.");
      }
      return new CreatedShipment(external, external, labelRef, blank(state) ? "Pendiente" : state);
    } catch (CarrierUnavailableException e) {
      throw e;
    } catch (RestClientException | IllegalArgumentException e) {
      throw unavailable("No pudimos generar el envío en Andreani.", e);
    }
  }

  @Override
  public Tracking track(Account account, String externalReference) {
    requireAccount(account, true);
    if (blank(externalReference)) throw new CarrierUnavailableException("El envío no tiene tracking.");
    try {
      String token = login(account);
      JsonNode response =
          client(account)
              .get()
              .uri("/v3/envios/{numero}", externalReference)
              .header("x-authorization-token", token)
              .retrieve()
              .body(JsonNode.class);
      String state = trackingState(response);
      Instant occurred = trackingInstant(response);
      return new Tracking(state, mapState(state), occurred);
    } catch (RestClientException | IllegalArgumentException e) {
      throw unavailable("No pudimos actualizar el seguimiento de Andreani.", e);
    }
  }

  @Override
  public byte[] label(Account account, String labelReference) {
    requireAccount(account, true);
    if (blank(labelReference)) throw new CarrierUnavailableException("El envío todavía no tiene etiqueta.");
    try {
      String token = login(account);
      byte[] data =
          client(account)
              .get()
              .uri("/v2/ordenes-de-envio/{agrupador}/etiquetas", labelReference)
              .accept(MediaType.APPLICATION_PDF)
              .header("x-authorization-token", token)
              .retrieve()
              .body(byte[].class);
      if (data == null || data.length == 0) {
        throw new CarrierUnavailableException("Andreani devolvió una etiqueta vacía.");
      }
      return data;
    } catch (CarrierUnavailableException e) {
      throw e;
    } catch (RestClientException e) {
      throw unavailable("No pudimos descargar la etiqueta de Andreani.", e);
    }
  }

  private String login(Account account) {
    JsonNode response =
        client(account)
            .post()
            .uri("/v2/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("usuario", account.username(), "password", account.password()))
            .retrieve()
            .body(JsonNode.class);
    String token = text(response, "token", "access_token");
    if (blank(token)) throw new CarrierUnavailableException("Andreani rechazó la autenticación.");
    return token;
  }

  private RestClient client(Account account) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(3));
    factory.setReadTimeout(Duration.ofSeconds(10));
    return RestClient.builder()
        .baseUrl(account.environment() == Environment.PRODUCTION ? PROD : QA)
        .requestFactory(factory)
        .build();
  }

  private void requireAccount(Account account, boolean auth) {
    if (account == null
        || blank(account.clientCode())
        || blank(account.contractCode())
        || account.origin() == null
        || account.defaultParcel() == null) {
      throw new CarrierUnavailableException("La configuración de Andreani está incompleta.");
    }
    if (auth && (blank(account.username()) || blank(account.password()))) {
      throw new CarrierUnavailableException("Faltan las credenciales de Andreani.");
    }
  }

  private Map<String, Object> postal(Origin origin) {
    return Map.of(
        "codigoPostal", origin.postalCode(),
        "calle", origin.street(),
        "numero", origin.number(),
        "localidad", origin.city(),
        "pais", origin.country());
  }

  private Map<String, Object> postal(com.comercioflex.shipping.domain.ShippingModels.Address address) {
    return Map.of(
        "codigoPostal", address.postalCode(),
        "calle", address.street(),
        "numero", address.number(),
        "localidad", address.city(),
        "pais", "Argentina");
  }

  private Map<String, Object> person(
      String name, String email, String documentType, String document, String phone, int phoneType) {
    return Map.of(
        "nombreCompleto", Objects.toString(name, ""),
        "eMail", Objects.toString(email, ""),
        "documentoTipo", documentType,
        "documentoNumero", document,
        "telefonos", List.of(Map.of("tipo", phoneType, "numero", Objects.toString(phone, ""))));
  }

  private BigDecimal money(JsonNode root, String group, String field) {
    if (root == null) return null;
    JsonNode node = root.path(group).path(field);
    if (node.isMissingNode() || node.isNull()) return null;
    try {
      return new BigDecimal(node.asText());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private String trackingState(JsonNode root) {
    if (root == null) return "Sin novedades";
    String direct = text(root, "estado", "state", "estadoActual");
    if (!blank(direct)) return direct;
    JsonNode actual = root.path("estadoActual");
    String nested = text(actual, "descripcion", "estado", "nombre");
    if (!blank(nested)) return nested;
    JsonNode traces = root.path("trazas");
    if (traces.isArray() && !traces.isEmpty()) {
      JsonNode last = traces.get(traces.size() - 1);
      String trace = text(last, "estado", "descripcion", "evento");
      if (!blank(trace)) return trace;
    }
    return "Sin novedades";
  }

  private Instant trackingInstant(JsonNode root) {
    if (root == null) return Instant.now();
    for (String field : List.of("fecha", "fechaEvento", "fechaUltimaNovedad", "updatedAt")) {
      String value = text(root, field);
      if (!blank(value)) {
        try {
          return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
        }
      }
    }
    return Instant.now();
  }

  private Status mapState(String raw) {
    String value = Objects.toString(raw, "").toLowerCase(Locale.ROOT);
    if (value.contains("entreg")) return Status.DELIVERED;
    if (value.contains("cancel")) return Status.CANCELLED;
    if (value.contains("distrib")
        || value.contains("transit")
        || value.contains("viaje")
        || value.contains("admit")
        || value.contains("despach")) return Status.SHIPPED;
    return Status.PREPARING;
  }

  private String text(JsonNode node, String... fields) {
    if (node == null) return null;
    for (String field : fields) {
      JsonNode value = node.path(field);
      if (value.isTextual() || value.isNumber()) {
        String result = value.asText();
        if (!blank(result)) return result;
      }
    }
    return null;
  }

  private String decimal(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private CarrierUnavailableException unavailable(String message, Exception cause) {
    return new CarrierUnavailableException(message, cause);
  }
}
