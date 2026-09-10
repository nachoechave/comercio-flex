package com.comercioflex.radio.domain;

import java.util.List;
import java.util.UUID;

public record RadioSite(
	Settings settings,
	List<Program> programs,
	List<TeamMember> team,
	List<Sponsor> sponsors) {
	public record Settings(String heroTitle, String heroSubtitle, String description,
			String youtubeUrl, String instagramUrl, String xUrl, String whatsappUrl) {}
	public record Program(UUID publicId, String name, String description, String days,
			String schedule, String imageUrl, String hosts, int displayOrder, boolean active) {}
	public record TeamMember(UUID publicId, String name, String role, String bio,
			String photoUrl, String socialUrl, int displayOrder, boolean active) {}
	public record Sponsor(UUID publicId, String name, String logoUrl, String targetUrl,
			String description, String level, int displayOrder, boolean active) {}
}
