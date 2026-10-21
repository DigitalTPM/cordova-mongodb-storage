package com.mikakrooswijk.mongodb.plugin;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONObject;

import com.mongodb.client.result.DeleteResult;

import org.bson.Document;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Puente Cordova del MongoDB embebido.
 *
 * <p>Todas las operaciones se ejecutan EN SERIE en un hilo propio. Antes (0.0.2) iban al pool de
 * hilos de Cordova, con operaciones concurrentes sobre el mismo mongod embebido; el cambio de
 * 2023 las pasó todas al hilo de interfaz, que las serializa pero bloquea la pantalla mientras
 * se insertan megas de documentos. Un ejecutor de un solo hilo da la misma serialización sin
 * tocar la interfaz.</p>
 */
public class MongoDBStorage extends CordovaPlugin {

    protected CallbackContext context;
    private final DatabaseControl database = new DatabaseControl();
    private final ExecutorService ejecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mongodb-storage");
        t.setDaemon(true);
        return t;
    });

    /** Una operación contra la base: lo que lanza, el puente lo devuelve como error. */
    private interface Operacion {
        void run() throws Exception;
    }

    private void enSerie(final CallbackContext callbackContext, final Operacion operacion) {
        ejecutor.execute(() -> {
            try {
                operacion.run();
            } catch (Exception e) {
                callbackContext.error(e.toString());
            }
        });
    }

    private static JSONArray aJsonArray(ArrayList<Document> documents) throws Exception {
        JSONArray jsonArray = new JSONArray();
        for (Document document : documents) {
            jsonArray.put(new JSONObject(document.toJson()));
        }
        return jsonArray;
    }

    @Override
    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) {

        context = callbackContext;
        boolean result = true;
        if (action.equals("initiate")) {
            try {
                database.initiate(args.getString(0));
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, true));
            } catch (Exception e) {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.toString()));
            }
        } else if (action.equals("insertOne")) {
            enSerie(callbackContext, () -> {
                Document document = database.insertOne(args.getString(0), args.getString(1), args.getJSONObject(2));
                callbackContext.success(new JSONObject(document.toJson()));
            });
        } else if (action.equals("insertMany")) {
            // Devuelve SOLO cuántos ha insertado. Devolver los documentos obligaba a serializarlos
            // a JSON, parsearlos a org.json, serializarlos para el puente y parsearlos otra vez en
            // el WebView, y nadie los usaba. Acepta los documentos como array (como siempre) o
            // como una cadena JSON, que se parsea una sola vez con el parser de BSON.
            enSerie(callbackContext, () -> {
                Object documentos = args.get(2);
                long insertados;
                if (documentos instanceof String) {
                    insertados = database.insertManyJson(args.getString(0), args.getString(1), (String) documentos);
                } else {
                    insertados = database.insertMany(args.getString(0), args.getString(1), args.getJSONArray(2)).size();
                }
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, (int) insertados));
            });
        } else if (action.equals("findOne")) {
            enSerie(callbackContext, () -> {
                Document document = database.findOne(args.getString(0), args.getString(1), args.getJSONObject(2));
                // TODO: return array containing the object. Swift requires the return object to be in an array
                // for consistancy the Java implementation should do the same.
                if (document.isEmpty()) {
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, false));
                } else {
                    callbackContext.success(new JSONObject(document.toJson()));
                }
            });
        } else if (action.equals("replaceOne")) {
            enSerie(callbackContext, () -> {
                Document document = database.replaceOne(args.getString(0), args.getString(1),
                        args.getJSONObject(2), args.getJSONObject(3));
                callbackContext.success(new JSONObject(document.toJson()));
            });
        } else if (action.equals("findAll")) {
            enSerie(callbackContext, () -> {
                callbackContext.success(aJsonArray(database.findAll(args.getString(0), args.getString(1))));
            });
        } else if (action.equals("find")) {
            enSerie(callbackContext, () -> {
                ArrayList<Document> documents = database.find(args.getString(0), args.getString(1),
                        args.getJSONObject(2), args.getJSONObject(3), args.getInt(4), args.getInt(5), args.getString(6));
                callbackContext.success(aJsonArray(documents));
            });
        } else if (action.equals("count")) {
            enSerie(callbackContext, () -> {
                Long count = database.count(args.getString(0), args.getString(1), args.getJSONObject(2));
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, count));
            });
        } else if (action.equals("deleteOne")) {
            enSerie(callbackContext, () -> {
                DeleteResult deleteResult = database.deleteOne(args.getString(0), args.getString(1), args.getJSONObject(2));
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, deleteResult.getDeletedCount() > 0));
            });
        } else if (action.equals("deleteMany")) {
            enSerie(callbackContext, () -> {
                DeleteResult deleteResult = database.deleteMany(args.getString(0), args.getString(1), args.getJSONObject(2));
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, deleteResult.getDeletedCount() > 0));
            });
        } else if (action.equals("deleteAll")) {
            enSerie(callbackContext, () -> {
                database.deleteAll(args.getString(0), args.getString(1));
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, true));
            });
        } else if (action.equals("updateOne")) {
            enSerie(callbackContext, () -> {
                Document document = database.updateOne(args.getString(0), args.getString(1),
                        args.getJSONObject(2), args.getJSONObject(3));
                callbackContext.success(new JSONObject(document.toJson()));
            });
        } else if (action.equals("updateMany")) {
            enSerie(callbackContext, () -> {
                database.updateMany(args.getString(0), args.getString(1), args.getJSONObject(2), args.getJSONObject(3));
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, true));
            });
        } else if (action.equals("createIndex")) {
            enSerie(callbackContext, () -> {
                callbackContext.success(aJsonArray(database.createIndex(args.getString(0), args.getString(1), args.getString(2))));
            });
        } else {
            context.error("Invalid Action");
            result = false;
        }

        return result;
    }

    @Override
    public void onDestroy() {
        ejecutor.shutdown();
        super.onDestroy();
    }
}
