package com.kems.fleet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class MainActivity extends Activity {

    // ==================================================
    // CONFIGURATION
    // ==================================================

    private final String GREEN = "#65E6A5";
    private final String BG = "#0C1714";
    private final String PANEL = "#14251F";
    private final String WHITE = "#F2FFF8";

    private static final int REQUEST_IMPORT_JSON = 501;
    private static final int REQUEST_IMPORT_LOGO = 502;

    private static final int MAX_JSON_SIZE = 20 * 1024 * 1024;
    private static final int MAX_LOGO_SIZE = 5 * 1024 * 1024;

    private SharedPreferences prefs;

    private String serverUrl = "";
    private String token = "";
    private String username = "";
    private String role = "";

    private LinearLayout root;
    private LinearLayout content;

    private TextView message;
    private TextView jsonFileLabel;
    private ImageView logoView;

    private JSONArray vehicles = new JSONArray();

    private Uri selectedJsonUri = null;
    private String selectedJsonName = "Aucun fichier sélectionné";

    private final ExecutorService executor =
            Executors.newFixedThreadPool(12);

    // ==================================================
    // DÉMARRAGE
    // ==================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        try {

            prefs = getSharedPreferences(
                    "kems_fleet",
                    MODE_PRIVATE
            );

            serverUrl = prefs.getString("server", "");
            token = prefs.getString("token", "");
            username = prefs.getString("username", "");
            role = prefs.getString("role", "");

            if (!token.isEmpty()) {

                showDashboard();
                loadVehicles();

            } else {

                showLogin();
            }

        } catch (Exception e) {

            e.printStackTrace();

            token = "";

            showLogin();
            showMessage("Erreur de démarrage : " + e.getMessage());
        }
    }

    // ==================================================
    // INTERFACE
    // ==================================================

    private void createScreen(String title) {

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor(BG));
        root.setPadding(18, 10, 18, 10);

        logoView = new ImageView(this);
        logoView.setAdjustViewBounds(true);
        logoView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        LinearLayout.LayoutParams logoParams =
                new LinearLayout.LayoutParams(180, 90);

        logoParams.gravity = Gravity.CENTER;
        logoParams.setMargins(0, 5, 0, 5);

        root.addView(logoView, logoParams);

        loadSavedLogo();

        TextView heading = new TextView(this);

        heading.setText("KEMS FLEET\n" + title);
        heading.setTextColor(Color.parseColor(GREEN));
        heading.setTextSize(21);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(5, 8, 5, 15);
        heading.setTypeface(null, 1);

        root.addView(heading);

        ScrollView scroll = new ScrollView(this);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(3, 5, 3, 15);

        scroll.addView(content);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(-1, 0, 1)
        );

        message = new TextView(this);

        message.setTextColor(Color.parseColor(GREEN));
        message.setTextSize(13);
        message.setGravity(Gravity.CENTER);
        message.setPadding(5, 8, 5, 8);

        root.addView(message);

        setContentView(root);
    }

    private TextView text(String value, int size) {

        TextView t = new TextView(this);

        t.setText(value);
        t.setTextColor(Color.parseColor(WHITE));
        t.setTextSize(size);
        t.setPadding(8, 10, 8, 10);

        return t;
    }

    private EditText field(String hint) {

        EditText e = new EditText(this);

        e.setHint(hint);
        e.setHintTextColor(Color.GRAY);
        e.setTextColor(Color.WHITE);
        e.setSingleLine(true);
        e.setPadding(14, 10, 14, 10);
        e.setBackgroundColor(Color.parseColor(PANEL));

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, -2);

        p.setMargins(0, 6, 0, 6);

        e.setLayoutParams(p);

        return e;
    }

    private Button button(String label, Runnable action) {

        Button b = new Button(this);

        b.setText(label);
        b.setTextColor(Color.parseColor(BG));

        b.setBackgroundTintList(
                ColorStateList.valueOf(Color.parseColor(GREEN))
        );

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, -2);

        p.setMargins(0, 5, 0, 5);

        b.setLayoutParams(p);

        b.setOnClickListener(v -> {

            try {
                action.run();

            } catch (Exception e) {

                showMessage("Erreur : " + e.getMessage());
            }
        });

        return b;
    }

    private void showMessage(String value) {

        runOnUiThread(() -> {

            if (message != null) {
                message.setText(value);
            }

        });
    }

    private void notifyUser(String value) {

        runOnUiThread(() ->
                Toast.makeText(
                        MainActivity.this,
                        value,
                        Toast.LENGTH_LONG
                ).show()
        );
    }

    private void addSpace() {

        TextView space = new TextView(this);
        space.setHeight(12);

        content.addView(space);
    }

    // ==================================================
    // LOGO DE L'APPLICATION
    // ==================================================

    private File getLogoFile() {

        return new File(
                getFilesDir(),
                "kems_fleet_logo.png"
        );
    }

    private void loadSavedLogo() {

        if (logoView == null) {
            return;
        }

        File file = getLogoFile();

        if (!file.exists()) {
            logoView.setImageDrawable(null);
            return;
        }

        try {

            BitmapFactory.Options options =
                    new BitmapFactory.Options();

            options.inJustDecodeBounds = true;

            BitmapFactory.decodeFile(
                    file.getAbsolutePath(),
                    options
            );

            int sampleSize = 1;

            while (options.outWidth / sampleSize > 1000 ||
                    options.outHeight / sampleSize > 1000) {

                sampleSize *= 2;
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize;

            Bitmap bitmap = BitmapFactory.decodeFile(
                    file.getAbsolutePath(),
                    options
            );

            if (bitmap != null) {
                logoView.setImageBitmap(bitmap);
            }

        } catch (Exception e) {

            showMessage("Impossible de charger le logo.");
        }
    }

    private void chooseLogo() {

        Intent intent = new Intent(
                Intent.ACTION_OPEN_DOCUMENT
        );

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        intent.setType("image/*");

        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        );

        try {

            startActivityForResult(
                    intent,
                    REQUEST_IMPORT_LOGO
            );

        } catch (Exception e) {

            showMessage(
                    "Impossible d'ouvrir les fichiers du téléphone."
            );
        }
    }

    private void saveLogo(Uri uri) {

        if (uri == null) {
            return;
        }

        showMessage("Enregistrement du logo...");

        executor.execute(() -> {

            File destination = getLogoFile();

            File temporary = new File(
                    getFilesDir(),
                    "kems_fleet_logo.tmp"
            );

            try {

                InputStream input =
                        getContentResolver().openInputStream(uri);

                if (input == null) {

                    throw new Exception(
                            "Impossible de lire l'image."
                    );
                }

                int total = 0;

                try (
                        InputStream in = input;
                        OutputStream out =
                                new FileOutputStream(temporary)
                ) {

                    byte[] buffer = new byte[8192];

                    int count;

                    while ((count = in.read(buffer)) != -1) {

                        total += count;

                        if (total > MAX_LOGO_SIZE) {

                            throw new Exception(
                                    "Logo trop volumineux. Maximum 5 Mo."
                            );
                        }

                        out.write(buffer, 0, count);
                    }
                }

                if (total == 0) {

                    throw new Exception("Image vide.");
                }

                if (!temporary.renameTo(destination)) {

                    try (
                            InputStream in =
                                    new java.io.FileInputStream(temporary);
                            OutputStream out =
                                    new FileOutputStream(destination)
                    ) {

                        byte[] buffer = new byte[8192];

                        int count;

                        while ((count = in.read(buffer)) != -1) {
                            out.write(buffer, 0, count);
                        }
                    }

                    temporary.delete();
                }

                runOnUiThread(() -> {

                    loadSavedLogo();

                    showMessage("Logo enregistré avec succès.");

                    notifyUser("Logo KEMS FLEET enregistré.");

                });

            } catch (Exception e) {

                temporary.delete();

                showMessage(
                        "Erreur logo : " + e.getMessage()
                );
            }
        });
    }

    private void deleteLogo() {

        new AlertDialog.Builder(this)
                .setTitle("Supprimer le logo")
                .setMessage(
                        "Voulez-vous supprimer le logo de l'application ?"
                )
                .setNegativeButton("ANNULER", null)
                .setPositiveButton(
                        "SUPPRIMER",
                        (dialog, which) -> {

                            File file = getLogoFile();

                            if (file.exists()) {
                                file.delete();
                            }

                            if (logoView != null) {
                                logoView.setImageDrawable(null);
                            }

                            showMessage("Logo supprimé.");
                        }
                )
                .show();
    }

    // ==================================================
    // ADRESSE SERVEUR
    // ==================================================

    private String normalizeServerUrl(String address) {

        address = address.trim();

        if (address.isEmpty()) {
            return "";
        }

        if (!address.startsWith("http://") &&
                !address.startsWith("https://")) {

            address = "http://" + address;
        }

        address = address.replaceAll("/+$", "");

        try {

            URL url = new URL(address);

            if (url.getPort() == -1) {

                address = url.getProtocol() +
                        "://" +
                        url.getHost() +
                        ":3001";
            }

            return address;

        } catch (Exception e) {

            throw new IllegalArgumentException(
                    "Adresse serveur invalide."
            );
        }
    }

    // ==================================================
    // CONNEXION
    // ==================================================

    private void showLogin() {

        createScreen("CONNEXION");

        EditText server = field(
                "Adresse serveur : 192.168.1.10:3001"
        );

        server.setText(serverUrl);

        EditText user = field("Nom d'utilisateur");

        EditText password = field("Mot de passe");

        password.setInputType(
                InputType.TYPE_CLASS_TEXT |
                        InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        content.addView(text("Adresse du serveur", 14));
        content.addView(server);

        content.addView(button(
                "DÉTECTER LE SERVEUR WI-FI",
                () -> detectServer(server)
        ));

        content.addView(button(
                "TESTER LA CONNEXION",
                () -> {

                    try {

                        serverUrl = normalizeServerUrl(
                                server.getText().toString()
                        );

                        testConnection();

                    } catch (Exception e) {

                        showMessage(e.getMessage());
                    }
                }
        ));

        content.addView(text("Identifiants", 14));
        content.addView(user);
        content.addView(password);

        content.addView(button(
                "SE CONNECTER",
                () -> {

                    try {

                        serverUrl = normalizeServerUrl(
                                server.getText().toString()
                        );

                        String u = user.getText().toString().trim();
                        String p = password.getText().toString();

                        if (serverUrl.isEmpty() ||
                                u.isEmpty() ||
                                p.isEmpty()) {

                            showMessage(
                                    "Veuillez remplir tous les champs."
                            );

                            return;
                        }

                        login(u, p);

                    } catch (Exception e) {

                        showMessage(e.getMessage());
                    }
                }
        ));

        content.addView(button(
                "PARAMÈTRES SERVEUR",
                this::showSettings
        ));
    }

    private void login(String user, String password) {

        showMessage("Connexion en cours...");

        executor.execute(() -> {

            try {

                JSONObject data = new JSONObject();

                data.put("username", user);
                data.put("password", password);

                JSONObject result = request(
                        "POST",
                        "/api/auth/login",
                        data,
                        false
                );

                if (!result.optBoolean("success", false)) {

                    throw new Exception(
                            result.optString(
                                    "message",
                                    "Identifiants incorrects."
                            )
                    );
                }

                String newToken = result.optString("token", "");

                JSONObject account = result.optJSONObject("user");

                if (newToken.isEmpty()) {

                    throw new Exception(
                            "Le serveur n'a pas retourné de token."
                    );
                }

                String accountRole = account == null
                        ? ""
                        : account.optString("role", "");

                token = newToken;
                username = user;
                role = accountRole;

                prefs.edit()
                        .putString("server", serverUrl)
                        .putString("token", token)
                        .putString("username", username)
                        .putString("role", role)
                        .apply();

                runOnUiThread(() -> {

                    showDashboard();
                    loadVehicles();

                });

            } catch (Exception e) {

                showMessage("Erreur : " + e.getMessage());
            }
        });
    }

    // ==================================================
    // REQUÊTES API
    // ==================================================

    private JSONObject request(
            String method,
            String path,
            JSONObject body,
            boolean authenticated
    ) throws Exception {

        String base = serverUrl.trim().replaceAll("/+$", "");

        if (base.isEmpty()) {
            throw new Exception("Adresse serveur non configurée.");
        }

        HttpURLConnection connection = null;

        try {

            URL url = new URL(base + path);

            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod(method);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(20000);

            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );

            if (authenticated && !token.isEmpty()) {

                connection.setRequestProperty(
                        "Authorization",
                        "Bearer " + token
                );
            }

            if (body != null) {

                connection.setDoOutput(true);

                connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );

                try (OutputStream os = connection.getOutputStream()) {

                    os.write(
                            body.toString().getBytes(StandardCharsets.UTF_8)
                    );
                }
            }

            int status = connection.getResponseCode();

            InputStream stream = status >= 200 && status < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            StringBuilder response = new StringBuilder();

            if (stream != null) {

                try (
                        InputStream in = stream;
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(
                                        in,
                                        StandardCharsets.UTF_8
                                )
                        )
                ) {

                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
            }

            JSONObject result;

            try {

                result = response.length() == 0
                        ? new JSONObject()
                        : new JSONObject(response.toString());

            } catch (Exception e) {

                throw new Exception(
                        "Réponse serveur invalide (HTTP " + status + ")."
                );
            }

            if (status < 200 || status >= 300) {

                throw new Exception(
                        result.optString(
                                "message",
                                "Erreur HTTP " + status
                        )
                );
            }

            return result;

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // ==================================================
    // TEST SERVEUR
    // ==================================================

    private void testConnection() {

        if (serverUrl.isEmpty()) {

            showMessage("Saisissez l'adresse du serveur.");
            return;
        }

        showMessage("Test de connexion...");

        executor.execute(() -> {

            try {

                JSONObject result = request(
                        "GET",
                        "/api/health",
                        null,
                        false
                );

                prefs.edit()
                        .putString("server", serverUrl)
                        .apply();

                showMessage(
                        "SERVEUR ACCESSIBLE\n" +
                                "Application : " +
                                result.optString("application", "KEMS") +
                                "\nVersion : " +
                                result.optString("version", "?")
                );

            } catch (Exception e) {

                showMessage(
                        "SERVEUR INACCESSIBLE : " + e.getMessage()
                );
            }
        });
    }

    // ==================================================
    // DÉTECTION AUTOMATIQUE WI-FI
    // ==================================================

    private void detectServer(EditText serverField) {

        showMessage("Recherche du serveur sur le Wi-Fi...");

        executor.execute(() -> {

            ExecutorService scanPool =
                    Executors.newFixedThreadPool(32);

            try {

                WifiManager wifi = (WifiManager)
                        getApplicationContext().getSystemService(
                                Context.WIFI_SERVICE
                        );

                if (wifi == null || !wifi.isWifiEnabled()) {

                    throw new Exception("Activez le Wi-Fi.");
                }

                int ip = wifi.getDhcpInfo().ipAddress;

                if (ip == 0) {

                    throw new Exception(
                            "Adresse Wi-Fi introuvable."
                    );
                }

                String localIp = Formatter.formatIpAddress(ip);

                String[] parts = localIp.split("\\.");

                if (parts.length != 4) {

                    throw new Exception(
                            "Adresse IP non reconnue."
                    );
                }

                String subnet =
                        parts[0] + "." +
                                parts[1] + "." +
                                parts[2] + ".";

                CompletionService<String> completion =
                        new ExecutorCompletionService<>(scanPool);

                int submitted = 0;

                for (int i = 1; i <= 254; i++) {

                    final String address = subnet + i;

                    if (address.equals(localIp)) continue;

                    completion.submit(() -> {

                        HttpURLConnection c = null;

                        try {

                            String candidate =
                                    "http://" + address + ":3001";

                            URL url = new URL(
                                    candidate + "/api/health"
                            );

                            c = (HttpURLConnection)
                                    url.openConnection();

                            c.setConnectTimeout(900);
                            c.setReadTimeout(900);
                            c.setRequestMethod("GET");

                            if (c.getResponseCode() == 200) {
                                return candidate;
                            }

                        } catch (Exception ignored) {

                        } finally {

                            if (c != null) {
                                c.disconnect();
                            }
                        }

                        return null;
                    });

                    submitted++;
                }

                long end =
                        System.currentTimeMillis() + 20000;

                for (int i = 0; i < submitted; i++) {

                    long remaining =
                            end - System.currentTimeMillis();

                    if (remaining <= 0) break;

                    Future<String> future =
                            completion.poll(
                                    remaining,
                                    TimeUnit.MILLISECONDS
                            );

                    if (future == null) break;

                    String found = future.get();

                    if (found != null) {

                        serverUrl = found;

                        prefs.edit()
                                .putString("server", serverUrl)
                                .apply();

                        runOnUiThread(() ->
                                serverField.setText(serverUrl)
                        );

                        showMessage(
                                "SERVEUR DÉTECTÉ : " + serverUrl
                        );

                        return;
                    }
                }

                showMessage(
                        "Serveur non détecté. Vérifiez le port 3001."
                );

            } catch (Exception e) {

                showMessage(
                        "Détection impossible : " + e.getMessage()
                );

            } finally {

                scanPool.shutdownNow();
            }
        });
    }

    // ==================================================
    // PARAMÈTRES
    // ==================================================

    private void showSettings() {

        createScreen("PARAMÈTRES");

        content.addView(text(
                "1. CONNEXION AU SERVEUR",
                18
        ));

        EditText server = field("192.168.1.10:3001");

        server.setText(serverUrl);

        content.addView(text("Adresse IP du serveur", 14));
        content.addView(server);

        content.addView(button(
                "DÉTECTION AUTOMATIQUE WI-FI",
                () -> detectServer(server)
        ));

        content.addView(button(
                "ENREGISTRER L'ADRESSE",
                () -> {

                    try {

                        serverUrl = normalizeServerUrl(
                                server.getText().toString()
                        );

                        prefs.edit()
                                .putString("server", serverUrl)
                                .apply();

                        server.setText(serverUrl);

                        showMessage(
                                "Adresse enregistrée : " + serverUrl
                        );

                    } catch (Exception e) {

                        showMessage(e.getMessage());
                    }
                }
        ));

        content.addView(button(
                "TESTER LA CONNEXION",
                () -> {

                    try {

                        serverUrl = normalizeServerUrl(
                                server.getText().toString()
                        );

                        testConnection();

                    } catch (Exception e) {

                        showMessage(e.getMessage());
                    }
                }
        ));

        addSpace();

        content.addView(text(
                "2. IMPORTATION BASE DE DONNÉES",
                18
        ));

        content.addView(text(
                "Sélectionnez un fichier JSON contenant " +
                        "les informations des véhicules.",
                13
        ));

        jsonFileLabel = text(selectedJsonName, 14);

        jsonFileLabel.setBackgroundColor(
                Color.parseColor(PANEL)
        );

        content.addView(jsonFileLabel);

        content.addView(button(
                "CHOISIR UN FICHIER JSON",
                this::chooseJsonFile
        ));

        content.addView(button(
                "IMPORTER LA BASE JSON",
                this::showImportOptions
        ));

        addSpace();

        content.addView(text(
                "3. LOGO DE L'APPLICATION",
                18
        ));

        content.addView(text(
                "Importer le logo de votre entreprise. " +
                        "Il sera conservé sur ce téléphone.",
                13
        ));

        content.addView(button(
                "IMPORTER UN LOGO",
                this::chooseLogo
        ));

        content.addView(button(
                "SUPPRIMER LE LOGO",
                this::deleteLogo
        ));

        addSpace();

        content.addView(text(
                "4. SESSION",
                18
        ));

        content.addView(button(
                "RETOUR",
                () -> {

                    if (!token.isEmpty()) {

                        showDashboard();
                        loadVehicles();

                    } else {

                        showLogin();
                    }
                }
        ));
    }

    // ==================================================
    // SÉLECTION FICHIER JSON
    // ==================================================

    private void chooseJsonFile() {

        Intent intent = new Intent(
                Intent.ACTION_OPEN_DOCUMENT
        );

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        intent.setType("application/json");

        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        );

        try {

            startActivityForResult(
                    intent,
                    REQUEST_IMPORT_JSON
            );

        } catch (Exception e) {

            showMessage(
                    "Impossible d'ouvrir le gestionnaire de fichiers."
            );
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == REQUEST_IMPORT_LOGO) {

            if (resultCode == RESULT_OK &&
                    data != null &&
                    data.getData() != null) {

                saveLogo(data.getData());
            }

            return;
        }

        if (requestCode != REQUEST_IMPORT_JSON) return;

        if (resultCode != RESULT_OK ||
                data == null ||
                data.getData() == null) {

            showMessage("Aucun fichier sélectionné.");
            return;
        }

        Uri uri = data.getData();

        String fileName = getFileName(uri);

        if (!fileName.toLowerCase().endsWith(".json")) {

            showMessage(
                    "Format invalide. Sélectionnez un fichier .json."
            );

            return;
        }

        selectedJsonUri = uri;
        selectedJsonName = fileName;

        if (jsonFileLabel != null) {

            jsonFileLabel.setText(
                    "Fichier sélectionné :\n" + fileName
            );
        }

        showMessage("Fichier sélectionné : " + fileName);

        notifyUser("Fichier JSON sélectionné : " + fileName);
    }

    private String getFileName(Uri uri) {

        String result = "fichier.json";

        try (
                Cursor cursor = getContentResolver()
                        .query(uri, null, null, null, null)
        ) {

            if (cursor != null && cursor.moveToFirst()) {

                int index = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                );

                if (index >= 0) {

                    String name = cursor.getString(index);

                    if (name != null && !name.isEmpty()) {
                        result = name;
                    }
                }
            }

        } catch (Exception ignored) {
        }

        return result;
    }

    // ==================================================
    // LECTURE JSON
    // ==================================================

    private JSONArray readJsonVehicles(Uri uri) throws Exception {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        try (
                InputStream input =
                        getContentResolver().openInputStream(uri)
        ) {

            if (input == null) {

                throw new Exception(
                        "Impossible de lire le fichier."
                );
            }

            byte[] buffer = new byte[8192];

            int count;
            int total = 0;

            while ((count = input.read(buffer)) != -1) {

                total += count;

                if (total > MAX_JSON_SIZE) {

                    throw new Exception(
                            "Fichier trop volumineux (maximum 20 Mo)."
                    );
                }

                output.write(buffer, 0, count);
            }
        }

        String json = new String(
                output.toByteArray(),
                StandardCharsets.UTF_8
        ).trim();

        if (json.startsWith("\uFEFF")) {
            json = json.substring(1);
        }

        if (json.startsWith("[")) {
            return new JSONArray(json);
        }

        JSONObject object = new JSONObject(json);

        JSONArray array = object.optJSONArray("vehicles");

        if (array == null) {

            throw new Exception(
                    "Le fichier ne contient pas de liste 'vehicles'."
            );
        }

        return array;
    }

    // ==================================================
    // OPTIONS IMPORT JSON
    // ==================================================

    private void showImportOptions() {

        if (selectedJsonUri == null) {

            showMessage(
                    "Aucun fichier sélectionné. Choisissez un fichier JSON."
            );

            notifyUser(
                    "Veuillez choisir un fichier JSON avant l'importation."
            );

            return;
        }

        String[] options = {
                "Ajouter les véhicules sans doublons",
                "Remplacer toute la base de véhicules"
        };

        new AlertDialog.Builder(this)
                .setTitle("Mode d'importation")
                .setItems(options, (dialog, which) -> {

                    if (which == 0) {

                        importJson("add");

                    } else if (which == 1) {

                        if (!role.equalsIgnoreCase("Administrateur")) {

                            showMessage(
                                    "Seul l'administrateur peut remplacer la base."
                            );

                            return;
                        }

                        new AlertDialog.Builder(this)
                                .setTitle("Attention")
                                .setMessage(
                                        "Cette opération remplace toute " +
                                                "la base de véhicules du serveur. " +
                                                "Vérifiez la sauvegarde avant de continuer."
                                )
                                .setNegativeButton("ANNULER", null)
                                .setPositiveButton(
                                        "CONFIRMER",
                                        (d, w) -> importJson("replace")
                                )
                                .show();
                    }
                })
                .setNegativeButton("ANNULER", null)
                .show();
    }

    private void importJson(String mode) {

        if (selectedJsonUri == null) {

            showMessage("Aucun fichier JSON sélectionné.");
            return;
        }

        Uri uri = selectedJsonUri;

        showMessage("Lecture du fichier " + selectedJsonName + "...");

        executor.execute(() -> {

            try {

                JSONArray imported = readJsonVehicles(uri);

                if (imported.length() == 0) {

                    throw new Exception(
                            "Le fichier ne contient aucun véhicule."
                    );
                }

                if (imported.length() > 5000) {

                    throw new Exception(
                            "Maximum autorisé : 5000 véhicules."
                    );
                }

                JSONObject body = new JSONObject();

                body.put("mode", mode);
                body.put("vehicles", imported);

                showMessage(
                        "Envoi de " + imported.length() +
                                " véhicule(s) au serveur..."
                );

                JSONObject result = request(
                        "POST",
                        "/api/fleet/vehicles/import",
                        body,
                        true
                );

                int importedCount =
                        result.optInt("imported", imported.length());

                int skippedCount =
                        result.optInt("skipped", 0);

                showMessage(
                        result.optString(
                                "message",
                                "Importation terminée."
                        ) +
                                "\nImportés : " + importedCount +
                                "\nIgnorés : " + skippedCount
                );

                notifyUser(
                        "Importation terminée : " +
                                importedCount + " véhicule(s)."
                );

                runOnUiThread(() -> {

                    showDashboard();
                    loadVehicles();

                });

            } catch (Exception e) {

                showMessage(
                        "ÉCHEC IMPORT JSON : " + e.getMessage()
                );

                notifyUser(
                        "Importation impossible : " + e.getMessage()
                );
            }
        });
    }

    // ==================================================
    // TABLEAU DE BORD
    // ==================================================

    private void showDashboard() {

        createScreen("GESTION DE PARC AUTOMOBILE");

        content.addView(text(
                "Utilisateur : " + username +
                        "\nProfil : " + role +
                        "\nServeur : " + serverUrl,
                13
        ));

        content.addView(button(
                "TABLEAU DE BORD",
                this::loadVehicles
        ));

        content.addView(button(
                "LISTE DES VÉHICULES",
                this::loadVehicles
        ));

        if (!role.equalsIgnoreCase("Consultation")) {

            content.addView(button(
                    "AJOUTER UN VÉHICULE",
                    () -> vehicleDialog(null)
            ));
        }

        content.addView(button(
                "PARAMÈTRES",
                this::showSettings
        ));

        content.addView(button(
                "DÉCONNEXION",
                this::logout
        ));

        addSpace();

        content.addView(text(
                "PARC AUTOMOBILE",
                19
        ));

        content.addView(button(
                "ACTUALISER",
                this::loadVehicles
        ));

        content.addView(text(
                "Véhicules enregistrés :",
                14
        ));

        content.addView(vehicleListView());
    }

    private LinearLayout vehicleListView() {

        LinearLayout list = new LinearLayout(this);

        list.setOrientation(LinearLayout.VERTICAL);
        list.setTag("vehicle_list");

        return list;
    }

    private LinearLayout findVehicleList() {

        if (content == null) return null;

        for (int i = 0; i < content.getChildCount(); i++) {

            View view = content.getChildAt(i);

            if (view instanceof LinearLayout &&
                    "vehicle_list".equals(view.getTag())) {

                return (LinearLayout) view;
            }
        }

        return null;
    }

    private void refreshVehicleList() {

        LinearLayout list = findVehicleList();

        if (list == null) return;

        list.removeAllViews();

        if (vehicles.length() == 0) {

            list.addView(text(
                    "Aucun véhicule enregistré.",
                    14
            ));

            return;
        }

        for (int i = 0; i < vehicles.length(); i++) {

            JSONObject vehicle = vehicles.optJSONObject(i);

            if (vehicle == null) continue;

            String id = vehicle.optString("id", "");

            String registration =
                    vehicle.optString("registration", "");

            String brand = vehicle.optString("brand", "");
            String model = vehicle.optString("model", "");
            String status = vehicle.optString("status", "");

            String assignment =
                    vehicle.optString("assignment", "");

            String mileage =
                    vehicle.optString("mileage", "");

            LinearLayout card = new LinearLayout(this);

            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(10, 8, 10, 8);

            card.setBackgroundColor(
                    Color.parseColor(PANEL)
            );

            LinearLayout.LayoutParams cp =
                    new LinearLayout.LayoutParams(-1, -2);

            cp.setMargins(0, 5, 0, 5);

            card.setLayoutParams(cp);

            card.addView(text(
                    registration + " - " + brand + " " + model,
                    17
            ));

            card.addView(text(
                    "Statut : " + status +
                            "\nAffectation : " + assignment +
                            "\nKilométrage : " + mileage + " km",
                    13
            ));

            if (!role.equalsIgnoreCase("Consultation")) {

                card.addView(button(
                        "MODIFIER",
                        () -> vehicleDialog(vehicle)
                ));
            }

            if (role.equalsIgnoreCase("Administrateur")) {

                card.addView(button(
                        "SUPPRIMER",
                        () -> confirmDelete(id, registration)
                ));
            }

            list.addView(card);
        }
    }

    // ==================================================
    // CHARGEMENT VÉHICULES
    // ==================================================

    private void loadVehicles() {

        showMessage("Chargement des véhicules...");

        executor.execute(() -> {

            try {

                JSONObject result = request(
                        "GET",
                        "/api/fleet/vehicles",
                        null,
                        true
                );

                JSONArray data =
                        result.optJSONArray("vehicles");

                vehicles = data == null
                        ? new JSONArray()
                        : data;

                runOnUiThread(() -> {

                    if (content == null) return;

                    if (findVehicleList() == null) {

                        showDashboard();
                    }

                    refreshVehicleList();

                    showMessage(
                            vehicles.length() +
                                    " véhicule(s) chargé(s)."
                    );
                });

            } catch (Exception e) {

                showMessage(
                        "Erreur chargement : " + e.getMessage()
                );
            }
        });
    }

    // ==================================================
    // AJOUT ET MODIFICATION
    // ==================================================

    private void vehicleDialog(JSONObject vehicle) {

        boolean editing = vehicle != null;

        LinearLayout form = new LinearLayout(this);

        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(12, 5, 12, 5);

        EditText registration = field("Immatriculation");
        EditText brand = field("Marque");
        EditText model = field("Modèle");
        EditText type = field("Type de véhicule");
        EditText year = field("Année");
        EditText mileage = field("Kilométrage");
        EditText fuel = field("Carburant");
        EditText status = field("Statut");
        EditText assignment = field("Affectation");

        year.setInputType(InputType.TYPE_CLASS_NUMBER);
        mileage.setInputType(
                InputType.TYPE_CLASS_NUMBER |
                        InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        form.addView(registration);
        form.addView(brand);
        form.addView(model);
        form.addView(type);
        form.addView(year);
        form.addView(mileage);
        form.addView(fuel);
        form.addView(status);
        form.addView(assignment);

        if (editing) {

            registration.setText(
                    vehicle.optString("registration")
            );

            brand.setText(vehicle.optString("brand"));
            model.setText(vehicle.optString("model"));
            type.setText(vehicle.optString("type"));
            year.setText(vehicle.optString("year"));
            mileage.setText(vehicle.optString("mileage"));
            fuel.setText(vehicle.optString("fuel"));
            status.setText(vehicle.optString("status"));

            assignment.setText(
                    vehicle.optString("assignment")
            );

        } else {

            status.setText("Disponible");
        }

        ScrollView scroll = new ScrollView(this);

        scroll.addView(form);

        new AlertDialog.Builder(this)
                .setTitle(
                        editing
                                ? "Modifier véhicule"
                                : "Nouveau véhicule"
                )
                .setView(scroll)
                .setNegativeButton("ANNULER", null)
                .setPositiveButton(
                        "ENREGISTRER",
                        (dialog, which) -> {

                            try {

                                if (registration.getText().toString().trim().isEmpty() ||
                                        brand.getText().toString().trim().isEmpty() ||
                                        model.getText().toString().trim().isEmpty() ||
                                        year.getText().toString().trim().isEmpty() ||
                                        mileage.getText().toString().trim().isEmpty()) {

                                    throw new Exception(
                                            "Remplissez les champs obligatoires."
                                    );
                                }

                                JSONObject data = new JSONObject();

                                data.put(
                                        "registration",
                                        registration.getText().toString().trim()
                                );

                                data.put(
                                        "brand",
                                        brand.getText().toString().trim()
                                );

                                data.put(
                                        "model",
                                        model.getText().toString().trim()
                                );

                                data.put(
                                        "type",
                                        type.getText().toString().trim()
                                );

                                data.put(
                                        "year",
                                        Integer.parseInt(
                                                year.getText().toString().trim()
                                        )
                                );

                                data.put(
                                        "mileage",
                                        Double.parseDouble(
                                                mileage.getText().toString().trim()
                                        )
                                );

                                data.put(
                                        "fuel",
                                        fuel.getText().toString().trim()
                                );

                                data.put(
                                        "status",
                                        status.getText().toString().trim()
                                );

                                data.put(
                                        "assignment",
                                        assignment.getText().toString().trim()
                                );

                                saveVehicle(
                                        data,
                                        editing
                                                ? vehicle.optString("id")
                                                : null
                                );

                            } catch (Exception e) {

                                showMessage(
                                        "Erreur : " + e.getMessage()
                                );
                            }
                        }
                )
                .show();
    }

    private void saveVehicle(JSONObject data, String id) {

        showMessage("Enregistrement...");

        executor.execute(() -> {

            try {

                String path = "/api/fleet/vehicles";

                if (id != null && !id.isEmpty()) {
                    path += "/" + id;
                }

                JSONObject result = request(
                        id == null ? "POST" : "PUT",
                        path,
                        data,
                        true
                );

                showMessage(
                        result.optString(
                                "message",
                                "Véhicule enregistré."
                        )
                );

                loadVehicles();

            } catch (Exception e) {

                showMessage(
                        "Erreur : " + e.getMessage()
                );
            }
        });
    }

    // ==================================================
    // SUPPRESSION
    // ==================================================

    private void confirmDelete(
            String id,
            String registration
    ) {

        new AlertDialog.Builder(this)
                .setTitle("Suppression")
                .setMessage(
                        "Supprimer le véhicule " +
                                registration + " ?"
                )
                .setNegativeButton("ANNULER", null)
                .setPositiveButton(
                        "SUPPRIMER",
                        (dialog, which) -> deleteVehicle(id)
                )
                .show();
    }

    private void deleteVehicle(String id) {

        showMessage("Suppression en cours...");

        executor.execute(() -> {

            try {

                JSONObject result = request(
                        "DELETE",
                        "/api/fleet/vehicles/" + id,
                        null,
                        true
                );

                showMessage(
                        result.optString(
                                "message",
                                "Véhicule supprimé."
                        )
                );

                loadVehicles();

            } catch (Exception e) {

                showMessage(
                        "Erreur suppression : " + e.getMessage()
                );
            }
        });
    }

    // ==================================================
    // DÉCONNEXION
    // ==================================================

    private void logout() {

        executor.execute(() -> {

            try {

                request(
                        "POST",
                        "/api/auth/logout",
                        new JSONObject(),
                        true
                );

            } catch (Exception ignored) {
            }

            token = "";
            username = "";
            role = "";

            prefs.edit()
                    .remove("token")
                    .remove("username")
                    .remove("role")
                    .apply();

            runOnUiThread(this::showLogin);
        });
    }

    // ==================================================
    // FERMETURE
    // ==================================================

    @Override
    protected void onDestroy() {

        super.onDestroy();

        // Ne pas arrêter l'executor ici :
        // une recréation de l'activité ne doit pas
        // interrompre brutalement les opérations réseau.
    }
}
