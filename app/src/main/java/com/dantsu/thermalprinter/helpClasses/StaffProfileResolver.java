package com.dantsu.thermalprinter.helpClasses;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Resolves the logged-in staff member and role from a TastyIgniter
 * {@code /api/users?include=role} JSON:API payload.
 */
public final class StaffProfileResolver {

    public static final class StaffProfile {
        public final String loginId;
        public final String displayName;
        public final String username;
        public final String email;
        public final String roleName;
        public final String roleCode;
        public final boolean superUser;

        public StaffProfile(String loginId, String displayName, String username, String email,
                            String roleName, String roleCode, boolean superUser) {
            this.loginId = loginId != null ? loginId : "";
            this.displayName = displayName != null ? displayName : "";
            this.username = username != null ? username : "";
            this.email = email != null ? email : "";
            this.roleName = roleName != null ? roleName : "";
            this.roleCode = roleCode != null ? roleCode : "";
            this.superUser = superUser;
        }

        public String primaryIdentity() {
            if (!displayName.isEmpty()) {
                return displayName;
            }
            if (!username.isEmpty()) {
                return username;
            }
            if (!email.isEmpty()) {
                return email;
            }
            return loginId;
        }

        public String roleLabel() {
            if (roleName.isEmpty() && superUser) {
                return "Super Admin";
            }
            if (roleName.isEmpty()) {
                return "";
            }
            if (superUser) {
                return roleName + " · Super Admin";
            }
            return roleName;
        }
    }

    private StaffProfileResolver() {
    }

    public static StaffProfile resolve(String usersJson, String loginIdentifier) {
        if (usersJson == null || usersJson.trim().isEmpty()) {
            return fallback(loginIdentifier);
        }

        String needle = normalize(loginIdentifier);
        try {
            JSONObject root = new JSONObject(usersJson);
            JSONArray data = root.optJSONArray("data");
            if (data == null || data.length() == 0) {
                return fallback(loginIdentifier);
            }

            JSONObject matched = null;
            for (int i = 0; i < data.length(); i++) {
                JSONObject user = data.getJSONObject(i);
                JSONObject attributes = user.optJSONObject("attributes");
                if (attributes == null) {
                    continue;
                }
                String email = normalize(attributes.optString("email", ""));
                String username = normalize(attributes.optString("username", ""));
                if (!needle.isEmpty() && (needle.equals(email) || needle.equals(username))) {
                    matched = user;
                    break;
                }
            }

            // Single-user shops: fall back to the only staff record
            if (matched == null && data.length() == 1 && !needle.isEmpty()) {
                matched = data.getJSONObject(0);
            }
            if (matched == null) {
                return fallback(loginIdentifier);
            }

            JSONObject attributes = matched.optJSONObject("attributes");
            if (attributes == null) {
                return fallback(loginIdentifier);
            }

            String username = attributes.optString("username", "");
            String email = attributes.optString("email", "");
            String displayName = firstNonEmpty(
                    attributes.optString("full_name", ""),
                    attributes.optString("name", ""),
                    username,
                    email
            );
            boolean superUser = attributes.optBoolean("super_user", false);
            String roleId = "";
            JSONObject relationships = matched.optJSONObject("relationships");
            if (relationships != null) {
                JSONObject roleRel = relationships.optJSONObject("role");
                if (roleRel != null) {
                    JSONObject roleData = roleRel.optJSONObject("data");
                    if (roleData != null) {
                        roleId = roleData.optString("id", "");
                    }
                }
            }

            String roleName = "";
            String roleCode = "";
            JSONArray included = root.optJSONArray("included");
            if (included != null && !roleId.isEmpty()) {
                for (int i = 0; i < included.length(); i++) {
                    JSONObject item = included.getJSONObject(i);
                    if (!"role".equals(item.optString("type", ""))) {
                        continue;
                    }
                    if (!roleId.equals(item.optString("id", ""))) {
                        continue;
                    }
                    JSONObject roleAttrs = item.optJSONObject("attributes");
                    if (roleAttrs != null) {
                        roleName = roleAttrs.optString("name", "");
                        roleCode = roleAttrs.optString("code", "");
                    }
                    break;
                }
            }

            return new StaffProfile(loginIdentifier, displayName, username, email, roleName, roleCode, superUser);
        } catch (Exception e) {
            return fallback(loginIdentifier);
        }
    }

    private static StaffProfile fallback(String loginIdentifier) {
        String id = loginIdentifier != null ? loginIdentifier : "";
        return new StaffProfile(id, id, "", id, "", "", false);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return "";
    }
}
