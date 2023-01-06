package com.michelin.ns4kafka.services.executors;

import com.michelin.ns4kafka.config.KafkaAsyncExecutorConfig;
import com.michelin.ns4kafka.models.AccessControlEntry;
import com.michelin.ns4kafka.models.KafkaStream;
import com.michelin.ns4kafka.models.Namespace;
import com.michelin.ns4kafka.repositories.NamespaceRepository;
import com.michelin.ns4kafka.repositories.kafka.KafkaStoreException;
import com.michelin.ns4kafka.services.AccessControlEntryService;
import com.michelin.ns4kafka.services.ConnectorService;
import com.michelin.ns4kafka.services.StreamService;
import io.micronaut.context.annotation.EachBean;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.michelin.ns4kafka.services.AccessControlEntryService.PUBLIC_GRANTED_TO;

@Slf4j
@EachBean(KafkaAsyncExecutorConfig.class)
@Singleton
public class AccessControlEntryAsyncExecutor {
    /**
     * The managed clusters configuration
     */
    private final KafkaAsyncExecutorConfig kafkaAsyncExecutorConfig;

    /**
     * The ACL service
     */
    @Inject
    AccessControlEntryService accessControlEntryService;

    /**
     * The Kafka Streams Service
     */
    @Inject
    StreamService streamService;

    /**
     * The Kafka Connect service
     */
    @Inject
    ConnectorService connectorService;

    /**
     * The namespace repository
     */
    @Inject
    NamespaceRepository namespaceRepository;

    /**
     * Constructor
     *
     * @param kafkaAsyncExecutorConfig The managed clusters configuration
     */
    public AccessControlEntryAsyncExecutor(KafkaAsyncExecutorConfig kafkaAsyncExecutorConfig) {
        this.kafkaAsyncExecutorConfig = kafkaAsyncExecutorConfig;
    }

    /**
     * Run the ACL executor
     */
    public void run() {
        if (this.kafkaAsyncExecutorConfig.isManageAcls()) {
            synchronizeACLs();
        }
    }

