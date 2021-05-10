package com.michelin.ns4kafka.cli.services;

import com.michelin.ns4kafka.cli.client.ClusterResourceClient;
import com.michelin.ns4kafka.cli.client.NamespacedResourceClient;
import com.michelin.ns4kafka.cli.models.ApiResource;
import com.michelin.ns4kafka.cli.models.Resource;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class ResourceService {

    @Inject
    NamespacedResourceClient namespacedClient;
    @Inject
    ClusterResourceClient nonNamespacedClient;

    @Inject
    LoginService loginService;

    public List<Resource> listAll(List<ApiResource> apiResources, String namespace) {
        return apiResources
                .stream()
                .flatMap(apiResource -> listResourcesWithType(apiResource, namespace).stream())
                .collect(Collectors.toList());
    }

    public List<Resource> listResourcesWithType(ApiResource apiResource, String namespace) {
        List<Resource> resources;
        if (apiResource.isNamespaced()) {
            try {
                resources = namespacedClient.list(namespace, apiResource.getPath(), loginService.getAuthorization());
            } catch (HttpClientResponseException e) {
                System.out.println("Error during list for resource type " + apiResource.getKind() + ": " + e.getMessage());
                resources = List.of();
            }
        } else {
            resources = nonNamespacedClient.list(loginService.getAuthorization(), apiResource.getPath());
        }
        return resources;
    }
    public Resource getSingleResourceWithType(ApiResource apiResource, String namespace, String resourceName){
        try {
            if (apiResource.isNamespaced()) {
                return namespacedClient.get(namespace, apiResource.getPath(), resourceName, loginService.getAuthorization());
            } else {
                return nonNamespacedClient.get(loginService.getAuthorization(), apiResource.getKind(), resourceName);
            }
        } catch (Exception e) {
            System.out.println("Error during get for resource type " + apiResource.getKind() + "/" + resourceName + ": " + e.getMessage());
        }

        return null;
    }
}
