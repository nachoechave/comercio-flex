package com.comercioflex.radio.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.comercioflex.identity.application.PublicIdentityService;
import com.comercioflex.identity.application.TenantPermissionGuard;
import com.comercioflex.identity.domain.TenantPermission;
import com.comercioflex.radio.domain.RadioSite;
import com.comercioflex.radio.infrastructure.JdbcRadioSiteRepository;
import com.comercioflex.tenant.application.ResolvedTenant;
import com.comercioflex.tenant.api.TenantResolutionFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}")
public class RadioSiteController {
	private final JdbcRadioSiteRepository repository;
	private final TenantPermissionGuard permissions;
	public RadioSiteController(JdbcRadioSiteRepository repository, TenantPermissionGuard permissions) { this.repository = repository; this.permissions = permissions; }

	@ModelAttribute
	void requireRadio(HttpServletRequest request) { PublicIdentityService.requireRadio((ResolvedTenant) request.getAttribute(TenantResolutionFilter.RESOLVED_TENANT_ATTRIBUTE)); }

	@GetMapping("/radio-site")
	RadioSite publicSite() { return repository.site(true); }

	@GetMapping("/admin/radio-site")
	RadioSite adminSite(HttpServletRequest request) { requireAdmin(request); return repository.site(false); }

	@PutMapping("/admin/radio-site")
	RadioSite updateSettings(@Valid @RequestBody SettingsInput input, HttpServletRequest request) { requireAdmin(request); repository.updateSettings(input.value()); return repository.site(false); }

	@PostMapping("/admin/radio-site/programs")
	RadioSite.Program createProgram(@Valid @RequestBody ProgramInput input, HttpServletRequest request) { requireAdmin(request); return findProgram(repository.saveProgram(null, input.value())); }
	@PutMapping("/admin/radio-site/programs/{id}")
	RadioSite.Program updateProgram(@PathVariable UUID id, @Valid @RequestBody ProgramInput input, HttpServletRequest request) { requireAdmin(request); return findProgram(repository.saveProgram(id, input.value())); }
	@DeleteMapping("/admin/radio-site/programs/{id}")
	void deleteProgram(@PathVariable UUID id, HttpServletRequest request) { requireAdmin(request); repository.delete("radio_programs", id); }

	@PostMapping("/admin/radio-site/team")
	RadioSite.TeamMember createTeam(@Valid @RequestBody TeamInput input, HttpServletRequest request) { requireAdmin(request); return findTeam(repository.saveTeam(null, input.value())); }
	@PutMapping("/admin/radio-site/team/{id}")
	RadioSite.TeamMember updateTeam(@PathVariable UUID id, @Valid @RequestBody TeamInput input, HttpServletRequest request) { requireAdmin(request); return findTeam(repository.saveTeam(id, input.value())); }
	@DeleteMapping("/admin/radio-site/team/{id}")
	void deleteTeam(@PathVariable UUID id, HttpServletRequest request) { requireAdmin(request); repository.delete("radio_team_members", id); }

	@PostMapping("/admin/radio-site/sponsors")
	RadioSite.Sponsor createSponsor(@Valid @RequestBody SponsorInput input, HttpServletRequest request) { requireAdmin(request); return findSponsor(repository.saveSponsor(null, input.value())); }
	@PutMapping("/admin/radio-site/sponsors/{id}")
	RadioSite.Sponsor updateSponsor(@PathVariable UUID id, @Valid @RequestBody SponsorInput input, HttpServletRequest request) { requireAdmin(request); return findSponsor(repository.saveSponsor(id, input.value())); }
	@DeleteMapping("/admin/radio-site/sponsors/{id}")
	void deleteSponsor(@PathVariable UUID id, HttpServletRequest request) { requireAdmin(request); repository.delete("radio_sponsors", id); }

	private void requireAdmin(HttpServletRequest request) { permissions.require(request, TenantPermission.MANAGE_BASIC_SETTINGS); }
	private RadioSite.Program findProgram(UUID id) { return repository.site(false).programs().stream().filter(v -> v.publicId().equals(id)).findFirst().orElseThrow(); }
	private RadioSite.TeamMember findTeam(UUID id) { return repository.site(false).team().stream().filter(v -> v.publicId().equals(id)).findFirst().orElseThrow(); }
	private RadioSite.Sponsor findSponsor(UUID id) { return repository.site(false).sponsors().stream().filter(v -> v.publicId().equals(id)).findFirst().orElseThrow(); }

	public record SettingsInput(@Size(max=160) String heroTitle, @Size(max=300) String heroSubtitle, @Size(max=1000) String description, @Pattern(regexp="^$|https://.+") @Size(max=500) String youtubeUrl, @Pattern(regexp="^$|https://.+") @Size(max=500) String instagramUrl, @Pattern(regexp="^$|https://.+") @Size(max=500) String xUrl, @Pattern(regexp="^$|https://.+") @Size(max=500) String whatsappUrl) { RadioSite.Settings value() { return new RadioSite.Settings(clean(heroTitle), clean(heroSubtitle), clean(description), clean(youtubeUrl), clean(instagramUrl), clean(xUrl), clean(whatsappUrl)); } }
	public record ProgramInput(@NotBlank @Size(max=160) String name, @NotNull @Size(max=1000) String description, @NotBlank @Size(max=160) String days, @NotBlank @Size(max=120) String schedule, @Size(max=1000) String imageUrl, @Size(max=500) String hosts, @Min(0) @Max(1000000) int displayOrder, boolean active) { RadioSite.Program value() { return new RadioSite.Program(null, clean(name), clean(description), clean(days), clean(schedule), clean(imageUrl), clean(hosts), displayOrder, active); } }
	public record TeamInput(@NotBlank @Size(max=160) String name, @NotBlank @Size(max=160) String role, @NotNull @Size(max=1000) String bio, @Size(max=1000) String photoUrl, @Size(max=500) String socialUrl, @Min(0) @Max(1000000) int displayOrder, boolean active) { RadioSite.TeamMember value() { return new RadioSite.TeamMember(null, clean(name), clean(role), clean(bio), clean(photoUrl), clean(socialUrl), displayOrder, active); } }
	public record SponsorInput(@NotBlank @Size(max=160) String name, @Size(max=1000) String logoUrl, @Pattern(regexp="^$|https://.+") @Size(max=500) String targetUrl, @Size(max=500) String description, @Pattern(regexp="PRIMARY|SECONDARY") String level, @Min(0) @Max(1000000) int displayOrder, boolean active) { RadioSite.Sponsor value() { return new RadioSite.Sponsor(null, clean(name), clean(logoUrl), clean(targetUrl), clean(description), level == null ? "SECONDARY" : level, displayOrder, active); } }
	private static String clean(String value) { return value == null ? null : value.strip(); }
}
