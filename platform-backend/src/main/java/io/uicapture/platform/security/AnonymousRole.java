package io.uicapture.platform.security;

import io.jmix.security.role.annotation.ResourceRole;

@ResourceRole(name = "Anonymous", code = AnonymousRole.CODE, scope = "UI")
public interface AnonymousRole {
    String CODE = "anonymous-role";
}
