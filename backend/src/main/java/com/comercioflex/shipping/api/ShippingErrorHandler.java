package com.comercioflex.shipping.api;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import com.comercioflex.shipping.application.ShippingException;
@RestControllerAdvice
public class ShippingErrorHandler {
 @ExceptionHandler(com.comercioflex.order.application.InvalidGuestOrderException.class)
 ResponseEntity<?> invalid(RuntimeException e) {return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
 @ExceptionHandler(com.comercioflex.order.application.OrderUnavailableException.class)
 ResponseEntity<?> unavailable(RuntimeException e) {return ResponseEntity.status(409).body(Map.of("message","Los productos ya no están disponibles."));}

 @ExceptionHandler(ShippingException.class)
 ResponseEntity<?> handle(ShippingException e) {
  return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code","SHIPPING_CONFLICT","message",e.getMessage(),"detail",e.getMessage()));
 }
}
