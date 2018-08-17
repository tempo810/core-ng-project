package core.framework.mongo.impl;

import com.mongodb.ReadPreference;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MapReduceIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import core.framework.log.ActionLogContext;
import core.framework.log.Markers;
import core.framework.mongo.Aggregate;
import core.framework.mongo.Collection;
import core.framework.mongo.Count;
import core.framework.mongo.FindOne;
import core.framework.mongo.Get;
import core.framework.mongo.MapReduce;
import core.framework.mongo.MongoCollection;
import core.framework.mongo.Query;
import core.framework.util.Exceptions;
import core.framework.util.Lists;
import core.framework.util.StopWatch;
import core.framework.util.Strings;
import org.bson.BsonDocument;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author neo
 */
class MongoCollectionImpl<T> implements MongoCollection<T> {
    private final Logger logger = LoggerFactory.getLogger(MongoCollectionImpl.class);
    private final MongoImpl mongo;
    private final Class<T> entityClass;
    private final String collectionName;
    private final EntityValidator<T> validator;
    private com.mongodb.client.MongoCollection<T> collection;

    MongoCollectionImpl(MongoImpl mongo, Class<T> entityClass) {
        this.mongo = mongo;
        this.entityClass = entityClass;
        validator = new EntityValidator<>(entityClass);
        collectionName = entityClass.getDeclaredAnnotation(Collection.class).name();
    }

    @Override
    public long count(Count count) {
        var watch = new StopWatch();
        Bson filter = count.filter == null ? new BsonDocument() : count.filter;
        try {
            return collection(count.readPreference).count(filter, new CountOptions().maxTime(mongo.timeoutInMs, TimeUnit.MILLISECONDS));
        } finally {
            long elapsed = watch.elapsed();
            ActionLogContext.track("mongoDB", elapsed, 1, 0);
            logger.debug("count, collection={}, filter={}, readPref={}, elapsed={}",
                    collectionName,
                    new BsonParam(filter, mongo.registry),
                    count.readPreference == null ? null : count.readPreference.getName(),
                    elapsed);
            checkSlowOperation(elapsed);
        }
    }

    @Override
    public void insert(T entity) {
        var watch = new StopWatch();
        validator.validate(entity);
        try {
            collection().insertOne(entity);
        } finally {
            long elapsed = watch.elapsed();
            ActionLogContext.track("mongoDB", elapsed, 0, 1);
            logger.debug("insert, collection={}, elapsed={}", collectionName, elapsed);
            checkSlowOperation(elapsed);
        }
    }

    @Override
    public void bulkInsert(List<T> entities) {
        if (entities == null || entities.isEmpty()) throw Exceptions.error("entities must not be empty");

        StopWatch watch = new StopWatch();
        for (T entity : entities) {
            validator.validate(entity);
        }
        try {
            collection().insertMany(entities, new InsertManyOptions().ordered(false));
        } finally {
            long elapsed = watch.elapsed();
            int size = entities.size();
            ActionLogContext.track("mongoDB", elapsed, 0, size);
            logger.debug("bulkInsert, collection={}, size={}, elapsed={}", collectionName, size, elapsed);
            checkSlowOperation(elapsed);
        }
    }

    @Override
    public Optional<T> get(Get get) {
        if (get.id == null) throw new Error("get.id must not be null");

        var watch = new StopWatch();
        int returnedRows = 0;
        try {
            T result = collection(get.readPreference).find(Filters.eq("_id", get.id)).first();
            if (result != null) returnedRows = 1;
            return Optional.ofNullable(result);
        } finally {
            long elapsed = watch.elapsed();
            ActionLogContext.track("mongoDB", elapsed, returnedRows, 0);
            logger.debug("get, collection={}, id={}, readPref={}, elapsed={}",
                    collectionName,
                    get.id,
                    get.readPreference == null ? null : get.readPreference.getName(),
                    elapsed);
            checkSlowOperation(elapsed);
        }
    }

