package com.comercioflex.membership.payment;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.comercioflex.identity.application.*;
import com.comercioflex.membership.application.*;
import com.comercioflex.payment.application.*;
import com.comercioflex.tenant.api.TenantResolutionFilter;
import com.comercioflex.tenant.application.ResolvedTenant;
@RestController @Validated @RequestMapping("/api/v1/stores/{storeSlug}")
public class MembershipPaymentController {
 private final MembershipCheckoutService checkout;
 private final MembershipPaymentRepository payments;
 private final MembershipRepository memberships;
 private final MemberIdentityDirectory identities;
 private final MembershipPaymentRouting routing;
 private final PaymentOAuthProperties oauth;
 private final PaymentCredentialResolver credentials;
 private final TransactionTemplate tx;
 public MembershipPaymentController(MembershipCheckoutService checkout,MembershipPaymentRepository payments,MembershipRepository memberships,MemberIdentityDirectory identities,MembershipPaymentRouting routing,PaymentOAuthProperties oauth,PaymentCredentialResolver credentials,@Qualifier("tenantTransactionTemplate") TransactionTemplate tx) {this.checkout=checkout;this.payments=payments;this.memberships=memberships;this.identities=identities;this.routing=routing;this.oauth=oauth;this.credentials=credentials;this.tx=tx;}
 @ModelAttribute void radio(HttpServletRequest request) {PublicIdentityService.requireRadio(tenant(request));}
 private ResolvedTenant tenant(HttpServletRequest request) {return (ResolvedTenant)request.getAttribute(TenantResolutionFilter.RESOLVED_TENANT_ATTRIBUTE);}
 @PostMapping("/me/membership/current-period/checkout-pro") MembershipCheckoutService.CheckoutView checkout(HttpServletRequest r,@AuthenticationPrincipal PlatformPrincipal p,@RequestBody(required=false) Map<String,Object> body) {if(body!=null&&!body.isEmpty())throw new MembershipProblem(400,"El checkout no acepta importe, identidad ni parámetros de pago.");return checkout.initiate(tenant(r),p.publicId());}
 @GetMapping("/me/membership/current-period/payment") MembershipCheckoutService.CheckoutView state(HttpServletRequest r,@AuthenticationPrincipal PlatformPrincipal p) {return checkout.state(tenant(r),p.publicId());}
 @GetMapping("/me/membership/payments") List<MembershipPaymentRepository.PaymentView> history(@AuthenticationPrincipal PlatformPrincipal p,@RequestParam(defaultValue="0") @Min(0) @Max(1000000) int offset) {identities.requireActive(p.publicId());return tx.execute(s->payments.history(p.publicId(),null,offset,false));}
 public record AdminPayments(List<MembershipPaymentRepository.PaymentView> payments,List<MembershipPaymentRepository.AttemptView> attempts) {}
 @GetMapping("/admin/paid-memberships/{id}/payments") AdminPayments admin(@PathVariable UUID id,@RequestParam(defaultValue="0") @Min(0) @Max(1000000) int offset) {return tx.execute(s->{memberships.memberByPublicId(id);return new AdminPayments(payments.history(null,id,offset,true),payments.attempts(id,offset));});}
 public record Settings(boolean enabled,boolean credentialAvailable,boolean available) {}
 @GetMapping("/admin/membership-payments/settings") Settings settings(HttpServletRequest r) {var t=tenant(r);return new Settings(routing.enabled(t.id(),oauth.environment()),credentials.isAvailable(t.id(),t.slug()),checkout.available(t));}
 public record Enable(@NotNull Boolean enabled) {@JsonAnySetter public void reject(String name,Object value){throw new IllegalArgumentException("Campo no permitido.");}}
 @PutMapping("/admin/membership-payments/settings") Settings enable(HttpServletRequest r,@Valid @RequestBody Enable input) {routing.enabled(tenant(r).id(),oauth.environment(),input.enabled());return settings(r);}
 @ExceptionHandler(MembershipProblem.class) ProblemDetail problem(MembershipProblem e) {return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(e.status()),e.getMessage());}
 @ExceptionHandler({CheckoutPaymentException.class,PaymentOAuthException.class}) ProblemDetail paymentError(RuntimeException e) {return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"No pudimos completar la operación. Consultá el estado antes de volver a pagar.");}
 @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,jakarta.validation.ConstraintViolationException.class}) ProblemDetail invalid(){return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Revisá la solicitud.");}
}
