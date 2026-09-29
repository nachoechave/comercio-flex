package com.comercioflex.shipping.application;

import com.comercioflex.payment.application.CredentialCipher;
import com.comercioflex.payment.application.EncryptionContext;
import com.comercioflex.shipping.application.CarrierRepository.StoredSettings;
import com.comercioflex.shipping.domain.CarrierModels.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CarrierConnectionTestService {
  private final CarrierRepository repository;
  private final Map<Provider, CarrierGateway> gateways;
  private final CredentialCipher cipher;

  public CarrierConnectionTestService(
      CarrierRepository repository, List<CarrierGateway> gateways, CredentialCipher cipher) {
    this.repository = repository;
    EnumMap<Provider, CarrierGateway> map = new EnumMap<>(Provider.class);
    gateways.forEach(gateway -> map.put(gateway.provider(), gateway));
    this.gateways = Map.copyOf(map);
    this.cipher = cipher;
  }

  public ConnectionTestResult test(ConnectionTestRequest command) {
    StoredSettings stored = repository.settings(false);
    Account account = account(command, stored);
    CarrierGateway gateway = gateway(command.provider());
    List<ConnectionCheck> checks = new ArrayList<>();

    try {
      gateway.authenticate(account);
      checks.add(
          new ConnectionCheck(
              "AUTH", true, "Andreani aceptó las credenciales en " + environmentLabel(command.environment()) + "."));
    } catch (CarrierUnavailableException e) {
      checks.add(new ConnectionCheck("AUTH", false, safeMessage(e, "Andreani rechazó la autenticación.")));
      checks.add(
          new ConnectionCheck(
              "QUOTE", false, "No se intentó la cotización porque la autenticación no fue válida."));
      return new ConnectionTestResult(
          false,
          false,
          false,
          command.environment(),
          command.destinationPostalCode().trim(),
          null,
          null,
          List.copyOf(checks));
    }

    try {
      QuoteResult quote =
          gateway.quote(
              account, command.destinationPostalCode().trim(), command.defaultParcel());
      checks.add(
          new ConnectionCheck(
              "QUOTE",
              true,
              "Cliente y contrato devolvieron una tarifa para el CP "
                  + command.destinationPostalCode().trim()
                  + "."));
      return new ConnectionTestResult(
          true,
          true,
          true,
          command.environment(),
          command.destinationPostalCode().trim(),
          quote.serviceCode(),
          quote.providerCost(),
          List.copyOf(checks));
    } catch (CarrierUnavailableException e) {
      checks.add(
          new ConnectionCheck(
              "QUOTE",
              false,
              safeMessage(
                  e,
                  "La autenticación funcionó, pero Andreani no pudo devolver una tarifa de prueba.")));
      return new ConnectionTestResult(
          false,
          true,
          false,
          command.environment(),
          command.destinationPostalCode().trim(),
          null,
          null,
          List.copyOf(checks));
    }
  }

  private Account account(ConnectionTestRequest command, StoredSettings stored) {
    boolean suppliedUser = !blank(command.username());
    boolean suppliedPassword = !blank(command.password());
    if (suppliedUser != suppliedPassword) {
      throw new ShippingException("Ingresá usuario y contraseña de Andreani juntos para probar la conexión.");
    }

    String username;
    String password;
    if (suppliedUser) {
      username = command.username().trim();
      password = command.password();
    } else {
      if (stored == null || stored.username() == null || stored.password() == null) {
        throw new ShippingException(
            "Ingresá las credenciales de Andreani o guardalas antes de probar la conexión.");
      }
      if (stored.environment() != command.environment()) {
        throw new ShippingException(
            "Al cambiar entre QA y Producción, volvé a ingresar las credenciales antes de probar.");
      }
      username =
          cipher.decrypt(
              stored.username(), context(stored.credentialContext(), command.environment(), "username"));
      password =
          cipher.decrypt(
              stored.password(), context(stored.credentialContext(), command.environment(), "password"));
    }

    return new Account(
        command.provider(),
        command.environment(),
        command.clientCode().trim(),
        command.contractCode().trim(),
        username,
        password,
        command.origin(),
        command.defaultParcel(),
        stored == null ? 15 : Math.max(5, stored.trackingSyncMinutes()));
  }

  private CarrierGateway gateway(Provider provider) {
    CarrierGateway gateway = gateways.get(provider);
    if (gateway == null) throw new CarrierUnavailableException("Transportista no disponible.");
    return gateway;
  }

  private EncryptionContext context(String ref, Environment environment, String field) {
    if (blank(ref)) {
      throw new ShippingException("Las credenciales guardadas de Andreani no tienen un contexto válido.");
    }
    return new EncryptionContext(ref, "ANDREANI", environment.name(), "shipping-carrier", field);
  }

  private String environmentLabel(Environment environment) {
    return environment == Environment.PRODUCTION ? "Producción" : "QA / Sandbox";
  }

  private String safeMessage(CarrierUnavailableException exception, String fallback) {
    String message = exception.getMessage();
    return blank(message) ? fallback : message;
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
