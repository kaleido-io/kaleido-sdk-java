# Kaleido Asset Manager SDK

A typed client for the Asset Manager: assets, addresses, pools, collections, activities, data,
events, fragments, NFTs, transfers and balances, plus bulk upsert and bulk query. It is built
on [`core-sdk`](../core-sdk/README.md)'s `ServiceClient`, so it reaches the Asset Manager the
same way through a hosted or a non-hosted binding.

### Creating a client

```java
// Inside a workflow engine provider, as the user behind the request:
var am = new AssetManagerClient(client.getServiceClientOptions("asset-manager", txn.authRef()));

// Outside one, from the asset-manager binding in KALEIDO_CONFIG_FILE:
var am = AssetManagerClient.fromConfig();

// Or straight to a URL, with no binding:
var am = new AssetManagerClient(new ServiceClientOptions.Http(
        "https://my-account.my-kaleido.io/endpoint/my-env/my-asset-manager/rest",
        ServiceBindingAuth.basic("user", "api-key"), 3, 30_000));
```

### Reading and writing

Inputs are built fluently, and only the fields you set are sent. A lookup returns empty when
the object does not exist.

```java
am.createAsset(new AssetInput().name("bond").displayName("Bond").labels(Map.of("env", "prod")));
Optional<Asset> bond = am.getAsset("bond");
ItemsResult<Transfer> page = am.getTransfers(Map.of("limit", 50));
```

### Bulk upsert

`BulkUpsertBuilder` collects updates into one request, merging repeats of the same object
(or skipping or replacing them, per `DuplicateStrategy`). If the request fails because an
object refers to one later in the batch, it retries them one by one until all are written.

```java
var batch = am.newBulkUpsertBuilder();
for (var t : transfers) {
    batch.upsertAddress(new AddressInput().address(t.from()).updateType(CREATE_OR_IGNORE));
    batch.upsertAddress(new AddressInput().address(t.to()).updateType(CREATE_OR_IGNORE));
    batch.upsertTransfer(new TransferInput().protocolId(t.id()).from(t.from()).to(t.to())
            .amount(t.amount()).transactionHash(t.hash()).parent(Parent.pool(poolRef)));
}
batch.addFinalizer(() -> log.info("indexed {} transfers", transfers.size()));
batch.execute();
```

### Bulk query

```java
var result = am.bulkQuery(new BulkQuery()
        .assets(DataModelQuery.create().label("env", "prod"))
        .transfers(DataModelQuery.create().eq("from", address).sort("-created").limit(20)));
```

### Install

```kotlin
implementation("io.kaleido:asset-manager-sdk:<version>")
```
