package com.michelin.ns4kafka.security.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.michelin.ns4kafka.models.Metadata;
import com.michelin.ns4kafka.models.RoleBinding;
import com.michelin.ns4kafka.properties.SecurityProperties;
import com.michelin.ns4kafka.security.ResourceBasedSecurityRule;
import com.michelin.ns4kafka.services.RoleBindingService;
import io.micronaut.security.authentication.AuthenticationException;
import io.micronaut.security.authentication.AuthenticationResponse;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Authentication service test.
 */
@ExtendWith(MockitoExtension.class)
public class AuthenticationServiceTest {
    @Mock
    RoleBindingService roleBindingService;

    @Mock
    SecurityProperties securityProperties;

    @Mock
    ResourceBasedSecurityRule resourceBasedSecurityRule;

    @InjectMocks
    AuthenticationService authenticationService;

    @Test
    void shouldThrowErrorWhenNoRoleBindingAndNotAdmin() {
        when(roleBindingService.listByGroups(any()))
            .thenReturn(Collections.emptyList());

        when(securityProperties.getAdminGroup())
            .thenReturn("admin");

        AuthenticationException exception = assertThrows(AuthenticationException.class,
            () -> authenticationService.buildAuthJwtGroups("username", List.of("group")));

        assertTrue(exception.getResponse().getMessage().isPresent());
        assertEquals("No namespace matches your groups", exception.getResponse().getMessage().get());
    }

    @Test
    void shouldReturnAuthenticationSuccessWhenAdminNoGroup() {
        when(roleBindingService.listByGroups(any()))
            .thenReturn(Collections.emptyList());

        when(securityProperties.getAdminGroup())
            .thenReturn("admin");

        when(resourceBasedSecurityRule.computeRolesFromGroups(any()))
            .thenReturn(List.of(ResourceBasedSecurityRule.IS_ADMIN));

        AuthenticationResponse response = authenticationService.buildAuthJwtGroups("admin", List.of("admin"));

        assertTrue(response.getAuthentication().isPresent());
        assertEquals("admin", response.getAuthentication().get().getName());
        assertTrue(response.getAuthentication().get().getRoles().contains(ResourceBasedSecurityRule.IS_ADMIN));
        assertTrue(response.getAuthentication().get().getAttributes()
            .containsKey("role-bindings"));
        assertTrue(
            ((List<JwtRoleBinding>) response.getAuthentication().get().getAttributes().get("role-bindings")).isEmpty());
    }

    @Test
    void shouldReturnAuthenticationSuccessWhenAdminWithGroups() {
        RoleBinding roleBinding = RoleBinding.builder()
            .metadata(Metadata.builder()
                .name("ns1-rb")
                .namespace("ns1")
                .build())
            .spec(RoleBinding.RoleBindingSpec.builder()
                .role(RoleBinding.Role.builder()
                    .resourceTypes(List.of("topics", "acls"))
                    .verbs(List.of(RoleBinding.Verb.POST, RoleBinding.Verb.GET))
                    .build())
                .subject(RoleBinding.Subject.builder()
                    .subjectName("group1")
                    .subjectType(RoleBinding.SubjectType.GROUP)
                    .build())
                .build())
            .build();

        when(roleBindingService.listByGroups(any()))
            .thenReturn(List.of(roleBinding));

        when(resourceBasedSecurityRule.computeRolesFromGroups(any()))
            .thenReturn(List.of(ResourceBasedSecurityRule.IS_ADMIN));

        AuthenticationResponse response = authenticationService.buildAuthJwtGroups("admin", List.of("admin"));

        assertTrue(response.getAuthentication().isPresent());
        assertEquals("admin", response.getAuthentication().get().getName());
        assertTrue(response.getAuthentication().get().getRoles().contains(ResourceBasedSecurityRule.IS_ADMIN));
        assertTrue(response.getAuthentication().get().getAttributes()
            .containsKey("role-bindings"));
        assertEquals("ns1",
            ((List<JwtRoleBinding>) response.getAuthentication().get().getAttributes().get("role-bindings")).get(0)
                .getNamespace());
        assertTrue(
            ((List<JwtRoleBinding>) response.getAuthentication().get().getAttributes().get("role-bindings")).get(0)
                .getVerbs()
                .containsAll(List.of(RoleBinding.Verb.POST, RoleBinding.Verb.GET)));
        assertTrue(
            ((List<JwtRoleBinding>) response.getAuthentication().get().getAttributes().get("role-bindings")).get(0)
                .getResources()
                .containsAll(List.of("topics", "acls")));
    }

    @Test
    void shouldReturnAuthenticationSuccessWhenUserWithGroups() {
        RoleBinding roleBinding = RoleBinding.builder()
            .metadata(Metadata.builder()
                .name("ns1-rb")
                .namespace("ns1")
                .build())
            .spec(RoleBinding.RoleBindingSpec.builder()
                .role(RoleBinding.Role.builder()
                    .resourceTypes(List.of("topics", "acls"))
                    .verbs(List.of(RoleBinding.Verb.POST, RoleBinding.Verb.GET))
                    .build())
                .subject(RoleBinding.Subject.builder()
                    .subjectName("group1")
                    .subjectType(RoleBinding.SubjectType.GROUP)
                    .build())
                .build())
            .build();

        when(roleBindingService.listByGroups(any()))
            .thenReturn(List.of(roleBinding));

        when(resourceBasedSecurityRule.computeRolesFromGroups(any()))
            .thenReturn(List.of());

        AuthenticationResponse response = authenticationService.buildAuthJwtGroups("user", List.of("group"));

        assertTrue(response.getAuthentication().isPresent());
        assertEquals("user", response.getAuthentication().get().getName());
        assertTrue(response.getAuthentication().get().getRoles().isEmpty());
        assertTrue(response.getAuthentication().get().getAttributes()
            .containsKey("role-bindings"));
        assertEquals("ns1",
            ((List<JwtRoleBinding>) response.getAuthentication().get().getAttributes().get("role-bindings")).get(0)
                .getNamespace());
        assertTrue(
            ((List<JwtRoleBinding>) response.getAuthentication().get().getAttributes().get("role-bindings")).get(0)
                .getVerbs()
                .containsAll(List.of(RoleBinding.Verb.POST, RoleBinding.Verb.GET)));
        assertTrue(
            ((List<JwtRoleBinding>) response.getAuthentication().get().getAttributes().get("role-bindings")).get(0)
                .getResources()
                .containsAll(List.of("topics", "acls")));
    }
}