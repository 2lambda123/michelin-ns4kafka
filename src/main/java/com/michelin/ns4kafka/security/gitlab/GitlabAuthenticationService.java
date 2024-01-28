package com.michelin.ns4kafka.security.gitlab;

import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Gitlab authentication service.
 */
@Slf4j
@Singleton
public class GitlabAuthenticationService {
    @Inject
    GitlabApiClient gitlabApiClient;

    /**
     * Get all GitLab user groups.
     *
     * @param token The user token
     * @return The user groups
     */
    public Flux<String> findAllGroups(String token, String parentGroupId) {
        return getPageAndNext(token, parentGroupId, 1)
            .flatMap(response -> Flux.fromStream(response.body()
                .stream()
                .map(stringObjectMap -> stringObjectMap.get("full_path").toString())));
    }

    /**
     * Get username of GitLab user.
     *
     * @param token The user token
     * @return The username
     */
    public Mono<String> findUsername(String token) {
        return gitlabApiClient.findUser(token)
            .map(stringObjectMap -> stringObjectMap.get("email").toString());
    }

    /**
     * Fetch all pages of GitLab user groups.
     *
     * @param token The user token
     * @param page  The current page to fetch
     * @return The user groups information
     */
    private Flux<HttpResponse<List<Map<String, Object>>>> getPageAndNext(String token, String parentGroupId, int page) {
        Flux<HttpResponse<List<Map<String, Object>>>> pages;
        if (parentGroupId == null) {
            pages = gitlabApiClient.getGroupsPage(token, page);
        } else {
            log.debug("Call GitLab subgroups of {}.", parentGroupId);
            pages = gitlabApiClient.getGroupsPage(token, parentGroupId, page);
        }
        return pages.concatMap(response -> {
            log.debug("Call GitLab groups page {}/{}.", page, response.header("X-Total-Pages"));

            if (StringUtils.isEmpty(response.header("X-Next-Page"))) {
                return Flux.just(response);
            } else {
                int nextPage = Integer.parseInt(response.header("X-Next-Page"));
                return Flux.just(response)
                    .concatWith(getPageAndNext(token, parentGroupId, nextPage));
            }
        });
    }
}