    @Override
    public Optional<T> findOne(FindOne findOne) {
        var watch = new StopWatch();
        Bson filter = findOne.filter == null ? new BsonDocument() : findOne.filter;
        int returnedRows = 0;
        try {
            List<T> results = new ArrayList<>(2);
            FindIterable<T> query = collection()
                    .find(filter)
                    .limit(2)
                    .maxTime(mongo.timeoutInMs, TimeUnit.MILLISECONDS);
            fetch(query, results);
            if (results.isEmpty()) return Optional.empty();
            if (results.size() > 1) throw new Error("more than one row returned");
            returnedRows = 1;
            return Optional.of(results.get(0));
        } finally {
            long elapsed = watch.elapsed();
            ActionLogContext.track("mongoDB", elapsed, returnedRows, 0);
            logger.debug("findOne, collection={}, filter={}, readPref={}, elapsed={}",
                    collectionName,
                    new BsonParam(filter, mongo.registry),
                    findOne.readPreference == null ? null : findOne.readPreference.getName(),
                    elapsed);
            checkSlowOperation(elapsed);
        }
    }

    @Override
    public List<T> find(Query query) {
        var watch = new StopWatch();
        List<T> results = query.limit == null ? new ArrayList<>() : new ArrayList<>(query.limit);
        try {
            FindIterable<T> mongoQuery = mongoQuery(query).maxTime(mongo.timeoutInMs, TimeUnit.MILLISECONDS);
            fetch(mongoQuery, results);
            checkTooManyRowsReturned(results.size());
            return results;
        } finally {
            long elapsed = watch.elapsed();
            int size = results.size();
            ActionLogContext.track("mongoDB", elapsed, size, 0);
            logger.debug("find, collection={}, filter={}, projection={}, sort={}, skip={}, limit={}, readPref={}, returnedRows={}, elapsed={}",
                    collectionName,
                    new BsonParam(query.filter, mongo.registry),
                    new BsonParam(query.projection, mongo.registry),
                    new BsonParam(query.sort, mongo.registry),
                    query.skip,
                    query.limit,
                    query.readPreference == null ? null : query.readPreference.getName(),
                    size,
                    elapsed);
            checkSlowOperation(elapsed);
        }
    }

    @Override
    public void forEach(Query query, Consumer<T> consumer) {
        StopWatch watch = new StopWatch();
        int returnedRows = 0;
        try {
            FindIterable<T> mongoQuery = mongoQuery(query);
            returnedRows = apply(mongoQuery, consumer);
        } finally {
            long elapsed = watch.elapsed();
            ActionLogContext.track("mongoDB", elapsed, returnedRows, 0);
            logger.debug("forEach, collection={}, filter={}, projection={}, sort={}, skip={}, limit={}, readPref={}, returnedRows={}, elapsed={}",
                    collectionName,
                    new BsonParam(query.filter, mongo.registry),
                    new BsonParam(query.projection, mongo.registry),
                    new BsonParam(query.sort, mongo.registry),
                    query.skip,
                    query.limit,
                    query.readPreference == null ? null : query.readPreference.getName(),
                    returnedRows,
                    elapsed);
        }
    }

    private FindIterable<T> mongoQuery(Query query) {
        FindIterable<T> mongoQuery = collection(query.readPreference).find(query.filter == null ? new BsonDocument() : query.filter);
        if (query.projection != null) mongoQuery.projection(query.projection);
        if (query.sort != null) mongoQuery.sort(query.sort);
        if (query.skip != null) mongoQuery.skip(query.skip);
        if (query.limit != null) mongoQuery.limit(query.limit);
        return mongoQuery;
    }

    private int apply(MongoIterable<T> mongoQuery, Consumer<T> consumer) {
        int total = 0;
        try (MongoCursor<T> cursor = mongoQuery.iterator()) {
            while (cursor.hasNext()) {
                T result = cursor.next();
                total++;
                consumer.accept(result);
            }
        }
        return total;
    }

