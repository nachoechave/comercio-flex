package com.comercioflex.membership.application;
public class MembershipProblem extends RuntimeException {
 private final int status;
 public MembershipProblem(int status, String message) { super(message); this.status = status; }
 public int status() { return status; }
 public static MembershipProblem missing() { return new MembershipProblem(404, "No se encontró el recurso."); }
 public static MembershipProblem conflict(String message) { return new MembershipProblem(409, message); }
}
