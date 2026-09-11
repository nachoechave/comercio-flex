package com.comercioflex.radio.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.comercioflex.radio.domain.RadioSite;

@Repository
public class JdbcRadioSiteRepository {
	private final JdbcTemplate jdbc;

	public JdbcRadioSiteRepository(@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public RadioSite site(boolean activeOnly) {
		return new RadioSite(settings(), programs(activeOnly), team(activeOnly), sponsors(activeOnly));
	}

	public void updateSettings(RadioSite.Settings value) {
		jdbc.update("""
			UPDATE radio_site_settings SET hero_title=?, hero_subtitle=?, description=?, youtube_url=?, youtube_channel_id=?,
			instagram_url=?, x_url=?, whatsapp_url=?, updated_at=CURRENT_TIMESTAMP(6) WHERE id=1
			""", value.heroTitle(), value.heroSubtitle(), value.description(), value.youtubeUrl(),
			value.youtubeChannelId(), value.instagramUrl(), value.xUrl(), value.whatsappUrl());
	}

	public UUID saveProgram(UUID id, RadioSite.Program value) {
		if (id == null) id = UUID.randomUUID();
		int updated = jdbc.update("""
			UPDATE radio_programs SET name=?,description=?,days=?,schedule=?,image_url=?,hosts=?,display_order=?,active=?,updated_at=CURRENT_TIMESTAMP(6)
			WHERE public_id=UUID_TO_BIN(?)
			""", value.name(), value.description(), value.days(), value.schedule(), value.imageUrl(), value.hosts(),
			value.displayOrder(), value.active(), id.toString());
		if (updated == 0) jdbc.update("""
			INSERT INTO radio_programs(public_id,name,description,days,schedule,image_url,hosts,display_order,active)
			VALUES(UUID_TO_BIN(?),?,?,?,?,?,?,?,?)
			""", id.toString(), value.name(), value.description(), value.days(), value.schedule(), value.imageUrl(),
			value.hosts(), value.displayOrder(), value.active());
		return id;
	}

	public UUID saveTeam(UUID id, RadioSite.TeamMember value) {
		if (id == null) id = UUID.randomUUID();
		int updated = jdbc.update("""
			UPDATE radio_team_members SET name=?,role_name=?,bio=?,photo_url=?,social_url=?,display_order=?,active=?,updated_at=CURRENT_TIMESTAMP(6)
			WHERE public_id=UUID_TO_BIN(?)
			""", value.name(), value.role(), value.bio(), value.photoUrl(), value.socialUrl(), value.displayOrder(),
			value.active(), id.toString());
		if (updated == 0) jdbc.update("""
			INSERT INTO radio_team_members(public_id,name,role_name,bio,photo_url,social_url,display_order,active)
			VALUES(UUID_TO_BIN(?),?,?,?,?,?,?,?)
			""", id.toString(), value.name(), value.role(), value.bio(), value.photoUrl(), value.socialUrl(),
			value.displayOrder(), value.active());
		return id;
	}

	public UUID saveSponsor(UUID id, RadioSite.Sponsor value) {
		if (id == null) id = UUID.randomUUID();
		int updated = jdbc.update("""
			UPDATE radio_sponsors SET name=?,logo_url=?,target_url=?,description=?,sponsor_level=?,display_order=?,active=?,updated_at=CURRENT_TIMESTAMP(6)
			WHERE public_id=UUID_TO_BIN(?)
			""", value.name(), value.logoUrl(), value.targetUrl(), value.description(), value.level(), value.displayOrder(),
			value.active(), id.toString());
		if (updated == 0) jdbc.update("""
			INSERT INTO radio_sponsors(public_id,name,logo_url,target_url,description,sponsor_level,display_order,active)
			VALUES(UUID_TO_BIN(?),?,?,?,?,?,?,?)
			""", id.toString(), value.name(), value.logoUrl(), value.targetUrl(), value.description(), value.level(),
			value.displayOrder(), value.active());
		return id;
	}

	public void delete(String table, UUID id) {
		if (!List.of("radio_programs", "radio_team_members", "radio_sponsors").contains(table)) throw new IllegalArgumentException();
		jdbc.update("DELETE FROM " + table + " WHERE public_id=UUID_TO_BIN(?)", id.toString());
	}

	private RadioSite.Settings settings() {
		return jdbc.query("SELECT hero_title,hero_subtitle,description,youtube_url,youtube_channel_id,instagram_url,x_url,whatsapp_url FROM radio_site_settings WHERE id=1",
			(ResultSet r, int n) -> new RadioSite.Settings(r.getString("hero_title"), r.getString("hero_subtitle"), r.getString("description"),
				r.getString("youtube_url"), r.getString("youtube_channel_id"), r.getString("instagram_url"), r.getString("x_url"), r.getString("whatsapp_url"))).stream()
			.findFirst().orElse(new RadioSite.Settings(null, null, null, null, null, null, null, null));
	}
	private List<RadioSite.Program> programs(boolean activeOnly) {
		return jdbc.query("SELECT BIN_TO_UUID(public_id) uuid,name,description,days,schedule,image_url,hosts,display_order,active FROM radio_programs "
			+ (activeOnly ? "WHERE active=TRUE " : "") + "ORDER BY display_order,id", this::program);
	}
	private List<RadioSite.TeamMember> team(boolean activeOnly) {
		return jdbc.query("SELECT BIN_TO_UUID(public_id) uuid,name,role_name,bio,photo_url,social_url,display_order,active FROM radio_team_members "
			+ (activeOnly ? "WHERE active=TRUE " : "") + "ORDER BY display_order,id", this::teamRow);
	}
	private List<RadioSite.Sponsor> sponsors(boolean activeOnly) {
		return jdbc.query("SELECT BIN_TO_UUID(public_id) uuid,name,logo_url,target_url,description,sponsor_level,display_order,active FROM radio_sponsors "
			+ (activeOnly ? "WHERE active=TRUE " : "") + "ORDER BY sponsor_level,display_order,id", this::sponsorRow);
	}
	private RadioSite.Program program(ResultSet r, int n) throws SQLException { return new RadioSite.Program(UUID.fromString(r.getString("uuid")), r.getString("name"), r.getString("description"), r.getString("days"), r.getString("schedule"), r.getString("image_url"), r.getString("hosts"), r.getInt("display_order"), r.getBoolean("active")); }
	private RadioSite.TeamMember teamRow(ResultSet r, int n) throws SQLException { return new RadioSite.TeamMember(UUID.fromString(r.getString("uuid")), r.getString("name"), r.getString("role_name"), r.getString("bio"), r.getString("photo_url"), r.getString("social_url"), r.getInt("display_order"), r.getBoolean("active")); }
	private RadioSite.Sponsor sponsorRow(ResultSet r, int n) throws SQLException { return new RadioSite.Sponsor(UUID.fromString(r.getString("uuid")), r.getString("name"), r.getString("logo_url"), r.getString("target_url"), r.getString("description"), r.getString("sponsor_level"), r.getInt("display_order"), r.getBoolean("active")); }
}
