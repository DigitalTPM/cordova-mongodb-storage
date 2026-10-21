package com.mikakrooswijk.mongodb.plugin;

import com.mongodb.DocumentToDBRefTransformer;
import com.mongodb.client.FindIterable;
import com.mongodb.embedded.client.MongoEmbeddedSettings;
import com.mongodb.client.model.UpdateOptions;
import org.bson.BsonString;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.util.Log;
import org.bson.types.ObjectId;
import com.mongodb.client.result.DeleteResult;


// Base Stitch Packages
import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.StitchAppClient;

// Packages needed to interact with MongoDB and Stitch
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;

// Necessary component for working with MongoDB Mobile
import com.mongodb.stitch.android.services.mongodb.local.LocalMongoDbService;

import static com.mongodb.client.model.Projections.*;

import java.util.ArrayList;
import java.util.List;

import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.InsertManyOptions;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.codecs.BsonArrayCodec;
import org.bson.codecs.DecoderContext;
import org.bson.json.JsonReader;

public class DatabaseControl {

    MongoClient mobileClient;

    public void initiate(String appID) {    
        final StitchAppClient client = Stitch.initializeDefaultAppClient(appID);
        mobileClient = client.getServiceClient(LocalMongoDbService.clientFactory);
    }

    public Document insertOne(String database, String collection, JSONObject document) {
        MongoCollection<Document> localCollection = mobileClient.getDatabase(database).getCollection(collection);
        Document insertDoc = Document.parse(document.toString());
        localCollection.insertOne(insertDoc);
        return insertDoc;
    }

    public ArrayList<Document> insertMany(String database, String collection, JSONArray documents) throws JSONException {
        MongoCollection<Document> localCollection = mobileClient.getDatabase(database).getCollection(collection);
        
        ArrayList<Document> documentsParsed = new ArrayList<Document>();
        for (int i = 0; i < documents.length(); i++) {
            JSONObject document = documents.getJSONObject(i);
            documentsParsed.add(Document.parse(document.toString()));
        }

        // Desordenada: el servidor no tiene que mantener el orden del array y va más rápido.
        localCollection.insertMany(documentsParsed, new InsertManyOptions().ordered(false));
        return documentsParsed;
    }

    /**
     * Inserta un array JSON que llega como CADENA, parseándolo una sola vez con el parser de
     * BSON. Con el array de org.json cada documento se parseaba (org.json), se volvía a
     * serializar (toString) y se parseaba otra vez (Document.parse): tres pasadas sobre
     * megas de texto por cada llamada. Devuelve cuántos ha insertado.
     */
    public long insertManyJson(String database, String collection, String documentsJson) {
        MongoCollection<BsonDocument> localCollection = mobileClient.getDatabase(database)
                .getCollection(collection, BsonDocument.class);
        BsonArray array = new BsonArrayCodec().decode(new JsonReader(documentsJson), DecoderContext.builder().build());
        List<BsonDocument> documentos = new ArrayList<BsonDocument>(array.size());
        for (BsonValue v : array) {
            if (v.isDocument()) {
                documentos.add(v.asDocument());
            }
        }
        if (documentos.isEmpty()) {
            return 0;
        }
        localCollection.insertMany(documentos, new InsertManyOptions().ordered(false));
        return documentos.size();
    }

    public Document findOne(String database, String collection, JSONObject filter) {
        MongoCollection<Document> localCollection = mobileClient.getDatabase(database).getCollection(collection);
        Document query = Document.parse(filter.toString());

        Document document;
        Document queryResult = localCollection.find(query).first();

        if(queryResult == null){
            document = new Document();
        } else {
            document = queryResult;
        }
        
        return document;
    }

    public Document replaceOne(String database, String collection, JSONObject filter, JSONObject updateJSON) {
        MongoCollection<Document> localCollection = mobileClient.getDatabase(database).getCollection(collection);
        Document query = Document.parse(filter.toString());
        Document updateDoc = Document.parse(updateJSON.toString());
        localCollection.replaceOne(query, updateDoc, new UpdateOptions().upsert(true));

        return updateDoc;
    }