    @Override
    public <V> List<V> aggregate(Aggregate<V> aggregate) {
        if (aggregate.pipeline == null || aggregate.pipeline.isEmpty()) throw new Error("aggregate.pipeline must not be empty");
        if (aggregate.resultClass == null) throw new Error("aggregate.resultClass must not be null");

        var watch = new StopWatch();
        List<V> results = Lists.newArrayList();
        try {
            AggregateIterable<V> query = collection(aggregate.readPreference)
                    .aggregate(aggregate.pipeline, aggregate.resultClass)
                    .maxTime(mongo.timeoutInMs, TimeUnit.MILLISECONDS);
            fetch(query, results);
            checkTooManyRowsReturned(results.size());
            return results;
        } finally {
            long elapsed = watch.elapsed();
            int size = results.size();
            ActionLogContext.track("mongoDB", elapsed, size, 0);
            logger.debug("aggregate, collection={}, pipeline={}, readPref={}, returnedRows={}, elapsed={}",
                    collectionName,
                    aggregate.pipeline.stream().map(stage -> new BsonParam(stage, mongo.registry)).toArray(),
                    aggregate.readPreference == null ? null : aggregate.readPreference.getName(),
                    size,
                    elapsed);
        }
    }

    @Override
    public <V> List<V> mapReduce(MapReduce<V> mapReduce) {
        if (Strings.isEmpty(mapReduce.mapFunction)) throw new Error("mapReduce.mapFunction must not be empty");
        if (Strings.isEmpty(mapReduce.reduceFunction)) throw new Error("mapReduce.reduceFunction must not be empty");
        if (mapReduce.resultClass == null) throw new Error("mapReduce.resultClass must not be null");

        var watch = new StopWatch();
        List<V> results = Lists.newArrayList();
        try {
            MapReduceIterable<V> query = collection(mapReduce.readPreference)
                    .mapReduce(mapReduce.mapFunction, mapReduce.reduceFunction, mapReduce.resultClass)
                    .maxTime(mongo.timeoutInMs, TimeUnit.MILLISECONDS);
            if (mapReduce.filter != null) query.filter(mapReduce.filter);
            fetch(query, results);
            checkTooManyRowsReturned(results.size());
            return results;
        } finally {
            long elapsed = watch.elapsed();
            int size = results.size();
            ActionLogContext.track("mongoDB", elapsed, size, 0);
            logger.debug("mapReduce, collection={}, map={}, reduce={}, filter={}, readPref={}, returnedRows={}, elapsed={}",
                    collectionName,
                    mapReduce.mapFunction,
                    mapReduce.reduceFunction,
                    new BsonParam(mapReduce.filter, mongo.registry),
                    mapReduce.readPreference == null ? null : mapReduce.readPreference.getName(),
                    size,
                    elapsed);
        }
    }

    private <V> void fetch(MongoIterable<V> iterable, List<V> results) {
        try (MongoCursor<V> cursor = iterable.iterator()) {
            while (cursor.hasNext()) {
                results.add(cursor.next());
            }
        }
    }

    @Override
    public void replace(T entity) {
        var watch = new StopWatch();
        Object id = null;
        validator.validate(entity);
        try {
            id = mongo.codecs.id(entity);
            if (id == null) throw Exceptions.error("entity must have id, entityClass={}", entityClass.getCanonicalName());
            collection().replaceOne(Filters.eq("_id", id), entity, new UpdateOptions().upsert(true));
        } finally {
            long elapsed = watch.elapsed();
            ActionLogContext.track("mongoDB", elapsed, 0, 1);
            logger.debug("replace, collection={}, id={}, elapsed={}", collectionName, id, elapsed);
            checkSlowOperation(elapsed);
        }
    }

    @Override
    public void bulkReplace(List<T> entities) {
        var watch = new StopWatch();
        if (entities == null || entities.isEmpty()) throw Exceptions.error("entities must not be empty");
        int size = entities.size();
        for (T entity : entities) {
            validator.validate(entity);
        }
        try {
            List<ReplaceOneModel<T>> models = new ArrayList<>(size);
            for (T entity : entities) {
                Object id = mongo.codecs.id(entity);
                if (id == null) throw Exceptions.error("entity must have id, entityClass={}", entityClass.getCanonicalName());
                models.add(new ReplaceOneModel<>(Filters.eq("_id", id), entity, new UpdateOptions().upsert(true)));
            }
            collection().bulkWrite(models, new BulkWriteOptions().ordered(false));
        } finally {
            long elapsed = watch.elapsed();
            ActionLogContext.track("mongoDB", elapsed, 0, size);
            logger.debug("bulkReplace, collection={}, size={}, elapsed={}", collectionName, size, elapsed);
            checkSlowOperation(elapsed);
        }
    }

