# CSDE-cordapp-template-kotlin


To help make the process of prototyping CorDapps on Corda 5 release more straight forward we have developed the Cordapp Standard Development Environment (CSDE).

The CSDE is obtained by cloning this CSDE-Cordapp-Template-Kotlin to your local machine. The CSDE provides:

- A pre-setup Cordapp Project which you can use as a starting point to develop your own prototypes.

- A base Gradle configuration which brings in the dependencies you need to write and test a Corda 5 Cordapp.

- A set of Gradle helper tasks which speed up and simplify the development and deployment process.

- Debug configuration for debugging a local Corda cluster.

- The MyFirstFlow code which forms the basis of this getting started documentation, this is located in package com.r3.developers.csdetemplate.flowexample

- A UTXO example in package com.r3.developers.csdetemplate.utxoexample packages

- Ability to configure the Members of the Local Corda Network.

Note, the CSDE is experimental, we may or may not release it as part of Corda 5.0, in part based on developer feedback using it.

To find out how to use the CSDE please refer to the getting started section in the Corda 5 Beta 2 documentation at https://docs.r3.com/

## Digital Currencies App
This is a simple currency + mortgage app to demo functionalities of the next gen Corda platform. This is based on a sample chat application which was extended to match financial use cases.

Use the provided Postman collection + environment variables to transact on Corda.
1. Issue Digital Currency
2. Transfer Digital Currency
3. Withdraw Digital Currency
4. Issue Mortgages
5. Sell Mortgages - DvP of two assets

### Running the Cordapp
Install the Corda CLI: https://docs.r3.com/en/platform/corda/5.0-beta/developing/getting-started/installing-corda-cli.html

Import the [postman collection](CSDE-digital-currency.postman_collection.json) and [environment variables](CSDE-digital-currency.postman_environment.json) into [Postman](https://www.postman.com/)

Use the `POST` requests to initiate a flow and their matching `GET` to check on the status and result of the response. The following order is recommend for initial testing:
1. Issue currency to Alice
2. Transfer currency to Bob
3. Redeem some currency
4. Issue a mortgage to Alice
5. Alice sell this mortgage to Bob

You can change the `POST` body in each request to try different scenarios. The request ids that are auto incremented when the flow is started, see "Tests" on the postman requests. The `GET` requests use these updated request ids when checking on flow status. 

Be aware that as of Beta 3 the CSDE worker takes ~40 seconds to complete. If you run a local cluster flows are much faster taking ~4 seconds to run.

### Setting up CSDE Deployment

1. Begin our test deployment with clicking the `startCorda`. This task will load up the combined Corda workers in docker.
   A successful deployment will allow you to open the REST APIs at: https://localhost:8888/api/v1/swagger#. You can test out some of the
   functions to check connectivity. (GET /cpi function call should return an empty list as for now.)
2. Now deploy the cordapp with a click of `5-vNodeSetup` task. Upon successful deployment of the CPI, the GET /cpi function call should now return the metadata of the cpi you uploaded.

### Setting up a local Corda Cluster
Set up helm, kubernetes and docker on your local system. Instructions are adapted from this blog: https://corda.net/blog/zero-to-corda-5-in-10-minutes-or-less/

Run on Docker Desktop or alternatively on minikube: `minikube start --cpus=6 --memory=8G`

Setup Postgres, Kafka and Corda:
```
helm install prereqs --namespace corda --create-namespace \
  oci://registry-1.docker.io/corda/corda-dev-prereqs \
  --timeout 10m --wait
helm install corda oci://registry-1.docker.io/corda/corda \
  --version 5.0.0-Hawk1.0.1 --namespace corda \
  --values https://gist.githubusercontent.com/davidcurrie/e9c090bdee99ea0a8412fc228218a0e0/raw/723a4ad8886853b07339288c85b86ef8fcb57c1e/corda-prereqs.yaml \
  --timeout 10m --wait
```

Confirm the Corda workers are running:
`kubectl get pods --namespace corda`

Get the admin password for the cluster:
`kubectl get secret corda-initial-admin-user --namespace corda -o go-template='{{ .data.password | base64decode }}'`
OR
`kubectl get secret corda-rest-api-admin --namespace corda -o go-template='{{ .data.password | base64decode }}'`

Update the admin password for the cluster in CSDE config of build.gradle:
```
cordaRpcUser = "admin"
cordaRpcPasswd ="<password here>"
```

Open port forwarding for the Corda cluster API endpoints (ideally in a second terminal tab):
`kubectl port-forward --namespace corda deployment/corda-rest-worker 8888 &`

The Cordapp can now be deployed using the same `5-vNodeSetup` task. Upon successful deployment of the CPI, the GET /cpi function call should now return the metadata of the cpi you uploaded.

#### Cleaning up the Corda Cluster
Quick. `kubectl delete namespace corda`

#### Access Postgres Outside the Corda Cluster
```
kubectl port-forward -n corda svc/prereqs-postgres 5432 &
export PGUSER=corda
export PGPASSWORD=$(kubectl get secret -n corda prereqs-postgres -o go-template='{{ index .data "corda-password" | base64decode }}')
psql -h localhost -p 5432 -d cordacluster
```

#### Access Kafka Outside the Corda Cluster
```
export KAFKA_PASSWORD=$(kubectl get secret -n corda prereqs-kafka -o go-template='{{ index .data "admin-password" | base64decode }}')
echo "security.protocol=SASL_PLAINTEXT" > client.properties
echo "sasl.mechanism=PLAIN" >> client.properties
echo "sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username=\"admin\" password=\"$KAFKA_PASSWORD\";" >> client.properties
kubectl port-forward -n corda $(kubectl get pods -n corda --selector=app.kubernetes.io/component=kafka,app.kubernetes.io/instance=prereqs -o=name) 9094 &
kafka-topics --list --bootstrap-server localhost:9094 --command-config client.properties
```