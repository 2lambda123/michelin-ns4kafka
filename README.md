ns4kafka
=======================
[![GitHub release](https://img.shields.io/github/v/release/michelin/ns4kafka)](https://github.com/michelin/ns4kafka/releases)
![GitHub commits since latest release (by SemVer)](https://img.shields.io/github/commits-since/michelin/ns4kafka/latest)
[![GitHub Workflow Status](https://img.shields.io/github/workflow/status/michelin/ns4kafka/Snapshot)](https://github.com/michelin/ns4kafka/actions/workflows/on_push_master.yml/)
[![GitHub issues](https://img.shields.io/github/issues/michelin/ns4kafka)](https://github.com/michelin/ns4kafka/issues)
[![SonarCloud Coverage](https://sonarcloud.io/api/project_badges/measure?project=michelin_ns4kafka&metric=coverage)](https://sonarcloud.io/component_measures/metric/coverage/list?id=michelin_ns4kafka)
[![SonarCloud Bugs](https://sonarcloud.io/api/project_badges/measure?project=michelin_ns4kafka&metric=bugs)](https://sonarcloud.io/component_measures/metric/reliability_rating/list?id=michelin_ns4kafka)
[![Docker Pulls](https://img.shields.io/docker/pulls/michelin/ns4kafka?label=ns4kafka%20pulls&logo=Docker)](https://hub.docker.com/r/michelin/ns4kafka/tags)
[![Docker Pulls](https://img.shields.io/docker/pulls/michelin/kafkactl?label=kafkactl%20pulls&logo=Docker)](https://hub.docker.com/r/michelin/kafkactl/tags)

# Table of contents

* [About the project](#about-the-project)
* [Install Kafkactl](#install-kafkactl)
* [Kafkactl commands](#kafkactl-commands)

# About the project

**Ns4kafka** brings to Apache Kafka a new deployment model for your different Kafka resources following the best practices from Kubernetes :

- **Namespace isolation.** You can manage your own Kafka resources within your namespace, and you don't see Kafka resources managed by other namespaces.
  Isolation is provided by granting ownership on names and prefixes to Namespaces
- **Desired state.** You define how the deployed resources should look like and ns4kafka will align the Kafka cluster with your desired state.
- **Server side validation.** Customizable validation rules defined by Kafka OPS to enforce values on Topic configs (``min.insync.replica``, ``replication.factor``, ...) or Connect configs (``connect.class``, ``consumer.override.jaas``, ...).
- **Robust CLI for all your CI/CD needs.** The `kafkactl` command line tool lets you control your resources within your namespace.
  You can deploy resources, list or delete them, reset consumer groups and so on.
- **An evolving list of Resources.** As Kafka project teams, you can now become fully autonomous managing Kafka ``Topics``, ``Connectors``, ``Schemas``, ``AccessControlEntries`` and ``ConsumerGroups``. Kafka Admin are treated equaly only with different resources to manage : `Namespaces`, `RoleBindings`, `ResourceQuotas`, `ResourceValidators`,  `AccessControlEntries`, ...

Ns4kafka is built on top of 2 components : an **API** and a **CLI**.

- The **Ns4kafka** API exposes all the required controllers to list, create and delete Kafka resources. It must be deployed and managed by Kafka administrators.
- The **Kafkactl** CLI is, much like K8S's kubectl, a wrapper on the API to let any user or CI/CD pipeline deploy Kafka resources using yaml descriptors. It is made available to any project who needs to manage Kafka resources.

# Install Kafkactl

**Kafkactl** can be downloaded at [https://github.com/michelin/ns4kafka/releases](https://github.com/michelin/ns4kafka/releases).

It is available in 3 different formats: JAR, Windows executable and Linux executable.

Windows and Linux binaries are generated using GraalVM and native-image.
Java package requires at least Java 11.

`kafkactl` requires 3 variables to work :
- The url of ns4kafka API (provided by your Kafka admin)
- The user default namespace (also provided by your Kafka admin)
- The user security token (a Gitlab Access Token for instance)
    - Technically, LDAP or OIDC is also supported, but it is untested yet.

Setup of these variables can be done in two different ways.

## Configuration file

Create a folder .kafkactl in your home directory:

- **Windows**: C:\Users\<Name>\.kafkactl
- **Linux**: ~/.kafkactl

Create .kafkactl/config.yml with the following content:

```yaml
kafkactl:
  contexts:
    - name: dev
      context:
        api: https://ns4kafka-dev-api.domain.com
        user-token: my_token
        namespace: my_namespace
    - name: prod
      context:
        api: https://ns4kafka-prod-api.domain.com
        user-token: my_token
        namespace: my_namespace
```

For each context, define your GitLab token and your namespace.

Check all your available contexts:

```shell
kafkactl config get-contexts
```

Set yourself on a given context:

```shell
kafkactl config use-context cloud-dev
```

Check your context is applied:

```shell
kafkactl config current-context
```

Check it works by reading the resources of the current context with:

```shell
kafkactl get all
```

## Environment variables

````bash
export KAFKACTL_API=http://ns4kafka-dev-api.domain.com
export KAFKACTL_USER_TOKEN=*******
export KAFKACTL_CURRENT_NAMESPACE=test
````

# Kafkactl commands

Here are given some instructions about all the Kafkactl commands.

To get all the Kafkactl commands:

```bash
kafkactl
```

```bash
user@local:/home/user$ kafkactl
Usage: kafkactl [-hvV] [-n=<optionalNamespace>] [COMMAND]
  -h, --help      Show this help message and exit.
  -n, --namespace=<optionalNamespace>
                  Override namespace defined in config or yaml resource
  -v, --verbose   ...
  -V, --version   Print version information and exit.
Commands:
  apply           Create or update a resource
  get             Get resources by resource type for the current namespace
  delete          Delete a resource
  api-resources   Print the supported API resources on the server
  diff            Get differences between the new resources and the old resource
  reset-offsets   Reset Consumer Group offsets
  delete-records  Deletes all records within a topic
  import          Import resources already present on the Kafka Cluster in
                    ns4kafka
  connectors      Interact with connectors (Pause/Resume/Restart)
  schemas         Update schema compatibility mode
  reset-password  Reset your Kafka password
  config          Manage configuration
```

To get more information about a command:

```bash
kafkactl <command>
```

```bash
user@local:/home/user$ kafkactl apply
Usage: kafkactl apply [-Rv] [--dry-run] [-f=<file>] [-n=<optionalNamespace>]
Create or update a resource
      --dry-run       Does not persist resources. Validate only
  -f, --file=<file>   YAML File or Directory containing YAML resources
  -n, --namespace=<optionalNamespace>
                      Override namespace defined in config or yaml resource
  -R, --recursive     Enable recursive search of file
  -v, --verbose       ...
```

## Apply

> kafkactl apply -f descriptor.yaml
>
> kafkactl apply -f \<folder\>

**Apply** command can be used to create and update one or multiple resources.

**Descriptor.yaml** contains the resources to create/update. You can mix several resource types into a single yaml file.

### Topic

```yaml
---
apiVersion: v1
kind: Topic
metadata:
  name: test.topic1
spec:
  replicationFactor: 3
  partitions: 3
  configs:
    min.insync.replicas: '2'
    cleanup.policy: delete
    retention.ms: '60000'
```

**metadata.name** must be part of your allowed ACLs. Visit your Namespace ACLs descriptor to understand which topics you are allowed to manage.
**spec** properties and more importantly **spec.config** properties validation is dependent on the “Topic Validation rules” associated to your Namespace (which you can view in the Kafka OPS project).

**spec.replicationFactor** and **spec.partitions** are immutable. They cannot be modified once the Topic is created.

```bash
user@local:/home/user$ kafkactl apply -f topic.yml
Success Topic/test.topic1 (created)
# Deploy twice
user@local:/home/user$ kafkactl apply -f topic.yml
Success Topic/test.topic1 (unchanged)
# Deploy folder
user@local:/home/user$ kafkactl apply -f /home/user/ # Applies all .yml files in the specified folder
Success Topic/test.topic1 (created)
Success Connector/test.connect1 (created)
```

### ACL

In order to provide access to your topics to another namespace, you can add an ACL using the following example, where daaagbl0 is your namespace and dbbbgbl0 the namespace that needs access your topics:

```yaml
---
apiVersion: v1
kind: AccessControlEntry
metadata:
  name: acl-topic-aaa-bbb
  namespace: daaagbl0
spec:
  resourceType: TOPIC
  resource: aaa.
  resourcePatternType: PREFIXED
  permission: READ
  grantedTo: dbbbgbl0
```

Available options :
- **spec.resourceType**: TOPIC
- **spec.resourcePatternType**: PREFIXED, LITERAL
- **spec.permission**: READ, WRITE

**spec.grantedTo** must reference a namespace on the same Kafka Cluster as yours. Put differently, you cannot use your namespace on OLS to grant rights to a namespace on HBG.

**spec.resource** must reference any “sub-resource” that you are OWNER of. For example, if you are OWNER of PREFIXED “priv_abc”, you can grant READ or WRITE:
- PREFIXED “priv_abc” (the whole prefix)
- PREFIXED “priv_abc_sub” (a sub prefix)
- LITERAL “priv_abc_topic” (a single topic)

but not :
- PREFIXED “priv_a”
- LITERAL “priv_other_topic”

### Connector

```yaml
---
apiVersion: v1
kind: Connector
metadata:
  name: test.connect1
spec:
  connectCluster: <myEntityConnectCluster>
  config:
    connector.class: <ConnectorClass>
    tasks.max: '1'
    topics: test-topic1
    file: /tmp/test-topic1.out
    consumer.override.sasl.jaas.config: o.a.k.s.s.ScramLoginModule required username="<user>" password="<password>";
```

**metadata.name** and **spec.name** are both mandatory and must be same.
Everything else is dependent on the “Connect Validation rules” associated to your Namespace (which you can view in the Kafka OPS project).

```bash
user@local:/home/user$ kafkactl apply -f connector.yml
Success Connector/test.connect1 (created)
```

### Kafka Streams

This resource only grants the necessary Kafka ACLs for your Kafka Stream to work properly (if you have internal topics). It doesn’t do anything with your actual Kafka Stream code or Kafka Stream deployment.

```yaml
---
apiVersion: v1
kind: KafkaStream
metadata:
  name: <kafkaStreamId>
```

The value in **metadata.name** must correspond to your Kafka Stream **application.id**.

### Schemas

Subjects can be declared by referencing a local _avsc_ file with **spec.schemaFile** or directly inline with **spec.schema**.

**Local file**

```yml
---
apiVersion: v1
kind: Schema
metadata:
  name: project1.topic1-value # your subject name
spec:
  schemaFile: schemas/topic1.avsc # relative to kafkactl binary
```

**Inline**

```yml
---
apiVersion: v1
kind: Schema
metadata:
  name: project1.topic1-value
spec:
  schema: |
    {
      "type": "long"
    }
```

**References**

If your schema references a type which is already stored in the Registry, you can do this:

```yml
---
apiVersion: v1
kind: Schema
metadata:
  name: project1.topic1-value
spec: 
  schema: |
    {
      "type": "record",
      "namespace": "com.michelin.avro",
      "name": "Client",
      "fields": [
        {
          "name": "name",
          "type": "string"
        },
        {
          "name": "address",
          "type": "com.michelin.avro.Address"
        }
      ]
    }
  references:
    - name: com.michelin.avro.Address
      subject: commons.address-value
      version: 1
```

This example assumes there is a subject named commons.address-value with a version 1 already available in the Schema Registry.

Your schemas ACLs are the same as your Topics ACLs.
If you are allowed to create a topic myproject.topic1, then you are automatically allowed to create subject myproject.topic1-key and myproject.topic1-value.

## Delete

> kafkactl delete \<resource-type\> \<name\>
>
> kafkactl delete -f descriptor.yaml
>
> kafkactl delete -f \<folder\>

**Delete** command can be used to delete one or multiple resources.

**Think twice before deleting something.**
Deleting a resource is **permanent** and **instantaneous**. There is no coming back after deleting a Topic or an ACL.
If the Topic contained data, this data is **LOST**.
If the ACL was associated to live/running user, the user will instantly lose access to the resource.

**resource-type** is one of:
- topic
- connect
- acl
- schema
- stream

**resource-name** is the name of the resource to delete.

```bash
user@local:/home/user$ kafkactl delete topic test.topic1
Success Topic/test.topic1 (deleted)
user@local:/home/user$ kafkactl delete connector test.connect1
Success Connector/test.connect1 (deleted)
```

## Get

> kafkactl get all
>
> kafkactl get \<resource-type\>
>
> kafkactl get \<resource-type\> \<name\>

**Get** command can be used to consult one or multiple resources.

**all** fetches all the resources.

**resource-type** is one of:
- topic
- connect
- acl
- schema
- stream

**resource-name** is the name of the resource to consult.

```bash
user@local:/home/user$ kafkactl get topic test.topic1 -o yaml
---
apiVersion: v1
kind: Topic
metadata:
name: test.topic1
spec:
replicationFactor: 3
...
```

## Api-resources

> kafkactl api-resources

**Api-resources** command can be used to consult which resources can be access.

## Diff

> kafkactl diff -f descriptor.yaml
>
> kafkactl diff -f \<folder\>

**Diff** command can be used to check differences between a new descriptor, and the current descriptor of a resource deployed in Ns4Kafka.

```bash
user@local:/home/user$ kafkactl diff -f topic.yml
---Topic/test.topic1-LIVE
+++Topic/test.topic1-MERGED
  configs:
    min.insync.replicas: '2'
    cleanup.policy: delete
-   retention.ms: '60000'
+   retention.ms: '86400000'

user@local:/home/user$ kafkactl apply -f topic.yml
Success Topic/test.topic1 (changed)
```

## Import

> kafkactl import \<resource-type\>

**Import** command can be used to import unsynchronized resources between Ns4Kafka, and the Kafka Broker/Kafka Connect cluster.

**resource-type** is one of:
- topic
- connect

## Reset-offsets

> kafkactl reset-offsets --group \<group\> all-topic \<method\>
>
> kafkactl reset-offsets --group \<group\> --topic \<topic\> \<method\>

**Reset-offsets** command can be used to reset the offsets of consumer groups and topics.

**--group** is one of your consumer group to reset

**--topic/--all-topic** is a given topic or all the topics to reset.

**method** is one of:
- to-earliest
- to-latest
- to-offset
- to-datetime
- shift-by

## Delete-records

> kafkactl delete-records \<topic\>

**Delete-records** command can be used to delete all records within "delete" typed topics.

**topic** is the name of the topic from which records should be deleted.

## Connectors

> kafkactl connectors \<action\> \<connectors\>

**Connectors** command can be used to interact with connectors.

**action** is one of:
- pause
- resume
- restart

**connectors** is a list of connector names separated by space.

## Schemas

> kafkactl schemas \<compatibility\> \<subject\>

**Schemas** command can be used to modify schema compatibility.

**compatibility** is one of:
- GLOBAL,
- BACKWARD,
- BACKWARD_TRANSITIVE,
- FORWARD,
- FORWARD_TRANSITIVE,
- FULL,
- FULL_TRANSITIVE,
- NONE

**subject** is the subject to update the compatibility.

The default compatibility of Confluent Schema Registry is FORWARD_TRANSITIVE. It is referred to as GLOBAL in ns4kafka. You should not change the default compatibility level of your subjects, but if you know what you are doing, it is possible with this command.

## Reset-password

> kafkactl reset-password \<user\>

**Reset-password** command can be used to reset the password of a user.

## Config

> kafkactl config get-contexts
>
> kafkactl config use-context \<context\>
>
> kafkactl config current-context

**Config** command can be used to manage Kafkactl configuration and contexts.

# Administrator Resources

Kafka Admins, we didn't forget you ! On the contrary, it is your role who will get the most out of ns4kafka. Let's have a look.

<details><summary>Show instructions</summary>

1. Create a Namespace

````yaml
# namespace.yml
---
apiVersion: v1
kind: Namespace
metadata:
  name: test
  cluster: local # This is the name of your Kafka cluster
spec:
  kafkaUser: toto # This is the Kafka Principal associated to this Namespace
  connectClusters: 
    - local # Authorize this namespace to deploy Connectors on this Connect cluster
````

````console
user@local:/home/user$ kafkactl apply -f namespace.yml
Success Namespace/test (created)
````

2. It's not enough. Now you must Grant access to Resources to this Namespace
````yaml
# acl.yml
---
apiVersion: v1
kind: AccessControlEntry
metadata:
  name: test-acl-topic
  namespace: test
spec:
  resourceType: TOPIC # Available Types : Connector, ConsumerGroup
  resource: test.
  resourcePatternType: PREFIXED
  permission: OWNER
  grantedTo: test
````

````console
# Since you're admin, you must override the namespace scope with -n
user@local:/home/user$ kafkactl apply -f acl.yml -n test
Success AccessControlEntry/test-acl-topic (created)
````

3. **Still** isn't enough. Now you must link this Namespace to a project team. Enters the RoleBinding Resource
````yaml
# role-binding.yml
---
apiVersion: v1
kind: RoleBinding
metadata:
  name: test-role-group1
  namespace: test
spec:
  role:
    resourceTypes:
    - topics
    - acls
    verbs:
    - GET
    - POST
    - DELETE
  subject:
    subjectType: GROUP
    subjectName: group1/test-ops
````

````console
user@local:/home/user$ kafkactl apply -f role-binding.yml -n test
Success RoleBinding/test-role-group1 (created)
````

4. From now on, members of the group ``group1/test-ops`` (either Gitlab, LDAP or OIDC groups) can use ns4kafka to manage topics starting with `test.` on the `local` Kafka cluster.  
   But wait ! **That's not enough.** Now you should only let them create Topics successfully if and only if their configuration is aligned with your strategy ! Let's add Validators !

````yaml
# namespace.yml
---
apiVersion: v1
kind: Namespace
metadata:
  name: project1
  cluster: local
spec:
  kafkaUser: toto
  connectClusters: 
  - local
  topicValidator:
    validationConstraints:
      partitions: # Enforce sensible partition count
        validation-type: Range
        min: 1
        max: 6
      replication.factor: # Enforce Durability
        validation-type: Range
        min: 3
        max: 3
      min.insync.replicas: # Enforce Durability
        validation-type: Range
        min: 2
        max: 2
      retention.ms: # Prevents Infinite Retention
        validation-type: Range
        min: 60000
        max: 604800000
      cleanup.policy: # This is pointless
        validation-type: ValidList
        validStrings:
        - delete
        - compact
````

````console
user@local:/home/user$ kafkactl apply -f namespace.yml
Success Namespace/test (changed)
````

5. And there's even more to come...
</details>