    public ArrayList<Document> findAll(String database, String collection) {
        MongoCollection<Document> localCollection = mobileClient.getDatabase(database).getCollection(collection);
        FindIterable<Document> cursor = localCollection.find();
        ArrayList<Document> results = (ArrayList<Document>) cursor.into(new ArrayList<Document>());
        return results;
    }

    public ArrayList<Document> find(String database, String collection, JSONObject filter, JSONObject sort, Integer skip, Integer limit, String includeFields) {
        MongoCollection<Document> localCollection = mobileClient.getDatabase(database).getCollection(collection);
        Document query = Document.parse(filter.toString());
        Document sortQuery = Document.parse(sort.toString());

        Document document;
        FindIterable<Document> cursor = localCollection.find(query);
        if (skip > -1) {
            cursor.skip(skip);
        }
        if (limit > -1) {
            cursor.limit(limit);
        }
        if (includeFields != null && !includeFields.isEmpty()) {
            cursor.projection(fields(include(includeFields.split(","))));
        }
        cursor.sort(sortQuery);

        ArrayList<Document> results = (ArrayList<Document>) cursor.into(new ArrayList<Document>());
        return results;
    }

    public Long count(String database, String collection, JSONObject filter) {
        MongoCollection<Document> localCollection = mobileClient.getDatabase(database).getCollection(collection);
        Document query = Document.parse(filter.toString());

        return localCollection.count(query);
    }

    public DeleteResult deleteOne(String database, String collection, JSONObject filter) {
        MongoCollection<Document> localCollection = mobileClient.getDatabase(database).getCollection(collection);
        Document query = Document.parse(filter.toString());
        DeleteResult deleteResult = localCollection.deleteOne(query);
        return deleteResult;
    }

    public DeleteResult deleteMany(String database, String collection, JSONObject filter) {
        MongoCollection<Document> localCollection = mobileClient.getDatabase(database).getCollection(collection);
        Document query = Document.parse(filter.toString());
        DeleteResult deleteResult = localCollection.deleteMany(query);
        return deleteResult;
    }

    public Boolean deleteAll(String database, String collection) {
        MongoCollection<Document> localCollection = mobileClient.getDatabase(database).getCollection(collection);
        localCollection.drop();
        return true;
    }

    public Document findById(String database, String collection, String id) {
        MongoCollection<Document> localCollection = mobileClient.getDatabase(database).getCollection(collection);
        Document query = new Document();
        query.put("_id", new ObjectId(id));
        return localCollection.find(query).first();
    }

    public Document updateOne(String database, String collection, JSONObject filter, JSONObject updateJSON) {
        MongoCollection<Document> localCollection = mobileClient.getDatabase(database).getCollection(collection);
        Document query = Document.parse(filter.toString());
        Document updateDoc = Document.parse(updateJSON.toString());
        localCollection.updateOne(query, updateDoc);

        return updateDoc;
    }

    public Document updateMany(String database, String collection, JSONObject filter, JSONObject updateJSON) {
        MongoCollection<Document> localCollection = mobileClient.getDatabase(database).getCollection(collection);
        Document query = Document.parse(filter.toString());
        Document updateDoc = Document.parse(updateJSON.toString());
        localCollection.updateMany(query, updateDoc);

        return Document.parse(updateJSON.toString());
    }

    public ArrayList<Document> createIndex(String database, String collection, String indexProperty) {
        MongoCollection<Document> localCollection = mobileClient.getDatabase(database).getCollection(collection);
        // Solo ascendente: Mongo recorre un índice en los dos sentidos, el descendente era
        // construir y mantener dos índices para lo mismo.
        localCollection.createIndex(Indexes.ascending(indexProperty));
        ArrayList<Document> results = localCollection.listIndexes().into(new ArrayList<Document>());
        return results;
    }
}