    @Override
    public long update(Bson filter, Bson update) {
        var watch = new StopWatch();
        long updatedRows = 0;
        try {
            UpdateResult result = collection().updateMany(filter, update);
            updatedRows = result.getModifiedCount();
            return updatedRows;
        } finally {
            long elapsed = watch.elapsed();
            ActionLogContext.track("mongoDB", elapsed, 0, (int) updatedRows);
            logger.debug("update, collection={}, filter={}, update={}, updatedRows={}, elapsed={}",
                    collectionName,
                    new BsonParam(filter, mongo.registry),
                    new BsonParam(update, mongo.registry),
                    updatedRows,
                    elapsed);
            checkSlowOperation(elapsed);
        }
    }

    @Override
    public boolean delete(Object id) {
        var watch = new StopWatch();
        long deletedRows = 0;
        try {
            DeleteResult result = collection().deleteOne(Filters.eq("_id", id));
            deletedRows = result.getDeletedCount();
            return deletedRows == 1;
        } finally {
            long elapsed = watch.elapsed();
            ActionLogContext.track("mongoDB", elapsed, 0, (int) deletedRows);
            logger.debug("delete, collection={}, id={}, elapsed={}", collectionName, id, elapsed);
            checkSlowOperation(elapsed);
        }
    }

    @Override
    public long delete(Bson filter) {
        var watch = new StopWatch();
        long deletedRows = 0;
        try {
            DeleteResult result = collection().deleteMany(filter == null ? new BsonDocument() : filter);
            deletedRows = result.getDeletedCount();
            return deletedRows;
        } finally {
            long elapsed = watch.elapsed();
            ActionLogContext.track("mongoDB", elapsed, 0, (int) deletedRows);
            logger.debug("delete, collection={}, filter={}, deletedRows={}, elapsed={}", collectionName, new BsonParam(filter, mongo.registry), deletedRows, elapsed);
            checkSlowOperation(elapsed);
        }
    }

    @Override
    public long bulkDelete(List<?> ids) {
        var watch = new StopWatch();
        int size = ids.size();
        int deletedRows = 0;
        try {
            List<DeleteOneModel<T>> models = new ArrayList<>(size);
            for (Object id : ids) {
                models.add(new DeleteOneModel<>(Filters.eq("_id", id)));
            }
            BulkWriteResult result = collection().bulkWrite(models, new BulkWriteOptions().ordered(false));
            deletedRows = result.getDeletedCount();
            return deletedRows;
        } finally {
            long elapsed = watch.elapsed();
            ActionLogContext.track("mongoDB", elapsed, 0, deletedRows);
            logger.debug("bulkDelete, collection={}, ids={}, size={}, elapsed={}", collectionName, ids, size, elapsed);
            checkSlowOperation(elapsed);
        }
    }

    private void checkSlowOperation(long elapsed) {
        if (elapsed > mongo.slowOperationThresholdInNanos) {
            logger.warn(Markers.errorCode("SLOW_MONGODB"), "slow mongoDB query, elapsed={}", elapsed);
        }
    }

    private void checkTooManyRowsReturned(int size) {
        if (size > mongo.tooManyRowsReturnedThreshold) {
            logger.warn(Markers.errorCode("TOO_MANY_ROWS_RETURNED"), "too many rows returned, returnedRows={}", size);
        }
    }

    private com.mongodb.client.MongoCollection<T> collection(ReadPreference readPreference) {
        if (readPreference != null)
            return collection().withReadPreference(readPreference);
        return collection();
    }

    private com.mongodb.client.MongoCollection<T> collection() {
        if (collection == null) {
            collection = mongo.mongoCollection(entityClass);
        }
        return collection;
    }

    static class BsonParam {
        final Bson bson;
        final CodecRegistry registry;

        BsonParam(Bson bson, CodecRegistry registry) {
            this.bson = bson;
            this.registry = registry;
        }

        @Override
        public String toString() {
            if (bson == null) return "null";
            return bson.toBsonDocument(null, registry).toJson();
        }
    }
}