    /**
     * Start the ACLs synchronization
     */
    private void synchronizeACLs() {
        log.debug("Starting ACLs collection for cluster {}", kafkaAsyncExecutorConfig.getName());

        try {
            // List ACLs from broker
            List<AclBinding> brokerACLs = collectBrokerACLs(true);

            // List ACLs from NS4Kafka
            List<AclBinding> ns4kafkaACLs = collectNs4KafkaACLs();

            List<AclBinding> toCreate = ns4kafkaACLs.stream()
                    .filter(aclBinding -> !brokerACLs.contains(aclBinding))
                    .collect(Collectors.toList());

            List<AclBinding> toDelete = brokerACLs.stream()
                    .filter(aclBinding -> !ns4kafkaACLs.contains(aclBinding))
                    .collect(Collectors.toList());

            if (log.isDebugEnabled()) {
                brokerACLs.stream()
                        .filter(ns4kafkaACLs::contains)
                        .forEach(aclBinding -> log.debug("ACLs found in broker and Ns4Kafka: " + aclBinding.toString()));

                toCreate.forEach(aclBinding -> log.debug("ACLs to create: " + aclBinding.toString()));

                if (!kafkaAsyncExecutorConfig.isDropUnsyncAcls() && !toDelete.isEmpty()) {
                    log.debug("The ACL drop is disabled. The following ACLs won't be deleted.");
                }

                toDelete.forEach(aclBinding -> log.debug("ACLs to delete: " + aclBinding.toString()));
            }

            // Execute toAdd list BEFORE toDelete list to avoid breaking ACL on connected user
            // such as deleting <LITERAL "toto.titi"> only to add one second later <PREFIX "toto.">
            createACLs(toCreate);

            if (kafkaAsyncExecutorConfig.isDropUnsyncAcls()) {
                deleteACLs(toDelete);
            }
        } catch (KafkaStoreException | ExecutionException | TimeoutException e) {
            log.error("An error occurred collecting ACLs from broker during ACLs synchronization", e);
        } catch (InterruptedException e) {
            log.error("An error occurred during ACLs synchronization", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Collect the ACLs from Ns4Kafka.
     * Whenever the permission is OWNER, create 2 entries (one READ and one WRITE)
     * This is necessary to translate ns4kafka grouped AccessControlEntry (OWNER, WRITE, READ)
     * into Kafka Atomic ACLs (READ and WRITE)
     * @return A list of ACLs
     */
    private List<AclBinding> collectNs4KafkaACLs() {
        List<Namespace> namespaces = namespaceRepository.findAllForCluster(kafkaAsyncExecutorConfig.getName());

        // Converts topic and group Ns4kafka ACLs to topic and group Kafka AclBindings
        Stream<AclBinding> aclBindingFromACLs = namespaces
                .stream()
                .flatMap(namespace -> accessControlEntryService.findAllGrantedToNamespace(namespace)
                        .stream()
                        .filter(accessControlEntry -> accessControlEntry.getSpec().getResourceType() == AccessControlEntry.ResourceType.TOPIC ||
                                accessControlEntry.getSpec().getResourceType() == AccessControlEntry.ResourceType.GROUP)
                        .flatMap(accessControlEntry -> buildAclBindingsFromAccessControlEntry(accessControlEntry, namespace.getSpec().getKafkaUser())
                                .stream())
                        .distinct());

        // Converts KafkaStream resources to topic (CREATE/DELETE) AclBindings
        Stream<AclBinding> aclBindingFromKStream = namespaces.stream()
                .flatMap(namespace -> streamService.findAllForNamespace(namespace)
                        .stream()
                        .flatMap(kafkaStream ->
                                buildAclBindingsFromKafkaStream(kafkaStream, namespace.getSpec().getKafkaUser()).stream()));

        // Converts connect ACLs to group AclBindings (connect-)
        Stream<AclBinding> aclBindingFromConnect = namespaces.stream()
                .flatMap(namespace -> accessControlEntryService.findAllGrantedToNamespace(namespace)
                        .stream()
                        .filter(accessControlEntry -> accessControlEntry.getSpec().getResourceType() == AccessControlEntry.ResourceType.CONNECT)
                        .filter(accessControlEntry -> accessControlEntry.getSpec().getPermission() == AccessControlEntry.Permission.OWNER)
                        .flatMap(accessControlEntry ->
                                buildAclBindingsFromConnector(accessControlEntry, namespace.getSpec().getKafkaUser()).stream()));

        List<AclBinding> ns4kafkaACLs = Stream.of(aclBindingFromACLs, aclBindingFromKStream, aclBindingFromConnect)
                .flatMap(Function.identity())
                .collect(Collectors.toList());

        if (log.isDebugEnabled()) {
            log.debug("ACLs found on ns4kafka : " + ns4kafkaACLs.size());
            ns4kafkaACLs.forEach(aclBinding -> log.debug(aclBinding.toString()));
        }

        return ns4kafkaACLs;
    }

    /**
     * Collect the ACLs from broker
     *
     * @param managedUsersOnly Only retrieve ACLs from Kafka user managed by Ns4Kafka or not ?
     * @return A list of ACLs
     * @throws ExecutionException   Any execution exception during ACLs description
     * @throws InterruptedException Any interrupted exception during ACLs description
     * @throws TimeoutException     Any timeout exception during ACLs description
     */
    private List<AclBinding> collectBrokerACLs(boolean managedUsersOnly) throws ExecutionException, InterruptedException, TimeoutException {
        //TODO soon : manage IDEMPOTENT_WRITE on CLUSTER 'kafka-cluster'
        //TODO eventually : manage DELEGATION_TOKEN and TRANSACTIONAL_ID
        //TODO eventually : manage host ?
        //TODO never ever : manage CREATE and DELETE Topics (managed by ns4kafka !)
        List<ResourceType> validResourceTypes = List.of(ResourceType.TOPIC, ResourceType.GROUP, ResourceType.TRANSACTIONAL_ID);

        List<AclBinding> userACLs = getAdminClient()
                .describeAcls(AclBindingFilter.ANY)
                .values().get(10, TimeUnit.SECONDS)
                .stream()
                .filter(aclBinding -> validResourceTypes.contains(aclBinding.pattern().resourceType()))
                .collect(Collectors.toList());

        log.debug("{} ACLs found on broker", userACLs.size());
        if (log.isTraceEnabled()) {
            userACLs.forEach(aclBinding -> log.trace(aclBinding.toString()));
        }

        // TODO add parameter to cluster configuration to scope ALL users vs "namespace" managed users
        //  as of now, this will prevent deletion of ACLs for users not in ns4kafka scope
        if (managedUsersOnly) {
            // we first collect the list of Users managed in ns4kafka
            List<String> managedUsers = namespaceRepository.findAllForCluster(kafkaAsyncExecutorConfig.getName())
                    .stream()
                    //TODO managed user list should include not only "defaultKafkaUser" (MVP35)
                    //1-N Namespace to KafkaUser
                    .flatMap(namespace -> List.of("User:" + namespace.getSpec().getKafkaUser()).stream())
                    .collect(Collectors.toList());

            // And then filter out the AclBinding to retain only those matching
            // or having principal equal to wildcard (public).
            userACLs = userACLs.stream()
                    .filter(aclBinding ->
                            managedUsers.contains(aclBinding.entry().principal()) ||
                                    aclBinding.entry().principal().equals(PUBLIC_GRANTED_TO)
                    )
                    .collect(Collectors.toList());
            log.debug("ACLs found on Broker (managed scope) : {}", userACLs.size());
        }

        if (log.isDebugEnabled()) {
            userACLs.forEach(aclBinding -> log.debug(aclBinding.toString()));
        }

        return userACLs;
    }

    /**
     * Convert Ns4Kafka topic/group ACL into Kafka ACL
     * @param accessControlEntry The Ns4Kafka ACL
     * @param kafkaUser          The ACL owner
     * @return A list of Kafka ACLs
     */
    private List<AclBinding> buildAclBindingsFromAccessControlEntry(AccessControlEntry accessControlEntry, String kafkaUser) {
        // Convert pattern, convert resource type from Ns4Kafka to org.apache.kafka.common types
        PatternType patternType = PatternType.fromString(accessControlEntry.getSpec().getResourcePatternType().toString());
        ResourceType resourceType = ResourceType.fromString(accessControlEntry.getSpec().getResourceType().toString());
        ResourcePattern resourcePattern = new ResourcePattern(resourceType, accessControlEntry.getSpec().getResource(), patternType);

        // Generate the required AclOperation based on ResourceType
        List<AclOperation> targetAclOperations;
        if (accessControlEntry.getSpec().getPermission() == AccessControlEntry.Permission.OWNER) {
            targetAclOperations = computeAclOperationForOwner(resourceType);
        } else {
            // Should be READ or WRITE
            targetAclOperations = List.of(AclOperation.fromString(accessControlEntry.getSpec().getPermission().toString()));
        }

        final String aclUser = accessControlEntry.getSpec().getGrantedTo().equals(PUBLIC_GRANTED_TO) ? PUBLIC_GRANTED_TO : kafkaUser;
        return targetAclOperations
                .stream()
                .map(aclOperation ->
                        new AclBinding(resourcePattern, new org.apache.kafka.common.acl.AccessControlEntry("User:" + aclUser,
                                "*", aclOperation, AclPermissionType.ALLOW)))
                .collect(Collectors.toList());
    }

    /**
     * Convert Kafka Stream resource into Kafka ACL
     * @param stream    The Kafka Stream resource
     * @param kafkaUser The ACL owner
     * @return A list of Kafka ACLs
     */
    private List<AclBinding> buildAclBindingsFromKafkaStream(KafkaStream stream, String kafkaUser) {
        // As per https://docs.confluent.io/platform/current/streams/developer-guide/security.html#required-acl-setting-for-secure-ak-clusters
        return List.of(
                // CREATE and DELETE on Stream Topics
                new AclBinding(
                        new ResourcePattern(ResourceType.TOPIC, stream.getMetadata().getName(), PatternType.PREFIXED),
                        new org.apache.kafka.common.acl.AccessControlEntry("User:" + kafkaUser, "*", AclOperation.CREATE, AclPermissionType.ALLOW)
                ),
                new AclBinding(
                        new ResourcePattern(ResourceType.TOPIC, stream.getMetadata().getName(), PatternType.PREFIXED),
                        new org.apache.kafka.common.acl.AccessControlEntry("User:" + kafkaUser, "*", AclOperation.DELETE, AclPermissionType.ALLOW)
                ),
                // WRITE on TransactionalId
                new AclBinding(
                        new ResourcePattern(ResourceType.TRANSACTIONAL_ID, stream.getMetadata().getName(), PatternType.PREFIXED),
                        new org.apache.kafka.common.acl.AccessControlEntry("User:" + kafkaUser, "*", AclOperation.WRITE, AclPermissionType.ALLOW)
                )
        );
    }

    /**
     * Convert Ns4Kafka connect ACL into Kafka ACL
     *
     * @param acl       The Ns4Kafka ACL
     * @param kafkaUser The ACL owner
     * @return A list of Kafka ACLs
     */
    private List<AclBinding> buildAclBindingsFromConnector(AccessControlEntry acl, String kafkaUser) {
        // READ on Consumer Group connect-{prefix}
        PatternType patternType = PatternType.fromString(acl.getSpec().getResourcePatternType().toString());
        ResourcePattern resourcePattern = new ResourcePattern(ResourceType.GROUP,
                "connect-" + acl.getSpec().getResource(),
                patternType);

        return List.of(
                new AclBinding(
                        resourcePattern,
                        new org.apache.kafka.common.acl.AccessControlEntry("User:" + kafkaUser, "*", AclOperation.READ, AclPermissionType.ALLOW)
                )
        );
    }

    /**
     * Get ACL operations from given resource type
     *
     * @param resourceType The resource type
     * @return A list of ACL operations
     */
    private List<AclOperation> computeAclOperationForOwner(ResourceType resourceType) {
        switch (resourceType) {
            case TOPIC:
                return List.of(AclOperation.WRITE, AclOperation.READ, AclOperation.DESCRIBE_CONFIGS);
            case GROUP:
                return List.of(AclOperation.READ);
            case CLUSTER:
            case TRANSACTIONAL_ID:
            case DELEGATION_TOKEN:
            default:
                throw new IllegalArgumentException("Not implemented yet: " + resourceType);
        }
    }

    /**
     * Delete a given list of ACLs
     *
     * @param toDelete The list of ACLs to delete
     */
    private void deleteACLs(List<AclBinding> toDelete) {
        getAdminClient()
                .deleteAcls(toDelete.stream()
                        .map(AclBinding::toFilter)
                        .collect(Collectors.toList()))
                .values().forEach((key, value) -> {
                    try {
                        value.get(10, TimeUnit.SECONDS);
                        log.info("Success deleting ACL {} on {}", key, this.kafkaAsyncExecutorConfig.getName());
                    } catch (InterruptedException e) {
                        log.error("Error", e);
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.error(String.format("Error while deleting ACL %s on %s", key, this.kafkaAsyncExecutorConfig.getName()), e);
                    }
                });
    }

    /**
     * Delete a given Ns4Kafka ACL
     * Convert Ns4Kafka ACL into Kafka ACLs before deletion
     *
     * @param namespace   The namespace
     * @param ns4kafkaACL The Kafka ACL
     */
    public void deleteNs4KafkaACL(Namespace namespace, AccessControlEntry ns4kafkaACL) {
        List<AclBinding> results = new ArrayList<>();

        if (ns4kafkaACL.getSpec().getResourceType() == AccessControlEntry.ResourceType.TOPIC ||
                ns4kafkaACL.getSpec().getResourceType() == AccessControlEntry.ResourceType.GROUP) {
            results.addAll(buildAclBindingsFromAccessControlEntry(ns4kafkaACL, namespace.getSpec().getKafkaUser()));
        }

        if (ns4kafkaACL.getSpec().getResourceType() == AccessControlEntry.ResourceType.CONNECT &&
                ns4kafkaACL.getSpec().getPermission() == AccessControlEntry.Permission.OWNER) {
            results.addAll(buildAclBindingsFromConnector(ns4kafkaACL, namespace.getSpec().getKafkaUser()));
        }

        deleteACLs(results);
    }

    /**
     * Delete a given Kafka Streams
     *
     * @param namespace   The namespace
     * @param kafkaStream The Kafka Streams
     */
    public void deleteKafkaStreams(Namespace namespace, KafkaStream kafkaStream) {
        List<AclBinding> results = new ArrayList<>(buildAclBindingsFromKafkaStream(kafkaStream, namespace.getSpec().getKafkaUser()));

        deleteACLs(results);
    }

    /**
     * Create a given list of ACLs
     *
     * @param toCreate The list of ACLs to create
     */
    private void createACLs(List<AclBinding> toCreate) {
        getAdminClient().createAcls(toCreate)
                .values()
                .forEach((key, value) -> {
                    try {
                        value.get(10, TimeUnit.SECONDS);
                        log.info("Success creating ACL {} on {}", key, this.kafkaAsyncExecutorConfig.getName());
                    } catch (InterruptedException e) {
                        log.error("Error", e);
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.error(String.format("Error while creating ACL %s on %s", key, this.kafkaAsyncExecutorConfig.getName()), e);
                    }
                });
    }

    /**
     * Getter for admin client service
     *
     * @return The admin client
     */
    private Admin getAdminClient() {
        return kafkaAsyncExecutorConfig.getAdminClient();
    }
}
