package com.comercioflex.membership.api;
import java.math.BigDecimal;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.identity.application.PublicIdentityService;
import com.comercioflex.membership.application.*;
import com.comercioflex.membership.domain.*;
import com.comercioflex.tenant.application.ResolvedTenant;
import com.comercioflex.tenant.api.TenantResolutionFilter;
@RestController
@Validated
@RequestMapping("/api/v1/stores/{storeSlug}")
public class MembershipController {
 private final MembershipService service;
 public MembershipController(MembershipService service) { this.service=service; }
 @ModelAttribute void requireRadio(HttpServletRequest request) { PublicIdentityService.requireRadio((ResolvedTenant)request.getAttribute(TenantResolutionFilter.RESOLVED_TENANT_ATTRIBUTE)); }
 @GetMapping("/membership-plans") List<MembershipPlan> plans() { return service.plans(true); }
 @GetMapping("/me/membership") MembershipService.View mine(@AuthenticationPrincipal PlatformPrincipal principal) { return service.mine(principal.publicId()); }
 @PostMapping("/me/membership") MembershipService.View join(@AuthenticationPrincipal PlatformPrincipal principal,@Valid @RequestBody Selection request) { return service.join(principal.publicId(),request.planPublicId()); }
 @GetMapping("/me/membership/current-period") MembershipPeriod current(@AuthenticationPrincipal PlatformPrincipal principal) { return service.mine(principal.publicId()).currentPeriod(); }
 @PostMapping("/me/membership/current-period") MembershipService.View ensure(@AuthenticationPrincipal PlatformPrincipal principal) { return service.ensureCurrent(principal.publicId()); }
 @PutMapping("/me/membership/plan") MembershipService.View change(@AuthenticationPrincipal PlatformPrincipal principal,@Valid @RequestBody Selection request) { return service.changePlan(principal.publicId(),request.planPublicId()); }
 @PostMapping("/me/membership/cancel") MembershipService.View cancel(@AuthenticationPrincipal PlatformPrincipal principal) { return service.cancel(principal.publicId()); }
 @GetMapping("/me/membership/periods") List<MembershipPeriod> periods(@AuthenticationPrincipal PlatformPrincipal principal,@RequestParam(defaultValue="0") @Min(0) @Max(1000000) int offset) { return service.periods(principal.publicId(),offset); }
 @GetMapping("/admin/membership-plans") List<MembershipPlan> adminPlans() { return service.plans(false); }
 @PostMapping("/admin/membership-plans") MembershipPlan create(@Valid @RequestBody PlanInput input) { return service.savePlan(null,input.plan()); }
 @PutMapping("/admin/membership-plans/{id}") MembershipPlan update(@PathVariable UUID id,@Valid @RequestBody PlanInput input) { return service.savePlan(id,input.plan()); }
 @GetMapping("/admin/paid-memberships") List<MembershipService.AdminView> members(@RequestParam(required=false) MembershipState state,@RequestParam(required=false) UUID planPublicId,@RequestParam(defaultValue="0") @Min(0) @Max(1000000) int offset) { return service.members(state,planPublicId,offset); }
 @GetMapping("/admin/paid-memberships/{id}/periods") List<MembershipPeriod> history(@PathVariable UUID id,@RequestParam(defaultValue="0") @Min(0) @Max(1000000) int offset) { return service.adminPeriods(id,offset); }
 @ExceptionHandler(MembershipProblem.class) ProblemDetail problem(MembershipProblem exception) { return ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(exception.status()),exception.getMessage()); }
 @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,jakarta.validation.ConstraintViolationException.class}) ProblemDetail invalid() { return ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatus.BAD_REQUEST,"Revisá los campos de la solicitud."); }
 public record Selection(@NotNull UUID planPublicId) {
  @JsonAnySetter public void reject(String name,Object value) { throw new IllegalArgumentException("Campo no permitido."); }
 }
 public record PlanInput(@NotBlank @Size(max=120) String name,@NotNull @Size(max=2000) String description,
  @NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal price,
  @NotNull @Pattern(regexp="[A-Z]{3}") String currency,
  @NotNull @Size(max=30) List<@NotBlank @Size(max=240) String> benefits,
  @NotNull Boolean active,@Min(0) @Max(1000000) int displayOrder) {
  @JsonAnySetter public void reject(String name,Object value) { throw new IllegalArgumentException("Campo no permitido."); }
  MembershipPlan plan() { return new MembershipPlan(0,null,name.strip(),description.strip(),price,currency,benefits.stream().map(String::strip).toList(),active,displayOrder,null,null); }
 }
}
